package com.patrones.sagamanager.infrastructure.persistence;

import java.util.concurrent.Callable;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Soporte de test: ejecuta una acción en una transacción nueva e independiente, para que dos
 * hilos puedan competir por la misma fila con conexiones/transacciones separadas.
 */
@Component
public class TestTransactionHelper {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public <T> T runInNewTransaction(Callable<T> action) {
		try {
			return action.call();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
