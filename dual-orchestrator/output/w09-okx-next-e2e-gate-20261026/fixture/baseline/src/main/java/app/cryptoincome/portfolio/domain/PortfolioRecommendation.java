package app.cryptoincome.portfolio.domain;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioRecommendation(
        BigDecimal totalCapitalUsdt,
        String policyName,
        boolean mathematicalOptimum,
        boolean liveAccountFactsUsed,
        BigDecimal shadowCandidateCapitalUsdt,
        List<PortfolioAllocationLine> allocations,
        BigDecimal proposedAllocatedCapitalUsdt,
        BigDecimal recommendedAllocatedCapitalUsdt,
        BigDecimal proposedProjectedNetIncomeUsdt,
        BigDecimal proposedProjectedReturnRate,
        BigDecimal proposedSimpleAnnualizedReturnRate,
        BigDecimal recommendedProjectedNetIncomeUsdt,
        BigDecimal recommendedProjectedReturnRate,
        BigDecimal projectedIncrementalVsRecommendedUsdt,
        String evaluationStatus,
        List<String> boundaries
) {
    public PortfolioRecommendation {
        allocations = List.copyOf(allocations);
        boundaries = List.copyOf(boundaries);
    }
}
