package com.ejemplo.tramitacion.dominio.saga.general;

/** La lanza el adaptador de persistencia cuando la versión del agregado no coincide. */
public class ConcurrenciaOptimistaException extends RuntimeException {
    public ConcurrenciaOptimistaException(SagaId id, long versionEsperada) {
        super("Conflicto de concurrencia en saga " + id.valor() + " (versión " + versionEsperada + ")");
    }
}
