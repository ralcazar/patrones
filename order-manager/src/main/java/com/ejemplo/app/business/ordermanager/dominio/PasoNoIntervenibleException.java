package com.ejemplo.app.business.ordermanager.dominio;

/** El paso actual de la orden no admite marcado manual OK (p. ej. ya pasó el punto de no retorno de la saga). */
public class PasoNoIntervenibleException extends RuntimeException {
    public PasoNoIntervenibleException(OrdenId id, String detalle) {
        super("La orden " + id.valor() + " no admite esta intervención: " + detalle);
    }
}
