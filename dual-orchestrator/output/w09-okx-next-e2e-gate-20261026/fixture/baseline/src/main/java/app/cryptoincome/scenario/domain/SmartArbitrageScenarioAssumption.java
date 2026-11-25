package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;

public record SmartArbitrageScenarioAssumption(
        BigDecimal hedgedNotionalRatio,
        BigDecimal cumulativeFundingRate,
        BigDecimal openingCost,
        BigDecimal exitCost,
        BigDecimal slippageCost,
        BigDecimal rebalanceCost,
        BigDecimal serviceCost
) {
    public SmartArbitrageScenarioAssumption {
        rate(hedgedNotionalRatio, "hedgedNotionalRatio");
        if (cumulativeFundingRate == null) throw new IllegalArgumentException("累计资金费率不能为空");
        nonNegative(openingCost, "openingCost");
        nonNegative(exitCost, "exitCost");
        nonNegative(slippageCost, "slippageCost");
        nonNegative(rebalanceCost, "rebalanceCost");
        nonNegative(serviceCost, "serviceCost");
    }

    public BigDecimal knownCosts() {
        return openingCost.add(exitCost).add(slippageCost).add(rebalanceCost).add(serviceCost);
    }

    private static void rate(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " 必须在0到1之间");
        }
    }

    private static void nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + " 不能为负数");
    }
}
