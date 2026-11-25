package app.cryptoincome.assessment;

import app.cryptoincome.assessment.application.SmartArbitrageAllocationCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmartArbitrageAllocationCalculatorTest {
    private final SmartArbitrageAllocationCalculator calculator = new SmartArbitrageAllocationCalculator();

    @Test
    void translatesTheOkxPreviewIntoBtcMarginAndUnallocatedUsdt() {
        var value = calculator.calculate(new BigDecimal("20000"), new BigDecimal("19047.61"),
                new BigDecimal("64150.7"), new BigDecimal("952.38"));

        assertThat(value.spotCapitalRatio()).isEqualByComparingTo("0.9523805");
        assertThat(value.reservedMarginRatio()).isEqualByComparingTo("0.047619");
        assertThat(value.unallocatedUsdt()).isEqualByComparingTo("0.01");
        assertThat(value.shortHedgeNotionalUsdt()).isEqualByComparingTo("-19047.61");
        assertThat(value.estimatedNetDirectionalNotionalUsdt()).isZero();
        assertThat(value.estimatedBtcQuantity()).isEqualByComparingTo(
                new BigDecimal("19047.61").divide(new BigDecimal("64150.7"), java.math.MathContext.DECIMAL128));
    }

    @Test
    void rejectsAnAllocationThatSpendsMoreThanTheCandidateCapital() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("20000"), new BigDecimal("19048"),
                new BigDecimal("64150.7"), new BigDecimal("953")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过共同候选资金");
    }
}
