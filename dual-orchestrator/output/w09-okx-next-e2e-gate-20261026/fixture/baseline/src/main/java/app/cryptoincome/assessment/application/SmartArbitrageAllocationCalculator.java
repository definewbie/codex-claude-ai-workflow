package app.cryptoincome.assessment.application;

import app.cryptoincome.assessment.domain.SmartArbitrageAllocation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

@Component
public class SmartArbitrageAllocationCalculator {
    private static final MathContext MC = MathContext.DECIMAL128;

    public SmartArbitrageAllocation calculate(BigDecimal candidateCapital,
                                              BigDecimal spotInvestmentUsdt,
                                              BigDecimal referenceSpotPrice,
                                              BigDecimal reservedMarginUsdt) {
        requirePositive(candidateCapital, "共同候选资金");
        requirePositive(spotInvestmentUsdt, "智能套利现货买入金额");
        requirePositive(referenceSpotPrice, "智能套利参考现货价");
        if (reservedMarginUsdt == null || reservedMarginUsdt.signum() < 0) {
            throw new IllegalArgumentException("智能套利预留保证金不能小于0");
        }
        BigDecimal allocated = spotInvestmentUsdt.add(reservedMarginUsdt);
        if (allocated.compareTo(candidateCapital) > 0) {
            throw new IllegalArgumentException("智能套利现货买入金额与预留保证金之和不能超过共同候选资金");
        }
        return new SmartArbitrageAllocation(
                candidateCapital,
                spotInvestmentUsdt,
                spotInvestmentUsdt.divide(candidateCapital, MC),
                referenceSpotPrice,
                spotInvestmentUsdt.divide(referenceSpotPrice, MC),
                spotInvestmentUsdt.negate(),
                BigDecimal.ZERO,
                reservedMarginUsdt,
                reservedMarginUsdt.divide(candidateCapital, MC),
                candidateCapital.subtract(allocated));
    }

    private static void requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(label + "必须大于0");
    }
}
