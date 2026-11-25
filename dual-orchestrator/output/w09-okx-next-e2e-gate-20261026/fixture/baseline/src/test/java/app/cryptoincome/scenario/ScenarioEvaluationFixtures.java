package app.cryptoincome.scenario;

import app.cryptoincome.scenario.application.ScenarioEvaluationCommand;
import app.cryptoincome.scenario.application.ScenarioQualificationRuleCatalog;
import app.cryptoincome.scenario.domain.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ScenarioEvaluationFixtures {
    static final Instant AS_OF = Instant.parse("2026-08-21T00:00:00Z");
    private static final Instant T30 = AS_OF.plus(Duration.ofDays(30));
    private static final Instant T60 = AS_OF.plus(Duration.ofDays(60));
    private static final Instant T90 = AS_OF.plus(Duration.ofDays(90));

    private ScenarioEvaluationFixtures() {}

    static ScenarioEvaluationCommand command() {
        ScenarioQualificationRuleCatalog rules = new ScenarioQualificationRuleCatalog();
        ScenarioProductAssumptionCatalog assumptions = assumptions();
        List<ProductEvidenceInput> evidence = assumptions.availableProducts().stream()
                .map(code -> new ProductEvidenceInput(code, AS_OF.minus(Duration.ofHours(1)),
                        "测试证据-" + code, rules.requiredFieldsFor(code), java.util.Set.of()))
                .toList();
        return new ScenarioEvaluationCommand(evaluation(constraints()), assumptions, evidence);
    }

    static FrozenScenarioEvaluation evaluation(PortfolioConstraintSnapshot constraints) {
        return new FrozenScenarioEvaluation("eval-batch-b", AS_OF, Duration.ofDays(90), bd("100000"),
                CapitalContext.NEW_MONEY_USDT, bd("100"), List.of(
                scenario(MarketRegime.BEAR, "85", "70", "70", "50"),
                scenario(MarketRegime.RANGE, "80", "120", "100", "90"),
                scenario(MarketRegime.BULL, "110", "130", "145", "165")
        ), constraints, "scenario-v1");
    }

    static PortfolioConstraintSnapshot constraints() {
        return new PortfolioConstraintSnapshot(bd("0.20"), bd("0.55"), bd("0.25"), Map.of(
                ScenarioProductCode.FIXED_EARN_90D, bd("0.80"),
                ScenarioProductCode.FLEXIBLE_EARN, BigDecimal.ONE,
                ScenarioProductCode.SMART_ARBITRAGE, bd("0.20"),
                ScenarioProductCode.SPOT_GRID, bd("0.20"),
                ScenarioProductCode.BTC_SPOT, bd("0.20")
        ), bd("0.05"), bd("0.10"), bd("0.20"));
    }

    static ScenarioProductAssumptionCatalog assumptions() {
        EnumMap<MarketRegime, SmartArbitrageScenarioAssumption> smart = new EnumMap<>(MarketRegime.class);
        smart.put(MarketRegime.BEAR, smart("-0.005"));
        smart.put(MarketRegime.RANGE, smart("0.010"));
        smart.put(MarketRegime.BULL, smart("0.018"));
        GridScenarioAssumption grid = new GridScenarioAssumption(bd("50"), bd("170"), 12,
                bd("0.0006"), bd("0.0007"), bd("10"), Optional.empty(), Optional.empty(), bd("4"));
        return new ScenarioProductAssumptionCatalog(
                Optional.of(new EarnScenarioAssumption(ScenarioProductCode.FIXED_EARN_90D,
                        bd("0.035"), bd("80000"), AS_OF, "定期人工报价", "net-apr-v1")),
                Optional.of(new EarnScenarioAssumption(ScenarioProductCode.FLEXIBLE_EARN,
                        bd("0.025"), bd("100000"), AS_OF, "活期人工报价", "net-apr-v1")),
                Optional.of(new SmartArbitrageScenarioProfile(bd("20000"), smart)),
                Optional.of(new GridScenarioProfile(bd("20000"), grid)),
                Optional.of(new BtcSpotScenarioAssumption(bd("0.0007"), bd("0.0007"), bd("10"))));
    }

    private static SmartArbitrageScenarioAssumption smart(String funding) {
        return new SmartArbitrageScenarioAssumption(bd("0.95"), bd(funding), bd("20"), bd("20"),
                bd("10"), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static MarketScenario scenario(MarketRegime regime, String repMid1, String repMid2,
                                           String repEnd, String stressEnd) {
        return new MarketScenario(regime, Optional.empty(), List.of(
                path(regime, "REPRESENTATIVE", repMid1, repMid2, repEnd),
                path(regime, "STRESS", repMid2, repMid1, stressEnd)), "用户情景");
    }

    private static ScenarioPricePath path(MarketRegime regime, String code,
                                          String middle1, String middle2, String end) {
        return new ScenarioPricePath(regime.name(), code, List.of(
                new ScenarioPricePoint(AS_OF, bd("100")),
                new ScenarioPricePoint(T30, bd(middle1)),
                new ScenarioPricePoint(T60, bd(middle2)),
                new ScenarioPricePoint(T90, bd(end))));
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
