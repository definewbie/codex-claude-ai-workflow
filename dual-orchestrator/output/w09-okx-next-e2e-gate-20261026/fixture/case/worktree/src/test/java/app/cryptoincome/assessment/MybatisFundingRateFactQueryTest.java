package app.cryptoincome.assessment;

import app.cryptoincome.assessment.application.FundingRateFactQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
class MybatisFundingRateFactQueryTest {

    private static final String INSTRUMENT = "BTC-ASOF-TEST-SWAP";

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

    @Autowired FundingRateFactQuery query;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void selectsLatestRevisionVisibleAsOfWithoutUsingPredictedRateOrObservations() {
        Instant fundingOne = Instant.parse("2026-08-01T00:00:00Z");
        Instant fundingTwo = Instant.parse("2026-08-01T11:00:00Z");
        insert(fundingOne, "0.900000", "0.001000", "2026-08-02T00:00:00Z", "1");
        insert(fundingOne, "0.800000", "0.002000", "2026-08-03T00:00:00Z", "2");
        insert(fundingOne, "0.750000", "0.002500", "2026-08-03T00:00:00Z", "5");
        insert(fundingOne, "0.700000", "0.003000", "2026-08-04T00:00:00Z", "3");
        insert(fundingTwo, "0.600000", "-0.000500", "2026-08-02T00:00:00Z", "4");

        var points = query.findRealizedRates(
                "OKX", INSTRUMENT,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-02T00:00:00Z"),
                Instant.parse("2026-08-03T12:00:00Z"));

        assertThat(points).hasSize(2);
        assertThat(points).extracting(point -> point.realizedRate())
                .containsExactly(new BigDecimal("0.00250000000000000000"), new BigDecimal("-0.00050000000000000000"));
    }

    private void insert(Instant fundingTime, String predicted, String realized, String observedAt, String seed) {
        jdbcTemplate.update("""
                INSERT INTO funding_rate_fact (
                    exchange, instrument_type, instrument_id, formula_type,
                    predicted_rate, realized_rate, funding_time, settlement_method,
                    source_endpoint, source_payload, payload_hash, first_observed_at
                ) VALUES ('OKX', 'SWAP', ?, 'WITH_RATE', ?, ?, ?, 'CURRENT_PERIOD',
                          '/test', JSON_OBJECT('seed', ?), ?, ?)
                """, INSTRUMENT, new BigDecimal(predicted), new BigDecimal(realized), fundingTime,
                seed, seed.repeat(64), Instant.parse(observedAt));
    }
}
