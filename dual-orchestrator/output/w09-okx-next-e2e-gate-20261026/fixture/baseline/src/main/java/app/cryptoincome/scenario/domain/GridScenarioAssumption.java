package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.util.Optional;

public record GridScenarioAssumption(
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        int gridCount,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal exitSlippageBps,
        Optional<BigDecimal> takeProfitPrice,
        Optional<BigDecimal> stopLossPrice,
        BigDecimal otherKnownCost
) {
    public GridScenarioAssumption {
        takeProfitPrice = takeProfitPrice == null ? Optional.empty() : takeProfitPrice;
        stopLossPrice = stopLossPrice == null ? Optional.empty() : stopLossPrice;
        if (otherKnownCost == null || otherKnownCost.signum() < 0) {
            throw new IllegalArgumentException("网格其他已知成本不能为负数");
        }
    }
}
