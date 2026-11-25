package app.cryptoincome.grid;

import app.cryptoincome.grid.application.SpotCandleBackfillProgress;
import app.cryptoincome.grid.application.SpotCandleBackfillResult;
import app.cryptoincome.grid.domain.CandleBackfillCoverage;
import app.cryptoincome.grid.sync.CandleSyncExecutionRepository;
import app.cryptoincome.grid.sync.CandleSyncState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MybatisCandleSyncExecutionRepositoryTest {
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

    @Autowired CandleSyncExecutionRepository repository;

    @Test
    void databaseEnforcesSingleActiveExecutionAndTerminalStateCannotBeReopened() {
        Instant from = Instant.parse("2026-05-22T00:00:00Z");
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        Instant created = Instant.parse("2026-08-20T01:00:00Z");
        long id = repository.create(from, to, created);

        assertThat(repository.findActive()).get().extracting(value -> value.id()).isEqualTo(id);
        assertThatThrownBy(() -> repository.create(from, to, created.plusSeconds(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.markRunning(id, created.plusSeconds(2))).isTrue();
        assertThat(repository.updateProgress(id,
                new SpotCandleBackfillProgress(1, 300, 299, 299, 1,
                        Instant.parse("2026-08-19T19:00:00Z")), created.plusSeconds(3))).isTrue();
        assertThat(repository.failStale(created.plusSeconds(2), created.plusSeconds(4))).isZero();

        SpotCandleBackfillResult result = new SpotCandleBackfillResult(
                CandleBackfillCoverage.BOUNDARY_REACHED, 2, 600, 599, 599, 1,
                Instant.parse("2026-05-21T23:59:00Z"));
        assertThat(repository.succeed(id, result, created.plusSeconds(4))).isTrue();
        assertThat(repository.fail(id, "LATE", "不得覆盖终态", created.plusSeconds(5))).isFalse();
        assertThat(repository.updateProgress(id,
                new SpotCandleBackfillProgress(3, 900, 899, 899, 1,
                        Instant.parse("2026-05-01T00:00:00Z")), created.plusSeconds(6))).isFalse();
        assertThat(repository.find(id)).get().satisfies(execution -> {
            assertThat(execution.state()).isEqualTo(CandleSyncState.SUCCEEDED);
            assertThat(execution.pages()).isEqualTo(2);
            assertThat(execution.failureCategory()).isNull();
        });
        assertThat(repository.findLatest()).get().extracting(value -> value.id()).isEqualTo(id);
        assertThat(repository.failStale(created.plusSeconds(100), created.plusSeconds(101))).isZero();

        long next = repository.create(from, to, created.plusSeconds(7));
        assertThat(next).isGreaterThan(id);
        assertThat(repository.findLatest()).get().extracting(value -> value.id()).isEqualTo(next);
    }
}
