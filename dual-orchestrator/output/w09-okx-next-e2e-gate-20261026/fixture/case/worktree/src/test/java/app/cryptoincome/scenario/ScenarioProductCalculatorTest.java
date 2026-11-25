package app.cryptoincome.scenario;

import app.cryptoincome.grid.application.FixedGridPathSimulator;
import app.cryptoincome.scenario.application.BtcSpotScenarioCalculator;
import app.cryptoincome.scenario.application.FixedGridScenarioCalculator;
import app.cryptoincome.scenario.application.SimpleEarnScenarioCalculator;
import app.cryptoincome.scenario.application.SmartArbitrageScenarioCalculator;
import app.cryptoincome.scenario.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioProductCalculatorTest {
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant MID = Instant.parse("2026-02-15T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    @Test
    void simpleEarnUsesRequestNetAprWithoutMarketSourceAndRejectsCapacityOverflow() {
        SimpleEarnScenarioCalculator calculator = new SimpleEarnScenarioCalculator();
        EarnScenarioAssumption assumption = new EarnScenarioAssumption(
                ScenarioProductCode.FIXED_EARN_90D, bd("0.035"), bd("20000"),
                FROM, "OKX确认页", "净APR已确认-v1");

        ProductScenarioOutcome outcome = calculator.calculate(context("20000", path("110", "100", "110")),
                assumption);

        assertThat(outcome.netIncomeUsdt()).isEqualByComparingTo(
                bd("20000").multiply(bd("0.035")).multiply(bd("90")).divide(bd("365"),
                        java.math.MathContext.DECIMAL128));
        assertThat(outcome.contributions()).singleElement()
                .extracting(IncomeContribution::code)
                .isEqualTo(ContributionCode.FIXED_EARN_INTEREST);
        assertThatThrownBy(() -> calculator.calculate(context("20001", path("110", "100", "110")),
                assumption)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("超过");
    }

    @Test
    void smartArbitrageKeepsFundingAndAllKnownCostsAdditive() {
        SmartArbitrageScenarioCalculator calculator = new SmartArbitrageScenarioCalculator();
        SmartArbitrageScenarioAssumption assumption = new SmartArbitrageScenarioAssumption(
                bd("0.95"), bd("0.01"), bd("36"), bd("20"), bd("10"), bd("2"), bd("1"));

        ProductScenarioOutcome outcome = calculator.calculate(
                context("20000", path("110", "100", "110")), assumption);

        assertThat(outcome.netIncomeUsdt()).isEqualByComparingTo("121");
        assertThat(sum(outcome.contributions())).isEqualByComparingTo(outcome.netIncomeUsdt());
        assertThat(outcome.peakBtcDirectionalExposureRate()).isZero();
        assertThat(outcome.maximumDrawdownRate()).isEmpty();
    }

    @Test
    void btcSpotUsesTerminalPriceForIncomeButPathPeakForExposureAndDrawdown() {
        BtcSpotScenarioCalculator calculator = new BtcSpotScenarioCalculator();
        BtcSpotScenarioAssumption assumption = new BtcSpotScenarioAssumption(
                bd("0.0007"), bd("0.0007"), bd("10"));

        ProductScenarioOutcome highFirst = calculator.calculate(
                context("20000", path("100", "140", "110")), assumption);
        ProductScenarioOutcome lowFirst = calculator.calculate(
                context("20000", path("100", "70", "110")), assumption);

        assertThat(highFirst.netIncomeUsdt()).isEqualByComparingTo(lowFirst.netIncomeUsdt());
        assertThat(highFirst.peakBtcDirectionalExposureRate())
                .isGreaterThan(lowFirst.peakBtcDirectionalExposureRate());
        assertThat(lowFirst.maximumDrawdownRate().orElseThrow()).isPositive();
        assertThat(sum(highFirst.contributions())).isEqualByComparingTo(highFirst.netIncomeUsdt());
    }

    @Test
    void fixedGridSharesTheStateMachineButUsesGrossAdditiveAttribution() {
        FixedGridScenarioCalculator calculator = new FixedGridScenarioCalculator(new FixedGridPathSimulator());
        GridScenarioAssumption assumption = new GridScenarioAssumption(
                bd("100"), bd("120"), 2, bd("0.001"), bd("0.002"), bd("10"),
                Optional.empty(), Optional.empty(), bd("2"));

        ProductScenarioOutcome lowThenHigh = calculator.calculate(
                context("1000", path("110", "100", "120")), assumption);
        ProductScenarioOutcome highThenLow = calculator.calculate(
                context("1000", path("110", "120", "100")), assumption);

        assertThat(lowThenHigh.netIncomeUsdt()).isNotEqualByComparingTo(highThenLow.netIncomeUsdt());
        assertThat(sum(lowThenHigh.contributions())).isEqualByComparingTo(lowThenHigh.netIncomeUsdt());
        assertThat(lowThenHigh.contributions()).extracting(IncomeContribution::code)
                .contains(ContributionCode.GRID_CYCLE_SPREAD, ContributionCode.GRID_INVENTORY_PNL,
                        ContributionCode.GRID_TRADING_FEE, ContributionCode.ENTRY_EXIT_FEE,
                        ContributionCode.SLIPPAGE, ContributionCode.OTHER_KNOWN_COST);
        assertThat(lowThenHigh.peakBtcDirectionalExposureRate())
                .isGreaterThanOrEqualTo(lowThenHigh.endingBtcDirectionalExposureRate());
    }

    @Test
    void outcomeRejectsContributionThatDoesNotEqualNetIncome() {
        ScenarioCalculationContext context = context("1000", path("100", "100", "100"));
        assertThatThrownBy(() -> ProductScenarioOutcome.create(
                ScenarioProductCode.FLEXIBLE_EARN, context, bd("10"), BigDecimal.ZERO,
                BigDecimal.ZERO, Optional.empty(),
                List.of(new IncomeContribution(ContributionCode.FLEXIBLE_EARN_INTEREST, bd("9"))), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("贡献合计");
    }

    @Test
    void smartProfileScalesQuotedCostsToActualEnumeratedAllocation() {
        SmartArbitrageScenarioAssumption quoted = new SmartArbitrageScenarioAssumption(
                bd("0.95"), bd("0.01"), bd("10"), bd("20"), bd("5"), bd("2"), bd("3"));
        SmartArbitrageScenarioProfile profile = new SmartArbitrageScenarioProfile(bd("20000"),
                java.util.Map.of(MarketRegime.BEAR, quoted, MarketRegime.RANGE, quoted,
                        MarketRegime.BULL, quoted));

        SmartArbitrageScenarioAssumption scaled = profile.forAllocation(MarketRegime.RANGE, bd("50000"));

        assertThat(scaled.cumulativeFundingRate()).isEqualByComparingTo(bd("0.01"));
        assertThat(scaled.knownCosts()).isEqualByComparingTo(bd("100"));
    }

    private static ScenarioCalculationContext context(String capital, ScenarioPricePath path) {
        return new ScenarioCalculationContext(bd(capital), path);
    }

    private static ScenarioPricePath path(String start, String middle, String end) {
        return new ScenarioPricePath("RANGE", "REPRESENTATIVE", List.of(
                new ScenarioPricePoint(FROM, bd(start)),
                new ScenarioPricePoint(MID, bd(middle)),
                new ScenarioPricePoint(TO, bd(end))));
    }

    private static BigDecimal sum(List<IncomeContribution> contributions) {
        return contributions.stream().map(IncomeContribution::amountUsdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
