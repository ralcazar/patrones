package com.ejemplo.app.business.ordermanager.dominio;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.UUID;

@ValueObject
public record OrdenId(UUID valor) {
    public static OrdenId nuevo() { return new OrdenId(UUID.randomUUID()); }
    public static OrdenId de(String valor) { return new OrdenId(UUID.fromString(valor)); }
}
