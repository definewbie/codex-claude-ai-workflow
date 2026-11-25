package app.cryptoincome.funding.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

public interface FundingBackfillRequestMapper {

    int insertOrGetId(FundingBackfillRequestEntity entity);

    FundingBackfillRequestEntity selectByKeyForUpdate(@Param("requestKey") String requestKey);

    FundingBackfillRequestEntity selectByIdForUpdate(@Param("id") long id);

    List<StaleExecutionRow> selectStaleRunning(@Param("micros") long micros);

    int markRunning(@Param("id") long id, @Param("runId") long runId);

    int releaseRunning(@Param("id") long id, @Param("runId") long runId);

    int advanceCursor(
            @Param("id") long id,
            @Param("runId") long runId,
            @Param("cursor") Instant cursor,
            @Param("earliest") Instant earliest,
            @Param("latest") Instant latest
    );

    int finishExecution(
            @Param("id") long id,
            @Param("runId") long runId,
            @Param("state") String state,
            @Param("coverage") String coverage
    );
}
