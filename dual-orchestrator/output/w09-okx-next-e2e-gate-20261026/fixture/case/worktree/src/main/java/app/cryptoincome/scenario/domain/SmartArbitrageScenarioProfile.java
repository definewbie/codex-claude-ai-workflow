package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.EnumMap;
import java.util.Map;

/** Cost snapshots are quoted at reference capital and scaled to each enumerated allocation. */
public record SmartArbitrageScenarioProfile(
        BigDecimal referenceCapitalUsdt,
        Map<MarketRegime, SmartArbitrageScenarioAssumption> assumptions
) {
    private static final MathContext MC = MathContext.DECIMAL128;

    public SmartArbitrageScenarioProfile {
        if (referenceCapitalUsdt == null || referenceCapitalUsdt.signum() <= 0 || assumptions == null
                || assumptions.size() != MarketRegime.values().length) {
            throw new IllegalArgumentException("智能套利必须提供参考资金及三类行情假设");
        }
        assumptions = Map.copyOf(new EnumMap<>(assumptions));
        if (!assumptions.keySet().containsAll(java.util.EnumSet.allOf(MarketRegime.class))) {
            throw new IllegalArgumentException("智能套利必须覆盖 BEAR、RANGE、BULL");
        }
    }

    public SmartArbitrageScenarioAssumption forAllocation(MarketRegime regime, BigDecimal allocatedCapitalUsdt) {
        SmartArbitrageScenarioAssumption source = assumptions.get(regime);
        if (source == null || allocatedCapitalUsdt == null || allocatedCapitalUsdt.signum() <= 0) {
            throw new IllegalArgumentException("智能套利情景或分配资金不合法");
        }
        BigDecimal scale = allocatedCapitalUsdt.divide(referenceCapitalUsdt, MC);
        return new SmartArbitrageScenarioAssumption(source.hedgedNotionalRatio(), source.cumulativeFundingRate(),
                source.openingCost().multiply(scale, MC), source.exitCost().multiply(scale, MC),
                source.slippageCost().multiply(scale, MC), source.rebalanceCost().multiply(scale, MC),
                source.serviceCost().multiply(scale, MC));
    }
}
