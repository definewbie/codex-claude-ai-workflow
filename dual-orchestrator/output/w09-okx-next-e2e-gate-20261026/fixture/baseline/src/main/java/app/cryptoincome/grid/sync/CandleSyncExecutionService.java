package app.cryptoincome.grid.sync;

import app.cryptoincome.grid.application.SpotCandleBackfillResult;
import app.cryptoincome.grid.application.SpotCandleBackfillService;
import app.cryptoincome.grid.application.SpotCandleCoverageQuery;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.CandleBackfillCoverage;
import app.cryptoincome.grid.domain.CandleWindowCoverage;
import app.cryptoincome.grid.domain.GridSourceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.Optional;

@Service
public class CandleSyncExecutionService {
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);
    private static final Duration TRANSIENT_FAILURE_COOLDOWN = Duration.ofMinutes(15);
    private static final Duration INCOMPLETE_COVERAGE_COOLDOWN = Duration.ofHours(6);
    private final CandleSyncExecutionRepository repository;
    private final SpotCandleBackfillService backfillService;
    private final SpotCandleCoverageQuery coverageQuery;
    private final Executor executor;
    private final Clock clock;

    public CandleSyncExecutionService(CandleSyncExecutionRepository repository,
                                      SpotCandleBackfillService backfillService,
                                      SpotCandleCoverageQuery coverageQuery,
                                      @Qualifier("candleSyncExecutor") Executor executor,
                                      Clock clock) {
        this.repository = repository;
        this.backfillService = backfillService;
        this.coverageQuery = coverageQuery;
        this.executor = executor;
        this.clock = clock;
    }

    public CandleSyncExecution start(Instant to) {
        validateTo(to);
        return startNow(to, clock.instant());
    }

    public CandleSyncAutoStartDecision startAutomatically(Instant to) {
        validateTo(to);
        Instant now = clock.instant();
        repository.failStale(now.minus(STALE_AFTER), now);
        Optional<CandleSyncExecution> active = repository.findActive();
        if (active.isPresent()) return new CandleSyncAutoStartDecision(active.get(), null);
        Optional<CandleSyncExecution> latest = repository.findLatest();
        Instant retryAt = latest.map(value -> automaticRetryAt(value, now)).orElse(null);
        if (retryAt != null) return new CandleSyncAutoStartDecision(latest.orElseThrow(), retryAt);
        return new CandleSyncAutoStartDecision(createAndExecute(to, now), null);
    }

    public CandleSyncAutoStartDecision automaticStatus() {
        Instant now = clock.instant();
        Optional<CandleSyncExecution> active = repository.findActive();
        if (active.isPresent()) return new CandleSyncAutoStartDecision(active.get(), null);
        Optional<CandleSyncExecution> latest = repository.findLatest();
        if (latest.isEmpty() || latest.get().state() != CandleSyncState.FAILED) {
            return new CandleSyncAutoStartDecision(null, null);
        }
        return new CandleSyncAutoStartDecision(latest.get(), automaticRetryAt(latest.get(), now));
    }

    private CandleSyncExecution startNow(Instant to, Instant now) {
        repository.failStale(now.minus(STALE_AFTER), now);
        var active = repository.findActive();
        if (active.isPresent()) return active.get();
        return createAndExecute(to, now);
    }

    private CandleSyncExecution createAndExecute(Instant to, Instant now) {
        Instant from = to.minus(SpotCandleBackfillService.REQUIRED_WINDOW);
        long id;
        try {
            id = repository.create(from, to, now);
        } catch (DataIntegrityViolationException raced) {
            return repository.findActive().orElseThrow(() -> raced);
        }
        executor.execute(() -> execute(id, from, to));
        return get(id);
    }

    private static void validateTo(Instant to) {
        if (to == null) throw new IllegalArgumentException("K线同步终点不能为空");
        if (to.getNano() % 1_000_000 != 0) throw new IllegalArgumentException("同步终点必须是毫秒精度");
    }

    private static Instant automaticRetryAt(CandleSyncExecution execution, Instant now) {
        if (execution.state() != CandleSyncState.FAILED) return null;
        Instant failedAt = execution.completedAt() != null ? execution.completedAt() : execution.updatedAt();
        Duration cooldown = "INCOMPLETE_COVERAGE".equals(execution.failureCategory())
                ? INCOMPLETE_COVERAGE_COOLDOWN : TRANSIENT_FAILURE_COOLDOWN;
        Instant retryAt = failedAt.plus(cooldown);
        return now.isBefore(retryAt) ? retryAt : null;
    }

    public CandleSyncExecution get(long id) {
        if (id <= 0) throw new IllegalArgumentException("同步执行 ID 必须大于0");
        return repository.find(id).orElseThrow(() -> new IllegalArgumentException("K线同步执行不存在"));
    }

    public Optional<CandleSyncExecution> active() {
        return repository.findActive();
    }

    private void execute(long id, Instant from, Instant to) {
        Instant started = clock.instant();
        if (!repository.markRunning(id, started)) return;
        try {
            SpotCandleBackfillResult result = backfillService.backfill(
                    "BTC-USDT", CandleBar.M1, from, to, 300, 1000,
                    progress -> {
                        if (!repository.updateProgress(id, progress, clock.instant())) {
                            throw new IllegalStateException("K线同步执行已不允许继续写入进度");
                        }
                    });
            Instant verifiedAt = clock.instant();
            CandleWindowCoverage coverage = coverageQuery.inspect(
                    "OKX", "BTC-USDT", CandleBar.M1, from, to, verifiedAt);
            if (!coverage.complete()) {
                repository.fail(id, "INCOMPLETE_COVERAGE",
                        "数据库窗口仍缺少 " + coverage.missingMinutes() + " 根分钟 K 线", verifiedAt);
                return;
            }
            SpotCandleBackfillResult verifiedResult = new SpotCandleBackfillResult(
                    CandleBackfillCoverage.BOUNDARY_REACHED,
                    result.pages(), result.received(), result.completedInWindow(), result.newFacts(),
                    result.duplicateFacts(), result.cursor());
            if (!repository.succeed(id, verifiedResult, clock.instant())) {
                throw new IllegalStateException("K线同步终态写入失败");
            }
        } catch (GridSourceException source) {
            repository.fail(id, source.category().name(), source.getMessage(), clock.instant());
        } catch (RuntimeException failure) {
            repository.fail(id, "INTERNAL", failure.getMessage(), clock.instant());
        }
    }
}
