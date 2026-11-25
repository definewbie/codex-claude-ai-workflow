package app.cryptoincome.funding.domain;

import app.cryptoincome.run.domain.AnalysisRun;

public record FundingSyncAcquisition(
        long requestId,
        FundingSyncDisposition disposition,
        AnalysisRun run,
        int linkedObservations
) {
}
