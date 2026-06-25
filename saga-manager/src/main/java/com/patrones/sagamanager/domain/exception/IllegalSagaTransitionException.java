package com.patrones.sagamanager.domain.exception;

public class IllegalSagaTransitionException extends SagaDomainException {

	public IllegalSagaTransitionException(String message) {
		super(message);
	}
}
