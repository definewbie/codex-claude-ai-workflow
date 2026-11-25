package app.cryptoincome.market;

import app.cryptoincome.market.domain.BtcMarketTick;
import app.cryptoincome.market.domain.BtcMarketTickStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MybatisBtcMarketTickTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("crypto_income").withUsername("crypto_income").withPassword("test-password")
            .withEnv("TZ", "UTC");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired BtcMarketTickStore store;

    @Test
    void deduplicatesPayloadAndReturnsLatestProviderFact() {
        Instant providerAt = Instant.parse("2026-08-22T00:00:00Z");
        BtcMarketTick first = tick("64185.1", providerAt, "2026-08-22T00:00:01Z", "a");
        BtcMarketTick revised = tick("64186.2", providerAt, "2026-08-22T00:00:02Z", "b");

        assertThat(store.append(first)).isTrue();
        assertThat(store.append(first)).isFalse();
        assertThat(store.append(revised)).isTrue();

        assertThat(store.latest("OKX", "BTC-USDT")).get()
                .extracting(BtcMarketTick::lastPrice).isEqualTo(new BigDecimal("64186.200000000000000000"));
    }

    private static BtcMarketTick tick(String last, Instant providerAt, String observedAt, String hash) {
        return new BtcMarketTick("OKX", "BTC-USDT", new BigDecimal(last),
                new BigDecimal("64180"), new BigDecimal("64190"), providerAt, Instant.parse(observedAt),
                "/api/v5/market/ticker", "{}", hash.repeat(64));
    }
}
