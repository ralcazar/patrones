package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.OffsetDateTime;

/**
 * Proyección nativa plana (join orden+proceso) para el modelo de lectura de
 * soporte. Las columnas TIMESTAMP se declaran {@link OffsetDateTime} (no
 * {@code Instant}): así lo entrega Hibernate para columnas mapeadas como
 * {@code Instant} (TIMESTAMP_UTC) en las proyecciones nativas, sin depender
 * del conversor estático (no configurable) de {@code ProxyProjectionFactory}.
 * {@link AdaptadorConsultaOrdenesSoporte} hace el {@code toInstant()}.
 */
interface OrdenResumenFila {
    String getOrdenId();
    String getTipo();
    String getExternalId();
    String getEstado();
    int getIntentos();
    OffsetDateTime getTicketAbiertoEn();
    OffsetDateTime getProximoReintentoEn();
    OffsetDateTime getIniciadaEn();
    OffsetDateTime getActualizadaEn();
}
