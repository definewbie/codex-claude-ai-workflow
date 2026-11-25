package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class ScenarioEvaluationService {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal HORIZON_DAYS = new BigDecimal("90");

    private final ProductQualificationPolicy qualificationPolicy;
    private final ScenarioQualificationRuleCatalog qualificationRules;
    private final PortfolioCandidateGenerator candidateGenerator;
    private final ScenarioProductCalculatorCatalog calculators;
    private final ParetoPortfolioSelector selector;

    public ScenarioEvaluationService(ProductQualificationPolicy qualificationPolicy,
                                     ScenarioQualificationRuleCatalog qualificationRules,
                                     PortfolioCandidateGenerator candidateGenerator,
                                     ScenarioProductCalculatorCatalog calculators,
                                     ParetoPortfolioSelector selector) {
        this.qualificationPolicy = qualificationPolicy;
        this.qualificationRules = qualificationRules;
        this.candidateGenerator = candidateGenerator;
        this.calculators = calculators;
        this.selector = selector;
    }

    public ScenarioDecisionReadModel evaluate(ScenarioEvaluationCommand command) {
        if (command == null) throw new IllegalArgumentException("情景评估命令不能为空");
        FrozenScenarioEvaluation evaluation = command.evaluation();
        ScenarioProductAssumptionCatalog assumptions = command.productAssumptions();
        Map<ScenarioProductCode, ProductEvidenceInput> submittedEvidence = command.productEvidence().stream()
                .collect(Collectors.toMap(ProductEvidenceInput::productCode, value -> value));
        if (!assumptions.availableProducts().containsAll(submittedEvidence.keySet())) {
            throw new IllegalArgumentException("产品证据没有对应的产品计算假设");
        }

        List<QualifiedProductEvidence> qualifications = assumptions.availableProducts().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(code -> qualify(code, submittedEvidence.get(code), evaluation))
                .toList();
        List<QualifiedProductEvidence> eligible = qualifications.stream()
                .filter(value -> value.status() == ScenarioEvidenceStatus.ELIGIBLE).toList();
        List<QualifiedProductEvidence> excluded = qualifications.stream()
                .filter(value -> value.status() != ScenarioEvidenceStatus.ELIGIBLE).toList();
        if (eligible.size() < 2) {
            throw new ScenarioEvaluationException(ScenarioEvaluationFailureCode.INSUFFICIENT_PRODUCT_EVIDENCE,
                    "至少需要两个证据完整且未过期的产品才能形成组合比较");
        }

        Set<ScenarioProductCode> eligibleCodes = eligible.stream()
                .map(QualifiedProductEvidence::productCode).collect(Collectors.toSet());
        Map<ScenarioProductCode, BigDecimal> amountCaps = amountCaps(assumptions);
        List<List<PortfolioAllocation>> allocationPlans = candidateGenerator.generate(
                evaluation.totalCapitalUsdt(), eligibleCodes, evaluation.constraints(), amountCaps);
        if (allocationPlans.isEmpty()) {
            throw new ScenarioEvaluationException(ScenarioEvaluationFailureCode.NO_FEASIBLE_PORTFOLIO,
                    "产品额度、流动性和单产品上限下没有金额守恒的候选组合");
        }

        Map<CalculationKey, ProductScenarioOutcome> cache = new LinkedHashMap<>();
        List<PortfolioCandidateEvaluation> feasible = new ArrayList<>();
        for (List<PortfolioAllocation> allocations : allocationPlans) {
            PortfolioCandidateEvaluation candidate = evaluateCandidate(evaluation, assumptions, allocations, cache);
            if (satisfiesDynamicConstraints(candidate, evaluation)) feasible.add(candidate);
        }
        PortfolioOptionSet optionSet = selector.select(feasible, evaluation.totalCapitalUsdt());
        return new ScenarioDecisionReadModel(evaluation, optionSet, eligible, excluded, List.of(
                "组合BTC峰值敞口采用CONSERVATIVE_SUM_OF_PRODUCT_PEAKS口径",
                "组合最大回撤为UNKNOWN，硬约束使用下行情景压力路径亏损",
                "结果是90天条件情景分析，不会创建或调整任何OKX产品"
        ));
    }

    private QualifiedProductEvidence qualify(ScenarioProductCode code, ProductEvidenceInput input,
                                              FrozenScenarioEvaluation evaluation) {
        if (input == null) {
            return new QualifiedProductEvidence(code, ScenarioEvidenceStatus.INELIGIBLE, evaluation.asOf(),
                    "未提供", List.of("MISSING_PRODUCT_EVIDENCE"));
        }
        return qualificationPolicy.qualify(input, evaluation.asOf(), qualificationRules.ruleFor(code));
    }

    private static Map<ScenarioProductCode, BigDecimal> amountCaps(ScenarioProductAssumptionCatalog assumptions) {
        EnumMap<ScenarioProductCode, BigDecimal> result = new EnumMap<>(ScenarioProductCode.class);
        assumptions.fixedEarn().ifPresent(value -> result.put(value.productCode(), value.maximumAllocationUsdt()));
        assumptions.flexibleEarn().ifPresent(value -> result.put(value.productCode(), value.maximumAllocationUsdt()));
        return result;
    }

    private PortfolioCandidateEvaluation evaluateCandidate(FrozenScenarioEvaluation evaluation,
                                                           ScenarioProductAssumptionCatalog assumptions,
                                                           List<PortfolioAllocation> allocations,
                                                           Map<CalculationKey, ProductScenarioOutcome> cache) {
        List<PortfolioPathOutcome> pathOutcomes = new ArrayList<>();
        for (MarketScenario scenario : evaluation.scenarios()) {
            for (ScenarioPricePath path : scenario.paths()) {
                List<ProductScenarioOutcome> products = allocations.stream()
                        .map(allocation -> productOutcome(allocation, scenario.regime(), path,
                                assumptions, evaluation.calculationVersion(), cache))
                        .toList();
                pathOutcomes.add(aggregatePath(evaluation.totalCapitalUsdt(), path, products));
            }
        }
        TargetAttainment attainment = targetAttainment(evaluation, pathOutcomes);
        BigDecimal bearWorst = minimumIncome(pathOutcomes,
                value -> value.scenarioCode().equals(MarketRegime.BEAR.name()));
        BigDecimal minimumRepresentative = minimumIncome(pathOutcomes,
                value -> value.pathCode().equals("REPRESENTATIVE"));
        BigDecimal bullRepresentative = pathOutcomes.stream()
                .filter(value -> value.scenarioCode().equals(MarketRegime.BULL.name())
                        && value.pathCode().equals("REPRESENTATIVE"))
                .findFirst().orElseThrow().netIncomeUsdt();
        BigDecimal maximumExposure = pathOutcomes.stream()
                .map(PortfolioPathOutcome::conservativePeakBtcExposureRate).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal liquidRatio = allocations.stream()
                .filter(value -> value.productCode() == ScenarioProductCode.FLEXIBLE_EARN)
                .map(PortfolioAllocation::ratio).findFirst().orElse(BigDecimal.ZERO);
        return new PortfolioCandidateEvaluation(candidateId(allocations), allocations, pathOutcomes, attainment,
                bearWorst, minimumRepresentative, bullRepresentative, maximumExposure, liquidRatio);
    }

    private ProductScenarioOutcome productOutcome(PortfolioAllocation allocation, MarketRegime regime,
                                                  ScenarioPricePath path,
                                                  ScenarioProductAssumptionCatalog assumptions,
                                                  String calculationVersion,
                                                  Map<CalculationKey, ProductScenarioOutcome> cache) {
        CalculationKey key = new CalculationKey(allocation.productCode(),
                allocation.amountUsdt().stripTrailingZeros().toPlainString(), regime, path.pathCode(),
                calculationVersion);
        return cache.computeIfAbsent(key, ignored -> calculators.calculate(allocation.productCode(),
                new ScenarioCalculationContext(allocation.amountUsdt(), path), assumptions, regime));
    }

    private static PortfolioPathOutcome aggregatePath(BigDecimal totalCapital, ScenarioPricePath path,
                                                       List<ProductScenarioOutcome> products) {
        BigDecimal netIncome = products.stream().map(ProductScenarioOutcome::netIncomeUsdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal holdingReturn = netIncome.divide(totalCapital, MC);
        BigDecimal annualized = holdingReturn.multiply(DAYS_PER_YEAR, MC).divide(HORIZON_DAYS, MC);
        BigDecimal conservativeExposureAmount = products.stream()
                .map(value -> value.allocatedCapitalUsdt().multiply(value.peakBtcDirectionalExposureRate(), MC))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal endingExposureAmount = products.stream()
                .map(value -> value.allocatedCapitalUsdt().multiply(value.endingBtcDirectionalExposureRate(), MC))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        EnumMap<ContributionCode, BigDecimal> grouped = new EnumMap<>(ContributionCode.class);
        products.stream().flatMap(value -> value.contributions().stream())
                .forEach(value -> grouped.merge(value.code(), value.amountUsdt(), BigDecimal::add));
        List<IncomeContribution> contributions = grouped.entrySet().stream()
                .map(value -> new IncomeContribution(value.getKey(), value.getValue())).toList();
        return new PortfolioPathOutcome(path.scenarioCode(), path.pathCode(), totalCapital, netIncome,
                totalCapital.add(netIncome), holdingReturn, annualized,
                conservativeExposureAmount.divide(totalCapital, MC),
                endingExposureAmount.divide(totalCapital, MC), Optional.empty(), products, contributions);
    }

    private static boolean satisfiesDynamicConstraints(PortfolioCandidateEvaluation candidate,
                                                        FrozenScenarioEvaluation evaluation) {
        if (candidate.maximumConservativeBtcExposureRate()
                .compareTo(evaluation.constraints().maximumBtcDirectionalExposureRatio()) > 0) return false;
        BigDecimal maximumLoss = evaluation.totalCapitalUsdt()
                .multiply(evaluation.constraints().maximumBearLossRate(), MC).negate();
        return candidate.bearWorstIncome().compareTo(maximumLoss) >= 0;
    }

    private static TargetAttainment targetAttainment(FrozenScenarioEvaluation evaluation,
                                                      List<PortfolioPathOutcome> outcomes) {
        List<PortfolioPathOutcome> representative = outcomes.stream()
                .filter(value -> value.pathCode().equals("REPRESENTATIVE")).toList();
        int lowCount = (int) representative.stream().filter(value -> value.simpleAnnualizedRate()
                .compareTo(evaluation.constraints().targetAprLow()) >= 0).count();
        int highCount = (int) representative.stream().filter(value -> value.simpleAnnualizedRate()
                .compareTo(evaluation.constraints().targetAprHigh()) >= 0).count();
        boolean probabilitiesPresent = evaluation.scenarios().getFirst().probability().isPresent();
        Optional<BigDecimal> lowProbability = probabilitiesPresent
                ? Optional.of(attainmentProbability(evaluation, representative,
                evaluation.constraints().targetAprLow())) : Optional.empty();
        Optional<BigDecimal> highProbability = probabilitiesPresent
                ? Optional.of(attainmentProbability(evaluation, representative,
                evaluation.constraints().targetAprHigh())) : Optional.empty();
        return new TargetAttainment(lowCount, highCount, representative.size(), lowProbability, highProbability);
    }

    private static BigDecimal attainmentProbability(FrozenScenarioEvaluation evaluation,
                                                     List<PortfolioPathOutcome> representative,
                                                     BigDecimal target) {
        BigDecimal result = BigDecimal.ZERO;
        for (MarketScenario scenario : evaluation.scenarios()) {
            PortfolioPathOutcome outcome = representative.stream()
                    .filter(value -> value.scenarioCode().equals(scenario.regime().name())).findFirst().orElseThrow();
            if (outcome.simpleAnnualizedRate().compareTo(target) >= 0) {
                result = result.add(scenario.probability().orElseThrow());
            }
        }
        return result;
    }

    private static BigDecimal minimumIncome(List<PortfolioPathOutcome> outcomes,
                                            Predicate<PortfolioPathOutcome> predicate) {
        return outcomes.stream().filter(predicate).map(PortfolioPathOutcome::netIncomeUsdt)
                .min(BigDecimal::compareTo).orElseThrow();
    }

    private static String candidateId(List<PortfolioAllocation> allocations) {
        return allocations.stream().map(value -> value.productCode().name() + "="
                        + value.ratio().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining("|"));
    }

    private record CalculationKey(ScenarioProductCode productCode, String allocatedAmount,
                                  MarketRegime regime, String pathCode, String calculationVersion) {}
}
