package com.ejemplo.app.business.ordermanager.dominio;

/** La lanza el adaptador de persistencia cuando la versión del agregado no coincide. */
public class ConcurrenciaOptimistaException extends RuntimeException {
    public ConcurrenciaOptimistaException(OrdenId id, long versionEsperada) {
        super("Conflicto de concurrencia en la orden " + id.valor() + " (versión " + versionEsperada + ")");
    }
}
