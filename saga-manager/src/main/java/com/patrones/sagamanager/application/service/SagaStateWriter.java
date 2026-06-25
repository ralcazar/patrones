package com.patrones.sagamanager.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patrones.sagamanager.application.port.out.SagaRepository;
import com.patrones.sagamanager.domain.model.SagaManager;

/**
 * Persiste una versión del agregado en su propia transacción corta, bean separado del
 * orquestador para evitar la auto-invocación de proxies de Spring (@Transactional solo
 * funciona en llamadas externas al bean).
 */
@Service
public class SagaStateWriter {

	private final SagaRepository sagaRepository;

	public SagaStateWriter(SagaRepository sagaRepository) {
		this.sagaRepository = sagaRepository;
	}

	@Transactional
	public void persist(SagaManager saga) {
		sagaRepository.append(saga);
	}
}
