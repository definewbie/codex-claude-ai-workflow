package app.cryptoincome.funding.application;

import app.cryptoincome.funding.domain.AppendResult;
import app.cryptoincome.funding.domain.FundingKeys;
import app.cryptoincome.funding.domain.FundingRateHistorySource;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSourceException;
import app.cryptoincome.funding.domain.FundingRateSyncInProgressException;
import app.cryptoincome.funding.domain.FundingRateSyncStore;
import app.cryptoincome.funding.domain.FundingSyncAcquisition;
import app.cryptoincome.funding.domain.FundingSyncDisposition;
import app.cryptoincome.funding.domain.StaleExecution;
import app.cryptoincome.funding.domain.StaleRecoveryOutcome;
import app.cryptoincome.funding.domain.StaleWindows;
import app.cryptoincome.run.domain.AnalysisRunStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class FundingRateSyncService {

    private final FundingRateHistorySource source;
    private final FundingRateSyncStore store;

    public FundingRateSyncService(
            FundingRateHistorySource source,
            FundingRateSyncStore store
    ) {
        this.source = source;
        this.store = store;
    }

    public FundingRateSyncResult syncLatest(String instrumentId, int limit, String syncKey) {
        String normalizedInstrument = normalizeInstrument(instrumentId);
        String requestKey = buildRequestKey(normalizedInstrument, limit, syncKey);

        FundingSyncAcquisition acquisition = store.acquire(requestKey, normalizedInstrument, limit);
        if (acquisition.disposition() == FundingSyncDisposition.REPLAYED) {
            int linked = acquisition.linkedObservations();
            return new FundingRateSyncResult(
                    acquisition.run().id(), AnalysisRunStatus.SUCCEEDED, linked, linked, 0, true);
        }
        if (acquisition.disposition() == FundingSyncDisposition.IN_PROGRESS) {
            throw new FundingRateSyncInProgressException(acquisition.run().id());
        }

        try {
            List<FundingRateObservation> observations = source.fetchLatest(normalizedInstrument, limit);
            AppendResult appended = store.complete(
                    acquisition.requestId(), acquisition.run().id(), observations);
            return new FundingRateSyncResult(
                    acquisition.run().id(),
                    AnalysisRunStatus.SUCCEEDED,
                    observations.size(),
                    appended.newObservationLinks(),
                    appended.duplicateObservationLinks(),
                    false
            );
        } catch (FundingRateSourceException sourceFailure) {
            failPreservingOriginal(acquisition, "SOURCE_" + sourceFailure.category().name(), sourceFailure);
            throw sourceFailure;
        } catch (RuntimeException persistenceOrUnexpectedFailure) {
            failPreservingOriginal(acquisition, "PERSISTENCE_OR_INTERNAL_ERROR", persistenceOrUnexpectedFailure);
            throw persistenceOrUnexpectedFailure;
        }
    }

    public List<StaleExecution> listStale(Duration olderThan) {
        StaleWindows.requireWithin(olderThan);
        return store.listStale(olderThan);
    }

    public StaleRecoveryOutcome recoverStale(String requestKey, long expectedRunId, Duration olderThan) {
        StaleWindows.requireWithin(olderThan);
        if (expectedRunId <= 0) {
            throw new IllegalArgumentException("expectedRunId 必须为正数");
        }
        String normalizedKey = FundingKeys.printableRequestKey(requestKey);
        return store.recoverStale(normalizedKey, expectedRunId, olderThan);
    }

    private void failPreservingOriginal(
            FundingSyncAcquisition acquisition,
            String failureCode,
            RuntimeException original
    ) {
        try {
            store.fail(
                    acquisition.requestId(), acquisition.run().id(), failureCode, boundedMessage(original));
        } catch (RuntimeException markingFailure) {
            original.addSuppressed(markingFailure);
        }
    }

    private static String boundedMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static String normalizeInstrument(String instrumentId) {
        return FundingKeys.normalizeInstrument(instrumentId);
    }

    private static String buildRequestKey(String instrumentId, int limit, String syncKey) {
        String normalizedInstrument = normalizeInstrument(instrumentId);
        if (limit < 1 || limit > 400) {
            throw new IllegalArgumentException("limit 必须在 1 到 400 之间");
        }
        String requestKey = "sync-funding:" + normalizedInstrument + ":" + limit + ":"
                + FundingKeys.safeSyncKey(syncKey);
        if (requestKey.length() > 128) {
            throw new IllegalArgumentException("组合后的幂等键长度不能超过 128");
        }
        return requestKey;
    }
}
