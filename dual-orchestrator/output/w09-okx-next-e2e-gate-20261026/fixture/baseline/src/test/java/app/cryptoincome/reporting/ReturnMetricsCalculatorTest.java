package app.cryptoincome.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnMetricsCalculatorTest {

    @Test
    void calculatesCumulativeReturnAndSimpleAnnualizedReturnFromObservedDuration() {
        ReturnMetrics result = ReturnMetricsCalculator.calculate(
                new BigDecimal("1000"), new BigDecimal("20000"), Duration.ofDays(90), 7);

        assertThat(result.cumulativeReturnRate()).isEqualByComparingTo("0.05");
        assertThat(result.simpleAnnualizedReturnRate()).isEqualByComparingTo("0.2027777777777777777777777777777778");
        assertThat(result.annualizationStatus()).isEqualTo(AnnualizationStatus.AVAILABLE);
        assertThat(result.observedMinutes()).isEqualTo(129_600);
    }

    @Test
    void withholdsAnnualizedNumberUntilTheMinimumShadowSampleIsReached() {
        ReturnMetrics result = ReturnMetricsCalculator.calculate(
                new BigDecimal("400"), new BigDecimal("20000"), Duration.ofDays(1), 7);

        assertThat(result.cumulativeReturnRate()).isEqualByComparingTo("0.02");
        assertThat(result.simpleAnnualizedReturnRate()).isNull();
        assertThat(result.annualizationStatus()).isEqualTo(AnnualizationStatus.SAMPLE_TOO_SHORT);
        assertThat(result.minimumAnnualizationDays()).isEqualTo(7);
    }
}
