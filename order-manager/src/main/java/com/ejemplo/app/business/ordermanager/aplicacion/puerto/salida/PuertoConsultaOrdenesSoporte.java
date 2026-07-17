package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.FiltroOrdenes;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarOrdenesSoporte.OrdenResumen;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Modelo de lectura para la pantalla de soporte: el adaptador lo implementa
 * con queries SQL directas sobre las tablas {@code orden}/{@code proceso}
 * (CQRS ligero), sin cargar agregados.
 */
public interface PuertoConsultaOrdenesSoporte {
    /** {@code intentos >= 8}. */
    List<OrdenResumen> ordenesBloqueadas();
    /** {@code token_trabajador IS NOT NULL AND token_expira_en > ahora AND resultado IS NULL}. */
    List<OrdenResumen> ordenesEnEjecucion();
    /** {@code intentos >= 8 AND ticket_abierto_en IS NULL AND resultado IS NULL}. */
    List<OrdenResumen> ordenesConTicketPendiente();
    /** WHERE dinámico sobre las tablas de órdenes; los criterios a null no aplican. */
    List<OrdenResumen> buscar(FiltroOrdenes filtro);
    /** Todas las órdenes de una tramitación, correlacionadas por externalId, sin componer la vista. */
    List<OrdenDetalle> porExternalId(ExternalId externalId);
    OrdenDetalle detalle(TipoOrden tipo, OrdenId id);
}
