package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.UUID;

@ValueObject
public record DatosNegocioId(UUID valor) {
    public static DatosNegocioId nuevo() { return new DatosNegocioId(UUID.randomUUID()); }
}
