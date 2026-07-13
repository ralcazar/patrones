package com.ejemplo.tramitacion.aplicacion.saga.servicio;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoProcesarResultadoPrincipal;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoColaTareas;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoEventos;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso1;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso2;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso3;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso4;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso5;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso6;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso7;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoPaso8;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.RepositorioSagaPrincipal;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.RepositorioSagasSucesoras;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;
import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.ContextoArranque;
import com.ejemplo.tramitacion.dominio.saga.paso1.DatoNegocio3;
import com.ejemplo.tramitacion.dominio.saga.general.Decision;
import com.ejemplo.tramitacion.dominio.saga.paso7.DatoNegocio2;
import com.ejemplo.tramitacion.dominio.saga.general.ExcepcionServicioExterno;
import com.ejemplo.tramitacion.dominio.saga.general.DatoNegocio1Id;
import com.ejemplo.tramitacion.dominio.saga.general.MensajeId;
import com.ejemplo.tramitacion.dominio.saga.general.MotivoFallo;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaAsincrona;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.SagaPrincipal;
import com.ejemplo.tramitacion.dominio.saga.general.SagaSecuencial;
import com.ejemplo.tramitacion.dominio.saga.general.SagaSimple;
import com.ejemplo.tramitacion.dominio.saga.general.SagaSucesora;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;
import com.ejemplo.tramitacion.dominio.saga.general.UsuarioSoporte;

/**
 * Orquestador de la saga principal, integrado con el GestorOrdenes.
 *
 * Regla de oro de la fusión: dentro de la transacción solo BBDD (estado de la
 * saga + tareas encoladas + sagas sucesoras creadas); fuera de ella solo I/O
 * externo (REST, tickets, eventos). Así:
 *  - COMPLETADA + creación de las sucesoras + tareas ArrancarSaga: un solo commit.
 *  - ProgramarReintento se convierte en una Orden con ejecutarDesde futuro,
 *    encolada en la misma transacción que deja el paso ESPERANDO_REINTENTO.
 *  - Si el proceso muere en cualquier punto, el lease del GestorOrdenes
 *    reentrega la tarea y continuar() reanuda desde el último paso confirmado.
 */
public class ServicioSagaPrincipal implements CasoUsoProcesarResultadoPrincipal {

    private final RepositorioSagaPrincipal repo;
    private final RepositorioSagasSucesoras repoSucesoras;
    private final UnidadDeTrabajo tx;
    private final PuertoMensajesProcesados dedup;
    private final PuertoColaTareas cola;
    private final PuertoTicketsSoporte tickets;
    private final PuertoEventos eventos;
    private final PuertoPaso1 puertoPaso1;
    private final PuertoPaso2 puertoPaso2;
    private final PuertoPaso3 puertoPaso3;
    private final PuertoPaso4 puertoPaso4;
    private final PuertoPaso5 puertoPaso5;
    private final PuertoPaso6 puertoPaso6;
    private final PuertoPaso7 puertoPaso7;
    private final PuertoPaso8 puertoPaso8;

    public ServicioSagaPrincipal(RepositorioSagaPrincipal repo, RepositorioSagasSucesoras repoSucesoras,
            UnidadDeTrabajo tx, PuertoMensajesProcesados dedup, PuertoColaTareas cola,
            PuertoTicketsSoporte tickets, PuertoEventos eventos,
            PuertoPaso1 puertoPaso1, PuertoPaso2 puertoPaso2, PuertoPaso3 puertoPaso3,
            PuertoPaso4 puertoPaso4, PuertoPaso5 puertoPaso5, PuertoPaso6 puertoPaso6,
            PuertoPaso7 puertoPaso7, PuertoPaso8 puertoPaso8) {
        this.repo = repo;
        this.repoSucesoras = repoSucesoras;
        this.tx = tx;
        this.dedup = dedup;
        this.cola = cola;
        this.tickets = tickets;
        this.eventos = eventos;
        this.puertoPaso1 = puertoPaso1;
        this.puertoPaso2 = puertoPaso2;
        this.puertoPaso3 = puertoPaso3;
        this.puertoPaso4 = puertoPaso4;
        this.puertoPaso5 = puertoPaso5;
        this.puertoPaso6 = puertoPaso6;
        this.puertoPaso7 = puertoPaso7;
        this.puertoPaso8 = puertoPaso8;
    }

    // ------------------------------------------------------------------
    // Entrada desde el ManejadorTareasSaga (tareas del GestorOrdenes)
    // ------------------------------------------------------------------

    /**
     * Maneja la tarea IniciarTramitacion. Idempotente ante reentregas del lease:
     * si la saga ya existe (el proceso murió a mitad de la cadena), continúa
     * desde el último paso confirmado en lugar de crearla de nuevo.
     */
    public void iniciarOContinuar(SagaId id, DatoNegocio1Id datoNegocio1Id,
                                  DatoNegocio3 datos, DatoNegocio2 datoNegocio2) {
        var decisiones = ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
            if (!repo.existe(id)) {
                var saga = SagaPrincipal.crear(id, datoNegocio1Id, datos, datoNegocio2);
                var ds = saga.iniciar();
                encolarDerivadas(id, ds);
                repo.crear(saga);
                return ds;
            }
            var saga = repo.cargar(id);
            var ds = saga.continuar(); // reanuda el paso SOLICITADO que quedó colgado
            encolarDerivadas(id, ds);
            repo.guardar(saga);
            return ds;
        }));
        despachar(id, decisiones);
    }

    @Override
    public void pasoCompletado(SagaId id, Paso paso, ResultadoPaso resultado, MensajeId msgId) {
        procesar(msgId, id, saga -> saga.completar(paso, resultado));
    }

    @Override
    public void pasoFallido(SagaId id, Paso paso, MotivoFallo motivo, MensajeId msgId) {
        procesar(msgId, id, saga -> saga.fallar(paso, motivo));
    }

    /** Maneja la tarea Reintentar cuando su ejecutarDesde vence. */
    public void reintentar(SagaId id, Paso paso) {
        procesar(MensajeId.interno(), id, saga -> saga.reintentar(paso));
    }

    // --- intervenciones de soporte (las enruta ServicioSoporteSagas) ---

    public void cancelar(SagaId id, UsuarioSoporte quien, String motivo) {
        procesar(MensajeId.interno(), id, saga -> saga.cancelarPorSoporte(quien, motivo));
    }

    public void reanudarPaso(SagaId id, Paso paso, UsuarioSoporte quien) {
        procesar(MensajeId.interno(), id, saga -> saga.reanudarPorSoporte(paso, quien));
    }

    public void marcarPasoOk(SagaId id, Paso paso, UsuarioSoporte quien,
                             String justificacion, ResultadoPaso datos) {
        procesar(MensajeId.interno(), id, saga -> saga.marcarOkManual(paso, quien, justificacion, datos));
    }

    // ------------------------------------------------------------------
    // Núcleo: transacción (estado + tareas) y despacho (I/O externo)
    // ------------------------------------------------------------------

    private void procesar(MensajeId msgId, SagaId id, Function<SagaPrincipal, List<Decision>> accion) {
        if (msgId.externo() && dedup.yaProcesado(msgId)) {
            return;
        }
        var decisiones = ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
            if (msgId.externo()) {
                dedup.registrar(msgId);
            }
            var saga = repo.cargar(id);
            var ds = accion.apply(saga);
            encolarDerivadas(id, ds); // sagas sucesoras + tareas: MISMO commit que el estado
            repo.guardar(saga);
            return ds;
        }));
        despachar(id, decisiones);
    }

    /** Parte transaccional de las decisiones: crear las sagas sucesoras y encolar tareas. */
    private void encolarDerivadas(SagaId id, List<Decision> decisiones) {
        for (var d : decisiones) {
            switch (d) {
                case Decision.ArrancarSaga(var contexto) -> {
                    SagaSucesora nueva = switch (contexto) {
                        case ContextoArranque.ContextoAsincrona c  -> SagaAsincrona.crear(SagaId.nuevo(), c);
                        case ContextoArranque.ContextoSecuencial c -> SagaSecuencial.crear(SagaId.nuevo(), c);
                        case ContextoArranque.ContextoSimple c     -> SagaSimple.crear(SagaId.nuevo(), c);
                    };
                    repoSucesoras.crear(nueva);
                    cola.encolar(new TareaSaga.ArrancarSaga(nueva.id()));
                }
                case Decision.ProgramarReintento(var paso, var espera, var intentoNum) ->
                        cola.encolar(new TareaSaga.Reintentar(TipoSaga.PRINCIPAL, id, paso, intentoNum),
                                Instant.now().plus(espera));
                default -> { /* el resto es I/O externo: va en despachar() */ }
            }
        }
    }

    /** Parte externa de las decisiones: REST, tickets, eventos. Nunca dentro de la tx. */
    private void despachar(SagaId id, List<Decision> decisiones) {
        for (var d : decisiones) {
            switch (d) {
                case Decision.Ejecutar(var paso, var cmd) -> ejecutar(id, paso, cmd);
                case Decision.AbrirTicketSoporte(var sagaId, var tipo, var paso, var motivo,
                                                 var intentos, var cancelable) ->
                        tickets.abrir(sagaId, tipo, paso, motivo, intentos, cancelable);
                case Decision.Compensar(var paso, var cmd) -> compensar(id, paso, cmd);
                case Decision.PublicarEvento(var evento) -> eventos.publicar(evento);
                case Decision.ProgramarReintento ignorada -> { /* ya encolada en la tx */ }
                case Decision.ArrancarSaga ignorada -> { /* sagas creadas y encoladas en la tx */ }
            }
        }
    }

    /**
     * Ejecuta un paso síncrono y reentra con el resultado o el fallo.
     * La cadena PASO1->...->PASO8 corre entera dentro del procesar() de UNA
     * Orden, con checkpoint en BBDD por paso. IMPORTANTE: el lease del
     * GestorOrdenes debe superar la duración del peor caso de la cadena
     * (ver application.yml).
     */
    private void ejecutar(SagaId id, Paso paso, ComandoPaso cmd) {
        try {
            switch (cmd) {
                case ComandoPaso.EjecutarPaso1 c ->
                        pasoCompletado(id, Paso.PASO1, puertoPaso1.ejecutar(c), MensajeId.interno());
                case ComandoPaso.EjecutarPaso2 c ->
                        pasoCompletado(id, Paso.PASO2, puertoPaso2.ejecutar(c), MensajeId.interno());
                case ComandoPaso.EjecutarPaso3 c ->
                        pasoCompletado(id, Paso.PASO3, puertoPaso3.ejecutar(c), MensajeId.interno());
                case ComandoPaso.EjecutarPaso4 c ->
                        pasoCompletado(id, Paso.PASO4, puertoPaso4.ejecutar(c), MensajeId.interno());
                case ComandoPaso.EjecutarPaso5 c ->
                        pasoCompletado(id, Paso.PASO5, puertoPaso5.ejecutar(c), MensajeId.interno());
                case ComandoPaso.EjecutarPaso6 c ->
                        pasoCompletado(id, Paso.PASO6, puertoPaso6.ejecutar(c), MensajeId.interno());
                case ComandoPaso.EjecutarPaso7 c ->
                        pasoCompletado(id, Paso.PASO7, puertoPaso7.ejecutar(c), MensajeId.interno());
                case ComandoPaso.EjecutarPaso8 c ->
                        pasoCompletado(id, Paso.PASO8, puertoPaso8.ejecutar(c), MensajeId.interno());
                default -> throw new IllegalStateException(
                        "Comando no ejecutable por la saga principal: " + cmd);
            }
        } catch (ExcepcionServicioExterno e) {
            pasoFallido(id, paso, e.motivo(), MensajeId.interno());
        }
    }

    private void compensar(SagaId id, Paso paso, ComandoPaso cmd) {
        try {
            switch (cmd) {
                case ComandoPaso.CompensarPaso2 c -> puertoPaso2.compensar(c);
                case ComandoPaso.CompensarPaso1 c -> puertoPaso1.compensar(c);
                default -> throw new IllegalStateException("Comando de compensación desconocido: " + cmd);
            }
            ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
                var saga = repo.cargar(id);
                saga.compensacionCompletada(paso);
                repo.guardar(saga);
                return null;
            }));
        } catch (ExcepcionServicioExterno e) {
            var decisiones = ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
                var saga = repo.cargar(id);
                var ds = saga.compensacionFallida(paso, e.motivo());
                repo.guardar(saga);
                return ds;
            }));
            despachar(id, decisiones);
        }
    }
}
