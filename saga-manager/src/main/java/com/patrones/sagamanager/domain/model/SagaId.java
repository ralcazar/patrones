package com.patrones.sagamanager.domain.model;

import java.util.Objects;
import java.util.UUID;

public record SagaId(UUID value) {

	public SagaId {
		Objects.requireNonNull(value, "SagaId value must not be null");
	}

	public static SagaId newId() {
		return new SagaId(UUID.randomUUID());
	}

	public static SagaId of(UUID value) {
		return new SagaId(value);
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
