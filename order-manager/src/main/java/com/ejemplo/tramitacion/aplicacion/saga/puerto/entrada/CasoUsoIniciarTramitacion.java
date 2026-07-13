package com.ejemplo.tramitacion.aplicacion.saga.puerto.entrada;

import com.ejemplo.tramitacion.dominio.saga.paso1.DatoNegocio3;
import com.ejemplo.tramitacion.dominio.saga.paso7.DatoNegocio2;
import com.ejemplo.tramitacion.dominio.saga.general.DatoNegocio1Id;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;

/** Lo invoca el adaptador de entrada que inicia la tramitación (REST, consumer, etc.). */
public interface CasoUsoIniciarTramitacion {

    SagaId iniciar(ComandoIniciarTramitacion cmd);

    record ComandoIniciarTramitacion(DatoNegocio1Id datoNegocio1Id,
                                     DatoNegocio3 datoNegocio3,
                                     DatoNegocio2 datoNegocio2) {}
}
