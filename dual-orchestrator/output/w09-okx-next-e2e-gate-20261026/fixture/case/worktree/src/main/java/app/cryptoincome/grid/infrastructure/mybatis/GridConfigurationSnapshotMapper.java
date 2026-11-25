package app.cryptoincome.grid.infrastructure.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

public interface GridConfigurationSnapshotMapper {
    @Insert("""
            INSERT INTO grid_configuration_snapshot (
                exchange, instrument_id, source_type, recommendation_horizon_label,
                lower_price, upper_price, grid_count, grid_type,
                displayed_annualized_rate, displayed_minimum_per_grid_rate, displayed_maximum_per_grid_rate,
                profit_reinvestment_enabled, auto_earn_enabled,
                trailing_up_enabled, trailing_down_enabled,
                take_profit_price, stop_loss_price, source_reference, observed_at
            ) VALUES (
                #{exchange}, #{instrumentId}, #{sourceType}, #{recommendationHorizonLabel},
                #{lowerPrice}, #{upperPrice}, #{gridCount}, #{gridType},
                #{displayedAnnualizedRate}, #{displayedMinimumPerGridRate}, #{displayedMaximumPerGridRate},
                #{profitReinvestmentEnabled}, #{autoEarnEnabled},
                #{trailingUpEnabled}, #{trailingDownEnabled},
                #{takeProfitPrice}, #{stopLossPrice}, #{sourceReference}, #{observedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(GridConfigurationSnapshotEntity entity);

    @Select("""
            SELECT id, exchange, instrument_id, source_type, recommendation_horizon_label,
                   lower_price, upper_price, grid_count, grid_type,
                   displayed_annualized_rate, displayed_minimum_per_grid_rate, displayed_maximum_per_grid_rate,
                   profit_reinvestment_enabled, auto_earn_enabled,
                   trailing_up_enabled, trailing_down_enabled,
                   take_profit_price, stop_loss_price, source_reference, observed_at
            FROM grid_configuration_snapshot
            WHERE id = #{id} AND observed_at <= #{evaluationAsOf}
            """)
    GridConfigurationSnapshotEntity findByIdAsOf(long id, Instant evaluationAsOf);
}
