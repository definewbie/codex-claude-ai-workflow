package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ParetoPortfolioSelector {
    private static final List<String> LIMITATIONS = List.of(
            "BTC敞口硬约束使用各产品路径峰值之和的保守上界",
            "未提供组合级同步权益序列，组合最大回撤为UNKNOWN",
            "条件情景结果不代表未来路径或发生概率"
    );

    public PortfolioOptionSet select(List<PortfolioCandidateEvaluation> feasible, java.math.BigDecimal totalCapital) {
        if (feasible == null || feasible.isEmpty()) {
            throw new ScenarioEvaluationException(ScenarioEvaluationFailureCode.NO_FEASIBLE_PORTFOLIO,
                    "当前产品资格和风险约束下没有可行组合");
        }
        List<PortfolioCandidateEvaluation> pareto = feasible.stream()
                .filter(candidate -> feasible.stream().noneMatch(other -> other != candidate && dominates(other, candidate)))
                .toList();
        LinkedHashMap<String, LabeledCandidate> selected = new LinkedHashMap<>();
        add(selected, best(pareto, conservativeComparator()), PortfolioOptionLabel.CONSERVATIVE);
        add(selected, best(pareto, balancedComparator()), PortfolioOptionLabel.BALANCED);
        add(selected, best(pareto, returnComparator()), PortfolioOptionLabel.RETURN_SEEKING);
        List<PortfolioOption> options = selected.values().stream().map(value -> new PortfolioOption(
                value.candidate.candidateId(), value.labels, totalCapital, value.candidate.allocations(),
                value.candidate.pathOutcomes(), value.candidate.targetAttainment(), LIMITATIONS)).toList();
        return new PortfolioOptionSet(options, feasible.size(), pareto.size());
    }

    private static boolean dominates(PortfolioCandidateEvaluation left, PortfolioCandidateEvaluation right) {
        boolean noWorse = left.minimumRepresentativeIncome().compareTo(right.minimumRepresentativeIncome()) >= 0
                && left.bearWorstIncome().compareTo(right.bearWorstIncome()) >= 0
                && left.liquidRatio().compareTo(right.liquidRatio()) >= 0;
        boolean better = left.minimumRepresentativeIncome().compareTo(right.minimumRepresentativeIncome()) > 0
                || left.bearWorstIncome().compareTo(right.bearWorstIncome()) > 0
                || left.liquidRatio().compareTo(right.liquidRatio()) > 0;
        return noWorse && better;
    }

    private static Comparator<PortfolioCandidateEvaluation> conservativeComparator() {
        return Comparator.comparing(PortfolioCandidateEvaluation::bearWorstIncome)
                .thenComparing(PortfolioCandidateEvaluation::maximumConservativeBtcExposureRate,
                        Comparator.reverseOrder())
                .thenComparing(PortfolioCandidateEvaluation::liquidRatio)
                .thenComparing(PortfolioCandidateEvaluation::candidateId, Comparator.reverseOrder());
    }

    private static Comparator<PortfolioCandidateEvaluation> balancedComparator() {
        return Comparator.comparing(PortfolioCandidateEvaluation::minimumRepresentativeIncome)
                .thenComparing(value -> value.targetAttainment().lowTargetScenarioCount())
                .thenComparing(PortfolioCandidateEvaluation::bearWorstIncome)
                .thenComparing(PortfolioCandidateEvaluation::candidateId, Comparator.reverseOrder());
    }

    private static Comparator<PortfolioCandidateEvaluation> returnComparator() {
        return Comparator.<PortfolioCandidateEvaluation>comparingInt(value ->
                        value.targetAttainment().lowTargetScenarioCount()
                                + value.targetAttainment().highTargetScenarioCount())
                .thenComparingInt(value -> value.targetAttainment().highTargetScenarioCount())
                .thenComparing(PortfolioCandidateEvaluation::minimumRepresentativeIncome)
                .thenComparing(PortfolioCandidateEvaluation::bullRepresentativeIncome)
                .thenComparing(PortfolioCandidateEvaluation::candidateId, Comparator.reverseOrder());
    }

    private static PortfolioCandidateEvaluation best(List<PortfolioCandidateEvaluation> candidates,
                                                     Comparator<PortfolioCandidateEvaluation> comparator) {
        return candidates.stream().max(comparator).orElseThrow();
    }

    private static void add(Map<String, LabeledCandidate> selected, PortfolioCandidateEvaluation candidate,
                            PortfolioOptionLabel label) {
        selected.computeIfAbsent(candidate.candidateId(), ignored -> new LabeledCandidate(candidate)).labels.add(label);
    }

    private static final class LabeledCandidate {
        private final PortfolioCandidateEvaluation candidate;
        private final EnumSet<PortfolioOptionLabel> labels = EnumSet.noneOf(PortfolioOptionLabel.class);

        private LabeledCandidate(PortfolioCandidateEvaluation candidate) {
            this.candidate = candidate;
        }
    }
}
