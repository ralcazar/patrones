package com.ejemplo.tramitacion.aplicacion.saga.servicio;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada.CasoUsoProcesarResultadoSucesora;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoAsincrono;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoColaTareas;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoEventos;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoSecuencial;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoSimple;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.RepositorioSagasSucesoras;
import com.ejemplo.tramitacion.aplicacion.saga.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.tramitacion.aplicacion.saga.tarea.TareaSaga;
import com.ejemplo.tramitacion.dominio.saga.general.ComandoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.Decision;
import com.ejemplo.tramitacion.dominio.saga.general.ExcepcionServicioExterno;
import com.ejemplo.tramitacion.dominio.saga.general.MensajeId;
import com.ejemplo.tramitacion.dominio.saga.general.MotivoFallo;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.ResultadoPaso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.SagaSucesora;
import com.ejemplo.tramitacion.dominio.saga.general.UsuarioSoporte;

/**
 * Orquestador de las 3 sagas sucesoras, integrado con el GestorOrdenes.
 *
 * El paralelismo de las sucesoras lo da el pool del GestorOrdenes: cada
 * ArrancarSaga es una Orden distinta y cae en un trabajador distinto.
 *
 * Detalle importante del paso ASINCRONO: el TimeoutAsincrono se encola DENTRO
 * de la misma transacción que deja el paso SOLICITADO. Si el proceso muere tras
 * el commit pero antes de publicar en Kafka, el timeout vence, se convierte en
 * fallo reintentable y el reintento vuelve a publicar: autocurativo.
 */
public class ServicioSagasSucesoras implements CasoUsoProcesarResultadoSucesora {

    private static final Duration TIMEOUT_ASINCRONO = Duration.ofMinutes(15);

    private final RepositorioSagasSucesoras repo;
    private final UnidadDeTrabajo tx;
    private final PuertoMensajesProcesados dedup;
    private final PuertoColaTareas cola;
    private final PuertoTicketsSoporte tickets;
    private final PuertoEventos eventos;
    private final PuertoAsincrono puertoAsincrono;
    private final PuertoSecuencial puertoSecuencial;
    private final PuertoSimple puertoSimple;

    public ServicioSagasSucesoras(RepositorioSagasSucesoras repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola, PuertoTicketsSoporte tickets,
            PuertoEventos eventos, PuertoAsincrono puertoAsincrono,
            PuertoSecuencial puertoSecuencial, PuertoSimple puertoSimple) {
        this.repo = repo;
        this.tx = tx;
        this.dedup = dedup;
        this.cola = cola;
        this.tickets = tickets;
        this.eventos = eventos;
        this.puertoAsincrono = puertoAsincrono;
        this.puertoSecuencial = puertoSecuencial;
        this.puertoSimple = puertoSimple;
    }

    // ------------------------------------------------------------------
    // Entrada desde el ManejadorTareasSaga
    // ------------------------------------------------------------------

    /** Maneja ArrancarSaga. Idempotente: cubre arranque nuevo y reanudación tras caída. */
    public void continuar(SagaId id) {
        procesar(MensajeId.interno(), id, SagaSucesora::continuar);
    }

    @Override
    public void pasoCompletado(SagaId id, Paso paso, ResultadoPaso resultado, MensajeId msgId) {
        procesar(msgId, id, saga -> saga.completar(paso, resultado));
    }

    @Override
    public void pasoFallido(SagaId id, Paso paso, MotivoFallo motivo, MensajeId msgId) {
        procesar(msgId, id, saga -> saga.fallar(paso, motivo));
    }

    /** Maneja TimeoutAsincrono: si la respuesta llegó entre medias, el guard del agregado lo ignora. */
    public void timeoutVencido(SagaId id, Paso paso) {
        pasoFallido(id, paso, MotivoFallo.timeout(), MensajeId.interno());
    }

    /** Maneja Reintentar cuando su ejecutarDesde vence. */
    public void reintentar(SagaId id, Paso paso) {
        procesar(MensajeId.interno(), id, saga -> saga.reintentar(paso));
    }

    // --- intervenciones de soporte ---

    public void reanudarPaso(SagaId id, Paso paso, UsuarioSoporte quien) {
        procesar(MensajeId.interno(), id, saga -> saga.reanudarPorSoporte(paso, quien));
    }

    public void marcarPasoOk(SagaId id, Paso paso, UsuarioSoporte quien,
                             String justificacion, ResultadoPaso datos) {
        procesar(MensajeId.interno(), id, saga -> saga.marcarOkManual(paso, quien, justificacion, datos));
    }

    // ------------------------------------------------------------------
    // Núcleo
    // ------------------------------------------------------------------

    private void procesar(MensajeId msgId, SagaId id, Function<SagaSucesora, List<Decision>> accion) {
        if (msgId.externo() && dedup.yaProcesado(msgId)) {
            return;
        }
        var decisiones = ReintentoOptimista.ejecutar(() -> tx.enTransaccion(() -> {
            if (msgId.externo()) {
                dedup.registrar(msgId);
            }
            var saga = repo.cargar(id);
            var ds = accion.apply(saga);
            encolarDerivadas(saga, ds);
            repo.guardar(saga);
            return ds;
        }));
        despachar(id, decisiones);
    }

    /** Parte transaccional: reintentos con backoff y timeout del ASINCRONO, junto al estado. */
    private void encolarDerivadas(SagaSucesora saga, List<Decision> decisiones) {
        for (var d : decisiones) {
            switch (d) {
                case Decision.ProgramarReintento(var paso, var espera, var intentoNum) ->
                        cola.encolar(new TareaSaga.Reintentar(saga.tipo(), saga.id(), paso, intentoNum),
                                Instant.now().plus(espera));
                case Decision.Ejecutar(var paso, var cmd) -> {
                    if (cmd instanceof ComandoPaso.EjecutarAsincrono) {
                        cola.encolar(new TareaSaga.TimeoutAsincrono(saga.id()),
                                Instant.now().plus(TIMEOUT_ASINCRONO));
                    }
                }
                default -> { }
            }
        }
    }

    /** Parte externa: REST, publicación en Kafka, tickets, eventos. */
    private void despachar(SagaId id, List<Decision> decisiones) {
        for (var d : decisiones) {
            switch (d) {
                case Decision.Ejecutar(var paso, var cmd) -> ejecutar(id, paso, cmd);
                case Decision.AbrirTicketSoporte(var sagaId, var tipo, var paso, var motivo,
                                                 var intentos, var cancelable) ->
                        tickets.abrir(sagaId, tipo, paso, motivo, intentos, cancelable);
                case Decision.PublicarEvento(var evento) -> eventos.publicar(evento);
                case Decision.ProgramarReintento ignorada -> { /* ya encolada en la tx */ }
                case Decision.Compensar ignorada ->
                        throw new IllegalStateException("Las sagas sucesoras nunca compensan");
                case Decision.ArrancarSaga ignorada ->
                        throw new IllegalStateException("Las sagas sucesoras no arrancan otras sagas");
            }
        }
    }

    private void ejecutar(SagaId id, Paso paso, ComandoPaso cmd) {
        try {
            switch (cmd) {
                // ASINCRONO: solo publica; su timeout ya quedó encolado en la tx.
                case ComandoPaso.EjecutarAsincrono c -> puertoAsincrono.ejecutar(id, c);
                // SECUENCIAL y SIMPLE: síncronos, reentran con el resultado.
                case ComandoPaso.EjecutarSecuencial1 c ->
                        pasoCompletado(id, Paso.SECUENCIAL1, puertoSecuencial.iniciar(c), MensajeId.interno());
                case ComandoPaso.EjecutarSecuencial2 c ->
                        pasoCompletado(id, Paso.SECUENCIAL2, puertoSecuencial.confirmar(c), MensajeId.interno());
                case ComandoPaso.EjecutarSimple c ->
                        pasoCompletado(id, Paso.SIMPLE, puertoSimple.ejecutar(c), MensajeId.interno());
                default -> throw new IllegalStateException(
                        "Comando no ejecutable por una saga sucesora: " + cmd);
            }
        } catch (ExcepcionServicioExterno e) {
            pasoFallido(id, paso, e.motivo(), MensajeId.interno());
        }
    }
}
