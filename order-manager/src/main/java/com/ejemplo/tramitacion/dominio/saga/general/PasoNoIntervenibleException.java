package com.ejemplo.tramitacion.dominio.saga.general;

public class PasoNoIntervenibleException extends RuntimeException {
    public PasoNoIntervenibleException(SagaId id, Paso paso, EstadoPaso estado) {
        super("El paso " + paso + " de la saga " + id.valor()
                + " no admite esta intervención (estado actual: " + estado + ")");
    }
}
