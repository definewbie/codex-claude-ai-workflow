package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;

public record IncomeContribution(ContributionCode code, BigDecimal amountUsdt) {
    public IncomeContribution {
        if (code == null || amountUsdt == null) {
            throw new IllegalArgumentException("收益贡献代码和金额不能为空");
        }
    }
}
