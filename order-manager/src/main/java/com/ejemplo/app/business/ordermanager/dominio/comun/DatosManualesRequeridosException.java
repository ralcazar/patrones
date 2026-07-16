package com.ejemplo.app.business.ordermanager.dominio.comun;

public class DatosManualesRequeridosException extends RuntimeException {
    public DatosManualesRequeridosException(SagaId id, String detalle) {
        super("Marcar OK el paso " + detalle + " de la saga " + id.valor()
                + " requiere aportar los datos que ese paso habría producido");
    }
}
