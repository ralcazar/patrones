package com.ejemplo.tramitacion.dominio.saga.general;

public class DatosManualesRequeridosException extends RuntimeException {
    public DatosManualesRequeridosException(SagaId id, Paso paso) {
        super("Marcar OK el paso " + paso + " de la saga " + id.valor()
                + " requiere aportar los datos que ese paso habría producido");
    }
}
