package app.cryptoincome.run.domain;

import java.time.Instant;

public record AnalysisRun(
        long id,
        Long syncRequestId,
        Long backfillRequestId,
        Integer attemptNo,
        String idempotencyKey,
        AnalysisRunType type,
        PortfolioMode mode,
        AnalysisRunStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String failureCode,
        String failureDetail
) {
}
