package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;

public record PortfolioAllocation(
        ScenarioProductCode productCode,
        BigDecimal ratio,
        BigDecimal amountUsdt
) {
    public PortfolioAllocation {
        if (productCode == null || ratio == null || ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0
                || amountUsdt == null || amountUsdt.signum() <= 0) {
            throw new IllegalArgumentException("组合分配必须包含正数比例和金额");
        }
    }
}
