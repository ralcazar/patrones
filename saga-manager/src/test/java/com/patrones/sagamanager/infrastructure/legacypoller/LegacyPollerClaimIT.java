package com.patrones.sagamanager.infrastructure.legacypoller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import com.patrones.sagamanager.SagaManagerApplication;
import com.patrones.sagamanager.application.port.in.TriggerSagaUseCase;
import com.patrones.sagamanager.application.port.out.FirstServicePort;
import com.patrones.sagamanager.application.port.out.SecondServicePort;
import com.patrones.sagamanager.application.port.out.ThirdServicePort;
import com.patrones.sagamanager.domain.model.ExternalId;
import com.patrones.sagamanager.infrastructure.adapter.in.legacypoller.LegacyPollerRepository;
import com.patrones.sagamanager.infrastructure.adapter.in.legacypoller.LegacyPollerScheduler;

@SpringBootTest(
		classes = SagaManagerApplication.class,
		properties = "saga-manager.legacy-poller.enabled=true")
class LegacyPollerClaimIT {

	@Autowired
	private LegacyPollerRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private LegacyPollerScheduler scheduler;

	@Autowired
	private TriggerSagaUseCase triggerSagaUseCase;

	@MockBean
	private FirstServicePort firstServicePort;

	@MockBean
	private SecondServicePort secondServicePort;

	@MockBean
	private ThirdServicePort thirdServicePort;

	@Test
	void onlyOneOfTwoConcurrentClaimsSucceedsOnTheSameRow() throws Exception {
		long id = insertLegacyRow("draft-claim-1", "PDTE-PROCESAR", null);
		Instant leaseThreshold = Instant.now().minusSeconds(30);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch goLatch = new CountDownLatch(1);
		AtomicInteger claimed = new AtomicInteger();

		Runnable claimAttempt = () -> {
			readyLatch.countDown();
			await(goLatch);
			if (repository.claim(id, leaseThreshold, Instant.now())) {
				claimed.incrementAndGet();
			}
		};

		pool.submit(claimAttempt);
		pool.submit(claimAttempt);
		readyLatch.await();
		goLatch.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		assertThat(claimed.get()).isEqualTo(1);
	}

	@Test
	void expiredLeaseRowBecomesClaimableAgain() {
		long id = insertLegacyRow("draft-claim-2", "PROCESANDO", Instant.now().minusSeconds(120));
		Instant leaseThreshold = Instant.now().minusSeconds(30);

		List<Long> claimable = repository.findClaimableIds(10, leaseThreshold);
		assertThat(claimable).contains(id);
		assertThat(repository.claim(id, leaseThreshold, Instant.now())).isTrue();
	}

	@Test
	void singleLegacyRowProducesExactlyOneSaga() {
		when(firstServicePort.call(any(), any())).thenReturn(ExternalId.of("first-1"));
		when(secondServicePort.call(any(), any())).thenReturn(ExternalId.of("second-1"));
		when(thirdServicePort.call(any(), any())).thenReturn(ExternalId.of("third-1"));

		insertLegacyRow("draft-claim-3", "PDTE-PROCESAR", null);

		scheduler.pollAndProcess();

		verify(firstServicePort).call(any(), any());
		String finalState = jdbcTemplate.queryForObject(
				"SELECT estado FROM legacy_request WHERE id_borrador = ?", String.class, "draft-claim-3");
		assertThat(finalState).isEqualTo("PROCESADO");
	}

	private long insertLegacyRow(String idBorrador, String estado, Instant fechaCogida) {
		jdbcTemplate.update(
				"INSERT INTO legacy_request (id_borrador, payload, estado, fecha_cogida, created_at) "
						+ "VALUES (?, ?, ?, ?, ?)",
				idBorrador, "{}", estado, fechaCogida, Instant.now());
		return jdbcTemplate.queryForObject(
				"SELECT id FROM legacy_request WHERE id_borrador = ?", Long.class, idBorrador);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
