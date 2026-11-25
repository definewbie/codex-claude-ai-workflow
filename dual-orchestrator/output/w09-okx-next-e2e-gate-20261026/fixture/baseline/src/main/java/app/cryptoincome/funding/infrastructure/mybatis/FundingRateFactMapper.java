package app.cryptoincome.funding.infrastructure.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;

public interface FundingRateFactMapper {

    @Insert("""
            INSERT INTO funding_rate_fact (
                exchange, instrument_type, instrument_id, formula_type,
                predicted_rate, realized_rate, funding_time, settlement_method,
                source_endpoint, source_payload, payload_hash, first_observed_at
            ) VALUES (
                #{exchange}, #{instrumentType}, #{instrumentId}, #{formulaType},
                #{predictedRate}, #{realizedRate}, #{fundingTime}, #{settlementMethod},
                #{sourceEndpoint}, CAST(#{sourcePayload} AS JSON), #{payloadHash}, #{firstObservedAt}
            )
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    int insertOrResolveId(
            @Param("exchange") String exchange,
            @Param("instrumentType") String instrumentType,
            @Param("instrumentId") String instrumentId,
            @Param("formulaType") String formulaType,
            @Param("predictedRate") BigDecimal predictedRate,
            @Param("realizedRate") BigDecimal realizedRate,
            @Param("fundingTime") Instant fundingTime,
            @Param("settlementMethod") String settlementMethod,
            @Param("sourceEndpoint") String sourceEndpoint,
            @Param("sourcePayload") String sourcePayload,
            @Param("payloadHash") String payloadHash,
            @Param("firstObservedAt") Instant firstObservedAt
    );

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("""
            INSERT INTO funding_rate_observation (
                analysis_run_id, funding_rate_fact_id, observed_at
            ) VALUES (#{analysisRunId}, #{factId}, #{observedAt})
            ON DUPLICATE KEY UPDATE analysis_run_id = analysis_run_id
            """)
    int insertObservation(
            @Param("analysisRunId") long analysisRunId,
            @Param("factId") long factId,
            @Param("observedAt") Instant observedAt
    );

    @Select("SELECT COUNT(*) FROM funding_rate_observation WHERE analysis_run_id = #{analysisRunId}")
    int countLinked(@Param("analysisRunId") long analysisRunId);

    @Select("SELECT COUNT(*) FROM funding_rate_observation")
    int countAllObservations();

    @Select("SELECT COUNT(*) FROM funding_rate_fact")
    int countFacts();
}
