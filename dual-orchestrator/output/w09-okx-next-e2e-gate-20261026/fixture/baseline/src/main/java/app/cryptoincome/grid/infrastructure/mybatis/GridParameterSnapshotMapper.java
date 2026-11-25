package app.cryptoincome.grid.infrastructure.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

public interface GridParameterSnapshotMapper {

    @Insert("""
            INSERT INTO grid_parameter_snapshot (
                exchange, instrument_id, duration, grid_type,
                lower_price, upper_price, grid_count, annualized_rate,
                minimum_per_grid_profit_rate, maximum_per_grid_profit_rate,
                minimum_investment, investment_currency,
                source_endpoint, source_payload, payload_hash, observed_at
            ) VALUES (
                #{exchange}, #{instrumentId}, #{duration}, #{gridType},
                #{lowerPrice}, #{upperPrice}, #{gridCount}, #{annualizedRate},
                #{minimumPerGridProfitRate}, #{maximumPerGridProfitRate},
                #{minimumInvestment}, #{investmentCurrency},
                #{sourceEndpoint}, CAST(#{sourcePayload} AS JSON), #{payloadHash}, #{observedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GridParameterSnapshotEntity entity);

    @Select("""
            SELECT id, exchange, instrument_id, duration, grid_type,
                   lower_price, upper_price, grid_count, annualized_rate,
                   minimum_per_grid_profit_rate, maximum_per_grid_profit_rate,
                   minimum_investment, investment_currency, source_endpoint,
                   CAST(source_payload AS CHAR) AS source_payload, payload_hash, observed_at
            FROM grid_parameter_snapshot
            WHERE id = #{id} AND observed_at <= #{evaluationAsOf}
            """)
    GridParameterSnapshotEntity findByIdAsOf(long id, Instant evaluationAsOf);
}
