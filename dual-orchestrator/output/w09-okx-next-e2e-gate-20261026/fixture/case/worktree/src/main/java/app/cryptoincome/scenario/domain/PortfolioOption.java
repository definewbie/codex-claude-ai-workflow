package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public record PortfolioOption(
        String optionId,
        Set<PortfolioOptionLabel> labels,
        BigDecimal totalCapitalUsdt,
        List<PortfolioAllocation> allocations,
        List<PortfolioPathOutcome> pathOutcomes,
        TargetAttainment targetAttainment,
        List<String> limitations
) {
    public PortfolioOption {
        if (optionId == null || optionId.isBlank() || labels == null || labels.isEmpty()
                || totalCapitalUsdt == null || totalCapitalUsdt.signum() <= 0
                || allocations == null || allocations.isEmpty() || pathOutcomes == null || pathOutcomes.isEmpty()
                || targetAttainment == null || limitations == null) {
            throw new IllegalArgumentException("组合方案字段不完整");
        }
        labels = Set.copyOf(labels);
        allocations = List.copyOf(allocations);
        pathOutcomes = List.copyOf(pathOutcomes);
        limitations = List.copyOf(limitations);
        BigDecimal amount = allocations.stream().map(PortfolioAllocation::amountUsdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ratio = allocations.stream().map(PortfolioAllocation::ratio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long products = allocations.stream().map(PortfolioAllocation::productCode).distinct().count();
        long paths = pathOutcomes.stream().map(outcome -> outcome.scenarioCode() + "/" + outcome.pathCode())
                .distinct().count();
        if (amount.compareTo(totalCapitalUsdt) != 0 || ratio.compareTo(BigDecimal.ONE) != 0
                || products != allocations.size() || pathOutcomes.stream()
                .anyMatch(outcome -> outcome.totalCapitalUsdt().compareTo(totalCapitalUsdt) != 0)
                || paths != pathOutcomes.size()) {
            throw new IllegalArgumentException("组合方案分配不守恒或包含重复产品");
        }
    }
}
