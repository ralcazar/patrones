package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Modelo de lectura del planificador de tickets: órdenes con la escalera de
 * reintentos consumida y sin ticket abierto todavía. El adaptador lo resuelve
 * con una query sobre la tabla {@code orden} (join con {@code proceso} para el
 * externalId), sin cargar agregados: {@code intentos >= 8 AND
 * ticket_abierto_en IS NULL AND resultado IS NULL}. Las órdenes ya con
 * ticket abierto no aparecen: por eso el barrido nunca duplica avisos.
 */
public interface PuertoOrdenesTicketPendiente {

    List<OrdenTicketPendiente> buscar();

    record OrdenTicketPendiente(TipoOrden tipo, OrdenId ordenId, ExternalId externalId, int intentos,
            DetalleError ultimoError) {}
}
