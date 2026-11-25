package app.cryptoincome.shadow;

import app.cryptoincome.grid.domain.*;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MybatisGridShadowStudyRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("crypto_income").withUsername("crypto_income")
            .withPassword("test-password").withEnv("TZ", "UTC");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired GridConfigurationSnapshotStore configurations;
    @Autowired GridShadowStudyRepository repository;

    @Test
    void persistsFrozenDefinitionIdempotentDailyFactsAndTerminalStop() {
        Instant start = Instant.parse("2026-08-19T00:00:00Z");
        long configurationId = configurations.append(new GridConfigurationSnapshot(
                "OKX", "BTC-USDT", GridConfigurationSource.OKX_APP_MANUAL_CONFIRMATION, "3-6个月",
                new BigDecimal("46781.1"), new BigDecimal("75680.3"), 50, GridType.ARITHMETIC,
                Optional.empty(), Optional.empty(), Optional.empty(), false, false, false, false,
                Optional.empty(), Optional.empty(), "OKX手机App", start.minusSeconds(60)));
        GridScenarioParameters candidate = new GridScenarioParameters(
                new BigDecimal("50054.2"), new BigDecimal("73173.56"), 30, Optional.empty(), Optional.empty());
        GridScenarioParameters baseline = new GridScenarioParameters(
                new BigDecimal("46781.1"), new BigDecimal("75680.3"), 50, Optional.empty(), Optional.empty());
        long id = repository.create(new GridShadowStudy(null, ShadowStudyState.ACTIVE, configurationId, start, 7,
                new BigDecimal("20000"), new BigDecimal("0.035"), new BigDecimal("0.0006"),
                new BigDecimal("0.0007"), new BigDecimal("10"), new BigDecimal("0.15"),
                new BigDecimal("0.02"), new BigDecimal("0.60"), candidate, baseline, new BigDecimal("64730.8"),
                start, "OKX参数页+本地K线", start, null));
        Instant valuation = start.plusSeconds(86_400);
        List<GridShadowDailyFact> facts = List.of(
                fact(id, ShadowVariantRole.CANDIDATE, valuation, "20"),
                fact(id, ShadowVariantRole.BASELINE, valuation, "10"));

        repository.appendFacts(facts);
        repository.appendFacts(facts);
        assertThat(repository.findAll()).extracting(GridShadowStudy::id).contains(id);
        assertThat(repository.findFacts(id)).hasSize(2);
        assertThat(repository.stop(id, valuation)).isTrue();
        assertThat(repository.stop(id, valuation.plusSeconds(1))).isFalse();
        assertThat(repository.find(id).orElseThrow().state()).isEqualTo(ShadowStudyState.STOPPED);
        assertThatThrownBy(() -> repository.appendFacts(List.of(
                fact(id, ShadowVariantRole.CANDIDATE, valuation.plusSeconds(86_400), "30"),
                fact(id, ShadowVariantRole.BASELINE, valuation.plusSeconds(86_400), "20"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已停止");
        assertThat(repository.findFacts(id)).hasSize(2);
    }

    private static GridShadowDailyFact fact(long id, ShadowVariantRole role, Instant valuation, String income) {
        return new GridShadowDailyFact(null, id, role, valuation, 1_440, new BigDecimal(income),
                new BigDecimal("1.9"), new BigDecimal("5"), new BigDecimal("0.03"),
                new BigDecimal("0.001"), new BigDecimal("0.40"), 4, new BigDecimal("3"),
                GridExitReason.WINDOW_END);
    }
}
