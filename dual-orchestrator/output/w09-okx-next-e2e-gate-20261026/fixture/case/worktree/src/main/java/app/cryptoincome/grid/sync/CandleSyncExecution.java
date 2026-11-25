package app.cryptoincome.grid.sync;

import app.cryptoincome.grid.domain.CandleBackfillCoverage;

import java.time.Instant;

public record CandleSyncExecution(
        long id,
        CandleSyncState state,
        String instrumentId,
        String bar,
        Instant from,
        Instant to,
        CandleBackfillCoverage coverage,
        int pages,
        int received,
        int completedInWindow,
        int newFacts,
        int duplicateFacts,
        Instant cursor,
        String failureCategory,
        String failureMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt
) {
    public boolean terminal() {
        return state == CandleSyncState.SUCCEEDED || state == CandleSyncState.FAILED;
    }
}
