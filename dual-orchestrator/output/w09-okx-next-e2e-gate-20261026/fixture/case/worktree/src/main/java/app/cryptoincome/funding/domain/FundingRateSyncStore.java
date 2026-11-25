package app.cryptoincome.funding.domain;

import java.time.Duration;
import java.util.List;

public interface FundingRateSyncStore {
    FundingSyncAcquisition acquire(String requestKey, String instrumentId, int limit);

    AppendResult complete(long requestId, long runId, List<FundingRateObservation> observations);

    void fail(long requestId, long runId, String failureCode, String failureDetail);

    List<StaleExecution> listStale(Duration olderThan);

    StaleRecoveryOutcome recoverStale(String requestKey, long expectedRunId, Duration olderThan);
}
