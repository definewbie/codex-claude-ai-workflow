package app.cryptoincome.funding;

import app.cryptoincome.funding.application.FundingRateSyncResult;
import app.cryptoincome.funding.application.FundingRateSyncService;
import app.cryptoincome.funding.domain.DerivativeInstrumentType;
import app.cryptoincome.funding.domain.FundingFormulaType;
import app.cryptoincome.funding.domain.FundingRateHistorySource;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSourceException;
import app.cryptoincome.funding.domain.FundingRateSyncInProgressException;
import app.cryptoincome.funding.domain.FundingRateSyncStore;
import app.cryptoincome.funding.domain.FundingSettlementMethod;
import app.cryptoincome.funding.domain.StaleExecution;
import app.cryptoincome.funding.domain.StaleRecoveryOutcome;
import app.cryptoincome.funding.infrastructure.mybatis.FundingRateFactMapper;
import app.cryptoincome.funding.infrastructure.mybatis.FundingSyncRequestEntity;
import app.cryptoincome.funding.infrastructure.mybatis.FundingSyncRequestMapper;
import app.cryptoincome.run.domain.AnalysisRunStatus;
import app.cryptoincome.run.application.AnalysisRunService;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunEntity;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FundingRateSyncServiceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("crypto_income")
            .withUsername("crypto_income")
            .withPassword("test-password")
            .withEnv("TZ", "UTC");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired FundingRateSyncStore store;
    @Autowired FundingRateFactMapper factMapper;
    @Autowired FundingSyncRequestMapper requestMapper;
    @Autowired AnalysisRunMapper runMapper;
    @Autowired AnalysisRunService runService;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistFactsAndReplaySuccessfulLogicalRequestIdempotently() {
        int factsBefore = factMapper.countFacts();
        int observationsBefore = factMapper.countAllObservations();
        AtomicInteger sourceCalls = new AtomicInteger();
        FundingRateSyncService service = service((instrument, limit) -> {
            sourceCalls.incrementAndGet();
            return List.of(observation("0.000118", "c"));
        });

        FundingRateSyncResult first = service.syncLatest("btc-usdt-swap", 10, "success-replay");
        FundingRateSyncResult replay = service.syncLatest("BTC-USDT-SWAP", 10, "success-replay");
        FundingRateSyncResult anotherRequest = service.syncLatest("BTC-USDT-SWAP", 10, "another-request");

        assertThat(first.status()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.analysisRunId()).isEqualTo(first.analysisRunId());
        assertThat(anotherRequest.analysisRunId()).isNotEqualTo(first.analysisRunId());
        assertThat(sourceCalls).hasValue(2);
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore + 1);
        assertThat(factMapper.countAllObservations()).isEqualTo(observationsBefore + 2);
    }

    @Test
    void shouldAtomicallyRollbackFactsThenCreateANewAttemptAfterPersistenceFailure() {
        int factsBefore = factMapper.countFacts();
        int observationsBefore = factMapper.countAllObservations();
        String syncKey = "atomic-failure-retry";

        assertThatThrownBy(() -> service((instrument, limit) -> List.of(invalidJsonObservation()))
                .syncLatest("BTC-USDT-SWAP", 1, syncKey)).isInstanceOf(RuntimeException.class);

        FundingSyncRequestEntity request = request(syncKey, 1);
        AnalysisRunEntity failed = runMapper.selectBySyncRequestAndAttempt(request.getId(), 1);
        assertThat(request.getState()).isEqualTo("READY");
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailureCode()).isEqualTo("PERSISTENCE_OR_INTERNAL_ERROR");
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore);
        assertThat(factMapper.countAllObservations()).isEqualTo(observationsBefore);

        FundingRateSyncResult retry = service((instrument, limit) -> List.of(observation("0.000117", "d")))
                .syncLatest("BTC-USDT-SWAP", 1, syncKey);
        AnalysisRunEntity succeeded = runMapper.selectById(retry.analysisRunId());
        assertThat(succeeded.getAttemptNo()).isEqualTo(2);
        assertThat(succeeded.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldAllowOnlyOneConcurrentExecutionForTheSameLogicalRequest() throws Exception {
        CountDownLatch sourceEntered = new CountDownLatch(1);
        CountDownLatch releaseSource = new CountDownLatch(1);
        AtomicInteger sourceCalls = new AtomicInteger();
        FundingRateSyncService service = service((instrument, limit) -> {
            sourceCalls.incrementAndGet();
            sourceEntered.countDown();
            await(releaseSource);
            return List.of(observation("0.000116", "e"));
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<FundingRateSyncResult> first = executor.submit(
                    () -> service.syncLatest("BTC-USDT-SWAP", 1, "concurrent-request"));
            assertThat(sourceEntered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.syncLatest("BTC-USDT-SWAP", 1, "concurrent-request"))
                    .isInstanceOf(FundingRateSyncInProgressException.class);
            releaseSource.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
            assertThat(sourceCalls).hasValue(1);
        }
    }

    @Test
    void shouldRecoverStaleExecutionWithoutReopeningItsTerminalState() {
        String syncKey = "stale-recovery";
        String key = requestKey(syncKey, 1);
        var first = store.acquire(key, "BTC-USDT-SWAP", 1);
        ageRunByOneHour(first.run().id());
        FundingRateSyncService service = service(
                (instrument, limit) -> List.of(observation("0.000115", "f")));

        assertThat(service.recoverStale(key, first.run().id(), Duration.ofMinutes(15)))
                .isEqualTo(StaleRecoveryOutcome.RECOVERED);
        AnalysisRunEntity recovered = runMapper.selectById(first.run().id());
        assertThat(recovered.getStatus()).isEqualTo("FAILED");
        assertThat(recovered.getFailureCode()).isEqualTo("STALE_RECOVERED");
        assertThatThrownBy(() -> store.complete(
                first.requestId(), first.run().id(), List.of(observation("0.000113", "9"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("失去当前同步请求的执行权");

        FundingRateSyncResult retry = service.syncLatest("BTC-USDT-SWAP", 1, syncKey);
        AnalysisRunEntity second = runMapper.selectById(retry.analysisRunId());
        assertThat(second.getAttemptNo()).isEqualTo(2);
        assertThat(second.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(runMapper.selectById(first.run().id()).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void listStaleReportsOnlyAgedRunningExecutionsUsingDatabaseClock() {
        String agedKey = requestKey("stale-aged", 1);
        String freshKey = requestKey("stale-fresh", 1);
        var aged = store.acquire(agedKey, "BTC-USDT-SWAP", 1);
        store.acquire(freshKey, "BTC-USDT-SWAP", 1);
        ageRunByOneHour(aged.run().id());

        List<StaleExecution> stale = service((instrument, limit) -> List.of())
                .listStale(Duration.ofMinutes(15));

        assertThat(stale).extracting(StaleExecution::requestKey)
                .contains(agedKey)
                .doesNotContain(freshKey);
        assertThat(stale).filteredOn(view -> view.requestKey().equals(agedKey))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.runId()).isEqualTo(aged.run().id());
                    assertThat(view.instrumentId()).isEqualTo("BTC-USDT-SWAP");
                    assertThat(view.startedAt()).isBefore(Instant.now());
                });
    }

    @Test
    void recoverStaleMustMatchExpectedRunIdAndRefuseWhenCurrentRunChanged() {
        String key = requestKey("stale-mismatch", 1);
        var first = store.acquire(key, "BTC-USDT-SWAP", 1);
        ageRunByOneHour(first.run().id());
        FundingRateSyncService service = service((instrument, limit) -> List.of());

        assertThat(service.recoverStale(key, first.run().id() + 999, Duration.ofMinutes(15)))
                .isEqualTo(StaleRecoveryOutcome.RUN_MISMATCH);
        assertThat(runMapper.selectById(first.run().id()).getStatus()).isEqualTo("RUNNING");

        assertThat(service.recoverStale(key, first.run().id(), Duration.ofMinutes(15)))
                .isEqualTo(StaleRecoveryOutcome.RECOVERED);
        var second = store.acquire(key, "BTC-USDT-SWAP", 1);
        assertThat(second.run().attemptNo()).isEqualTo(2);
        ageRunByOneHour(second.run().id());

        assertThat(service.recoverStale(key, first.run().id(), Duration.ofMinutes(15)))
                .isEqualTo(StaleRecoveryOutcome.RUN_MISMATCH);
        assertThat(runMapper.selectById(second.run().id()).getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void recoverStaleRefusesExecutionYoungerThanThreshold() {
        String key = requestKey("stale-young", 1);
        var acquired = store.acquire(key, "BTC-USDT-SWAP", 1);

        assertThat(service((instrument, limit) -> List.of())
                .recoverStale(key, acquired.run().id(), Duration.ofHours(1)))
                .isEqualTo(StaleRecoveryOutcome.NOT_STALE);
        assertThat(runMapper.selectById(acquired.run().id()).getStatus()).isEqualTo("RUNNING");
        assertThat(requestMapper.selectByKey(key).getState()).isEqualTo("RUNNING");
        store.fail(acquired.requestId(), acquired.run().id(), "TEST_CLEANUP", "young execution kept RUNNING");
    }

    @Test
    void recoverStaleRefusesUnknownOrNotRunningRequest() {
        FundingRateSyncService service = service((instrument, limit) -> List.of());

        assertThat(service.recoverStale("sync-funding:BTC-USDT-SWAP:1:never-created", 1, Duration.ofMinutes(15)))
                .isEqualTo(StaleRecoveryOutcome.NOT_FOUND);

        String key = requestKey("stale-done", 1);
        var acquired = store.acquire(key, "BTC-USDT-SWAP", 1);
        store.fail(acquired.requestId(), acquired.run().id(), "TEST_CLEANUP", "request no longer running");
        assertThat(service.recoverStale(key, acquired.run().id(), Duration.ofMinutes(15)))
                .isEqualTo(StaleRecoveryOutcome.NOT_RUNNING);
    }

    @Test
    void staleWindowPolicyRejectsIllegalDurationsWithoutTouchingState() {
        String key = requestKey("stale-policy", 1);
        var acquired = store.acquire(key, "BTC-USDT-SWAP", 1);
        FundingRateSyncService service = service((instrument, limit) -> List.of());

        List<Duration> illegal = List.of(
                Duration.parse("PT59S"),
                Duration.parse("PT0S"),
                Duration.parse("-PT5M"),
                Duration.parse("P31D"),
                Duration.parse("PT2M0.0000005S"));
        assertThatThrownBy(() -> service.listStale(null))
                .isInstanceOf(IllegalArgumentException.class);
        for (Duration olderThan : illegal) {
            assertThatThrownBy(() -> service.listStale(olderThan))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.recoverStale(key, acquired.run().id(), olderThan))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(runMapper.selectById(acquired.run().id()).getStatus()).isEqualTo("RUNNING");
        assertThat(requestMapper.selectByKey(key).getState()).isEqualTo("RUNNING");
        store.fail(acquired.requestId(), acquired.run().id(), "TEST_CLEANUP", "policy rejection left state untouched");
    }

    @Test
    void staleWindowAcceptsExactBoundaries() {
        String minKey = requestKey("stale-policy-min", 1);
        var minAcquired = store.acquire(minKey, "BTC-USDT-SWAP", 1);
        ageRunBy(minAcquired.run().id(), "INTERVAL 1 HOUR");
        assertThat(service((instrument, limit) -> List.of())
                .recoverStale(minKey, minAcquired.run().id(), Duration.parse("PT1M")))
                .isEqualTo(StaleRecoveryOutcome.RECOVERED);

        String maxKey = requestKey("stale-policy-max", 1);
        var maxAcquired = store.acquire(maxKey, "BTC-USDT-SWAP", 1);
        ageRunBy(maxAcquired.run().id(), "INTERVAL 31 DAY");
        assertThat(service((instrument, limit) -> List.of())
                .recoverStale(maxKey, maxAcquired.run().id(), Duration.parse("P30D")))
                .isEqualTo(StaleRecoveryOutcome.RECOVERED);
    }

    @Test
    void syncKeyIsLowercasedAndMustStayWithinSafeCharacters() {
        int runsBefore = runCount();
        AtomicInteger sourceCalls = new AtomicInteger();
        FundingRateSyncService service = service((instrument, limit) -> {
            sourceCalls.incrementAndGet();
            return List.of(observation("0.000114", "p"));
        });

        FundingRateSyncResult first = service.syncLatest("BTC-USDT-SWAP", 1, "Manual-Key-A");
        FundingRateSyncResult replay = service.syncLatest("BTC-USDT-SWAP", 1, "manual-KEY-a");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.analysisRunId()).isEqualTo(first.analysisRunId());
        assertThat(sourceCalls).hasValue(1);

        for (String unsafe : List.of("bad key", "bad\nkey", "bad:key", "bad/key", "bad key")) {
            assertThatThrownBy(() -> service.syncLatest("BTC-USDT-SWAP", 1, unsafe))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> service((instrument, limit) -> List.of()).recoverStale(
                "sync-funding:BTC-USDT-SWAP:1:bad\nkey", 1, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(runCount()).isEqualTo(runsBefore + 1);
    }

    @Test
    void genericRunServiceMustNotBypassSyncRequestOwnership() {
        var acquired = store.acquire(requestKey("owner-boundary", 1), "BTC-USDT-SWAP", 1);

        assertThatThrownBy(() -> runService.succeed(acquired.run().id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("原子 Store");
        assertThatThrownBy(() -> runService.fail(acquired.run().id(), "BYPASS", "should fail"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("原子 Store");

        assertThat(runMapper.selectById(acquired.run().id()).getStatus()).isEqualTo("RUNNING");
        assertThat(requestMapper.selectByKey(requestKey("owner-boundary", 1)).getState()).isEqualTo("RUNNING");
        store.fail(acquired.requestId(), acquired.run().id(), "TEST_CLEANUP", "owner boundary verified");
    }

    @Test
    void shouldRouteTypedSourceFailureToFailedAttemptAndLeaveRequestRetryable() {
        String syncKey = "source-failure";
        FundingRateHistorySource failing = (instrument, limit) -> {
            throw new FundingRateSourceException(
                    FundingRateSourceException.Category.TIMEOUT, null, true, "provider timeout");
        };

        assertThatThrownBy(() -> service(failing).syncLatest("BTC-USDT-SWAP", 5, syncKey))
                .isInstanceOf(FundingRateSourceException.class);

        FundingSyncRequestEntity request = request(syncKey, 5);
        AnalysisRunEntity failed = runMapper.selectBySyncRequestAndAttempt(request.getId(), 1);
        assertThat(request.getState()).isEqualTo("READY");
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getFailureCode()).isEqualTo("SOURCE_TIMEOUT");
    }

    @Test
    void shouldAppendProviderCorrectionAtSameFundingTimeInsteadOfOverwritingHistory() {
        int factsBefore = factMapper.countFacts();
        service((instrument, limit) -> List.of(observation("0.000120", "a")))
                .syncLatest("BTC-USDT-SWAP", 1, "revision-original");
        service((instrument, limit) -> List.of(observation("0.000119", "b")))
                .syncLatest("BTC-USDT-SWAP", 1, "revision-corrected");
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore + 2);
    }

    private FundingRateSyncService service(FundingRateHistorySource source) {
        return new FundingRateSyncService(source, store);
    }

    private void ageRunByOneHour(long runId) {
        ageRunBy(runId, "INTERVAL 1 HOUR");
    }

    private void ageRunBy(long runId, String sqlInterval) {
        jdbcTemplate.update(
                "UPDATE analysis_run SET started_at = DATE_SUB(started_at, " + sqlInterval + ") WHERE id = ?",
                runId);
    }

    private int runCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_run", Integer.class);
        return count == null ? 0 : count;
    }

    private FundingSyncRequestEntity request(String syncKey, int limit) {
        return requestMapper.selectByKey(requestKey(syncKey, limit));
    }

    private static String requestKey(String syncKey, int limit) {
        return "sync-funding:BTC-USDT-SWAP:" + limit + ":" + syncKey;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static FundingRateObservation observation(String realizedRate, String hashSeed) {
        return new FundingRateObservation(
                "OKX", DerivativeInstrumentType.SWAP, "BTC-USDT-SWAP",
                FundingFormulaType.WITH_RATE, new BigDecimal("0.000123"), new BigDecimal(realizedRate),
                Instant.parse("2026-01-01T00:00:00Z"), FundingSettlementMethod.CURRENT_PERIOD,
                "/api/v5/public/funding-rate-history", "{\"fixture\":true}", hashSeed.repeat(64),
                Instant.parse("2026-08-15T00:00:00Z"));
    }

    private static FundingRateObservation invalidJsonObservation() {
        FundingRateObservation valid = observation("0.000114", "1");
        return new FundingRateObservation(
                valid.exchange(), valid.instrumentType(), valid.instrumentId(), valid.formulaType(),
                valid.predictedRate(), valid.realizedRate(), valid.fundingTime(), valid.settlementMethod(),
                valid.sourceEndpoint(), "not-json", valid.payloadHash(), valid.observedAt());
    }
}
