package com.patrones.sagamanager.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.patrones.sagamanager.application.port.in.ProcessSagaUseCase;
import com.patrones.sagamanager.application.port.in.TriggerSagaUseCase;
import com.patrones.sagamanager.application.port.out.SagaIdentityPort;
import com.patrones.sagamanager.domain.exception.SagaAlreadyExistsException;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaPayload;

/**
 * Entrada única y compartida por Kafka y por el poller legacy. No es transaccional: delega la
 * creación atómica a {@link StartSagaService} y la orquestación a {@link ProcessSagaUseCase}.
 */
@Service
public class TriggerSagaService implements TriggerSagaUseCase {

	private static final int FIND_SAGA_ID_MAX_ATTEMPTS = 10;
	private static final long FIND_SAGA_ID_RETRY_DELAY_MS = 25;

	private final StartSagaService startSagaService;
	private final SagaIdentityPort identityPort;
	private final ProcessSagaUseCase processSagaUseCase;

	public TriggerSagaService(
			StartSagaService startSagaService,
			SagaIdentityPort identityPort,
			ProcessSagaUseCase processSagaUseCase) {
		this.startSagaService = startSagaService;
		this.identityPort = identityPort;
		this.processSagaUseCase = processSagaUseCase;
	}

	@Override
	public SagaId handle(ExternalIdentity identity, SagaPayload payload) {
		SagaId sagaId;
		try {
			sagaId = startSagaService.start(identity, payload);
		} catch (SagaAlreadyExistsException ex) {
			sagaId = awaitWinningSagaId(identity, ex);
		}
		processSagaUseCase.process(sagaId);
		return sagaId;
	}

	/**
	 * El registro perdió la carrera por la identidad, pero el ganador puede no haber confirmado
	 * su transacción todavía: reintenta brevemente antes de propagar el fallo.
	 */
	private SagaId awaitWinningSagaId(ExternalIdentity identity, SagaAlreadyExistsException cause) {
		for (int attempt = 1; attempt <= FIND_SAGA_ID_MAX_ATTEMPTS; attempt++) {
			Optional<SagaId> found = identityPort.findSagaId(identity);
			if (found.isPresent()) {
				return found.get();
			}
			sleep(FIND_SAGA_ID_RETRY_DELAY_MS);
		}
		throw new SagaAlreadyExistsException(
				"Identity " + identity + " reported as existing but no sagaId was found", cause);
	}

	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
