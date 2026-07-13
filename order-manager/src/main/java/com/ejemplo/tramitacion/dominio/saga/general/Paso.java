package com.ejemplo.tramitacion.dominio.saga.general;

public enum Paso {
    // Saga principal (el orden de declaración ES el orden del flujo)
    PASO1, PASO2, PASO3, PASO4, PASO5, PASO6, PASO7, PASO8,
    // Sagas que arrancan al completarse la principal
    ASINCRONO, SECUENCIAL1, SECUENCIAL2, SIMPLE
}
