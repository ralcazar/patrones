package com.patrones.sagamanager.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patrones.sagamanager.application.port.out.SagaIdentityPort;
import com.patrones.sagamanager.application.port.out.SagaRepository;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaManager;
import com.patrones.sagamanager.domain.model.SagaPayload;

/**
 * Crea una saga nueva en una única transacción corta y atómica: registra la identidad externa
 * y la versión 1 del estado. Si la identidad ya existe, ambos inserts se revierten y el caller
 * (TriggerSagaService) debe buscar el sagaId existente y reanudar esa saga.
 */
@Service
public class StartSagaService {

	private final SagaIdentityPort identityPort;
	private final SagaRepository sagaRepository;

	public StartSagaService(SagaIdentityPort identityPort, SagaRepository sagaRepository) {
		this.identityPort = identityPort;
		this.sagaRepository = sagaRepository;
	}

	@Transactional
	public SagaId start(ExternalIdentity identity, SagaPayload payload) {
		SagaId sagaId = SagaId.newId();
		identityPort.register(identity, sagaId);
		sagaRepository.append(SagaManager.start(sagaId, identity, payload));
		return sagaId;
	}
}
