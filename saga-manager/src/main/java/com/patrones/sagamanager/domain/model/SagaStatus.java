package com.patrones.sagamanager.domain.model;

public enum SagaStatus {
	STARTED,
	FIRST_DONE,
	SECOND_DONE,
	COMPLETED,
	FAILED;

	public boolean isTerminal() {
		return this == COMPLETED || this == FAILED;
	}
}
