package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;

public record SmartArbitrageAllocation(
        BigDecimal candidateCapital,
        BigDecimal spotInvestmentUsdt,
        BigDecimal spotCapitalRatio,
        BigDecimal referenceSpotPrice,
        BigDecimal estimatedBtcQuantity,
        BigDecimal shortHedgeNotionalUsdt,
        BigDecimal estimatedNetDirectionalNotionalUsdt,
        BigDecimal reservedMarginUsdt,
        BigDecimal reservedMarginRatio,
        BigDecimal unallocatedUsdt
) {}
