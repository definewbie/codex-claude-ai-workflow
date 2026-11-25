package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.math.MathContext;

/** Other known cost is quoted at reference capital; trading fees remain simulator-native. */
public record GridScenarioProfile(
        BigDecimal referenceCapitalUsdt,
        GridScenarioAssumption assumption
) {
    private static final MathContext MC = MathContext.DECIMAL128;

    public GridScenarioProfile {
        if (referenceCapitalUsdt == null || referenceCapitalUsdt.signum() <= 0 || assumption == null) {
            throw new IllegalArgumentException("网格必须提供参考资金与参数假设");
        }
    }

    public GridScenarioAssumption forAllocation(BigDecimal allocatedCapitalUsdt) {
        if (allocatedCapitalUsdt == null || allocatedCapitalUsdt.signum() <= 0) {
            throw new IllegalArgumentException("网格分配资金必须大于0");
        }
        BigDecimal scaledOtherCost = assumption.otherKnownCost()
                .multiply(allocatedCapitalUsdt.divide(referenceCapitalUsdt, MC), MC);
        return new GridScenarioAssumption(assumption.lowerPrice(), assumption.upperPrice(), assumption.gridCount(),
                assumption.makerFeeRate(), assumption.takerFeeRate(), assumption.exitSlippageBps(),
                assumption.takeProfitPrice(), assumption.stopLossPrice(), scaledOtherCost);
    }
}
