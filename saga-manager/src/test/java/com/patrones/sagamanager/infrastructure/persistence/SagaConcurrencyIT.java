package com.patrones.sagamanager.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.patrones.sagamanager.SagaManagerApplication;
import com.patrones.sagamanager.application.port.out.SagaIdentityPort;
import com.patrones.sagamanager.application.port.out.SagaRepository;
import com.patrones.sagamanager.domain.exception.ConcurrentSagaModificationException;
import com.patrones.sagamanager.domain.model.AppId;
import com.patrones.sagamanager.domain.model.ExternalId;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.ExternalRef;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaManager;
import com.patrones.sagamanager.domain.model.SagaPayload;
import com.patrones.sagamanager.domain.model.SagaStep;

/**
 * Verifica el lock optimista vía PK(saga_id, version): dos hilos cargan la saga en la misma
 * versión y ambos intentan append de N+1; exactamente uno gana. Corre contra el fallback H2
 * (no hay Docker disponible en este entorno); el mismo test es válido contra Postgres.
 */
@SpringBootTest(classes = SagaManagerApplication.class)
class SagaConcurrencyIT {

	@Autowired
	private SagaIdentityPort identityPort;

	@Autowired
	private SagaRepository sagaRepository;

	@Autowired
	private TestTransactionHelper transactionHelper;

	@Test
	void exactlyOneConcurrentAppendOfTheSameNextVersionWins() throws Exception {
		SagaId sagaId = SagaId.newId();
		ExternalIdentity identity = ExternalIdentity.of(AppId.of("APP1"), ExternalRef.of("concurrency-1"));
		transactionHelper.runInNewTransaction(() -> {
			identityPort.register(identity, sagaId);
			sagaRepository.append(SagaManager.start(sagaId, identity, SagaPayload.empty()));
			return null;
		});

		SagaManager baseline = sagaRepository.findLatest(sagaId).orElseThrow();
		assertThat(baseline.version()).isEqualTo(1L);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch goLatch = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		AtomicInteger conflicts = new AtomicInteger();

		Runnable attempt = () -> {
			readyLatch.countDown();
			try {
				goLatch.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			SagaManager nextVersion = baseline.recordStep(SagaStep.FIRST, ExternalId.of("first-1"));
			try {
				transactionHelper.runInNewTransaction(() -> {
					sagaRepository.append(nextVersion);
					return null;
				});
				successes.incrementAndGet();
			} catch (ConcurrentSagaModificationException ex) {
				conflicts.incrementAndGet();
			}
		};

		pool.submit(attempt);
		pool.submit(attempt);
		readyLatch.await();
		goLatch.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(successes.get()).isEqualTo(1);
		assertThat(conflicts.get()).isEqualTo(1);
		assertThat(sagaRepository.findLatest(sagaId).orElseThrow().version()).isEqualTo(2L);
	}
}
