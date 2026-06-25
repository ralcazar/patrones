package com.patrones.sagamanager.application.port.in;

import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaPayload;

/**
 * Entrada única compartida por los adaptadores de entrada (Kafka y poller legacy). Hace
 * find-or-create de la saga por su identidad externa y dispara/reanuda su procesamiento.
 */
public interface TriggerSagaUseCase {

	SagaId handle(ExternalIdentity identity, SagaPayload payload);
}
