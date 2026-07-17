package com.ejemplo.app.business.ordermanager.aplicacion.puerto.entrada;

import java.time.Instant;
import java.util.List;

import com.ejemplo.app.business.ordermanager.dominio.comun.AuditoriaIntervencion;
import com.ejemplo.app.business.ordermanager.dominio.comun.ExternalId;
import com.ejemplo.app.business.ordermanager.dominio.comun.SagaId;
import com.ejemplo.app.business.ordermanager.dominio.comun.TipoSaga;

/**
 * Consultas para la pantalla de soporte. Es un modelo de lectura puro CQRS:
 * el adaptador lo resuelve con queries SQL sobre las tablas {@code orden} y
 * {@code saga}, sin cargar agregados. Por eso el estado de la FSM viaja como
 * {@code String} (el nombre del enum tal cual está en la columna) en vez del
 * enum de dominio: esta interfaz no depende de las 4 FSM concretas.
 */
public interface CasoUsoConsultarSagasSoporte {

    /** Bandeja de trabajo: órdenes con la escalera de reintentos consumida (intentos >= 8). */
    List<SagaResumen> sagasBloqueadas();

    /** Órdenes con token de ejecución vigente (en curso ahora mismo, en algún pod). */
    List<SagaResumen> sagasEnEjecucion();

    /** Órdenes con la escalera consumida y sin ticket abierto todavía. */
    List<SagaResumen> sagasConTicketPendiente();

    /** Búsqueda con filtros combinables; los criterios a null no aplican. */
    List<SagaResumen> buscar(FiltroSagas filtro);

    SagaDetalle detalle(TipoSaga tipo, SagaId id);

    /** Criterios de la pantalla de soporte: estado (nombre del enum), fecha de inicio y de última actualización. */
    record FiltroSagas(String estado,
                       Instant iniciadaDesde, Instant iniciadaHasta,
                       Instant actualizadaDesde, Instant actualizadaHasta) {

        public static FiltroSagas porEstado(String estado) {
            return new FiltroSagas(estado, null, null, null, null);
        }

        public static FiltroSagas iniciadaEntre(Instant desde, Instant hasta) {
            return new FiltroSagas(null, desde, hasta, null, null);
        }

        public static FiltroSagas actualizadaEntre(Instant desde, Instant hasta) {
            return new FiltroSagas(null, null, null, desde, hasta);
        }
    }

    /**
     * Lo que soporte necesita ver de un vistazo:
     * - estado: nombre del estado de la FSM de negocio (p. ej. "PASO3_HECHO").
     * - intentos: reintentos de ejecución consumidos; &gt;= 8 marca la orden como bloqueada.
     * - ticketAbiertoEn: null si no hay ticket abierto.
     * - proximoReintentoEn: próxima vez que el planificador la recogerá.
     */
    record SagaResumen(SagaId id, TipoSaga tipo, ExternalId externalId,
                       String estado, int intentos,
                       Instant ticketAbiertoEn, Instant proximoReintentoEn,
                       Instant iniciadaEn, Instant actualizadaEn) {}

    /** El paso pendiente actual (derivado de la FSM), si la orden sigue viva. */
    record PasoDetalle(String nombrePaso, boolean datosManualesObligatorios) {}

    record SagaDetalle(SagaResumen resumen, boolean cancelable,
                       List<PasoDetalle> pasos, List<AuditoriaIntervencion> auditoria) {}
}
