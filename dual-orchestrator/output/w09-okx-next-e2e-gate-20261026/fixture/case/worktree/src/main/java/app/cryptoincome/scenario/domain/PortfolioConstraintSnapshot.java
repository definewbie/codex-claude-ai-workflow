package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.util.Map;

public record PortfolioConstraintSnapshot(
        BigDecimal minimumLiquidUsdtRatio,
        BigDecimal maximumBtcDirectionalExposureRatio,
        BigDecimal maximumBearLossRate,
        Map<ScenarioProductCode, BigDecimal> maximumProductRatios,
        BigDecimal allocationStep,
        BigDecimal targetAprLow,
        BigDecimal targetAprHigh
) {
    public PortfolioConstraintSnapshot {
        rate(minimumLiquidUsdtRatio, "minimumLiquidUsdtRatio");
        rate(maximumBtcDirectionalExposureRatio, "maximumBtcDirectionalExposureRatio");
        rate(maximumBearLossRate, "maximumBearLossRate");
        rate(allocationStep, "allocationStep");
        rate(targetAprLow, "targetAprLow");
        rate(targetAprHigh, "targetAprHigh");
        if (allocationStep.signum() <= 0 || BigDecimal.ONE.remainder(allocationStep).signum() != 0) {
            throw new IllegalArgumentException("allocationStep 必须大于0且能整除1");
        }
        if (targetAprHigh.compareTo(targetAprLow) < 0) {
            throw new IllegalArgumentException("高目标年化不能低于低目标年化");
        }
        if (maximumProductRatios == null) throw new IllegalArgumentException("产品上限不能为空");
        maximumProductRatios = Map.copyOf(maximumProductRatios);
        maximumProductRatios.forEach((code, value) -> {
            if (code == null) throw new IllegalArgumentException("产品上限代码不能为空");
            rate(value, "maximumProductRatio");
        });
    }

    private static void rate(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " 必须在0到1之间");
        }
    }
}
