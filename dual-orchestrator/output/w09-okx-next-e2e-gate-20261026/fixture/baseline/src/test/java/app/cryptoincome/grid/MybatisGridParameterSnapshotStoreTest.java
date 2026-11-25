package app.cryptoincome.grid;

import app.cryptoincome.grid.domain.GridParameterSnapshotStore;
import app.cryptoincome.grid.application.GridParameterSnapshotQuery;
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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MybatisGridParameterSnapshotStoreTest {

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

    @Autowired GridParameterSnapshotStore store;
    @Autowired GridParameterSnapshotQuery query;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void appendsEveryObservationWithoutOverwritingEarlierSnapshot() {
        Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM grid_parameter_snapshot", Integer.class);
        long first = store.append(GridParameterSnapshotServiceTest.snapshot());
        long second = store.append(GridParameterSnapshotServiceTest.snapshot());

        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM grid_parameter_snapshot", Integer.class);
        assertThat(count).isEqualTo(before + 2);
        String payload = jdbcTemplate.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(source_payload, '$.fixture')) "
                        + "FROM grid_parameter_snapshot WHERE id = ?", String.class, first);
        assertThat(payload).isEqualTo("true");
    }

    @Test
    void readsSnapshotOnlyWhenItWasVisibleAtEvaluationTime() {
        long id = store.append(GridParameterSnapshotServiceTest.snapshot());

        assertThat(query.findByIdAsOf(id, Instant.parse("2026-08-19T07:59:59Z"))).isEmpty();
        assertThat(query.findByIdAsOf(id, Instant.parse("2026-08-19T08:00:00Z")))
                .get().extracting(value -> value.instrumentId()).isEqualTo("BTC-USDT");
    }
}
