package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoColaTareas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoMensajesProcesados;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoTicketsSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.aplicacion.tarea.TareaSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ComandoPaso;
import com.ejemplo.app.business.ordermanager.dominio.comun.Decision;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.MensajeId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.ComandoPasoSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.PasoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2Root;

/**
 * Orquestador de la saga secundaria 2: la solicitud es una llamada REST y la
 * respuesta llega a posteriori como evento Kafka (puede tardar), que el
 * consumer traduce a tareas ResultadoSagaSecundaria2Ok/Error.
 *
 * Detalle importante: el TimeoutSagaSecundaria2 (24h) se encola DENTRO de la
 * misma transacción que deja el paso SOLICITADO. Si el proceso muere tras el
 * commit pero antes de la llamada REST, el timeout vence, se convierte en
 * fallo reintentable y el reintento vuelve a llamar: autocurativo. Si la
 * respuesta llegó antes, el guard del agregado convierte el timeout en no-op.
 */
@Service
public class ServicioSagaSecundaria2 extends ServicioSagaBase<PasoSagaSecundaria2, SagaSecundaria2Root> {

    /** La respuesta puede tardar horas: se le da un día antes de darla por perdida. */
    static final Duration TIMEOUT_RESPUESTA = Duration.ofHours(24);

    private final RepositorioSagaSecundaria2 repo;
    private final PuertoSagaSecundaria2 puerto;

    public ServicioSagaSecundaria2(RepositorioSagaSecundaria2 repo, UnidadDeTrabajo tx,
            PuertoMensajesProcesados dedup, PuertoColaTareas cola, PuertoTicketsSoporte tickets,
            PuertoSagaSecundaria2 puerto) {
        super(tx, dedup, cola, tickets);
        this.repo = repo;
        this.puerto = puerto;
    }

    /** Maneja TimeoutSagaSecundaria2: si la respuesta llegó entre medias, el guard del agregado lo ignora. */
    public void timeoutVencido(SagaId id) {
        pasoFallido(id, PasoSagaSecundaria2.SOLICITUD, MotivoFallo.timeout(), MensajeId.interno());
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
