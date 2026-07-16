package com.ejemplo.app.business.ordermanager.aplicacion.servicio;

import java.time.Instant;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoIniciarTramitacion;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.RepositorioOrden;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.UnidadDeTrabajo;
import com.ejemplo.app.business.ordermanager.dominio.comun.OrdenRoot;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.SagaPrincipalRoot;

/**
 * Iniciar una tramitación: crea el agregado (orden + saga principal) en una
 * transacción y responde al instante con el sagaId. Al nacer con
 * {@code proximoReintentoEn = ahora}, el planificador la recoge como
 * candidata inmediata en su siguiente pasada y arranca la ejecución.
 */
@Service
public class ServicioIniciarTramitacion implements CasoUsoIniciarTramitacion {

    private final RepositorioOrden repo;
    private final UnidadDeTrabajo tx;

    public ServicioIniciarTramitacion(RepositorioOrden repo, UnidadDeTrabajo tx) {
        this.repo = repo;
        this.tx = tx;
    }

    @Override
    public SagaId iniciar(ComandoIniciarTramitacion cmd) {
        var sagaId = SagaId.nuevo();
        tx.enTransaccion(() -> {
            var saga = SagaPrincipalRoot.crear(sagaId, cmd.externalId(), cmd.datoNegocio3(), cmd.datoNegocio2());
            repo.crear(OrdenRoot.nueva(saga, Instant.now()));
        });
        return sagaId;
    }
}
