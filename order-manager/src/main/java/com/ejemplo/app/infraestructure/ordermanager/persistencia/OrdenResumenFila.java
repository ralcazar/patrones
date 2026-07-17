package com.ejemplo.app.infraestructure.ordermanager.persistencia;

import java.time.Instant;

/** Proyección nativa plana (join orden+saga) para el modelo de lectura de soporte. */
interface OrdenResumenFila {
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
