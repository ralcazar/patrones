package com.ejemplo.app.business.ordermanager.dominio;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * Tipo de orden que procesa el motor: un VO abierto (no un enum cerrado) para
 * que aplicaciones que reutilicen el motor puedan definir sus propios tipos
 * sin tocar este paquete. Cada tipo concreto vive fuera de {@code ordermanager}
 * (p. ej. las constantes {@code TIPO} de los tipos de orden concretos).
 */
@ValueObject
public record TipoOrden(String valor) {
    public TipoOrden {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El tipo de orden no puede ser nulo ni vacío");
        }
    }
}
