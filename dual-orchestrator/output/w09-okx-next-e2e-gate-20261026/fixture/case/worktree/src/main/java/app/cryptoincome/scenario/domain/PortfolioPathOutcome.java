package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

public record PortfolioPathOutcome(
        String scenarioCode,
        String pathCode,
        BigDecimal totalCapitalUsdt,
        BigDecimal netIncomeUsdt,
        BigDecimal endingEquityUsdt,
        BigDecimal holdingReturnRate,
        BigDecimal simpleAnnualizedRate,
        BigDecimal conservativePeakBtcExposureRate,
        BigDecimal endingBtcExposureRate,
        Optional<BigDecimal> maximumDrawdownRate,
        List<ProductScenarioOutcome> productOutcomes,
        List<IncomeContribution> contributions
) {
    private static final MathContext MC = MathContext.DECIMAL128;

    public PortfolioPathOutcome {
        if (scenarioCode == null || scenarioCode.isBlank() || pathCode == null || pathCode.isBlank()
                || totalCapitalUsdt == null || totalCapitalUsdt.signum() <= 0
                || netIncomeUsdt == null || endingEquityUsdt == null
                || holdingReturnRate == null || simpleAnnualizedRate == null
                || conservativePeakBtcExposureRate == null || conservativePeakBtcExposureRate.signum() < 0
                || endingBtcExposureRate == null || endingBtcExposureRate.signum() < 0
                || maximumDrawdownRate == null || productOutcomes == null || productOutcomes.isEmpty()
                || contributions == null) {
            throw new IllegalArgumentException("组合路径结果字段不完整");
        }
        productOutcomes = List.copyOf(productOutcomes);
        contributions = List.copyOf(contributions);
        BigDecimal allocated = productOutcomes.stream().map(ProductScenarioOutcome::allocatedCapitalUsdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productIncome = productOutcomes.stream().map(ProductScenarioOutcome::netIncomeUsdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal attributed = contributions.stream().map(IncomeContribution::amountUsdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (allocated.compareTo(totalCapitalUsdt) != 0 || productIncome.compareTo(netIncomeUsdt) != 0
                || attributed.compareTo(netIncomeUsdt) != 0
                || endingEquityUsdt.compareTo(totalCapitalUsdt.add(netIncomeUsdt)) != 0
                || holdingReturnRate.compareTo(netIncomeUsdt.divide(totalCapitalUsdt, MC)) != 0
                || simpleAnnualizedRate.compareTo(holdingReturnRate.multiply(new BigDecimal("365"), MC)
                .divide(new BigDecimal("90"), MC)) != 0) {
            throw new IllegalArgumentException("组合路径金额或收益贡献不守恒");
        }
        if (productOutcomes.stream().anyMatch(outcome -> !outcome.scenarioCode().equals(scenarioCode)
                || !outcome.pathCode().equals(pathCode))) {
            throw new IllegalArgumentException("组合路径中的产品结果必须属于同一情景路径");
        }
    }
}
