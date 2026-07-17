package com.ejemplo.app.business.ordermanager.aplicacion.puerto.salida;

import java.util.List;

import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.FiltroSagas;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaDetalle;
import com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada.CasoUsoConsultarSagasSoporte.SagaResumen;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoOrden;

/**
 * Modelo de lectura para la pantalla de soporte: el adaptador lo implementa
 * con queries SQL directas sobre las tablas {@code orden}/{@code saga}
 * (CQRS ligero), sin cargar agregados.
 */
public interface PuertoConsultaSagasSoporte {
    /** {@code intentos >= 8}. */
    List<SagaResumen> sagasBloqueadas();
    /** {@code token_trabajador IS NOT NULL AND token_expira_en > ahora AND resultado IS NULL}. */
    List<SagaResumen> sagasEnEjecucion();
    /** {@code intentos >= 8 AND ticket_abierto_en IS NULL AND resultado IS NULL}. */
    List<SagaResumen> sagasConTicketPendiente();
    /** WHERE dinámico sobre las tablas de sagas/órdenes; los criterios a null no aplican. */
    List<SagaResumen> buscar(FiltroSagas filtro);
    /** Todas las sagas de una tramitación, correlacionadas por externalId, sin componer la vista. */
    List<SagaDetalle> porExternalId(ExternalId externalId);
    SagaDetalle detalle(TipoOrden tipo, SagaId id);
}
