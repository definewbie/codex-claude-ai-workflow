package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record FixedGridAssessmentRequest(
        Instant evaluationAsOf,
        Instant from,
        Instant to,
        long configurationSnapshotId,
        BigDecimal capital,
        BigDecimal simpleEarnApr,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal exitSlippageBps,
        BigDecimal maximumDrawdownRate,
        BigDecimal maximumPathDivergenceRate,
        BigDecimal maximumEndingBtcValueRatio
) {
    public FixedGridAssessmentRequest {
        if (evaluationAsOf == null || from == null || to == null) throw new IllegalArgumentException("时间不能为空");
        if (!Duration.ofDays(90).equals(Duration.between(from, to))) {
            throw new IllegalArgumentException("第一期固定网格只支持精确 90 天窗口");
        }
        if (to.isAfter(evaluationAsOf)) throw new IllegalArgumentException("窗口终点不能晚于评估时点");
        if (configurationSnapshotId <= 0) throw new IllegalArgumentException("configurationSnapshotId 必须大于 0");
        positive(capital, "capital");
        rate(simpleEarnApr, "simpleEarnApr");
        rate(makerFeeRate, "makerFeeRate");
        rate(takerFeeRate, "takerFeeRate");
        if (exitSlippageBps == null || exitSlippageBps.signum() < 0
                || exitSlippageBps.compareTo(new BigDecimal("10000")) >= 0) {
            throw new IllegalArgumentException("exitSlippageBps 必须在 0 到 10000 之间");
        }
        rate(maximumDrawdownRate, "maximumDrawdownRate");
        rate(maximumPathDivergenceRate, "maximumPathDivergenceRate");
        rate(maximumEndingBtcValueRatio, "maximumEndingBtcValueRatio");
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }

    private static void rate(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " 必须在 0 到 1 之间");
        }
    }
}
