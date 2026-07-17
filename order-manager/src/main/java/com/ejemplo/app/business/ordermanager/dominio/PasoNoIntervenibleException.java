package com.ejemplo.app.business.ordermanager.dominio;

public class PasoNoIntervenibleException extends RuntimeException {
    public PasoNoIntervenibleException(OrdenId id, String detalle) {
        super("La orden " + id.valor() + " no admite esta intervención: " + detalle);
    }
}
