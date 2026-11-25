package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

public record ProductScenarioOutcome(
        ScenarioProductCode productCode,
        String scenarioCode,
        String pathCode,
        BigDecimal allocatedCapitalUsdt,
        BigDecimal netIncomeUsdt,
        BigDecimal endingEquityUsdt,
        BigDecimal holdingReturnRate,
        BigDecimal simpleAnnualizedRate,
        BigDecimal peakBtcDirectionalExposureRate,
        BigDecimal endingBtcDirectionalExposureRate,
        Optional<BigDecimal> maximumDrawdownRate,
        List<IncomeContribution> contributions,
        List<String> limitations
) {
    private static final MathContext MC = MathContext.DECIMAL128;

    public ProductScenarioOutcome {
        if (productCode == null || scenarioCode == null || pathCode == null
                || allocatedCapitalUsdt == null || allocatedCapitalUsdt.signum() <= 0
                || netIncomeUsdt == null || endingEquityUsdt == null
                || holdingReturnRate == null || simpleAnnualizedRate == null
                || peakBtcDirectionalExposureRate == null || endingBtcDirectionalExposureRate == null
                || maximumDrawdownRate == null || contributions == null || limitations == null) {
            throw new IllegalArgumentException("产品情景结果字段不能为空");
        }
        contributions = List.copyOf(contributions);
        limitations = List.copyOf(limitations);
        BigDecimal attributed = contributions.stream().map(IncomeContribution::amountUsdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (attributed.compareTo(netIncomeUsdt) != 0) {
            throw new IllegalArgumentException("产品收益贡献合计必须等于净收益");
        }
        if (endingEquityUsdt.compareTo(allocatedCapitalUsdt.add(netIncomeUsdt)) != 0) {
            throw new IllegalArgumentException("产品期末权益必须等于本金加净收益");
        }
    }

    public static ProductScenarioOutcome create(
            ScenarioProductCode productCode, ScenarioCalculationContext context,
            BigDecimal netIncomeUsdt, BigDecimal peakBtcExposure, BigDecimal endingBtcExposure,
            Optional<BigDecimal> maximumDrawdownRate, List<IncomeContribution> contributions,
            List<String> limitations) {
        BigDecimal capital = context.allocatedCapitalUsdt();
        BigDecimal holdingReturn = netIncomeUsdt.divide(capital, MC);
        BigDecimal days = new BigDecimal(context.pricePath().horizon().toSeconds())
                .divide(new BigDecimal("86400"), MC);
        BigDecimal annualized = holdingReturn.multiply(new BigDecimal("365"), MC).divide(days, MC);
        return new ProductScenarioOutcome(productCode, context.pricePath().scenarioCode(),
                context.pricePath().pathCode(), capital, netIncomeUsdt, capital.add(netIncomeUsdt),
                holdingReturn, annualized, peakBtcExposure, endingBtcExposure,
                maximumDrawdownRate, contributions, limitations);
    }
}
