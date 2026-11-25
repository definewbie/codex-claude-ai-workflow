package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record EarnScenarioAssumption(
        ScenarioProductCode productCode,
        BigDecimal netApr,
        BigDecimal maximumAllocationUsdt,
        Instant observedAt,
        String sourceReference,
        String shareRuleVersion
) {
    public EarnScenarioAssumption {
        if (productCode != ScenarioProductCode.FIXED_EARN_90D
                && productCode != ScenarioProductCode.FLEXIBLE_EARN) {
            throw new IllegalArgumentException("简单赚币假设只支持定期或活期产品");
        }
        rate(netApr, "netApr");
        if (maximumAllocationUsdt == null || maximumAllocationUsdt.signum() <= 0) {
            throw new IllegalArgumentException("简单赚币已确认额度必须大于0");
        }
        if (observedAt == null || sourceReference == null || sourceReference.isBlank()
                || shareRuleVersion == null || shareRuleVersion.isBlank()) {
            throw new IllegalArgumentException("简单赚币证据来源和规则版本不能为空");
        }
    }

    private static void rate(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " 必须在0到1之间");
        }
    }
}
