package app.cryptoincome.funding.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 回补存储边界: 逻辑请求/游标/覆盖状态的 Source of Truth。
 * 每个方法都是独立短事务; 页面提交校验执行权(fencing), HTTP 请求不得处于这些事务内。
 */
public interface FundingBackfillStore {

    BackfillAcquisition begin(String requestKey, String instrumentId, Instant from, Instant to, int limit);

    BackfillPageCommit commitPage(
            long requestId,
            long runId,
            int pageNo,
            Instant afterCursor,
            List<FundingRateObservation> observations,
            boolean budgetExhaustedAfterPage
    );

    void fail(long requestId, long runId, String failureCode, String failureDetail);

    List<StaleExecution> listStale(Duration olderThan);

    StaleRecoveryOutcome recoverStale(String requestKey, long expectedRunId, Duration olderThan);
}
