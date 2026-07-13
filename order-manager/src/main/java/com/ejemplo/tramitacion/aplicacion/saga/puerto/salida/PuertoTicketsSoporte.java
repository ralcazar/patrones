package com.ejemplo.tramitacion.aplicacion.saga.puerto.salida;

import com.ejemplo.tramitacion.dominio.saga.general.MotivoFallo;
import com.ejemplo.tramitacion.dominio.saga.general.Paso;
import com.ejemplo.tramitacion.dominio.saga.general.SagaId;
import com.ejemplo.tramitacion.dominio.saga.general.TicketId;
import com.ejemplo.tramitacion.dominio.saga.general.TipoSaga;

/**
 * Apertura de tickets al equipo de soporte. El adaptador puede consolidar:
 * si ya existe ticket abierto para la misma saga/datoNegocio1, añadir comentario
 * en lugar de abrir otro.
 */
public interface PuertoTicketsSoporte {
    TicketId abrir(SagaId sagaId, TipoSaga tipo, Paso paso, MotivoFallo motivo,
                   int intentos, boolean sagaCancelable);
}
