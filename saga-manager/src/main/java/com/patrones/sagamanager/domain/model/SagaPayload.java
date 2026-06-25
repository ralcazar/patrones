package com.patrones.sagamanager.domain.model;

import java.util.Map;
import java.util.Objects;

/**
 * Datos del pedido que viajan con la saga. Se modela como mapa de atributos para no acoplar
 * el dominio a ningún formato de serialización concreto.
 */
public record SagaPayload(Map<String, Object> attributes) {

	public SagaPayload {
		Objects.requireNonNull(attributes, "attributes must not be null");
		attributes = Map.copyOf(attributes);
	}

	public static SagaPayload of(Map<String, Object> attributes) {
		return new SagaPayload(attributes);
	}

	public static SagaPayload empty() {
		return new SagaPayload(Map.of());
	}
}
