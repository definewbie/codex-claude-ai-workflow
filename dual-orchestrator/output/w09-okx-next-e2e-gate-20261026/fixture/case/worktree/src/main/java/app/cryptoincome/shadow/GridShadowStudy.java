package app.cryptoincome.shadow;

import app.cryptoincome.grid.domain.GridScenarioParameters;

import java.math.BigDecimal;
import java.time.Instant;

public record GridShadowStudy(
        Long id, ShadowStudyState state, long configurationSnapshotId,
        Instant startAt, int targetDays, BigDecimal capital, BigDecimal simpleEarnApr,
        BigDecimal makerFeeRate, BigDecimal takerFeeRate, BigDecimal exitSlippageBps,
        BigDecimal maximumDrawdownRate, BigDecimal maximumPathDivergenceRate,
        BigDecimal maximumEndingBtcValueRatio,
        GridScenarioParameters candidate, GridScenarioParameters baseline,
        BigDecimal entryReferencePrice, Instant evidenceAsOf, String evidenceReference,
        Instant createdAt, Instant stoppedAt) {
    public GridShadowStudy {
        if (state == null || startAt == null || capital == null || candidate == null || baseline == null
                || entryReferencePrice == null || evidenceAsOf == null || evidenceReference == null
                || evidenceReference.isBlank()) throw new IllegalArgumentException("Shadow定义字段不能为空");
        if (configurationSnapshotId <= 0 || targetDays < 7 || targetDays > 14 || capital.signum() <= 0) {
            throw new IllegalArgumentException("Shadow配置不合法");
        }
        if (startAt.getNano() != 0 || startAt.getEpochSecond() % 60 != 0) {
            throw new IllegalArgumentException("Shadow开始时间必须对齐整分钟");
        }
        rate(simpleEarnApr, "simpleEarnApr");
        rate(makerFeeRate, "makerFeeRate");
        rate(takerFeeRate, "takerFeeRate");
        rate(maximumDrawdownRate, "maximumDrawdownRate");
        rate(maximumPathDivergenceRate, "maximumPathDivergenceRate");
        rate(maximumEndingBtcValueRatio, "maximumEndingBtcValueRatio");
        if (exitSlippageBps == null || exitSlippageBps.signum() < 0
                || exitSlippageBps.compareTo(new BigDecimal("10000")) >= 0) {
            throw new IllegalArgumentException("exitSlippageBps 必须在 0 到 10000 之间");
        }
        if (!inside(entryReferencePrice, candidate) || !inside(entryReferencePrice, baseline)) {
            throw new IllegalArgumentException("Shadow入场参考价必须同时位于两组网格区间内");
        }
    }

    private static boolean inside(BigDecimal price, GridScenarioParameters parameters) {
        return price.compareTo(parameters.lowerPrice()) >= 0 && price.compareTo(parameters.upperPrice()) <= 0;
    }

    private static void rate(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " 必须在 0 到 1 之间");
        }
    }
}
