package com.patrones.sagamanager.domain.model;

public record IdempotencyKey(String value) {

	public IdempotencyKey {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("IdempotencyKey must not be blank");
		}
	}

	/**
	 * Determinista a partir de (sagaId, step): un reintento del mismo paso siempre produce
	 * la misma clave, de forma que el servicio downstream idempotente devuelva el mismo id.
	 */
	public static IdempotencyKey forStep(SagaId sagaId, SagaStep step) {
		return new IdempotencyKey(sagaId.value() + ":" + step.name());
	}

	@Override
	public String toString() {
		return value;
	}
}
