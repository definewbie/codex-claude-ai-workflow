package app.cryptoincome.grid;

import app.cryptoincome.grid.application.GridConfigurationSnapshotQuery;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MybatisGridConfigurationSnapshotTest {
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

    @Autowired GridConfigurationSnapshotStore store;
    @Autowired GridConfigurationSnapshotQuery query;

    @Test
    void preservesNonExactHorizonAndEveryExplicitFeatureSwitch() {
        Instant observedAt = Instant.parse("2026-08-19T08:00:00Z");
        long id = store.append(new GridConfigurationSnapshot(
                "OKX", "BTC-USDT", GridConfigurationSource.OKX_APP_MANUAL_CONFIRMATION,
                "3-6个月", new BigDecimal("46781.1"), new BigDecimal("75680.3"), 50,
                GridType.ARITHMETIC, Optional.of(new BigDecimal("0.2147")),
                Optional.of(new BigDecimal("0.0061")), Optional.of(new BigDecimal("0.0107")),
                false, false, false, false,
                Optional.empty(), Optional.empty(), "OKX手机App手动创建页", observedAt));

        assertThat(query.findByIdAsOf(id, observedAt.minusMillis(1))).isEmpty();
        GridConfigurationSnapshot saved = query.findByIdAsOf(id, observedAt).orElseThrow();
        assertThat(saved.recommendationHorizonLabel()).isEqualTo("3-6个月");
        assertThat(saved.lowerPrice()).isEqualByComparingTo("46781.1");
        assertThat(saved.gridCount()).isEqualTo(50);
        assertThat(saved.displayedAnnualizedRate()).isPresent();
        assertThat(saved.displayedAnnualizedRate().orElseThrow()).isEqualByComparingTo("0.2147");
        assertThat(saved.autoEarnEnabled()).isFalse();
        assertThat(saved.takeProfitPrice()).isEmpty();
    }
}
