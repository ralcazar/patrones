package com.ejemplo.app.business.sagas.dominio.datosnegocio;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.UUID;

/** Identidad del agregado {@link DatosNegocio}, ajena al {@code ExternalId} que correlaciona las sagas. */
@ValueObject
public record DatosNegocioId(UUID valor) {
    public static DatosNegocioId nuevo() { return new DatosNegocioId(UUID.randomUUID()); }
}
