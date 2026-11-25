package app.cryptoincome.shadow;

import app.cryptoincome.grid.domain.GridExitReason;

import java.math.BigDecimal;
import java.time.Instant;

public record GridShadowDailyFact(
        Long id, long studyId, ShadowVariantRole role, Instant valuationAt, int candleCount,
        BigDecimal netIncome, BigDecimal simpleEarnIncome, BigDecimal buyHoldIncome,
        BigDecimal maximumDrawdownRate, BigDecimal pathDivergenceRate,
        BigDecimal endingBtcValueRatio, int completedCycles, BigDecimal totalFees,
        GridExitReason exitReason) {}
