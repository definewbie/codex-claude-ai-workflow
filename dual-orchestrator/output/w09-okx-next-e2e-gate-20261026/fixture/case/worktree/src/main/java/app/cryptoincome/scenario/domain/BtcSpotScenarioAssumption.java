package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;

public record BtcSpotScenarioAssumption(
        BigDecimal entryFeeRate,
        BigDecimal exitFeeRate,
        BigDecimal exitSlippageBps
) {
    public BtcSpotScenarioAssumption {
        rate(entryFeeRate, "entryFeeRate");
        rate(exitFeeRate, "exitFeeRate");
        if (exitSlippageBps == null || exitSlippageBps.signum() < 0
                || exitSlippageBps.compareTo(new BigDecimal("10000")) >= 0) {
            throw new IllegalArgumentException("exitSlippageBps 必须在0到10000之间");
        }
    }

    private static void rate(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " 必须在0到1之间");
        }
    }
}
