package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

@Component
public class SimpleEarnScenarioCalculator implements ProductScenarioCalculator<EarnScenarioAssumption> {
    private static final MathContext MC = MathContext.DECIMAL128;

    @Override
    public boolean supports(ScenarioProductCode productCode) {
        return productCode == ScenarioProductCode.FIXED_EARN_90D
                || productCode == ScenarioProductCode.FLEXIBLE_EARN;
    }

    @Override
    public ProductScenarioOutcome calculate(ScenarioCalculationContext context, EarnScenarioAssumption assumption) {
        if (context.allocatedCapitalUsdt().compareTo(assumption.maximumAllocationUsdt()) > 0) {
            throw new IllegalArgumentException("简单赚币分配金额超过已确认产品额度");
        }
        BigDecimal days = new BigDecimal(context.pricePath().horizon().toSeconds())
                .divide(new BigDecimal("86400"), MC);
        BigDecimal income = context.allocatedCapitalUsdt().multiply(assumption.netApr(), MC)
                .multiply(days, MC).divide(new BigDecimal("365"), MC);
        ContributionCode contributionCode = assumption.productCode() == ScenarioProductCode.FIXED_EARN_90D
                ? ContributionCode.FIXED_EARN_INTEREST : ContributionCode.FLEXIBLE_EARN_INTEREST;
        return ProductScenarioOutcome.create(assumption.productCode(), context, income,
                BigDecimal.ZERO, BigDecimal.ZERO, Optional.of(BigDecimal.ZERO),
                List.of(new IncomeContribution(contributionCode, income)),
                assumption.productCode() == ScenarioProductCode.FLEXIBLE_EARN
                        ? List.of("活期净APR是本次证据快照，不是锁定收益") : List.of());
    }
}
