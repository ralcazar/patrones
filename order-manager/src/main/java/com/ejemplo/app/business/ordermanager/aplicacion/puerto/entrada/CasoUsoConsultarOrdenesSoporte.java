package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.time.Instant;
import java.util.List;

import com.ejemplo.app.business.ordermanager.dominio.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.DetalleError;
import com.ejemplo.app.business.ordermanager.dominio.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.OrdenId;
import com.ejemplo.app.business.ordermanager.dominio.TipoOrden;

/**
 * Consultas para la pantalla de soporte. Es un modelo de lectura puro CQRS:
 * el adaptador lo resuelve con queries SQL sobre las tablas {@code orden} y
 * {@code proceso}, sin cargar agregados. Por eso el estado de la FSM viaja como
 * {@code String} (el nombre del enum tal cual está en la columna) en vez del
 * enum de dominio: esta interfaz no depende de las FSM concretas de cada tipo.
 */
public interface CasoUsoConsultarOrdenesSoporte {

    /** Bandeja de trabajo: órdenes con la escalera de reintentos consumida (intentos >= 8). */
    List<OrdenResumen> ordenesBloqueadas();

    /** Órdenes con token de ejecución vigente (en curso ahora mismo, en algún pod). */
    List<OrdenResumen> ordenesEnEjecucion();

    /** Órdenes con la escalera consumida y sin ticket abierto todavía. */
    List<OrdenResumen> ordenesConTicketPendiente();

    /** Búsqueda con filtros combinables; los criterios a null no aplican. */
    List<OrdenResumen> buscar(FiltroOrdenes filtro);

    OrdenDetalle detalle(TipoOrden tipo, OrdenId id);

    /** Criterios de la pantalla de soporte: estado (nombre del enum), fecha de inicio y de última actualización. */
    record FiltroOrdenes(String estado,
                       Instant iniciadaDesde, Instant iniciadaHasta,
                       Instant actualizadaDesde, Instant actualizadaHasta) {

        public static FiltroOrdenes porEstado(String estado) {
            return new FiltroOrdenes(estado, null, null, null, null);
        }

        public static FiltroOrdenes iniciadaEntre(Instant desde, Instant hasta) {
            return new FiltroOrdenes(null, desde, hasta, null, null);
        }

        public static FiltroOrdenes actualizadaEntre(Instant desde, Instant hasta) {
            return new FiltroOrdenes(null, null, null, desde, hasta);
        }
    }

    /**
     * Lo que soporte necesita ver de un vistazo:
     * - estado: nombre del estado de la FSM de negocio (p. ej. "PASO3_HECHO").
     * - intentos: reintentos de ejecución consumidos; &gt;= 8 marca la orden como bloqueada.
     * - ticketAbiertoEn: null si no hay ticket abierto.
     * - proximoReintentoEn: próxima vez que el planificador la recogerá.
     * - ultimoError: clase de excepción + mensaje del último fallo de paso
     *   (null si nunca falló o el paso más reciente fue OK); sin stacktrace.
     */
    record OrdenResumen(OrdenId id, TipoOrden tipo, ExternalId externalId,
                       String estado, int intentos,
                       Instant ticketAbiertoEn, Instant proximoReintentoEn,
                       Instant iniciadaEn, Instant actualizadaEn,
                       DetalleError ultimoError) {}

    /** El paso pendiente actual (derivado de la FSM), si la orden sigue viva. */
    record PasoDetalle(String nombrePaso, boolean datosManualesObligatorios) {}

    record OrdenDetalle(OrdenResumen resumen, boolean cancelable,
                       List<PasoDetalle> pasos, List<AuditoriaIntervencion> auditoria) {}
}
