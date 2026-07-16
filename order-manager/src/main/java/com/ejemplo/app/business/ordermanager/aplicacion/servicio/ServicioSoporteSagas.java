package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoIntervenirSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipal;

/**
 * Fachada de la pantalla de soporte: cada intervención actúa directamente
 * sobre el agregado a través de RepositorioOrden (siempre un único agregado,
 * un único guardado), sin pasar por los servicios de saga de ejecución; las
 * consultas van al modelo de lectura.
 */
@Service
public class ServicioSoporteSagas implements CasoUsoIntervenirSaga, CasoUsoConsultarSagasSoporte {

    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;
    private final PuertoConsultaSagasSoporte consultas;

    public ServicioSoporteSagas(RepositorioOrden repo, UnidadDeTrabajo tx, PuertoConsultaSagasSoporte consultas) {
        this.repo = repo;
        this.tx = tx;
        this.consultas = consultas;
    }

    // --- intervenciones ---

    @Override
    public void cancelarPrincipal(SagaId id, UsuarioSoporte quien, String motivo) {
        // El agregado valida el punto de no retorno; las excepciones
        // (PuntoNoRetornoSuperadoException, etc.) suben al adaptador REST tal cual.
        tx.enTransaccion(() -> {
            var orden = repo.cargar(id);
            var saga = (SagaPrincipal) orden.saga();
            saga.cancelar(quien, motivo);
            orden.despertar(Instant.now());
            repo.guardar(orden);
        });
    }

    @Override
    public void reintentarPaso(TipoSaga tipo, SagaId id, String nombrePaso, UsuarioSoporte quien) {
        tx.enTransaccion(() -> {
            var orden = repo.cargar(id);
            orden.resetearIntentos();
            orden.despertar(Instant.now());
            repo.guardar(orden);
        });
    }

    @Override
    public void marcarPasoOk(TipoSaga tipo, SagaId id, String nombrePaso, UsuarioSoporte quien,
                             String justificacion, Map<String, String> datosManuales) {
        tx.enTransaccion(() -> {
            var orden = repo.cargar(id);
            orden.saga().marcarPasoActualOkManual(quien, justificacion, datosManuales);
            orden.despertar(Instant.now());
            repo.guardar(orden);
        });
    }

    // --- consultas (modelo de lectura) ---

    @Override
    public List<SagaResumen> sagasBloqueadas() {
        return consultas.sagasBloqueadas();
    }

    @Override
    public List<SagaResumen> sagasEnEjecucion() {
        return consultas.sagasEnEjecucion();
    }

    @Override
    public List<SagaResumen> sagasConTicketPendiente() {
        return consultas.sagasConTicketPendiente();
    }

    @Override
    public List<SagaResumen> buscar(FiltroSagas filtro) {
        return consultas.buscar(filtro);
    }

    @Override
    public VistaTramitacion vistaTramitacion(ExternalId externalId) {
        return consultas.vistaTramitacion(externalId);
    }

    @Override
    public SagaDetalle detalle(TipoSaga tipo, SagaId id) {
        return consultas.detalle(tipo, id);
    }
}
