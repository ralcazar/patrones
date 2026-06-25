package com.patrones.sagamanager.domain.exception;

public class SagaAlreadyExistsException extends SagaDomainException {

	public SagaAlreadyExistsException(String message) {
		super(message);
	}

	public SagaAlreadyExistsException(String message, Throwable cause) {
		super(message, cause);
	}
}
