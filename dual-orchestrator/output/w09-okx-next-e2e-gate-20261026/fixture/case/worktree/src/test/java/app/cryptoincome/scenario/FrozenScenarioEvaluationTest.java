package app.cryptoincome.scenario;

import app.cryptoincome.scenario.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrozenScenarioEvaluationTest {
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant MID = Instant.parse("2026-02-15T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    @Test
    void acceptsThreeRegimesWithoutProbabilitiesWhenAllPathsShareTimeline() {
        new FrozenScenarioEvaluation("eval-1", FROM, Duration.ofDays(90), bd("200000"),
                CapitalContext.NEW_MONEY_USDT, bd("100"),
                List.of(scenario(MarketRegime.BEAR, Optional.empty(), "80", "70"),
                        scenario(MarketRegime.RANGE, Optional.empty(), "105", "95"),
                        scenario(MarketRegime.BULL, Optional.empty(), "130", "145")),
                constraints(), "scenario-v1");
    }

    @Test
    void rejectsPartialProbabilitiesAndDifferentTimelinesBeforeCalculation() {
        assertThatThrownBy(() -> new FrozenScenarioEvaluation(
                "eval-2", FROM, Duration.ofDays(90), bd("200000"), CapitalContext.NEW_MONEY_USDT, bd("100"),
                List.of(scenario(MarketRegime.BEAR, Optional.of(bd("0.3")), "80", "70"),
                        scenario(MarketRegime.RANGE, Optional.empty(), "105", "95"),
                        scenario(MarketRegime.BULL, Optional.of(bd("0.7")), "130", "145")),
                constraints(), "scenario-v1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("全部为空或全部填写");

        MarketScenario shifted = new MarketScenario(MarketRegime.BEAR, Optional.empty(), List.of(
                path(MarketRegime.BEAR, "REPRESENTATIVE", FROM.plusSeconds(60), MID, TO, "80"),
                path(MarketRegime.BEAR, "STRESS", FROM.plusSeconds(60), MID, TO, "70")), "用户情景");
        assertThatThrownBy(() -> new FrozenScenarioEvaluation(
                "eval-3", FROM, Duration.ofDays(90), bd("200000"), CapitalContext.NEW_MONEY_USDT, bd("100"),
                List.of(shifted, scenario(MarketRegime.RANGE, Optional.empty(), "105", "95"),
                        scenario(MarketRegime.BULL, Optional.empty(), "130", "145")),
                constraints(), "scenario-v1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("起点必须等于评估时点");
    }

    @Test
    void rejectsDuplicatePathCodesWithinOneRegime() {
        ScenarioPricePath representative = path(
                MarketRegime.BEAR, "REPRESENTATIVE", FROM, MID, TO, "80");

        assertThatThrownBy(() -> new MarketScenario(
                MarketRegime.BEAR, Optional.empty(),
                List.of(representative, representative), "用户情景"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("路径代码不能重复");
    }

    private static MarketScenario scenario(MarketRegime regime, Optional<BigDecimal> probability,
                                           String representativeEnd, String stressEnd) {
        return new MarketScenario(regime, probability, List.of(
                path(regime, "REPRESENTATIVE", FROM, MID, TO, representativeEnd),
                path(regime, "STRESS", FROM, MID, TO, stressEnd)), "用户情景");
    }

    private static ScenarioPricePath path(MarketRegime regime, String code,
                                          Instant from, Instant mid, Instant to, String end) {
        return new ScenarioPricePath(regime.name(), code, List.of(
                new ScenarioPricePoint(from, bd("100")),
                new ScenarioPricePoint(mid, bd("90")),
                new ScenarioPricePoint(to, bd(end))));
    }

    private static PortfolioConstraintSnapshot constraints() {
        return new PortfolioConstraintSnapshot(bd("0.20"), bd("0.40"), bd("0.15"),
                Map.of(ScenarioProductCode.FIXED_EARN_90D, bd("0.80")),
                bd("0.05"), bd("0.10"), bd("0.20"));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
