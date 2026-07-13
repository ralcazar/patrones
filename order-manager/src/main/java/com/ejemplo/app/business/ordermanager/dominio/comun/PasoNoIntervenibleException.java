package com.ejemplo.app.business.ordermanager.dominio.comun;

public class PasoNoIntervenibleException extends RuntimeException {
    public PasoNoIntervenibleException(SagaId id, PasoSaga paso, EstadoPaso estado) {
        super("El paso " + paso + " de la saga " + id.valor()
                + " no admite esta intervención (estado actual: " + estado + ")");
    }
}
