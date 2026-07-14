package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.MotivoFallo;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Modelo de lectura del planificador de tickets: sagas con el flag
 * "abrir ticket pendiente" (EstadoTicket.PENDIENTE). El adaptador lo resuelve
 * con una query sobre las tablas de sagas, sin cargar agregados. Las sagas ya
 * en ABIERTO no aparecen: por eso el barrido nunca duplica avisos.
 */
public interface PuertoSagasTicketPendiente {

    List<SagaTicketPendiente> buscar();

    /**
     * Lo que el ticket cuenta de cada saga. sigueReintentando distingue el
     * fallo reintentable que ya consumió la escalera de backoff (true: sigue
     * probando cada 180 min) del fallo no reintentable o compensación fallida
     * (false: paso BLOQUEADO_SOPORTE, nadie lo reintentará automáticamente).
     */
    record SagaTicketPendiente(TipoSaga tipo, SagaId sagaId, ExternalId externalId,
                               String nombrePaso, MotivoFallo ultimoFallo, int intentos,
                               boolean sigueReintentando) {}
}
