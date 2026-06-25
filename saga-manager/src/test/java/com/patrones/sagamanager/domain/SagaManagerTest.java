package com.patrones.sagamanager.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.patrones.sagamanager.domain.exception.IllegalSagaTransitionException;
import com.patrones.sagamanager.domain.model.AppId;
import com.patrones.sagamanager.domain.model.ExternalId;
import com.patrones.sagamanager.domain.model.ExternalIdentity;
import com.patrones.sagamanager.domain.model.ExternalRef;
import com.patrones.sagamanager.domain.model.SagaId;
import com.patrones.sagamanager.domain.model.SagaManager;
import com.patrones.sagamanager.domain.model.SagaPayload;
import com.patrones.sagamanager.domain.model.SagaStatus;
import com.patrones.sagamanager.domain.model.SagaStep;

class SagaManagerTest {

	private final ExternalIdentity identity = ExternalIdentity.of(AppId.of("APP1"), ExternalRef.of("ext-1"));

	@Test
	void startCreatesVersionOneInStartedStatusWithoutIds() {
		SagaId id = SagaId.newId();
		SagaManager saga = SagaManager.start(id, identity, SagaPayload.empty());

		assertThat(saga.id()).isEqualTo(id);
		assertThat(saga.identity()).isEqualTo(identity);
		assertThat(saga.version()).isEqualTo(1L);
		assertThat(saga.status()).isEqualTo(SagaStatus.STARTED);
		assertThat(saga.firstId()).isEmpty();
		assertThat(saga.secondId()).isEmpty();
		assertThat(saga.thirdId()).isEmpty();
		assertThat(saga.nextPendingStep()).contains(SagaStep.FIRST);
	}

	@Test
	void recordStepAdvancesStrictlyInOrderIncrementingVersion() {
		SagaManager saga = SagaManager.start(SagaId.newId(), identity, SagaPayload.empty());

		saga = saga.recordStep(SagaStep.FIRST, ExternalId.of("first-1"));
		assertThat(saga.version()).isEqualTo(2L);
		assertThat(saga.status()).isEqualTo(SagaStatus.FIRST_DONE);
		assertThat(saga.firstId()).contains(ExternalId.of("first-1"));
		assertThat(saga.nextPendingStep()).contains(SagaStep.SECOND);

		saga = saga.recordStep(SagaStep.SECOND, ExternalId.of("second-1"));
		assertThat(saga.version()).isEqualTo(3L);
		assertThat(saga.status()).isEqualTo(SagaStatus.SECOND_DONE);
		assertThat(saga.nextPendingStep()).contains(SagaStep.THIRD);

		saga = saga.recordStep(SagaStep.THIRD, ExternalId.of("third-1"));
		assertThat(saga.version()).isEqualTo(4L);
		assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
		assertThat(saga.nextPendingStep()).isEmpty();
	}

	@Test
	void recordStepRejectsOutOfOrderTransitions() {
		SagaManager saga = SagaManager.start(SagaId.newId(), identity, SagaPayload.empty());

		assertThatThrownBy(() -> saga.recordStep(SagaStep.SECOND, ExternalId.of("second-1")))
				.isInstanceOf(IllegalSagaTransitionException.class);
		assertThatThrownBy(() -> saga.recordStep(SagaStep.THIRD, ExternalId.of("third-1")))
				.isInstanceOf(IllegalSagaTransitionException.class);
	}

	@Test
	void completedSagaIsTerminalAndRejectsFurtherTransitions() {
		SagaManager saga = SagaManager.start(SagaId.newId(), identity, SagaPayload.empty())
				.recordStep(SagaStep.FIRST, ExternalId.of("first-1"))
				.recordStep(SagaStep.SECOND, ExternalId.of("second-1"))
				.recordStep(SagaStep.THIRD, ExternalId.of("third-1"));

		assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
		assertThatThrownBy(() -> saga.recordStep(SagaStep.FIRST, ExternalId.of("x")))
				.isInstanceOf(IllegalSagaTransitionException.class);
		assertThatThrownBy(() -> saga.fail("boom"))
				.isInstanceOf(IllegalSagaTransitionException.class);
	}

	@Test
	void failedSagaIsTerminal() {
		SagaManager started = SagaManager.start(SagaId.newId(), identity, SagaPayload.empty());
		SagaManager saga = started.fail("downstream unavailable");

		assertThat(saga.status()).isEqualTo(SagaStatus.FAILED);
		assertThat(saga.version()).isEqualTo(2L);
		assertThat(saga.failureReason()).contains("downstream unavailable");
		assertThat(saga.nextPendingStep()).isEmpty();
		assertThatThrownBy(() -> saga.recordStep(SagaStep.FIRST, ExternalId.of("x")))
				.isInstanceOf(IllegalSagaTransitionException.class);
	}
}
