package app.cryptoincome.grid.infrastructure.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SpotCandleFactMapper {
    @Insert("""
            INSERT INTO spot_candle_fact (
                exchange, instrument_id, bar, open_time,
                open_price, high_price, low_price, close_price,
                base_volume, quote_volume, confirmed,
                source_endpoint, source_payload, payload_hash, first_observed_at
            ) VALUES (
                #{exchange}, #{instrumentId}, #{bar}, #{openTime},
                #{open}, #{high}, #{low}, #{close},
                #{baseVolume}, #{quoteVolume}, TRUE,
                #{sourceEndpoint}, CAST(#{sourcePayload} AS JSON), #{payloadHash}, #{observedAt}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insert(
            @Param("exchange") String exchange,
            @Param("instrumentId") String instrumentId,
            @Param("bar") String bar,
            @Param("openTime") Instant openTime,
            @Param("open") BigDecimal open,
            @Param("high") BigDecimal high,
            @Param("low") BigDecimal low,
            @Param("close") BigDecimal close,
            @Param("baseVolume") BigDecimal baseVolume,
            @Param("quoteVolume") BigDecimal quoteVolume,
            @Param("sourceEndpoint") String sourceEndpoint,
            @Param("sourcePayload") String sourcePayload,
            @Param("payloadHash") String payloadHash,
            @Param("observedAt") Instant observedAt);

    @Select("""
            SELECT exchange, instrument_id, bar, open_time,
                   open_price, high_price, low_price, close_price,
                   base_volume, quote_volume, source_endpoint, source_payload,
                   payload_hash, first_observed_at
            FROM (
                SELECT id, exchange, instrument_id, bar, open_time,
                       open_price, high_price, low_price, close_price,
                       base_volume, quote_volume, source_endpoint, source_payload,
                       payload_hash, first_observed_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY exchange, instrument_id, bar, open_time
                           ORDER BY first_observed_at DESC, id DESC
                       ) revision_rank
                FROM spot_candle_fact
                WHERE exchange = #{exchange}
                  AND instrument_id = #{instrumentId}
                  AND bar = #{bar}
                  AND open_time >= #{from}
                  AND open_time < #{to}
                  AND first_observed_at <= #{evaluationAsOf}
                  AND confirmed = TRUE
            ) ranked
            WHERE revision_rank = 1
            ORDER BY open_time ASC
            """)
    List<SpotCandleRow> selectCompletedAsOf(
            @Param("exchange") String exchange,
            @Param("instrumentId") String instrumentId,
            @Param("bar") String bar,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("evaluationAsOf") Instant evaluationAsOf);

    @Select("""
            SELECT COUNT(DISTINCT open_time) actual_minutes,
                   MIN(open_time) first_open_time,
                   MAX(open_time) last_open_time
            FROM spot_candle_fact
            WHERE exchange = #{exchange}
              AND instrument_id = #{instrumentId}
              AND bar = #{bar}
              AND open_time >= #{from}
              AND open_time < #{to}
              AND first_observed_at <= #{evaluationAsOf}
              AND confirmed = TRUE
            """)
    SpotCandleCoverageRow inspectCoverage(
            @Param("exchange") String exchange,
            @Param("instrumentId") String instrumentId,
            @Param("bar") String bar,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("evaluationAsOf") Instant evaluationAsOf);
}
