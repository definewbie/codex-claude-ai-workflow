package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class SmartArbitrageScenarioCalculator
        implements ProductScenarioCalculator<SmartArbitrageScenarioAssumption> {
    @Override
    public boolean supports(ScenarioProductCode productCode) {
        return productCode == ScenarioProductCode.SMART_ARBITRAGE;
    }

    @Override
    public ProductScenarioOutcome calculate(ScenarioCalculationContext context,
                                            SmartArbitrageScenarioAssumption assumption) {
        BigDecimal hedgedNotional = context.allocatedCapitalUsdt().multiply(assumption.hedgedNotionalRatio());
        BigDecimal fundingIncome = hedgedNotional.multiply(assumption.cumulativeFundingRate());
        BigDecimal costs = assumption.knownCosts();
        BigDecimal netIncome = fundingIncome.subtract(costs);
        return ProductScenarioOutcome.create(ScenarioProductCode.SMART_ARBITRAGE, context, netIncome,
                BigDecimal.ZERO, BigDecimal.ZERO, Optional.empty(),
                List.of(new IncomeContribution(ContributionCode.SMART_ARBITRAGE_FUNDING, fundingIncome),
                        new IncomeContribution(ContributionCode.SMART_ARBITRAGE_COST, costs.negate())),
                List.of("假设现货多头与永续空头方向盈亏抵消；未提供统一权益时间轴，最大回撤未知"));
    }
}
