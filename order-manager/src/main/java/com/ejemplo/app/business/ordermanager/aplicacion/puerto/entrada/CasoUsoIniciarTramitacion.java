package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio2;
import com.ejemplo.app.business.ordermanager.dominio.sagaprincipal.DatoNegocio3;

/** Lo invoca el adaptador de entrada que inicia la tramitación (REST, consumer, etc.). */
public interface CasoUsoIniciarTramitacion {

    SagaId iniciar(ComandoIniciarTramitacion cmd);

    record ComandoIniciarTramitacion(ExternalId externalId,
                                     DatoNegocio3 datoNegocio3,
                                     DatoNegocio2 datoNegocio2) {}
}
