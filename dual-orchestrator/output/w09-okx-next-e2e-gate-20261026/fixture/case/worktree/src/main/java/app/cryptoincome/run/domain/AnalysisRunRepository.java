package app.cryptoincome.run.domain;

import java.util.Optional;

public interface AnalysisRunRepository {

    AnalysisRun createOrGet(String idempotencyKey, AnalysisRunType type, PortfolioMode mode);

    Optional<AnalysisRun> findById(long id);

    Optional<AnalysisRun> findByIdempotencyKey(String idempotencyKey);

    boolean transitionToRunning(long id);

    boolean transitionToSucceeded(long id);

    boolean transitionToFailed(long id, String failureCode, String failureDetail);
}
