package app.cryptoincome.grid.sync;

import app.cryptoincome.grid.application.SpotCandleBackfillProgress;
import app.cryptoincome.grid.application.SpotCandleBackfillResult;

import java.time.Instant;
import java.util.Optional;

public interface CandleSyncExecutionRepository {
    long create(Instant from, Instant to, Instant now);
    Optional<CandleSyncExecution> find(long id);
    Optional<CandleSyncExecution> findActive();
    Optional<CandleSyncExecution> findLatest();
    boolean markRunning(long id, Instant now);
    boolean updateProgress(long id, SpotCandleBackfillProgress progress, Instant now);
    boolean succeed(long id, SpotCandleBackfillResult result, Instant now);
    boolean fail(long id, String category, String message, Instant now);
    int failStale(Instant olderThan, Instant now);
}
