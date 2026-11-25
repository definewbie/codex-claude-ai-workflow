package app.cryptoincome.funding.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

public interface FundingSyncRequestMapper {
    int insertOrGetId(FundingSyncRequestEntity entity);

    FundingSyncRequestEntity selectByKeyForUpdate(@Param("requestKey") String requestKey);

    FundingSyncRequestEntity selectByKey(@Param("requestKey") String requestKey);

    FundingSyncRequestEntity selectByIdForUpdate(@Param("id") long id);

    java.util.List<StaleExecutionRow> selectStaleRunning(@Param("micros") long micros);

    int markRunning(@Param("id") long id, @Param("runId") long runId);

    int markReady(@Param("id") long id, @Param("runId") long runId);

    int markSucceeded(@Param("id") long id, @Param("runId") long runId);
}
