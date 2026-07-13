package com.ejemplo.tramitacion.dominio.saga.general;

import java.util.UUID;

public record SagaId(UUID valor) {
    public static SagaId nuevo() { return new SagaId(UUID.randomUUID()); }
    public static SagaId de(String valor) { return new SagaId(UUID.fromString(valor)); }
}
