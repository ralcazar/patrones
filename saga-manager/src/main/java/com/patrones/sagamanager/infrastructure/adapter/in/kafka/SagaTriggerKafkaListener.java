package com.patrones.sagamanager.infrastructure.adapter.in.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.patrones.sagamanager.application.port.in.TriggerSagaUseCase;
import com.patrones.sagamanager.domain.model.AppId;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.ExternalRef;
import com.patrones.sagamanager.domain.model.SagaPayload;

/**
 * Adaptador de entrada (driving): traduce el mensaje Kafka y dispara TriggerSagaUseCase. El
 * offset se confirma SOLO si handle termina sin error; si lanza, no se confirma y Kafka
 * reentrega el mensaje (reproceso seguro por dedup de identidad + idempotencia downstream).
 */
@Component
public class SagaTriggerKafkaListener {

	private static final Logger log = LoggerFactory.getLogger(SagaTriggerKafkaListener.class);

	private final TriggerSagaUseCase triggerSagaUseCase;
	private final ObjectMapper objectMapper;

	public SagaTriggerKafkaListener(TriggerSagaUseCase triggerSagaUseCase, ObjectMapper objectMapper) {
		this.triggerSagaUseCase = triggerSagaUseCase;
		this.objectMapper = objectMapper;
	}

	@KafkaListener(topics = "${saga-manager.kafka.topic}")
	public void onMessage(String rawMessage, Acknowledgment acknowledgment) {
		try {
			SagaTriggerMessage message = objectMapper.readValue(rawMessage, SagaTriggerMessage.class);
			ExternalIdentity identity = ExternalIdentity.of(
					AppId.of(message.idApp()), ExternalRef.of(message.idExterno()));
			triggerSagaUseCase.handle(identity, SagaPayload.of(message.payload()));
			acknowledgment.acknowledge();
		} catch (Exception ex) {
			log.error("Failed to handle saga trigger message, offset will not be committed: {}",
					ex.getMessage(), ex);
			throw ex instanceof RuntimeException re ? re : new IllegalStateException(ex);
		}
	}
}
