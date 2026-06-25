package com.patrones.sagamanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.patrones.sagamanager.SagaManagerApplication;
import com.patrones.sagamanager.application.port.in.TriggerSagaUseCase;
import com.patrones.sagamanager.application.port.out.FirstServicePort;
import com.patrones.sagamanager.application.port.out.SagaIdentityPort;
import com.patrones.sagamanager.application.port.out.SecondServicePort;
import com.patrones.sagamanager.application.port.out.ThirdServicePort;
import com.patrones.sagamanager.domain.model.AppId;
import com.patrones.sagamanager.domain.model.ExternalId;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.ExternalRef;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaPayload;

/**
 * Dos invocaciones concurrentes de TriggerSaga.handle con la MISMA (id_app, id_externo) deben
 * producir UNA sola fila en saga_identity y el MISMO sagaId. Incluye el caso "redelivery":
 * una segunda invocación sobre una saga ya COMPLETED es un no-op (no vuelve a llamar a los
 * servicios downstream).
 */
@SpringBootTest(classes = SagaManagerApplication.class)
class SagaIdentityDedupIT {

	@Autowired
	private TriggerSagaUseCase triggerSagaUseCase;

	@Autowired
	private SagaIdentityPort identityPort;

	@MockBean
	private FirstServicePort firstServicePort;

	@MockBean
	private SecondServicePort secondServicePort;

	@MockBean
	private ThirdServicePort thirdServicePort;

	@Test
	void concurrentTriggersWithSameIdentityResolveToTheSameSingleSaga() throws Exception {
		when(firstServicePort.call(any(), any())).thenReturn(ExternalId.of("first-1"));
		when(secondServicePort.call(any(), any())).thenReturn(ExternalId.of("second-1"));
		when(thirdServicePort.call(any(), any())).thenReturn(ExternalId.of("third-1"));

		ExternalIdentity identity = ExternalIdentity.of(AppId.of("APP1"), ExternalRef.of("dedup-1"));

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch goLatch = new CountDownLatch(1);
		AtomicReference<SagaId> resultA = new AtomicReference<>();
		AtomicReference<SagaId> resultB = new AtomicReference<>();

		pool.submit(() -> {
			readyLatch.countDown();
			await(goLatch);
			resultA.set(triggerSagaUseCase.handle(identity, SagaPayload.empty()));
		});
		pool.submit(() -> {
			readyLatch.countDown();
			await(goLatch);
			resultB.set(triggerSagaUseCase.handle(identity, SagaPayload.empty()));
		});

		readyLatch.await();
		goLatch.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(resultA.get()).isNotNull().isEqualTo(resultB.get());
		assertThat(identityPort.findSagaId(identity)).contains(resultA.get());

		// Bajo la carrera real, ambos triggers pueden invocar el downstream antes de que el
		// optimistic-lock decida el ganador (la idempotencia es responsabilidad del downstream,
		// vía la misma IdempotencyKey); lo que SÍ garantizamos es que solo una saga avanza a
		// COMPLETED y que la redelivery sobre una saga ya terminada no genera llamadas nuevas.
		verify(firstServicePort, atLeastOnce()).call(any(), any());
		verify(secondServicePort, atLeastOnce()).call(any(), any());
		verify(thirdServicePort, atLeastOnce()).call(any(), any());
		int firstCallsBeforeRedelivery = mockingDetails(firstServicePort).getInvocations().size();
		int secondCallsBeforeRedelivery = mockingDetails(secondServicePort).getInvocations().size();
		int thirdCallsBeforeRedelivery = mockingDetails(thirdServicePort).getInvocations().size();

		// Redelivery: una tercera invocación sobre la misma identidad ya COMPLETED es un no-op.
		SagaId thirdCall = triggerSagaUseCase.handle(identity, SagaPayload.empty());
		assertThat(thirdCall).isEqualTo(resultA.get());
		assertThat(mockingDetails(firstServicePort).getInvocations()).hasSize(firstCallsBeforeRedelivery);
		assertThat(mockingDetails(secondServicePort).getInvocations()).hasSize(secondCallsBeforeRedelivery);
		assertThat(mockingDetails(thirdServicePort).getInvocations()).hasSize(thirdCallsBeforeRedelivery);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
