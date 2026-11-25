package app.cryptoincome.grid.sync.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

public interface CandleSyncExecutionMapper extends BaseMapper<CandleSyncExecutionEntity> {
    @Select("SELECT * FROM candle_sync_execution WHERE state IN ('QUEUED','RUNNING') ORDER BY id DESC LIMIT 1")
    CandleSyncExecutionEntity findActive();

    @Select("SELECT * FROM candle_sync_execution ORDER BY id DESC LIMIT 1")
    CandleSyncExecutionEntity findLatest();

    @Update("UPDATE candle_sync_execution SET state='RUNNING', started_at=#{now}, updated_at=#{now} " +
            "WHERE id=#{id} AND state='QUEUED'")
    int markRunning(long id, Instant now);

    @Update("UPDATE candle_sync_execution SET pages=#{pages}, received=#{received}, " +
            "completed_in_window=#{completedInWindow}, new_facts=#{newFacts}, duplicate_facts=#{duplicateFacts}, " +
            "cursor_ts=#{cursor}, updated_at=#{now} WHERE id=#{id} AND state='RUNNING'")
    int updateProgress(long id, int pages, int received, int completedInWindow, int newFacts,
                       int duplicateFacts, Instant cursor, Instant now);

    @Update("UPDATE candle_sync_execution SET state='SUCCEEDED', coverage=#{coverage}, pages=#{pages}, " +
            "received=#{received}, completed_in_window=#{completedInWindow}, new_facts=#{newFacts}, " +
            "duplicate_facts=#{duplicateFacts}, cursor_ts=#{cursor}, completed_at=#{now}, updated_at=#{now} " +
            "WHERE id=#{id} AND state='RUNNING'")
    int succeed(long id, String coverage, int pages, int received, int completedInWindow,
                int newFacts, int duplicateFacts, Instant cursor, Instant now);

    @Update("UPDATE candle_sync_execution SET state='FAILED', failure_category=#{category}, " +
            "failure_message=#{message}, completed_at=#{now}, updated_at=#{now} " +
            "WHERE id=#{id} AND state IN ('QUEUED','RUNNING')")
    int fail(long id, String category, String message, Instant now);

    @Update("UPDATE candle_sync_execution SET state='FAILED', failure_category='INTERRUPTED', " +
            "failure_message='应用重启或执行超时，原同步已终止', completed_at=#{now}, updated_at=#{now} " +
            "WHERE state IN ('QUEUED','RUNNING') AND updated_at < #{olderThan}")
    int failStale(Instant olderThan, Instant now);
}
