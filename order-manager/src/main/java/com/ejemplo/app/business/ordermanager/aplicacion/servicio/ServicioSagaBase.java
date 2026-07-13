package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoProcesarResultadoPaso;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Decision;
import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ResultadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Saga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Plomería común de los 4 orquestadores de saga, integrados con el GestorOrdenes.
 *
 * Regla de oro de la fusión: dentro de la transacción solo BBDD (estado de la
 * saga + tareas encoladas); fuera de ella solo I/O externo (REST, tickets).
 * Así:
 *  - ProgramarReintento se convierte en una Orden con ejecutarDesde futuro,
 *    encolada en la misma transacción que deja el paso ESPERANDO_REINTENTO.
 *  - Si el proceso muere en cualquier punto, el lease del GestorOrdenes
 *    reentrega la tarea y continuar() reanuda desde el último paso confirmado.
 */
public abstract class ServicioSagaBase<P extends Enum<P> & PasoSaga, S extends Saga<P>>
        implements CasoUsoProcesarResultadoPaso<P> {

    protected final UnidadDeTrabajo tx;
    protected final PuertoMensajesProcesados dedup;
    protected final PuertoColaTareas cola;
    protected final PuertoTicketsSoporte tickets;

    protected ServicioSagaBase(UnidadDeTrabajo tx, PuertoMensajesProcesados dedup,
                               PuertoColaTareas cola, PuertoTicketsSoporte tickets) {
        this.tx = tx;
        this.dedup = dedup;
        this.cola = cola;
        this.tickets = tickets;
    }

    // --- lo que cada saga concreta aporta ---

    protected abstract S cargar(SagaId id);

    protected abstract void guardar(S saga);

    /** I/O externo del paso: llamada REST (y reentrada con el resultado si es síncrono). */
    protected abstract void ejecutar(SagaId id, P paso, ComandoPaso cmd);

    /** Hook transaccional al solicitar un paso (p. ej. encolar el timeout de la secundaria 2). */
    protected void alSolicitarEjecucion(S saga, Decision.Ejecutar<P> decision) {
        // por defecto nada
    }

    /** Hook transaccional para ArrancarSaga; solo la principal arranca otras sagas. */
    protected void arrancarSaga(S saga, Decision.ArrancarSaga<P> decision) {
        throw new IllegalStateException("La saga " + saga.tipo() + " no arranca otras sagas");
    }

    /** I/O externo de una compensación; solo la principal compensa. */
    protected void compensar(SagaId id, P paso, ComandoPaso cmd) {
        throw new IllegalStateException("Esta saga no compensa pasos");
    }

    // --- entrada desde el ManejadorTareasSaga ---

    /** Maneja ArrancarSaga/reanudación. Idempotente: cubre arranque nuevo y reanudación tras caída. */
    public void continuar(SagaId id) {
        procesar(MensajeId.interno(), id, Saga::continuar);
    }

    @Override
    public void pasoCompletado(SagaId id, P paso, ResultadoPaso resultado, MensajeId msgId) {
        procesar(msgId, id, saga -> saga.completar(paso, resultado));
    }

    @Override
    public void pasoFallido(SagaId id, P paso, MotivoFallo motivo, MensajeId msgId) {
        procesar(msgId, id, saga -> saga.fallar(paso, motivo));
    }

    /** Maneja Reintentar cuando su ejecutarDesde vence. */
    public void reintentar(SagaId id, P paso) {
        procesar(MensajeId.interno(), id, saga -> saga.reintentar(paso));
    }

    // --- intervenciones de soporte (las enruta ServicioSoporteSagas) ---

    public void reanudarPaso(SagaId id, P paso, UsuarioSoporte quien) {
        procesar(MensajeId.interno(), id, saga -> saga.reanudarPorSoporte(paso, quien));
    }

    public void marcarPasoOk(SagaId id, P paso, UsuarioSoporte quien,
                             String justificacion, ResultadoPaso datos) {
        procesar(MensajeId.interno(), id, saga -> saga.marcarOkManual(paso, quien, justificacion, datos));
    }

    // --- núcleo: transacción (estado + tareas) y despacho (I/O externo) ---

    protected final void procesar(MensajeId msgId, SagaId id, Function<S, List<Decision<P>>> accion) {
        if (msgId.externo() && dedup.yaProcesado(msgId)) {
            return;
        }
        var decisiones = ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
            if (msgId.externo()) {
                dedup.registrar(msgId);
            }
            var saga = cargar(id);
            var ds = accion.apply(saga);
            encolarDerivadas(saga, ds); // tareas (y sagas nuevas): MISMO commit que el estado
            guardar(saga);
            return ds;
        }));
        despachar(id, decisiones);
    }

    /** Parte transaccional de las decisiones: reintentos con backoff, timeouts, sagas arrancadas. */
    private void encolarDerivadas(S saga, List<Decision<P>> decisiones) {
        for (var d : decisiones) {
            switch (d) {
                case Decision.ProgramarReintento<P>(var paso, var espera, var intentoNum) ->
                        cola.encolar(new TareaSaga.Reintentar(saga.tipo(), saga.id(), paso, intentoNum),
                                Instant.now().plus(espera));
                case Decision.Ejecutar<P> e -> alSolicitarEjecucion(saga, e);
                case Decision.ArrancarSaga<P> a -> arrancarSaga(saga, a);
                case Decision.AbrirTicketSoporte<P> ignorada -> { /* I/O externo: va en despachar() */ }
                case Decision.Compensar<P> ignorada -> { /* I/O externo: va en despachar() */ }
            }
        }
    }

    /** Parte externa de las decisiones: REST y tickets. Nunca dentro de la tx. */
    protected final void despachar(SagaId id, List<Decision<P>> decisiones) {
        for (var d : decisiones) {
            switch (d) {
                case Decision.Ejecutar<P>(var paso, var cmd) -> ejecutar(id, paso, cmd);
                case Decision.AbrirTicketSoporte<P>(var sagaId, var tipo, var paso, var motivo,
                                                    var intentos, var cancelable) ->
                        tickets.abrir(sagaId, tipo, paso, motivo, intentos, cancelable);
                case Decision.Compensar<P>(var paso, var cmd) -> compensar(id, paso, cmd);
                case Decision.ProgramarReintento<P> ignorada -> { /* ya encolada en la tx */ }
                case Decision.ArrancarSaga<P> ignorada -> { /* sagas creadas y encoladas en la tx */ }
            }
        }
    }
}
