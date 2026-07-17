package com.ejemplo.app.business.ordermanager.dominio;

public class OrdenYaCompletadaException extends RuntimeException {
    public OrdenYaCompletadaException(OrdenId id) {
        super("La orden " + id.valor() + " ya está completada");
    }
}
