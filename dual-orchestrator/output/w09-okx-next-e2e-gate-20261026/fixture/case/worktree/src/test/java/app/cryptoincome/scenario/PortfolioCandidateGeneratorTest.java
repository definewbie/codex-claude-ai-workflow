package app.cryptoincome.scenario;

import app.cryptoincome.scenario.application.PortfolioCandidateGenerator;
import app.cryptoincome.scenario.domain.PortfolioAllocation;
import app.cryptoincome.scenario.domain.ScenarioProductCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioCandidateGeneratorTest {
    @Test
    void enumeratesOnlyConservingPlansWithinLiquidityProductAndAmountCaps() {
        var constraints = new app.cryptoincome.scenario.domain.PortfolioConstraintSnapshot(
                bd("0.20"), bd("0.50"), bd("0.20"), Map.of(
                ScenarioProductCode.FIXED_EARN_90D, bd("0.60"),
                ScenarioProductCode.FLEXIBLE_EARN, bd("0.50"),
                ScenarioProductCode.BTC_SPOT, bd("0.30")),
                bd("0.05"), bd("0.10"), bd("0.20"));

        var plans = new PortfolioCandidateGenerator().generate(bd("100000"), Set.of(
                        ScenarioProductCode.FIXED_EARN_90D,
                        ScenarioProductCode.FLEXIBLE_EARN,
                        ScenarioProductCode.BTC_SPOT), constraints,
                Map.of(ScenarioProductCode.FIXED_EARN_90D, bd("40000")));

        assertThat(plans).isNotEmpty();
        assertThat(plans).allSatisfy(plan -> {
            assertThat(plan).allSatisfy(allocation -> {
                assertThat(allocation.ratio()).isPositive();
                assertThat(allocation.amountUsdt()).isPositive();
            });
            assertThat(plan.stream().map(PortfolioAllocation::ratio).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(BigDecimal.ONE);
            assertThat(ratio(plan, ScenarioProductCode.FLEXIBLE_EARN)).isGreaterThanOrEqualTo(bd("0.20"));
            assertThat(ratio(plan, ScenarioProductCode.FIXED_EARN_90D)).isLessThanOrEqualTo(bd("0.40"));
            assertThat(ratio(plan, ScenarioProductCode.BTC_SPOT)).isLessThanOrEqualTo(bd("0.30"));
        });
    }

    private static BigDecimal ratio(java.util.List<PortfolioAllocation> plan, ScenarioProductCode code) {
        return plan.stream().filter(value -> value.productCode() == code)
                .map(PortfolioAllocation::ratio).findFirst().orElse(BigDecimal.ZERO);
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
