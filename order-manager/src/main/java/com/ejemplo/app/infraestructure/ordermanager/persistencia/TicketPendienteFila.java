package com.ejemplo.app.infraestructure.ordermanager.persistencia;

/** Proyección nativa plana (join orden+proceso) para el barrido de tickets pendientes. */
interface TicketPendienteFila {
    String getOrdenId();
    String getTipo();
    String getExternalId();
    int getIntentos();
    String getUltimoErrorTipo();
    String getUltimoErrorMensaje();
}
