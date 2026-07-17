package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.time.Instant;

import jakarta.transaction.Transactional;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;

/**
 * Iniciar una tramitación: crea el agregado (orden + saga principal) en una
 * transacción y responde al instante con el sagaId. Al nacer con
 * {@code proximoReintentoEn = ahora}, el planificador la recoge como
 * candidata inmediata en su siguiente pasada y arranca la ejecución.
 */
@Service
public class ServicioIniciarTramitacion implements CasoUsoIniciarTramitacion {

    private final RepositorioOrden repo;

    public ServicioIniciarTramitacion(RepositorioOrden repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public SagaId iniciar(ComandoIniciarTramitacion cmd) {
        var sagaId = SagaId.nuevo();
        var saga = SagaPrincipal.crear(sagaId, cmd.externalId(), cmd.datoNegocio3(), cmd.datoNegocio2());
        repo.crear(OrdenRoot.nueva(saga, Instant.now()));
        return sagaId;
    }
}
