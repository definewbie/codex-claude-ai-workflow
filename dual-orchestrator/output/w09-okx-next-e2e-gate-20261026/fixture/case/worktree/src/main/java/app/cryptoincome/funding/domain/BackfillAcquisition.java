package app.cryptoincome.funding.domain;

import app.cryptoincome.run.domain.AnalysisRun;

import java.time.Instant;

public record BackfillAcquisition(
        FundingSyncDisposition disposition,
        long requestId,
        AnalysisRun run,
        Instant cursor,
        int pagesFetched,
        BackfillCoverage coverage,
        Instant earliestFactTime,
        Instant latestFactTime
) {
}
