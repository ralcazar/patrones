package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

import java.util.List;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaDetalle;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;

/** Lo invoca la pantalla de soporte. Compone la vista de conjunto de una tramitación. */
public interface CasoUsoVistaTramitacion {

    /** Vista de conjunto de una tramitación: sus 4 sagas, correlacionadas por externalId. */
    VistaTramitacion vistaTramitacion(ExternalId externalId);

    record VistaTramitacion(ExternalId externalId, SagaDetalle principal,
                            List<SagaDetalle> secundarias) {}
}
