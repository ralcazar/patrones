package com.ejemplo.app.infraestructure.ordermanager.persistencia;

/** Proyección nativa de la query de candidatas del planificador. */
interface CandidataFila {
    String getSagaId();
    String getTipo();
}
