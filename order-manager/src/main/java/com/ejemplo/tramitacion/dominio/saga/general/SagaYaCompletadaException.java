package com.ejemplo.tramitacion.dominio.saga.general;

public class SagaYaCompletadaException extends RuntimeException {
    public SagaYaCompletadaException(SagaId id) {
        super("La saga " + id.valor() + " ya está completada");
    }
}
