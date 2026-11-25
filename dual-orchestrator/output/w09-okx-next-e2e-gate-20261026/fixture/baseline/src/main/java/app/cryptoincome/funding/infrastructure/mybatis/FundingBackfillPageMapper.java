package app.cryptoincome.funding.infrastructure.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

/** 回补分页账本: 只插入与读取, 不提供修改入口(账本本身也是追加式证据)。 */
public interface FundingBackfillPageMapper {

    @Insert("""
            INSERT INTO funding_backfill_page (
                backfill_request_id, run_id, page_no, after_cursor,
                received, in_window_received, out_of_window_received,
                new_observation_links, new_global_facts,
                oldest_time, newest_time, next_cursor
            ) VALUES (
                #{backfillRequestId}, #{runId}, #{pageNo}, #{afterCursor},
                #{received}, #{inWindowReceived}, #{outOfWindowReceived},
                #{newObservationLinks}, #{newGlobalFacts},
                #{oldestTime}, #{newestTime}, #{nextCursor}
            )
            """)
    int insertPage(
            @Param("backfillRequestId") long backfillRequestId,
            @Param("runId") long runId,
            @Param("pageNo") int pageNo,
            @Param("afterCursor") Instant afterCursor,
            @Param("received") int received,
            @Param("inWindowReceived") int inWindowReceived,
            @Param("outOfWindowReceived") int outOfWindowReceived,
            @Param("newObservationLinks") int newObservationLinks,
            @Param("newGlobalFacts") int newGlobalFacts,
            @Param("oldestTime") Instant oldestTime,
            @Param("newestTime") Instant newestTime,
            @Param("nextCursor") Instant nextCursor
    );

    @Select("SELECT COUNT(*) FROM funding_backfill_page WHERE backfill_request_id = #{backfillRequestId}")
    int countPagesByRequest(@Param("backfillRequestId") long backfillRequestId);

    @Select("SELECT COUNT(*) FROM funding_backfill_page WHERE run_id = #{runId}")
    int countPagesByRun(@Param("runId") long runId);

    @Select("SELECT COUNT(*) FROM funding_backfill_page")
    int countAllPages();
}
