package com.ejemplo.app.business.ordermanager.dominio.comun;

public class SagaYaCompletadaException extends RuntimeException {
    public SagaYaCompletadaException(SagaId id) {
        super("La saga " + id.valor() + " ya está completada");
    }
}
