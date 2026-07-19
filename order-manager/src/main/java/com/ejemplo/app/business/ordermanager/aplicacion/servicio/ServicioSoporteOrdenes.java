package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoIntervenirOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaOrdenesSoporte;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;

/**
 * Fachada de la pantalla de soporte: cada intervención actúa directamente
 * sobre el agregado a través de RepositorioOrden (siempre un único agregado,
 * un único guardado), sin pasar por los procesadores de ejecución; las
 * consultas van al modelo de lectura.
 */
@Service
public class ServicioSoporteOrdenes implements CasoUsoIntervenirOrden, CasoUsoConsultarOrdenesSoporte {

    private final RepositorioOrden repo;
    private final PuertoConsultaOrdenesSoporte consultas;

    public ServicioSoporteOrdenes(RepositorioOrden repo, PuertoConsultaOrdenesSoporte consultas) {
        this.repo = repo;
        this.consultas = consultas;
    }

    // --- intervenciones ---

    @Override
    @Transactional
    public void reintentarPaso(TipoOrden tipo, OrdenId id, UsuarioSoporte quien) {
        var orden = repo.cargar(id);
        orden.resetearIntentos();
        orden.despertar(Instant.now());
        repo.guardar(orden);
    }

    @Override
    @Transactional
    public void marcarPasoOk(TipoOrden tipo, OrdenId id, UsuarioSoporte quien,
                             String justificacion, Map<String, String> datosManuales) {
        var orden = repo.cargar(id);
        orden.reemplazarProceso(orden.proceso().marcarPasoActualOkManual(quien, justificacion, datosManuales));
        orden.despertar(Instant.now());
        repo.guardar(orden);
    }

    // --- consultas (modelo de lectura) ---

    @Override
    public List<OrdenResumen> ordenesBloqueadas() {
        return consultas.ordenesBloqueadas();
    }

    @Override
    public List<OrdenResumen> ordenesEnEjecucion() {
        return consultas.ordenesEnEjecucion();
    }

    @Override
    public List<OrdenResumen> ordenesConTicketPendiente() {
        return consultas.ordenesConTicketPendiente();
    }

    @Override
    public List<OrdenResumen> buscar(FiltroOrdenes filtro) {
        return consultas.buscar(filtro);
    }

    @Override
    public OrdenDetalle detalle(TipoOrden tipo, OrdenId id) {
        return consultas.detalle(tipo, id);
    }
}
