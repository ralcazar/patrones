package com.ejemplo.app.business.ordermanager.dominio;

/** Se intenta una intervención de soporte sobre una orden cuyo {@link Proceso} ya terminó. */
public class OrdenYaCompletadaException extends RuntimeException {
    public OrdenYaCompletadaException(OrdenId id) {
        super("La orden " + id.valor() + " ya está completada");
    }
}
