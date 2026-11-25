package app.cryptoincome.assessment.infrastructure.mybatis;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

public interface FundingRateAssessmentMapper {

    @Select("""
            SELECT funding_time, realized_rate
            FROM (
                SELECT id, funding_time, realized_rate,
                       ROW_NUMBER() OVER (
                           PARTITION BY exchange, instrument_id, funding_time
                           ORDER BY first_observed_at DESC, id DESC
                       ) AS revision_rank
                FROM funding_rate_fact
                WHERE exchange = #{exchange}
                  AND instrument_id = #{instrumentId}
                  AND funding_time >= #{from}
                  AND funding_time < #{to}
                  AND first_observed_at <= #{evaluationAsOf}
            ) ranked
            WHERE revision_rank = 1
            ORDER BY funding_time ASC
            """)
    List<FundingRateAssessmentRow> selectRealizedRatesAsOf(
            @Param("exchange") String exchange,
            @Param("instrumentId") String instrumentId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("evaluationAsOf") Instant evaluationAsOf);
}
