package app.cryptoincome.shadow.infrastructure.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface GridShadowDailyFactMapper extends BaseMapper<GridShadowDailyFactEntity> {
    @Insert("""
            INSERT INTO grid_shadow_daily_fact (
              shadow_study_id, variant_role, valuation_at, candle_count,
              net_income, simple_earn_income, buy_hold_income, maximum_drawdown_rate,
              path_divergence_rate, ending_btc_value_ratio, completed_cycles, total_fees, exit_reason
            ) VALUES (
              #{shadowStudyId}, #{variantRole}, #{valuationAt}, #{candleCount},
              #{netIncome}, #{simpleEarnIncome}, #{buyHoldIncome}, #{maximumDrawdownRate},
              #{pathDivergenceRate}, #{endingBtcValueRatio}, #{completedCycles}, #{totalFees}, #{exitReason}
            ) ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(GridShadowDailyFactEntity entity);

    @Select("SELECT * FROM grid_shadow_daily_fact WHERE shadow_study_id=#{studyId} ORDER BY valuation_at, variant_role")
    List<GridShadowDailyFactEntity> findByStudyId(long studyId);
}
