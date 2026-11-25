package app.cryptoincome.income;

import app.cryptoincome.funding.infrastructure.okx.OkxPublicApiProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.*;

import static org.assertj.core.api.Assertions.*;

class OkxSimpleEarnMarketRateClientTest {
    @Test
    void parsesOfficialPublicFlexibleRateFieldsAsBorrowAprEvidence() {
        Instant now = Instant.parse("2026-08-20T02:00:00Z");
        var client = new OkxSimpleEarnMarketRateClient(HttpClient.newHttpClient(), new ObjectMapper(),
                new OkxPublicApiProperties(null, Duration.ofSeconds(1), Duration.ofSeconds(1)),
                Clock.fixed(now, ZoneOffset.UTC));

        var value = client.parse("""
                {"code":"0","data":[{"avgAmt":"","avgAmtUsd":"","avgRate":"0.03",
                "ccy":"USDT","estRate":"0.044","preRate":"0.03"}],"msg":""}
                """);

        assertThat(value.currency()).isEqualTo("USDT");
        assertThat(value.averageBorrowApr24h()).isEqualByComparingTo("0.03");
        assertThat(value.estimatedNextBorrowApr()).isEqualByComparingTo("0.044");
        assertThat(value.observedAt()).isEqualTo(now);
    }

    @Test
    void rejectsWrongCurrencyInsteadOfShowingItAsUsdt() {
        var client = new OkxSimpleEarnMarketRateClient(HttpClient.newHttpClient(), new ObjectMapper(),
                new OkxPublicApiProperties(null, Duration.ofSeconds(1), Duration.ofSeconds(1)), Clock.systemUTC());
        assertThatThrownBy(() -> client.parse("""
                {"code":"0","data":[{"avgRate":"0.03","ccy":"BTC","estRate":"0.04","preRate":"0.03"}],"msg":""}
                """))
                .isInstanceOf(SimpleEarnSourceException.class).hasMessageContaining("不是 USDT");
    }
}
