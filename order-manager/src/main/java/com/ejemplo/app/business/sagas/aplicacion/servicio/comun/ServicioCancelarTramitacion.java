package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.UsuarioSoporte;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoCancelarTramitacion;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;

/**
 * Cancelación de la saga principal: actúa directamente sobre el agregado a
 * través de RepositorioOrden (un único agregado, un único guardado), sin
 * pasar por los servicios de saga de ejecución.
 */
@Service
public class ServicioCancelarTramitacion implements CasoUsoCancelarTramitacion {

    private final RepositorioOrden repo;

    public ServicioCancelarTramitacion(RepositorioOrden repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void cancelarPrincipal(OrdenId id, UsuarioSoporte quien, String motivo) {
        // El agregado valida el punto de no retorno; las excepciones
        // (PuntoNoRetornoSuperadoException, etc.) suben al adaptador REST tal cual.
        var orden = repo.cargar(id);
        var saga = (SagaPrincipal) orden.proceso();
        saga.cancelar(quien, motivo);
        orden.despertar(Instant.now());
        repo.guardar(orden);
    }
}
