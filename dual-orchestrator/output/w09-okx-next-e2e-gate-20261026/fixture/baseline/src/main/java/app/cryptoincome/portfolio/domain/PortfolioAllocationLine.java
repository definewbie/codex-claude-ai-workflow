package app.cryptoincome.portfolio.domain;

import java.math.BigDecimal;

public record PortfolioAllocationLine(
        String productCode,
        String productName,
        BigDecimal proposedAmountUsdt,
        BigDecimal proposedRatio,
        BigDecimal recommendedAmountUsdt,
        BigDecimal recommendedRatio,
        BigDecimal candidateNetIncomeUsdt,
        BigDecimal proposedProjectedNetIncomeUsdt,
        BigDecimal proposedReturnRate,
        BigDecimal recommendedProjectedNetIncomeUsdt,
        String evidenceStatus,
        String reason
) {}
