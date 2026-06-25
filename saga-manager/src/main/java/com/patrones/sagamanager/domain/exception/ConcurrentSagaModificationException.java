package com.patrones.sagamanager.domain.exception;

public class ConcurrentSagaModificationException extends SagaDomainException {

	public ConcurrentSagaModificationException(String message) {
		super(message);
	}

	public ConcurrentSagaModificationException(String message, Throwable cause) {
		super(message, cause);
	}
}
