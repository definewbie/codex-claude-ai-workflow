package app.cryptoincome.funding.application;

import app.cryptoincome.run.domain.AnalysisRunStatus;

public record FundingRateSyncResult(
        long analysisRunId,
        AnalysisRunStatus status,
        int received,
        int newObservationLinks,
        int duplicateObservationLinks,
        boolean replayed
) {
}
