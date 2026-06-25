package com.patrones.sagamanager.domain.model;

/**
 * Identificador recibido desde fuera para correlacionar el disparo (id_externo de Kafka,
 * id_borrador del poller legacy). No confundir con {@link ExternalId}, que es el resultado
 * devuelto por los servicios downstream.
 */
public record ExternalRef(String value) {

	public ExternalRef {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("ExternalRef must not be blank");
		}
	}

	public static ExternalRef of(String value) {
		return new ExternalRef(value);
	}

	@Override
	public String toString() {
		return value;
	}
}
