package com.ejemplo.app.infraestructure.ordermanager.saga;

import java.time.Instant;

/** Proyección nativa plana (join orden+saga) para el modelo de lectura de soporte. */
interface SagaResumenFila {
    String getSagaId();
    String getTipo();
    String getExternalId();
    String getEstado();
    int getIntentos();
    Instant getTicketAbiertoEn();
    Instant getProximoReintentoEn();
    Instant getIniciadaEn();
    Instant getActualizadaEn();
}
