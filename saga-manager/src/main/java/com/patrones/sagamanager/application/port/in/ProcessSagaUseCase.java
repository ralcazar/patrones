package com.patrones.sagamanager.application.port.in;

import com.patrones.sagamanager.domain.model.SagaId;

/**
 * Orquestación/reanudación de una saga por su id técnico. Delegado interno de
 * {@link TriggerSagaUseCase}; también reutilizable por un futuro retry.
 */
public interface ProcessSagaUseCase {

	void process(SagaId sagaId);
}
