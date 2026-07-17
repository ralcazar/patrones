package com.ejemplo.app.business.sagas.aplicacion.servicio.sagasecundaria2;

import java.time.Duration;
import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.sagas.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.SenalPaso;
import com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun.ServicioSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.ComandoPasoSecundaria2;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.RefRespuesta;
import com.ejemplo.app.business.sagas.dominio.sagasecundaria2.SagaSecundaria2;

/**
 * Servicio de la saga secundaria 2: la solicitud es una llamada REST y la
 * respuesta llega a posteriori como evento Kafka (puede tardar), vigilada por
 * una ventana de espera de 3h. Al vencer, en vez de dar la respuesta por
 * perdida se concilia con el servicio destino: puede que el evento se
 * perdiera o esté aún en camino.
 *
 * Cuando el consumer de Kafka resuelve la saga directamente (respuestaOk /
 * respuestaError), solo despierta o reprograma la orden: es este servicio,
 * en su siguiente pasada, quien deja el agregado en su estado operativo final
 * (finalizar u otra solicitud), manteniendo un único punto que decide cuándo
 * una orden queda finalizada.
 *
 * El REST (solicitud/conciliación) ocurre fuera de transacción; aplicar el
 * resultado y guardar van en {@code @Transactional}. Como este servicio es un
 * POJO creado por {@code @Bean}, se invoca a través de {@code self} (el
 * propio proxy, inyectado por ConfiguracionSagas) para que la anotación
 * no se ignore por auto-invocación.
 */
@Service
public class ServicioSagaSecundaria2 implements ServicioSaga {

    /** La respuesta puede tardar: ventana de espera entre reconciliaciones. */
    static final Duration VENTANA_ESPERA = Duration.ofHours(3);

    private final RepositorioOrden repo;
    private final PuertoSagaSecundaria2 puerto;
    private final PuertoConciliacionSecundaria2 conciliacion;
    private ServicioSagaSecundaria2 self;

    public ServicioSagaSecundaria2(RepositorioOrden repo, PuertoSagaSecundaria2 puerto,
            PuertoConciliacionSecundaria2 conciliacion) {
        this.repo = repo;
        this.puerto = puerto;
        this.conciliacion = conciliacion;
        this.self = this; // valor por defecto (tests unitarios); ConfiguracionSagas lo sustituye por el proxy
    }

    /** Referencia al proxy transaccional de Spring de este mismo bean (ver ConfiguracionSagas). */
    public void establecerSelf(ServicioSagaSecundaria2 self) {
        this.self = self;
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA2; }

    /**
     * Recibe el agregado ya cargado por el llamante (una única carga por paso,
     * antes del REST): la tx guarda esa misma instancia (con su version) para
     * que una escritura intermedia (takeover, consumer de Kafka, soporte) haga
     * fallar el guardar.
     */
    @Override
    public SenalPaso ejecutarPaso(OrdenRoot orden) {
        var saga = (SagaSecundaria2) orden.saga();
        return switch (saga.estado()) {
            case INICIAL -> solicitar(orden, saga);
            case ESPERANDO_RESPUESTA -> conciliar(orden, saga);
            case TERMINADA -> self.aplicarFinalizacionYaResuelta(orden, saga); // via proxy -> @Transactional
        };
    }

    private SenalPaso solicitar(OrdenRoot orden, SagaSecundaria2 saga) {
        var cmd = (ComandoPasoSecundaria2.Solicitar) saga.comandoActual();
        puerto.solicitar(saga.id(), cmd); // REST fuera de tx
        return self.aplicarSolicitud(orden, saga); // via proxy -> @Transactional
    }

    @Transactional
    public SenalPaso aplicarSolicitud(OrdenRoot orden, SagaSecundaria2 saga) {
        saga.solicitudEnviada();
        orden.aparcar(VENTANA_ESPERA, Instant.now());
        repo.guardar(orden);
        return new SenalPaso.Aparcar(VENTANA_ESPERA);
    }

    private SenalPaso conciliar(OrdenRoot orden, SagaSecundaria2 saga) {
        var resultado = conciliacion.consultar(saga.id(), saga.externalId()); // REST fuera de tx
        return switch (resultado) {
            case PuertoConciliacionSecundaria2.Resultado.Disponible(var ref) ->
                    self.aplicarConciliacionDisponible(orden, saga, ref); // via proxy -> @Transactional
            case PuertoConciliacionSecundaria2.Resultado.SinResultado() ->
                    self.aplicarConciliacionSinResultado(orden, saga); // via proxy -> @Transactional
            case PuertoConciliacionSecundaria2.Resultado.FalloRegistrado(var motivo) ->
                    throw new ExcepcionServicioExterno(motivo, null);
        };
    }

    @Transactional
    public SenalPaso aplicarConciliacionDisponible(OrdenRoot orden, SagaSecundaria2 saga, RefRespuesta ref) {
        saga.respuestaRecibida(ref);
        orden.finalizar(saga.resultadoFinal());
        repo.guardar(orden);
        return new SenalPaso.Finalizada(saga.resultadoFinal());
    }

    @Transactional
    public SenalPaso aplicarConciliacionSinResultado(OrdenRoot orden, SagaSecundaria2 saga) {
        orden.aparcar(VENTANA_ESPERA, Instant.now());
        repo.guardar(orden);
        return new SenalPaso.Aparcar(VENTANA_ESPERA);
    }

    /** El consumer de Kafka ya dejó la saga en TERMINADA; solo falta el cierre operativo de la orden. */
    @Transactional
    public SenalPaso aplicarFinalizacionYaResuelta(OrdenRoot orden, SagaSecundaria2 saga) {
        orden.finalizar(saga.resultadoFinal());
        repo.guardar(orden);
        return new SenalPaso.Finalizada(saga.resultadoFinal());
    }
}
