package com.ejemplo.app.infraestructure.ordermanager.saga;

/** Proyección nativa de la query de candidatas del planificador. */
interface CandidataFila {
    String getSagaId();
    String getTipo();
}
