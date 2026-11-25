package app.cryptoincome.reporting;

import java.math.BigDecimal;

public record ReturnMetrics(
        BigDecimal cumulativeReturnRate,
        BigDecimal simpleAnnualizedReturnRate,
        long observedMinutes,
        int minimumAnnualizationDays,
        AnnualizationStatus annualizationStatus
) {}
