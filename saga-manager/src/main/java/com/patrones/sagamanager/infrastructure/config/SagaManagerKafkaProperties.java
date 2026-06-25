package com.patrones.sagamanager.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "saga-manager.kafka")
public class SagaManagerKafkaProperties {

	/** Topic de entrada con los disparos de saga (id_app, id_externo, payload). */
	private String topic = "saga-manager.trigger";

	public String getTopic() {
		return topic;
	}

	public void setTopic(String topic) {
		this.topic = topic;
	}
}
