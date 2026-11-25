package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.ScenarioProductCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** Backend-owned capability catalog. Request callers cannot weaken these rules. */
@Component
public class ScenarioQualificationRuleCatalog {
    private final Map<ScenarioProductCode, ProductQualificationPolicy.QualificationRule> rules = Map.of(
            ScenarioProductCode.FIXED_EARN_90D, rule(2, "NET_APR", "MAX_ALLOCATION", "TERMS_CONFIRMED", "RULE_VERSION"),
            ScenarioProductCode.FLEXIBLE_EARN, rule(2, "NET_APR", "MAX_ALLOCATION", "REDEMPTION_ASSUMPTION", "RULE_VERSION"),
            ScenarioProductCode.SMART_ARBITRAGE, rule(2, "HEDGED_NOTIONAL", "FUNDING_SCENARIOS", "ALL_KNOWN_COSTS"),
            ScenarioProductCode.SPOT_GRID, rule(7, "GRID_PARAMETERS", "TRADING_FEES", "EXIT_SLIPPAGE", "SHADOW_STATUS"),
            ScenarioProductCode.BTC_SPOT, rule(2, "ENTRY_FEE", "EXIT_FEE", "EXIT_SLIPPAGE")
    );

    public ProductQualificationPolicy.QualificationRule ruleFor(ScenarioProductCode productCode) {
        ProductQualificationPolicy.QualificationRule rule = rules.get(productCode);
        if (rule == null) throw new IllegalArgumentException("没有该产品的后端资格规则");
        return rule;
    }

    public Set<String> requiredFieldsFor(ScenarioProductCode productCode) {
        return ruleFor(productCode).requiredKnownFields();
    }

    private static ProductQualificationPolicy.QualificationRule rule(int maximumAgeDays, String... fields) {
        return new ProductQualificationPolicy.QualificationRule(Duration.ofDays(maximumAgeDays), Set.of(fields));
    }
}
