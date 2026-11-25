package app.cryptoincome.run.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

public interface AnalysisRunMapper {

    int insertOrGetId(AnalysisRunEntity entity);

    int insertNew(AnalysisRunEntity entity);

    int insertBackfillRun(AnalysisRunEntity entity);

    AnalysisRunEntity selectById(@Param("id") long id);

    AnalysisRunEntity selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    AnalysisRunEntity selectBySyncRequestAndAttempt(
            @Param("syncRequestId") long syncRequestId,
            @Param("attemptNo") int attemptNo
    );

    AnalysisRunEntity selectByBackfillRequestAndAttempt(
            @Param("backfillRequestId") long backfillRequestId,
            @Param("attemptNo") int attemptNo
    );

    AnalysisRunEntity selectLastByBackfillRequest(@Param("backfillRequestId") long backfillRequestId);

    int transitionToRunning(@Param("id") long id);

    int transitionToSucceeded(@Param("id") long id);

    int transitionToFailed(
            @Param("id") long id,
            @Param("failureCode") String failureCode,
            @Param("failureDetail") String failureDetail
    );

    int failStaleIfOlderThan(
            @Param("id") long id,
            @Param("micros") long micros,
            @Param("failureCode") String failureCode,
            @Param("failureDetail") String failureDetail
    );
}
