package com.patrones.sagamanager.domain.model;

/**
 * Identificador devuelto por uno de los servicios downstream (firstId/secondId/thirdId).
 * No confundir con {@link ExternalRef}, que es el identificador de entrada.
 */
public record ExternalId(String value) {

	public ExternalId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("ExternalId must not be blank");
		}
	}

	public static ExternalId of(String value) {
		return new ExternalId(value);
	}

	@Override
	public String toString() {
		return value;
	}
}
