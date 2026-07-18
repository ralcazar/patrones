package com.ejemplo.app.business.ordermanager.dominio;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Lo que soporte necesita ver de un fallo: la clase de la excepción y su
 * mensaje, nunca el stacktrace (demasiado detalle de implementación para la
 * pantalla de soporte; para eso están los logs).
 */
@ValueObject
public record DetalleError(String tipo, String mensaje) {
    public static DetalleError de(Throwable causa) {
        return new DetalleError(causa.getClass().getName(), causa.getMessage());
    }
}
