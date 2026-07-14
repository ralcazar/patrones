package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Decision;
import com.ejemplo.app.business.ordermanager.dominio.comun.EstadoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.EstadoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.ComandoPasoSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.PasoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.ResultadoPasoSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2Root;

/**
 * Orquestador de la saga secundaria 2: la solicitud es una llamada REST y la
 * respuesta llega a posteriori como evento Kafka (puede tardar), que el
 * consumer traduce a tareas ResultadoSagaSecundaria2Ok/Error.
 *
 * Detalle importante: el TimeoutSagaSecundaria2 (24h) se encola DENTRO de la
 * misma transacción que deja el paso SOLICITADO. Si el proceso muere tras el
 * commit pero antes de la llamada REST, el timeout vence y la conciliación
 * (SinResultado) abre otra ventana de 24h; la reanudación por lease reemite la
 * solicitud: autocurativo. Si la respuesta llegó antes, el timeout es un no-op.
 *
 * Al vencer el timeout NO se da la respuesta por perdida: se pregunta al
 * servicio REST de conciliación si el resultado ya existe. Si existe se
 * procesa (ok o error) como si hubiera llegado por Kafka; si no, se programa
 * otra ventana de espera de 24h y se seguirá esperando.
 */
@Service
public class ServicioSagaSecundaria2 extends ServicioSagaBase<PasoSagaSecundaria2, SagaSecundaria2Root> {

    /** La respuesta puede tardar horas: ventana de espera entre conciliaciones. */
    static final Duration TIMEOUT_RESPUESTA = Duration.ofHours(24);

    private final RepositorioSagaSecundaria2 repo;
    private final PuertoSagaSecundaria2 puerto;
    private final PuertoConciliacionSecundaria2 conciliacion;

    public ServicioSagaSecundaria2(RepositorioSagaSecundaria2 repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola,
            PuertoSagaSecundaria2 puerto, PuertoConciliacionSecundaria2 conciliacion) {
        super(tx, dedup, cola);
        this.repo = repo;
        this.puerto = puerto;
        this.conciliacion = conciliacion;
    }

    /**
     * Maneja TimeoutSagaSecundaria2: concilia con el servicio destino antes de
     * decidir. La lectura inicial es solo un guard barato (fuera de tx); las
     * escrituras reales pasan por pasoCompletado/pasoFallido, que revalidan el
     * estado dentro de su transacción, así que la carrera con una respuesta
     * Kafka que llegue entre medias es inofensiva (guards + dedup).
     */
    public void timeoutVencido(SagaId id) {
        var saga = repo.cargar(id);
        if (saga.estado() != EstadoSaga.EN_CURSO
                || saga.pasos().get(PasoSagaSecundaria2.SOLICITUD).estado() != EstadoPaso.SOLICITADO) {
            return; // la respuesta llegó (o el paso falló/se intervino) entre medias: timeout obsoleto
        }

        PuertoConciliacionSecundaria2.Resultado resultado;
        try {
            resultado = conciliacion.consultar(id, saga.externalId());
        } catch (ExcepcionServicioExterno e) {
            // No se pudo saber: se sigue esperando, la próxima conciliación decidirá.
            resultado = new PuertoConciliacionSecundaria2.Resultado.SinResultado();
        }

        switch (resultado) {
            case PuertoConciliacionSecundaria2.Resultado.Disponible(var ref) ->
                    pasoCompletado(id, PasoSagaSecundaria2.SOLICITUD,
                            new ResultadoPasoSecundaria2.Respuesta(ref), MensajeId.interno());
            case PuertoConciliacionSecundaria2.Resultado.FalloRegistrado(var motivo) ->
                    pasoFallido(id, PasoSagaSecundaria2.SOLICITUD, motivo, MensajeId.interno());
            case PuertoConciliacionSecundaria2.Resultado.SinResultado() ->
                    tx.enTransaccion(() -> cola.encolar(new TareaSaga.TimeoutSagaSecundaria2(id),
                            Instant.now().plus(TIMEOUT_RESPUESTA)));
        }
    }

    @Override
    protected SagaSecundaria2Root cargar(SagaId id) {
        return repo.cargar(id);
    }

    @Override
    protected void guardar(SagaSecundaria2Root saga) {
        repo.guardar(saga);
    }

    /** El timeout se encola junto al estado SOLICITADO: mismo commit. */
    @Override
    protected void alSolicitarEjecucion(SagaSecundaria2Root saga,
                                        Decision.Ejecutar<PasoSagaSecundaria2> decision) {
        cola.encolar(new TareaSaga.TimeoutSagaSecundaria2(saga.id()),
                Instant.now().plus(TIMEOUT_RESPUESTA));
    }

    /** Solo la llamada REST de solicitud; NO reentra: la respuesta llegará por Kafka. */
    @Override
    protected void ejecutar(SagaId id, PasoSagaSecundaria2 paso, ComandoPaso cmd) {
        try {
            puerto.solicitar(id, (ComandoPasoSecundaria2.Solicitar) cmd);
        } catch (ExcepcionServicioExterno e) {
            pasoFallido(id, paso, e.motivo(), MensajeId.interno());
        }
    }
}
