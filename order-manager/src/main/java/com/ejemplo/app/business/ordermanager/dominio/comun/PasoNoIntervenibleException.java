package com.ejemplo.app.business.ordermanager.dominio.comun;

public class PasoNoIntervenibleException extends RuntimeException {
    public PasoNoIntervenibleException(SagaId id, String detalle) {
        super("La saga " + id.valor() + " no admite esta intervención: " + detalle);
    }
}
