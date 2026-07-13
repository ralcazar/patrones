package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.PasoSaga;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TicketId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Apertura de tickets al equipo de soporte. El adaptador puede consolidar:
 * si ya existe ticket abierto para la misma saga/tramitación, añadir comentario
 * en lugar de abrir otro.
 */
public interface PuertoTicketsSoporte {
    TicketId abrir(SagaId sagaId, TipoSaga tipo, PasoSaga paso, MotivoFallo motivo,
                   int intentos, boolean sagaCancelable);
}
