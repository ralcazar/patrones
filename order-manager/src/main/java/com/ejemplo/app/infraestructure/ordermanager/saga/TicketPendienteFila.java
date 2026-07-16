package com.ejemplo.app.infraestructure.ordermanager.saga;

/** Proyección nativa plana (join orden+saga) para el barrido de tickets pendientes. */
interface TicketPendienteFila {
    String getSagaId();
    String getTipo();
    String getExternalId();
    int getIntentos();
}
