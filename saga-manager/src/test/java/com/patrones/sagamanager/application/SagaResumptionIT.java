package com.patrones.sagamanager.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.patrones.sagamanager.SagaManagerApplication;
import com.patrones.sagamanager.application.port.in.ProcessSagaUseCase;
import com.patrones.sagamanager.application.port.out.FirstServicePort;
import com.patrones.sagamanager.application.port.out.SagaIdentityPort;
import com.patrones.sagamanager.application.port.out.SagaRepository;
import com.patrones.sagamanager.application.port.out.SecondServicePort;
import com.patrones.sagamanager.application.port.out.ThirdServicePort;
import com.patrones.sagamanager.domain.model.AppId;
import com.patrones.sagamanager.domain.model.ExternalId;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.ExternalRef;
import com.patrones.sagamanager.domain.model.IdempotencyKey;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaManager;
import com.patrones.sagamanager.domain.model.SagaPayload;
import com.patrones.sagamanager.domain.model.SagaStatus;
import com.patrones.sagamanager.domain.model.SagaStep;

/**
 * Partiendo de una saga a medias (FIRST_DONE ya persistido), el orquestador debe continuar
 * desde SECOND usando la IdempotencyKey determinista, sin reprocesar el paso FIRST.
 */
@SpringBootTest(classes = SagaManagerApplication.class)
class SagaResumptionIT {

	@Autowired
	private ProcessSagaUseCase processSagaUseCase;

	@Autowired
	private SagaIdentityPort identityPort;

	@Autowired
	private SagaRepository sagaRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockBean
	private FirstServicePort firstServicePort;

	@MockBean
	private SecondServicePort secondServicePort;

	@MockBean
	private ThirdServicePort thirdServicePort;

	@Test
	void resumesFromFirstDoneWithoutReprocessingCompletedSteps() {
		when(secondServicePort.call(any(), any())).thenReturn(ExternalId.of("second-1"));
		when(thirdServicePort.call(any(), any())).thenReturn(ExternalId.of("third-1"));

		SagaId sagaId = SagaId.newId();
		ExternalIdentity identity = ExternalIdentity.of(AppId.of("APP1"), ExternalRef.of("resume-1"));
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			identityPort.register(identity, sagaId);
			SagaManager started = SagaManager.start(sagaId, identity, SagaPayload.empty());
			sagaRepository.append(started);
			SagaManager firstDone = started.recordStep(SagaStep.FIRST, ExternalId.of("first-1"));
			sagaRepository.append(firstDone);
		});

		processSagaUseCase.process(sagaId);

		verify(firstServicePort, never()).call(any(), any());
		verify(secondServicePort).call(eq(IdempotencyKey.forStep(sagaId, SagaStep.SECOND)), any());
		verify(thirdServicePort).call(eq(IdempotencyKey.forStep(sagaId, SagaStep.THIRD)), any());

		SagaManager finalState = sagaRepository.findLatest(sagaId).orElseThrow();
		assertThat(finalState.status()).isEqualTo(SagaStatus.COMPLETED);
		assertThat(finalState.firstId()).contains(ExternalId.of("first-1"));
		assertThat(finalState.secondId()).contains(ExternalId.of("second-1"));
		assertThat(finalState.thirdId()).contains(ExternalId.of("third-1"));
		assertThat(finalState.version()).isEqualTo(4L);
	}
}
