package app.cryptoincome.grid;

import app.cryptoincome.grid.application.*;
import app.cryptoincome.grid.domain.*;
import app.cryptoincome.grid.sync.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandleSyncExecutionServiceTest {
    @Test
    void executionOwnsProgressAndTerminalStateWhileCandleFactsRemainSeparate() {
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(90));
        FakeRepository repository = new FakeRepository();
        SpotCandle candle = new SpotCandle("OKX", "BTC-USDT", CandleBar.M1, from,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, true, "source", "[]", "0".repeat(64), to);
        SpotCandleBackfillService backfill = new SpotCandleBackfillService(
                (instrument, bar, limit, after) -> List.of(candle),
                rows -> new CandleAppendResult(rows.size(), rows.size(), 0), ignored -> {});
        CandleSyncExecutionService service = new CandleSyncExecutionService(repository, backfill,
                (exchange, instrument, bar, windowFrom, windowTo, asOf) ->
                        new CandleWindowCoverage(windowFrom, windowTo, 129_600, 129_600,
                                windowFrom, windowTo.minusSeconds(60), true),
                Runnable::run, Clock.fixed(to.plusSeconds(60), ZoneOffset.UTC));

        CandleSyncExecution result = service.start(to);

        assertThat(result.state()).isEqualTo(CandleSyncState.SUCCEEDED);
        assertThat(result.coverage()).isEqualTo(CandleBackfillCoverage.BOUNDARY_REACHED);
        assertThat(result.pages()).isEqualTo(1);
        assertThat(result.completedInWindow()).isEqualTo(1);
        assertThat(repository.progressWrites).isEqualTo(1);
    }

    @Test
    void incompleteDatabaseCoverageFailsTheExecutionWithTheMissingCount() {
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(90));
        FakeRepository repository = new FakeRepository();
        CandleSyncExecutionService service = new CandleSyncExecutionService(repository, onePageBackfill(from, to),
                (exchange, instrument, bar, windowFrom, windowTo, asOf) ->
                        new CandleWindowCoverage(windowFrom, windowTo, 129_600, 129_599,
                                windowFrom, windowTo.minusSeconds(60), false),
                Runnable::run, Clock.fixed(to.plusSeconds(60), ZoneOffset.UTC));

        CandleSyncExecution result = service.start(to);

        assertThat(result.state()).isEqualTo(CandleSyncState.FAILED);
        assertThat(result.failureCategory()).isEqualTo("INCOMPLETE_COVERAGE");
        assertThat(result.failureMessage()).isEqualTo("数据库窗口仍缺少 1 根分钟 K 线");
    }

    @Test
    void completeDatabaseCoverageSucceedsEvenWhenBackfillBudgetWasExhausted() {
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(90));
        FakeRepository repository = new FakeRepository();
        SpotCandleBackfillService backfill = mock(SpotCandleBackfillService.class);
        when(backfill.backfill(eq("BTC-USDT"), eq(CandleBar.M1), eq(from), eq(to), eq(300), eq(1000), any()))
                .thenAnswer(invocation -> {
                    Consumer<SpotCandleBackfillProgress> progress = invocation.getArgument(6);
                    progress.accept(new SpotCandleBackfillProgress(
                            1000, 129_600, 129_600, 100, 129_500, from));
                    return new SpotCandleBackfillResult(CandleBackfillCoverage.BUDGET_EXHAUSTED,
                            1000, 129_600, 129_600, 100, 129_500, from);
                });
        CandleSyncExecutionService service = new CandleSyncExecutionService(repository, backfill,
                (exchange, instrument, bar, windowFrom, windowTo, asOf) ->
                        new CandleWindowCoverage(windowFrom, windowTo, 129_600, 129_600,
                                windowFrom, windowTo.minusSeconds(60), true),
                Runnable::run, Clock.fixed(to.plusSeconds(60), ZoneOffset.UTC));

        CandleSyncExecution result = service.start(to);

        assertThat(result.state()).isEqualTo(CandleSyncState.SUCCEEDED);
        assertThat(result.coverage()).isEqualTo(CandleBackfillCoverage.BOUNDARY_REACHED);
    }

    @Test
    void automaticStatusDoesNotProjectACompletedExecutionOntoANewWindow() {
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(90));
        FakeRepository repository = new FakeRepository();
        CandleSyncExecutionService service = new CandleSyncExecutionService(repository, onePageBackfill(from, to),
                (exchange, instrument, bar, windowFrom, windowTo, asOf) ->
                        new CandleWindowCoverage(windowFrom, windowTo, 129_600, 129_600,
                                windowFrom, windowTo.minusSeconds(60), true),
                Runnable::run, Clock.fixed(to.plusSeconds(60), ZoneOffset.UTC));

        assertThat(service.start(to).state()).isEqualTo(CandleSyncState.SUCCEEDED);

        CandleSyncAutoStartDecision status = service.automaticStatus();

        assertThat(status.execution()).isNull();
        assertThat(status.automaticRetryAt()).isNull();
    }

    @Test
    void automaticStartHonorsFailureCooldownWhileManualStartBypassesIt() {
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        Instant now = to.plusSeconds(60);
        Instant failedAt = now.minusSeconds(60);
        FakeRepository repository = new FakeRepository();
        repository.value = terminalFailure(to, failedAt, "INCOMPLETE_COVERAGE");
        CandleSyncExecutionService service = new CandleSyncExecutionService(repository,
                onePageBackfill(to.minus(Duration.ofDays(90)), to),
                (exchange, instrument, bar, windowFrom, windowTo, asOf) ->
                        new CandleWindowCoverage(windowFrom, windowTo, 129_600, 129_600,
                                windowFrom, windowTo.minusSeconds(60), true),
                Runnable::run, Clock.fixed(now, ZoneOffset.UTC));

        CandleSyncAutoStartDecision automatic = service.startAutomatically(to);

        assertThat(automatic.blocked()).isTrue();
        assertThat(automatic.execution().state()).isEqualTo(CandleSyncState.FAILED);
        assertThat(automatic.automaticRetryAt()).isEqualTo(failedAt.plus(Duration.ofHours(6)));
        assertThat(repository.createWrites).isZero();

        CandleSyncExecution manual = service.start(to);
        assertThat(manual.state()).isEqualTo(CandleSyncState.SUCCEEDED);
        assertThat(repository.createWrites).isEqualTo(1);
    }

    @Test
    void transientFailureUsesTheShortAutomaticCooldown() {
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        Instant now = to.plusSeconds(60);
        Instant failedAt = now.minusSeconds(30);
        FakeRepository repository = new FakeRepository();
        repository.value = terminalFailure(to, failedAt, "INTERNAL");
        CandleSyncExecutionService service = new CandleSyncExecutionService(repository,
                mock(SpotCandleBackfillService.class), mock(SpotCandleCoverageQuery.class),
                Runnable::run, Clock.fixed(now, ZoneOffset.UTC));

        CandleSyncAutoStartDecision decision = service.startAutomatically(to);

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.automaticRetryAt()).isEqualTo(failedAt.plus(Duration.ofMinutes(15)));
        assertThat(repository.createWrites).isZero();
    }

    private static SpotCandleBackfillService onePageBackfill(Instant from, Instant observedAt) {
        SpotCandle candle = new SpotCandle("OKX", "BTC-USDT", CandleBar.M1, from,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, true, "source", "[]", "0".repeat(64), observedAt);
        return new SpotCandleBackfillService(
                (instrument, bar, limit, after) -> List.of(candle),
                rows -> new CandleAppendResult(rows.size(), rows.size(), 0), ignored -> {});
    }

    private static CandleSyncExecution terminalFailure(Instant to, Instant failedAt, String category) {
        Instant from = to.minus(Duration.ofDays(90));
        return new CandleSyncExecution(1, CandleSyncState.FAILED, "BTC-USDT", "1m", from, to,
                null, 1, 300, 299, 0, 299, from, category, "失败",
                failedAt.minusSeconds(10), failedAt.minusSeconds(9), failedAt, failedAt);
    }

    private static final class FakeRepository implements CandleSyncExecutionRepository {
        CandleSyncExecution value; int progressWrites; int createWrites;
        @Override public long create(Instant from, Instant to, Instant now) {
            createWrites++;
            value = new CandleSyncExecution(1, CandleSyncState.QUEUED, "BTC-USDT", "1m", from, to,
                    null, 0, 0, 0, 0, 0, null, null, null, now, null, null, now); return 1;
        }
        @Override public Optional<CandleSyncExecution> find(long id) { return Optional.ofNullable(value); }
        @Override public Optional<CandleSyncExecution> findActive() {
            return value != null && !value.terminal() ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<CandleSyncExecution> findLatest() { return Optional.ofNullable(value); }
        @Override public boolean markRunning(long id, Instant now) { value = copy(CandleSyncState.RUNNING, null, value.pages(), value.received(), value.completedInWindow(), value.newFacts(), value.duplicateFacts(), value.cursor(), null, null, now, null, now); return true; }
        @Override public boolean updateProgress(long id, SpotCandleBackfillProgress p, Instant now) { progressWrites++; value = copy(CandleSyncState.RUNNING, null, p.pages(), p.received(), p.completedInWindow(), p.newFacts(), p.duplicateFacts(), p.cursor(), null, null, value.startedAt(), null, now); return true; }
        @Override public boolean succeed(long id, SpotCandleBackfillResult r, Instant now) { value = copy(CandleSyncState.SUCCEEDED, r.coverage(), r.pages(), r.received(), r.completedInWindow(), r.newFacts(), r.duplicateFacts(), r.cursor(), null, null, value.startedAt(), now, now); return true; }
        @Override public boolean fail(long id, String category, String message, Instant now) { value = copy(CandleSyncState.FAILED, null, value.pages(), value.received(), value.completedInWindow(), value.newFacts(), value.duplicateFacts(), value.cursor(), category, message, value.startedAt(), now, now); return true; }
        @Override public int failStale(Instant olderThan, Instant now) { return 0; }
        private CandleSyncExecution copy(CandleSyncState state, CandleBackfillCoverage coverage, int pages, int received,
                                         int completed, int newFacts, int duplicates, Instant cursor, String category,
                                         String message, Instant started, Instant completedAt, Instant updated) {
            return new CandleSyncExecution(1, state, "BTC-USDT", "1m", value.from(), value.to(), coverage,
                    pages, received, completed, newFacts, duplicates, cursor, category, message,
                    value.createdAt(), started, completedAt, updated);
        }
    }
}
