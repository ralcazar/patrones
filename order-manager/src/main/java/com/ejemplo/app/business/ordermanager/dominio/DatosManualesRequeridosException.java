package com.ejemplo.app.business.ordermanager.dominio;

public class DatosManualesRequeridosException extends RuntimeException {
    public DatosManualesRequeridosException(OrdenId id, String detalle) {
        super("Marcar OK el paso " + detalle + " de la orden " + id.valor()
                + " requiere aportar los datos que ese paso habría producido");
    }
}
