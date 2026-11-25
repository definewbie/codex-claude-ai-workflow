package app.cryptoincome.grid;

import app.cryptoincome.grid.application.SpotCandleQuery;
import app.cryptoincome.grid.application.SpotCandleCoverageQuery;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.SpotCandle;
import app.cryptoincome.grid.domain.SpotCandleFactStore;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MybatisSpotCandleFactTest {
    private static final Instant OPEN_TIME = Instant.parse("2026-08-01T00:00:00Z");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("crypto_income")
            .withUsername("crypto_income")
            .withPassword("test-password")
            .withEnv("TZ", "UTC");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired SpotCandleFactStore store;
    @Autowired SpotCandleQuery query;
    @Autowired SpotCandleCoverageQuery coverageQuery;

    @Test
    void deduplicatesSameFactButKeepsRevisionAndSelectsLatestVisibleAsOf() {
        SpotCandle original = candle("64000", "2026-08-02T00:00:00Z", "a");
        SpotCandle revised = candle("64100", "2026-08-03T00:00:00Z", "b");

        var first = store.appendCompleted(List.of(original));
        var duplicate = store.appendCompleted(List.of(original));
        var revision = store.appendCompleted(List.of(revised));

        assertThat(first.newFacts()).isEqualTo(1);
        assertThat(duplicate.newFacts()).isZero();
        assertThat(duplicate.duplicateFacts()).isEqualTo(1);
        assertThat(revision.newFacts()).isEqualTo(1);

        var beforeRevision = query.findCompletedAsOf(
                "OKX", "BTC-USDT", CandleBar.M1, OPEN_TIME, OPEN_TIME.plusSeconds(60),
                Instant.parse("2026-08-02T12:00:00Z"));
        var afterRevision = query.findCompletedAsOf(
                "OKX", "BTC-USDT", CandleBar.M1, OPEN_TIME, OPEN_TIME.plusSeconds(60),
                Instant.parse("2026-08-03T12:00:00Z"));

        assertThat(beforeRevision).singleElement().extracting(SpotCandle::close)
                .isEqualTo(new BigDecimal("64000.000000000000000000"));
        assertThat(afterRevision).singleElement().extracting(SpotCandle::close)
                .isEqualTo(new BigDecimal("64100.000000000000000000"));

        var complete = coverageQuery.inspect("OKX", "BTC-USDT", CandleBar.M1,
                OPEN_TIME, OPEN_TIME.plusSeconds(60), Instant.parse("2026-08-03T12:00:00Z"));
        var incomplete = coverageQuery.inspect("OKX", "BTC-USDT", CandleBar.M1,
                OPEN_TIME, OPEN_TIME.plusSeconds(120), Instant.parse("2026-08-03T12:00:00Z"));
        assertThat(complete.complete()).isTrue();
        assertThat(complete.actualMinutes()).isEqualTo(1);
        assertThat(incomplete.complete()).isFalse();
        assertThat(incomplete.missingMinutes()).isEqualTo(1);
    }

    private static SpotCandle candle(String close, String observedAt, String hashSeed) {
        return new SpotCandle(
                "OKX", "BTC-USDT", CandleBar.M1, OPEN_TIME,
                new BigDecimal("64000"), new BigDecimal("64200"), new BigDecimal("63900"),
                new BigDecimal(close), BigDecimal.ONE, new BigDecimal("64000"), true,
                "/test", "[\"fixture\"]", hashSeed.repeat(64), Instant.parse(observedAt));
    }
}
