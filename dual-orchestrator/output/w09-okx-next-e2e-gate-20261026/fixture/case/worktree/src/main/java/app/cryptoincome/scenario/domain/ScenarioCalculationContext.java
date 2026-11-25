package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.time.Duration;

public record ScenarioCalculationContext(BigDecimal allocatedCapitalUsdt, ScenarioPricePath pricePath) {
    public ScenarioCalculationContext {
        if (allocatedCapitalUsdt == null || allocatedCapitalUsdt.signum() <= 0) {
            throw new IllegalArgumentException("产品实际分配金额必须大于0");
        }
        if (pricePath == null || pricePath.horizon().isZero() || pricePath.horizon().isNegative()) {
            throw new IllegalArgumentException("产品情景路径期限必须大于0");
        }
        if (!Duration.ofDays(90).equals(pricePath.horizon())) {
            throw new IllegalArgumentException("第一批情景计算只支持精确90天");
        }
    }
}
