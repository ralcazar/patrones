package com.patrones.sagamanager.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import com.patrones.sagamanager.SagaManagerApplication;
import com.patrones.sagamanager.application.port.out.FirstServicePort;
import com.patrones.sagamanager.application.port.out.SagaIdentityPort;
import com.patrones.sagamanager.application.port.out.SecondServicePort;
import com.patrones.sagamanager.application.port.out.ThirdServicePort;
import com.patrones.sagamanager.domain.model.AppId;
import com.patrones.sagamanager.domain.model.ExternalId;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.ExternalRef;

/**
 * Un mensaje Kafka consumido dispara TriggerSagaUseCase.handle. No hay Docker disponible en
 * este entorno, así que se usa el broker embebido de spring-kafka-test en vez de Testcontainers.
 */
@SpringBootTest(
		classes = SagaManagerApplication.class,
		properties = {
				"spring.autoconfigure.exclude=",
				"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
				"saga-manager.kafka.topic=saga-manager.trigger.test"
		})
@EmbeddedKafka(partitions = 1, topics = "saga-manager.trigger.test")
@DirtiesContext
class SagaTriggerKafkaListenerIT {

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private SagaIdentityPort identityPort;

	@MockBean
	private FirstServicePort firstServicePort;

	@MockBean
	private SecondServicePort secondServicePort;

	@MockBean
	private ThirdServicePort thirdServicePort;

	@Test
	void consumingAMessageTriggersTheSagaAndCommitsTheOffsetOnlyAfterSuccess() {
		when(firstServicePort.call(any(), any())).thenReturn(ExternalId.of("first-1"));
		when(secondServicePort.call(any(), any())).thenReturn(ExternalId.of("second-1"));
		when(thirdServicePort.call(any(), any())).thenReturn(ExternalId.of("third-1"));

		String message = "{\"id_app\":\"APP1\",\"id_externo\":\"kafka-1\",\"payload\":{\"foo\":\"bar\"}}";
		kafkaTemplate.send("saga-manager.trigger.test", message);

		ExternalIdentity identity = ExternalIdentity.of(AppId.of("APP1"), ExternalRef.of("kafka-1"));
		await().atMost(Duration.ofSeconds(15))
				.untilAsserted(() -> assertThat(identityPort.findSagaId(identity)).isPresent());
	}
}
