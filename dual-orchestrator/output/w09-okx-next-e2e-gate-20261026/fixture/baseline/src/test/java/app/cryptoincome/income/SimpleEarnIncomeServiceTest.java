package app.cryptoincome.income;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleEarnIncomeServiceTest {
    @Test
    void calculatesOnlyDailyAndNinetyDayIncomeWithoutInventingAProductModel() {
        Instant observed = Instant.parse("2026-08-20T02:00:00Z");
        SimpleEarnIncomeService service = new SimpleEarnIncomeService(() -> new SimpleEarnMarketRate(
                "USDT", new BigDecimal("0.03"), new BigDecimal("0.03"), new BigDecimal("0.044"),
                observed, OkxSimpleEarnMarketRateClient.ENDPOINT));

        var result = service.assess(new BigDecimal("20000"), new BigDecimal("0.035"), observed);

        assertThat(result.flexible().apr()).isEqualByComparingTo("0.03740");
        assertThat(result.flexible().dailyIncome()).isEqualByComparingTo("2.049315068493150684931506849315068");
        assertThat(result.fixed90Days().dailyIncome()).isEqualByComparingTo("1.917808219178082191780821917808219");
        assertThat(result.fixed90Days().income90Days()).isCloseTo(
                new BigDecimal("172.6027397260273972602739726027397"),
                org.assertj.core.data.Offset.offset(new BigDecimal("0.000000000000000000000000000001")));
        assertThat(result.fixed90Days().manuallyQuoted()).isTrue();
    }
}
