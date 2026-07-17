package com.ejemplo.app.business.sagas.aplicacion.servicio.comun;

import java.util.ArrayList;

import org.jmolecules.ddd.annotation.Service;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida.PuertoConsultaSagasSoporte;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.sagas.aplicacion.puerto.entrada.CasoUsoVistaTramitacion;
import com.ejemplo.app.business.sagas.dominio.sagaprincipal.SagaPrincipal;

/**
 * Compone la vista de conjunto de una tramitación a partir del modelo de
 * lectura genérico del motor, particionando por tipo: la saga principal
 * aparte de las secundarias.
 */
@Service
public class ServicioVistaTramitacion implements CasoUsoVistaTramitacion {

    private final PuertoConsultaSagasSoporte consultas;

    public ServicioVistaTramitacion(PuertoConsultaSagasSoporte consultas) {
        this.consultas = consultas;
    }

    @Override
    public VistaTramitacion vistaTramitacion(ExternalId externalId) {
        SagaDetalle principal = null;
        var secundarias = new ArrayList<SagaDetalle>();
        for (var detalle : consultas.porExternalId(externalId)) {
            if (SagaPrincipal.TIPO.equals(detalle.resumen().tipo())) {
                principal = detalle;
            } else {
                secundarias.add(detalle);
            }
        }
        return new VistaTramitacion(externalId, principal, secundarias);
    }
}
