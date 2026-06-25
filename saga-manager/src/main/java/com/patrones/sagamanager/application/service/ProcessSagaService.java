package com.patrones.sagamanager.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.patrones.sagamanager.application.port.in.ProcessSagaUseCase;
import com.patrones.sagamanager.application.port.out.FirstServicePort;
import com.patrones.sagamanager.application.port.out.SagaRepository;
import com.patrones.sagamanager.application.port.out.SecondServicePort;
import com.patrones.sagamanager.application.port.out.ThirdServicePort;
import com.patrones.sagamanager.domain.exception.ConcurrentSagaModificationException;
import com.patrones.sagamanager.domain.exception.SagaNotFoundException;
import com.patrones.sagamanager.domain.model.ExternalId;
import com.patrones.sagamanager.domain.model.IdempotencyKey;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaManager;
import com.patrones.sagamanager.domain.model.SagaPayload;
import com.patrones.sagamanager.domain.model.SagaStep;

/**
 * Orquesta los pasos pendientes de una saga: por cada paso llama al servicio downstream fuera
 * de transacción y persiste la nueva versión en su propia transacción corta (vía
 * {@link SagaStateWriter}) antes de continuar con el siguiente paso. Siempre reanuda a partir
 * de {@link SagaRepository#findLatest}, nunca de memoria.
 */
@Service
public class ProcessSagaService implements ProcessSagaUseCase {

	private static final Logger log = LoggerFactory.getLogger(ProcessSagaService.class);

	private final SagaRepository sagaRepository;
	private final SagaStateWriter sagaStateWriter;
	private final FirstServicePort firstServicePort;
	private final SecondServicePort secondServicePort;
	private final ThirdServicePort thirdServicePort;

	public ProcessSagaService(
			SagaRepository sagaRepository,
			SagaStateWriter sagaStateWriter,
			FirstServicePort firstServicePort,
			SecondServicePort secondServicePort,
			ThirdServicePort thirdServicePort) {
		this.sagaRepository = sagaRepository;
		this.sagaStateWriter = sagaStateWriter;
		this.firstServicePort = firstServicePort;
		this.secondServicePort = secondServicePort;
		this.thirdServicePort = thirdServicePort;
	}

	@Override
	public void process(SagaId sagaId) {
		SagaManager saga = sagaRepository.findLatest(sagaId)
				.orElseThrow(() -> new SagaNotFoundException("No saga state found for sagaId " + sagaId));

		while (saga.nextPendingStep().isPresent()) {
			SagaStep step = saga.nextPendingStep().orElseThrow();
			ExternalId externalId;
			try {
				externalId = callServiceFor(step, sagaId, saga.payload());
			} catch (RuntimeException ex) {
				log.error("Saga {} failed at step {}: {}", sagaId, step, ex.getMessage(), ex);
				SagaManager failed = saga.fail(ex.getMessage());
				sagaStateWriter.persist(failed);
				return;
			}
			saga = saga.recordStep(step, externalId);
			try {
				sagaStateWriter.persist(saga);
			} catch (ConcurrentSagaModificationException ex) {
				log.debug("Saga {} version {} already written by another processor, stopping", sagaId, saga.version());
				return;
			}
		}
	}

	private ExternalId callServiceFor(SagaStep step, SagaId sagaId, SagaPayload payload) {
		IdempotencyKey key = IdempotencyKey.forStep(sagaId, step);
		return switch (step) {
			case FIRST -> firstServicePort.call(key, payload);
			case SECOND -> secondServicePort.call(key, payload);
			case THIRD -> thirdServicePort.call(key, payload);
		};
	}
}
