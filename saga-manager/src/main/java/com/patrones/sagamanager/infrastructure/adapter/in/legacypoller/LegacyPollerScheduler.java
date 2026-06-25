package com.patrones.sagamanager.infrastructure.adapter.in.legacypoller;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.patrones.sagamanager.application.port.in.TriggerSagaUseCase;
import com.patrones.sagamanager.domain.model.AppId;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.ExternalRef;
import com.patrones.sagamanager.domain.model.SagaPayload;
import com.patrones.sagamanager.infrastructure.config.LegacyPollerProperties;

/**
 * Adaptador de entrada (driving): sondea la tabla legacy y dispara el MISMO TriggerSagaUseCase
 * que el adaptador Kafka. Toda fila legacy pertenece al app LEGACY; su id_borrador es el
 * ExternalRef. El claim atómico evita que dos workers cojan la misma fila; la dedup de
 * (id_app, id_externo) evita saga duplicada aunque la fila se procesara dos veces.
 */
@Component
public class LegacyPollerScheduler {

	private static final Logger log = LoggerFactory.getLogger(LegacyPollerScheduler.class);

	private final LegacyPollerRepository repository;
	private final TriggerSagaUseCase triggerSagaUseCase;
	private final LegacyPollerProperties properties;
	private final ObjectMapper objectMapper;

	public LegacyPollerScheduler(
			LegacyPollerRepository repository,
			TriggerSagaUseCase triggerSagaUseCase,
			LegacyPollerProperties properties,
			ObjectMapper objectMapper) {
		this.repository = repository;
		this.triggerSagaUseCase = triggerSagaUseCase;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Scheduled(fixedDelayString = "${saga-manager.legacy-poller.fixed-delay-ms}")
	public void pollAndProcess() {
		if (!properties.isEnabled()) {
			return;
		}
		Instant now = Instant.now();
		Instant leaseThreshold = now.minusSeconds(properties.getLeaseSeconds());

		for (long id : repository.findClaimableIds(properties.getPageSize(), leaseThreshold)) {
			if (!repository.claim(id, leaseThreshold, now)) {
				log.debug("Legacy row {} was claimed by another worker, skipping", id);
				continue;
			}
			processClaimedRow(id);
		}
	}

	private void processClaimedRow(long id) {
		LegacyRequestRow row = repository.findById(id)
				.orElseThrow(() -> new IllegalStateException("Claimed legacy row " + id + " disappeared"));
		try {
			ExternalIdentity identity = ExternalIdentity.of(AppId.legacy(), ExternalRef.of(row.idBorrador()));
			triggerSagaUseCase.handle(identity, parsePayload(row.payload()));
			repository.markFinal(id, properties.getSuccessStatus());
		} catch (Exception ex) {
			log.error("Failed to process legacy row {}, leaving lease to expire for retry: {}",
					id, ex.getMessage(), ex);
		}
	}

	private SagaPayload parsePayload(String payload) {
		if (payload == null) {
			return SagaPayload.empty();
		}
		try {
			Map<String, Object> attributes = objectMapper.readValue(payload, new TypeReference<>() {
			});
			return SagaPayload.of(attributes);
		} catch (Exception ex) {
			throw new IllegalStateException("Could not parse legacy payload", ex);
		}
	}
}
