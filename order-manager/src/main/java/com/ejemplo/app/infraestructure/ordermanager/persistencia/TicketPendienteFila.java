package com.ejemplo.app.infraestructure.ordermanager.persistencia;

/** Proyección nativa plana sobre la tabla {@code orden} para el barrido de tickets pendientes. */
interface TicketPendienteFila {
    String getOrdenId();
    String getTipo();
    String getExternalId();
    int getIntentos();
    String getUltimoErrorTipo();
    String getUltimoErrorMensaje();
}
