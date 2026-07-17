package com.ejemplo.app.business.ordermanager.aplicacion.servicio.comun;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoIntervenirSaga;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.UsuarioSoporte;

/**
 * Fachada de la pantalla de soporte: cada intervención actúa directamente
 * sobre el agregado a través de RepositorioOrden (siempre un único agregado,
 * un único guardado), sin pasar por los servicios de saga de ejecución; las
 * consultas van al modelo de lectura.
 */
@Service
public class ServicioSoporteSagas implements CasoUsoIntervenirSaga, CasoUsoConsultarSagasSoporte {

    private final RepositorioOrden repo;
    private final PuertoConsultaSagasSoporte consultas;

    public ServicioSoporteSagas(RepositorioOrden repo, PuertoConsultaSagasSoporte consultas) {
        this.repo = repo;
        this.consultas = consultas;
    }

    // --- intervenciones ---

    @Override
    @Transactional
    public void reintentarPaso(TipoOrden tipo, SagaId id, String nombrePaso, UsuarioSoporte quien) {
        var orden = repo.cargar(id);
        orden.resetearIntentos();
        orden.despertar(Instant.now());
        repo.guardar(orden);
    }

    @Override
    @Transactional
    public void marcarPasoOk(TipoOrden tipo, SagaId id, String nombrePaso, UsuarioSoporte quien,
                             String justificacion, Map<String, String> datosManuales) {
        var orden = repo.cargar(id);
        orden.saga().marcarPasoActualOkManual(quien, justificacion, datosManuales);
        orden.despertar(Instant.now());
        repo.guardar(orden);
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
    public SagaDetalle detalle(TipoOrden tipo, SagaId id) {
        return consultas.detalle(tipo, id);
    }
}
