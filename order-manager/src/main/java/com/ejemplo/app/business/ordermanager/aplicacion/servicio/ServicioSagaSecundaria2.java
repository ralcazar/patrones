package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Duration;
import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConciliacionSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoSagaSecundaria2;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExcepcionServicioExterno;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.ComandoPasoSecundaria2;
import com.ejemplo.app.business.ordermanager.dominio.sagasecundaria2.SagaSecundaria2Root;

/**
 * Orquestador de la saga secundaria 2: la solicitud es una llamada REST y la
 * respuesta llega a posteriori como evento Kafka (puede tardar), vigilada por
 * una ventana de espera de 3h. Al vencer, en vez de dar la respuesta por
 * perdida se concilia con el servicio destino: puede que el evento se
 * perdiera o esté aún en camino.
 *
 * Cuando el consumer de Kafka resuelve la saga directamente (respuestaOk /
 * respuestaError), solo despierta o reprograma la orden: es este orquestador,
 * en su siguiente pasada, quien deja el agregado en su estado operativo final
 * (finalizar u otra solicitud), manteniendo un único punto que decide cuándo
 * una orden queda finalizada.
 */
@Service
public class ServicioSagaSecundaria2 implements OrquestadorSaga {

    /** La respuesta puede tardar: ventana de espera entre reconciliaciones. */
    static final Duration VENTANA_ESPERA = Duration.ofHours(3);

    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;
    private final PuertoSagaSecundaria2 puerto;
    private final PuertoConciliacionSecundaria2 conciliacion;

    public ServicioSagaSecundaria2(RepositorioOrden repo, UnidadDeTrabajo tx,
            PuertoSagaSecundaria2 puerto, PuertoConciliacionSecundaria2 conciliacion) {
        this.repo = repo;
        this.tx = tx;
        this.puerto = puerto;
        this.conciliacion = conciliacion;
    }

    @Override public TipoSaga tipo() { return TipoSaga.SECUNDARIA2; }

    @Override
    public SenalPaso ejecutarPaso(SagaId id) {
        var saga = (SagaSecundaria2Root) repo.cargar(id).saga();
        return switch (saga.estado()) {
            case INICIAL -> solicitar(id, saga);
            case ESPERANDO_RESPUESTA -> conciliar(id, saga);
            case TERMINADA -> finalizarYa(id);
        };
    }

    private SenalPaso solicitar(SagaId id, SagaSecundaria2Root saga) {
        var cmd = (ComandoPasoSecundaria2.Solicitar) saga.comandoActual();
        puerto.solicitar(id, cmd); // REST fuera de tx

        return tx.enTransaccion(() -> {
            var orden = repo.cargar(id);
            var sagaActual = (SagaSecundaria2Root) orden.saga();
            sagaActual.solicitudEnviada();
            orden.aparcar(VENTANA_ESPERA, Instant.now());
            repo.guardar(orden);
            return new SenalPaso.Aparcar(VENTANA_ESPERA);
        });
    }

    private SenalPaso conciliar(SagaId id, SagaSecundaria2Root saga) {
        var resultado = conciliacion.consultar(id, saga.externalId()); // REST fuera de tx

        return switch (resultado) {
            case PuertoConciliacionSecundaria2.Resultado.Disponible(var ref) -> tx.enTransaccion(() -> {
                var orden = repo.cargar(id);
                var sagaActual = (SagaSecundaria2Root) orden.saga();
                sagaActual.respuestaRecibida(ref);
                orden.finalizar(sagaActual.resultadoFinal());
                repo.guardar(orden);
                return new SenalPaso.Finalizada(sagaActual.resultadoFinal());
            });
            case PuertoConciliacionSecundaria2.Resultado.SinResultado() -> tx.enTransaccion(() -> {
                var orden = repo.cargar(id);
                orden.aparcar(VENTANA_ESPERA, Instant.now());
                repo.guardar(orden);
                return new SenalPaso.Aparcar(VENTANA_ESPERA);
            });
            case PuertoConciliacionSecundaria2.Resultado.FalloRegistrado(var motivo) ->
                    throw new ExcepcionServicioExterno(motivo, null);
        };
    }

    /** El consumer de Kafka ya dejó la saga en TERMINADA; solo falta el cierre operativo de la orden. */
    private SenalPaso finalizarYa(SagaId id) {
        return tx.enTransaccion(() -> {
            var orden = repo.cargar(id);
            var sagaActual = (SagaSecundaria2Root) orden.saga();
            orden.finalizar(sagaActual.resultadoFinal());
            repo.guardar(orden);
            return new SenalPaso.Finalizada(sagaActual.resultadoFinal());
        });
    }
}
