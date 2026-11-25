package app.cryptoincome.scenario;

import app.cryptoincome.grid.application.FixedGridPathSimulator;
import app.cryptoincome.scenario.application.*;
import app.cryptoincome.scenario.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static app.cryptoincome.scenario.ScenarioEvaluationFixtures.*;
import static org.assertj.core.api.Assertions.*;

class ScenarioEvaluationServiceTest {
    @Test
    void evaluatesFiveProductsByActualAllocationAndReturnsOnlyFeasibleConservingOptions() {
        ScenarioEvaluationService service = service();
        ScenarioDecisionReadModel first = service.evaluate(command());
        ScenarioDecisionReadModel replay = service.evaluate(command());

        assertThat(replay).isEqualTo(first);
        assertThat(first.optionSet().options()).hasSizeBetween(1, 3);
        assertThat(first.optionSet().options().stream().flatMap(option -> option.labels().stream()).toList())
                .containsExactlyInAnyOrder(PortfolioOptionLabel.CONSERVATIVE,
                        PortfolioOptionLabel.BALANCED, PortfolioOptionLabel.RETURN_SEEKING);
        assertThat(first.excludedProducts()).isEmpty();
        assertThat(first.limitations()).anyMatch(value -> value.contains("CONSERVATIVE_SUM_OF_PRODUCT_PEAKS"));
        assertThat(first.optionSet().options()).allSatisfy(option -> {
            assertThat(option.allocations()).allSatisfy(allocation -> {
                assertThat(allocation.ratio()).isPositive();
                assertThat(allocation.amountUsdt()).isPositive();
            });
            assertThat(option.allocations().stream().map(PortfolioAllocation::amountUsdt)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(bd("100000"));
            assertThat(option.pathOutcomes()).hasSize(6).allSatisfy(path -> {
                assertThat(path.maximumDrawdownRate()).isEmpty();
                assertThat(path.conservativePeakBtcExposureRate()).isLessThanOrEqualTo(bd("0.55"));
                assertThat(path.productOutcomes().stream().map(ProductScenarioOutcome::netIncomeUsdt)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(path.netIncomeUsdt());
                assertThat(path.contributions().stream().map(IncomeContribution::amountUsdt)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(path.netIncomeUsdt());
            });
            BigDecimal worstBear = option.pathOutcomes().stream()
                    .filter(path -> path.scenarioCode().equals("BEAR"))
                    .map(PortfolioPathOutcome::netIncomeUsdt).min(BigDecimal::compareTo).orElseThrow();
            assertThat(worstBear).isGreaterThanOrEqualTo(bd("-25000"));
            assertThat(option.targetAttainment().lowTargetProbability()).isEmpty();
            assertThat(option.targetAttainment().highTargetProbability()).isEmpty();
        });
    }

    @Test
    void returnsStableFailuresForInsufficientEvidenceAndNoFeasiblePortfolio() {
        ScenarioEvaluationCommand original = command();
        ScenarioEvaluationCommand insufficient = new ScenarioEvaluationCommand(original.evaluation(),
                original.productAssumptions(), List.of(original.productEvidence().getFirst()));
        assertThatThrownBy(() -> service().evaluate(insufficient))
                .isInstanceOfSatisfying(ScenarioEvaluationException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                ScenarioEvaluationFailureCode.INSUFFICIENT_PRODUCT_EVIDENCE));

        ScenarioProductAssumptionCatalog twoEarn = new ScenarioProductAssumptionCatalog(
                original.productAssumptions().fixedEarn(), original.productAssumptions().flexibleEarn(),
                Optional.empty(), Optional.empty(), Optional.empty());
        var impossibleConstraints = new PortfolioConstraintSnapshot(bd("0.80"), bd("0.10"), bd("0.10"), Map.of(
                ScenarioProductCode.FIXED_EARN_90D, bd("0.50"),
                ScenarioProductCode.FLEXIBLE_EARN, bd("0.50")),
                bd("0.05"), bd("0.10"), bd("0.20"));
        FrozenScenarioEvaluation impossibleEvaluation = evaluation(impossibleConstraints);
        List<ProductEvidenceInput> earnEvidence = original.productEvidence().stream()
                .filter(value -> value.productCode() == ScenarioProductCode.FIXED_EARN_90D
                        || value.productCode() == ScenarioProductCode.FLEXIBLE_EARN).toList();
        assertThatThrownBy(() -> service().evaluate(new ScenarioEvaluationCommand(
                impossibleEvaluation, twoEarn, earnEvidence)))
                .isInstanceOfSatisfying(ScenarioEvaluationException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                ScenarioEvaluationFailureCode.NO_FEASIBLE_PORTFOLIO));
    }

    @Test
    void excludesStaleProductsAndOnlyReportsProbabilitiesWhenAllThreeAreSupplied() {
        ScenarioEvaluationCommand original = command();
        List<ProductEvidenceInput> withStaleGrid = original.productEvidence().stream().map(value ->
                value.productCode() == ScenarioProductCode.SPOT_GRID
                        ? new ProductEvidenceInput(value.productCode(), AS_OF.minus(Duration.ofDays(8)),
                        value.sourceReference(), value.knownFields(), value.unknownFields()) : value).toList();
        ScenarioDecisionReadModel staleResult = service().evaluate(new ScenarioEvaluationCommand(
                original.evaluation(), original.productAssumptions(), withStaleGrid));
        assertThat(staleResult.excludedProducts()).singleElement().satisfies(value -> {
            assertThat(value.productCode()).isEqualTo(ScenarioProductCode.SPOT_GRID);
            assertThat(value.status()).isEqualTo(ScenarioEvidenceStatus.RESEARCH_ONLY);
        });
        assertThat(staleResult.optionSet().options()).allSatisfy(option -> assertThat(option.allocations())
                .noneMatch(value -> value.productCode() == ScenarioProductCode.SPOT_GRID));

        EnumMap<MarketRegime, BigDecimal> probabilities = new EnumMap<>(MarketRegime.class);
        probabilities.put(MarketRegime.BEAR, bd("0.30"));
        probabilities.put(MarketRegime.RANGE, bd("0.40"));
        probabilities.put(MarketRegime.BULL, bd("0.30"));
        List<MarketScenario> weightedScenarios = original.evaluation().scenarios().stream()
                .map(value -> new MarketScenario(value.regime(), Optional.of(probabilities.get(value.regime())),
                        value.paths(), value.sourceReference())).toList();
        FrozenScenarioEvaluation weighted = new FrozenScenarioEvaluation("eval-weighted", AS_OF,
                original.evaluation().horizon(), original.evaluation().totalCapitalUsdt(),
                original.evaluation().capitalContext(), original.evaluation().startingBtcPrice(),
                weightedScenarios, original.evaluation().constraints(), original.evaluation().calculationVersion());
        ScenarioDecisionReadModel weightedResult = service().evaluate(new ScenarioEvaluationCommand(
                weighted, original.productAssumptions(), original.productEvidence()));
        ScenarioDecisionReadModel unweightedResult = service().evaluate(original);
        assertThat(weightedResult.optionSet().options()).extracting(PortfolioOption::allocations)
                .containsExactlyElementsOf(unweightedResult.optionSet().options().stream()
                        .map(PortfolioOption::allocations).toList());
        assertThat(weightedResult.optionSet().options()).allSatisfy(option -> {
            assertThat(option.targetAttainment().lowTargetProbability()).isPresent();
            assertThat(option.targetAttainment().highTargetProbability()).isPresent();
        });
    }

    @Test
    void doesNotRelaxBearLossConstraintWhenEveryStaticPlanFailsDynamically() {
        ScenarioEvaluationCommand original = command();
        ScenarioProductAssumptionCatalog liquidAndSpot = new ScenarioProductAssumptionCatalog(
                Optional.empty(), original.productAssumptions().flexibleEarn(), Optional.empty(), Optional.empty(),
                original.productAssumptions().btcSpot());
        PortfolioConstraintSnapshot zeroLoss = new PortfolioConstraintSnapshot(bd("0.20"), BigDecimal.ONE,
                BigDecimal.ZERO, Map.of(ScenarioProductCode.FLEXIBLE_EARN, bd("0.20"),
                ScenarioProductCode.BTC_SPOT, bd("0.80")), bd("0.05"), bd("0.10"), bd("0.20"));
        List<ProductEvidenceInput> evidence = original.productEvidence().stream()
                .filter(value -> value.productCode() == ScenarioProductCode.FLEXIBLE_EARN
                        || value.productCode() == ScenarioProductCode.BTC_SPOT).toList();

        assertThatThrownBy(() -> service().evaluate(new ScenarioEvaluationCommand(
                evaluation(zeroLoss), liquidAndSpot, evidence)))
                .isInstanceOfSatisfying(ScenarioEvaluationException.class,
                        error -> assertThat(error.code()).isEqualTo(
                                ScenarioEvaluationFailureCode.NO_FEASIBLE_PORTFOLIO));
    }

    static ScenarioEvaluationService service() {
        var calculators = new ScenarioProductCalculatorCatalog(
                new SimpleEarnScenarioCalculator(), new SmartArbitrageScenarioCalculator(),
                new FixedGridScenarioCalculator(new FixedGridPathSimulator()), new BtcSpotScenarioCalculator());
        return new ScenarioEvaluationService(new ProductQualificationPolicy(),
                new ScenarioQualificationRuleCatalog(), new PortfolioCandidateGenerator(), calculators,
                new ParetoPortfolioSelector());
    }
}
