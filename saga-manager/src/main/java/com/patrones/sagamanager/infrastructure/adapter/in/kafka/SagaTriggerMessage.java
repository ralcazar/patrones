package com.patrones.sagamanager.infrastructure.adapter.in.kafka;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO propio del adaptador Kafka: traduce el mensaje externo (id_app, id_externo, payload) sin
 * filtrar este formato hacia el dominio.
 */
public record SagaTriggerMessage(
		@JsonProperty("id_app") String idApp,
		@JsonProperty("id_externo") String idExterno,
		@JsonProperty("payload") Map<String, Object> payload) {
}
