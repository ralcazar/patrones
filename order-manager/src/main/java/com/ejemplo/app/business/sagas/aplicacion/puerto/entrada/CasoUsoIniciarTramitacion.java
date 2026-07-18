package com.ejemplo.app.business.sagas.aplicacion.puerto.entrada;

import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;

/** Lo invoca el adaptador de entrada que inicia la tramitación (REST, consumer, etc.). */
public interface CasoUsoIniciarTramitacion {

    OrdenId iniciar(ComandoIniciarTramitacion cmd);

    record ComandoIniciarTramitacion(ExternalId externalId) {}
}
