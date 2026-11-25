package app.cryptoincome.reporting;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;

public final class ReturnMetricsCalculator {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal MINUTES_PER_YEAR = new BigDecimal("525600");

    private ReturnMetricsCalculator() {}

    public static ReturnMetrics calculate(BigDecimal income, BigDecimal capital,
                                          Duration observedDuration, int minimumAnnualizationDays) {
        if (income == null || capital == null || observedDuration == null) {
            throw new IllegalArgumentException("收益、资金和观察时长不能为空");
        }
        if (capital.signum() <= 0 || observedDuration.isNegative() || observedDuration.isZero()
                || minimumAnnualizationDays < 1) {
            throw new IllegalArgumentException("资金、观察时长和最小年化天数必须为正");
        }
        long observedMinutes = observedDuration.toMinutes();
        if (observedMinutes < 1) throw new IllegalArgumentException("观察时长至少为一分钟");
        BigDecimal cumulative = income.divide(capital, MC);
        if (observedMinutes < Duration.ofDays(minimumAnnualizationDays).toMinutes()) {
            return new ReturnMetrics(cumulative, null, observedMinutes, minimumAnnualizationDays,
                    AnnualizationStatus.SAMPLE_TOO_SHORT);
        }
        BigDecimal annualized = cumulative.multiply(MINUTES_PER_YEAR, MC)
                .divide(BigDecimal.valueOf(observedMinutes), MC);
        return new ReturnMetrics(cumulative, annualized, observedMinutes, minimumAnnualizationDays,
                AnnualizationStatus.AVAILABLE);
    }
}
