package com.ejemplo.app.business.ordermanager.dominio.comun;

import org.jmolecules.ddd.annotation.ValueObject;

import java.util.UUID;

@ValueObject
public record SagaId(UUID valor) {
    public static SagaId nuevo() { return new SagaId(UUID.randomUUID()); }
    public static SagaId de(String valor) { return new SagaId(UUID.fromString(valor)); }
}
