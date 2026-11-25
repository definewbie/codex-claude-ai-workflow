package app.cryptoincome.scenario.domain;

import java.util.List;

public record PortfolioOptionSet(
        List<PortfolioOption> options,
        int feasibleCandidateCount,
        int paretoCandidateCount
) {
    public PortfolioOptionSet {
        if (options == null || options.isEmpty() || options.size() > 3
                || feasibleCandidateCount < options.size()
                || paretoCandidateCount < options.size() || paretoCandidateCount > feasibleCandidateCount) {
            throw new IllegalArgumentException("组合方案集合不合法");
        }
        options = List.copyOf(options);
        if (options.stream().map(PortfolioOption::optionId).distinct().count() != options.size()) {
            throw new IllegalArgumentException("组合方案不得重复");
        }
    }
}
