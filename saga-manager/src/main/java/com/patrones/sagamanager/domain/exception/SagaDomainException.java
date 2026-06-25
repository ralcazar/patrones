package com.patrones.sagamanager.domain.exception;

public abstract class SagaDomainException extends RuntimeException {

	protected SagaDomainException(String message) {
		super(message);
	}

	protected SagaDomainException(String message, Throwable cause) {
		super(message, cause);
	}
}
