package com.patrones.sagamanager.domain.model;

import java.util.Optional;

import com.patrones.sagamanager.domain.exception.IllegalSagaTransitionException;

/**
 * Agregado raíz de la saga. Inmutable: cada transición devuelve una nueva instancia con
 * version incrementada en 1, modelando una tabla de histórico append-only.
 */
public final class SagaManager {

	private final SagaId id;
	private final ExternalIdentity identity;
	private final long version;
	private final SagaStatus status;
	private final SagaPayload payload;
	private final ExternalId firstId;
	private final ExternalId secondId;
	private final ExternalId thirdId;
	private final String failureReason;

	private SagaManager(
			SagaId id,
			ExternalIdentity identity,
			long version,
			SagaStatus status,
			SagaPayload payload,
			ExternalId firstId,
			ExternalId secondId,
			ExternalId thirdId,
			String failureReason) {
		this.id = id;
		this.identity = identity;
		this.version = version;
		this.status = status;
		this.payload = payload;
		this.firstId = firstId;
		this.secondId = secondId;
		this.thirdId = thirdId;
		this.failureReason = failureReason;
	}

	public static SagaManager start(SagaId id, ExternalIdentity identity, SagaPayload payload) {
		return new SagaManager(id, identity, 1L, SagaStatus.STARTED, payload, null, null, null, null);
	}

	public static SagaManager rehydrate(
			SagaId id,
			ExternalIdentity identity,
			long version,
			SagaStatus status,
			SagaPayload payload,
			ExternalId firstId,
			ExternalId secondId,
			ExternalId thirdId,
			String failureReason) {
		return new SagaManager(id, identity, version, status, payload, firstId, secondId, thirdId, failureReason);
	}

	public SagaManager recordStep(SagaStep step, ExternalId externalId) {
		if (status.isTerminal()) {
			throw new IllegalSagaTransitionException(
					"Saga " + id + " is terminal (" + status + "), cannot record step " + step);
		}
		Optional<SagaStep> expected = nextPendingStep();
		if (expected.isEmpty() || expected.get() != step) {
			throw new IllegalSagaTransitionException(
					"Saga " + id + " expected step " + expected.orElse(null) + " but got " + step);
		}

		ExternalId newFirstId = firstId;
		ExternalId newSecondId = secondId;
		ExternalId newThirdId = thirdId;
		SagaStatus newStatus;

		switch (step) {
			case FIRST -> {
				newFirstId = externalId;
				newStatus = SagaStatus.FIRST_DONE;
			}
			case SECOND -> {
				newSecondId = externalId;
				newStatus = SagaStatus.SECOND_DONE;
			}
			case THIRD -> {
				newThirdId = externalId;
				newStatus = SagaStatus.COMPLETED;
			}
			default -> throw new IllegalSagaTransitionException("Unknown step " + step);
		}

		return new SagaManager(
				id, identity, version + 1, newStatus, payload, newFirstId, newSecondId, newThirdId, null);
	}

	public SagaManager fail(String reason) {
		if (status.isTerminal()) {
			throw new IllegalSagaTransitionException(
					"Saga " + id + " is terminal (" + status + "), cannot fail");
		}
		return new SagaManager(
				id, identity, version + 1, SagaStatus.FAILED, payload, firstId, secondId, thirdId, reason);
	}

	/**
	 * Deriva el siguiente paso pendiente del estado persistido, permitiendo reanudación sin
	 * depender de memoria.
	 */
	public Optional<SagaStep> nextPendingStep() {
		return switch (status) {
			case STARTED -> Optional.of(SagaStep.FIRST);
			case FIRST_DONE -> Optional.of(SagaStep.SECOND);
			case SECOND_DONE -> Optional.of(SagaStep.THIRD);
			case COMPLETED, FAILED -> Optional.empty();
		};
	}

	public SagaId id() {
		return id;
	}

	public ExternalIdentity identity() {
		return identity;
	}

	public long version() {
		return version;
	}

	public SagaStatus status() {
		return status;
	}

	public SagaPayload payload() {
		return payload;
	}

	public Optional<ExternalId> firstId() {
		return Optional.ofNullable(firstId);
	}

	public Optional<ExternalId> secondId() {
		return Optional.ofNullable(secondId);
	}

	public Optional<ExternalId> thirdId() {
		return Optional.ofNullable(thirdId);
	}

	public Optional<String> failureReason() {
		return Optional.ofNullable(failureReason);
	}
}
