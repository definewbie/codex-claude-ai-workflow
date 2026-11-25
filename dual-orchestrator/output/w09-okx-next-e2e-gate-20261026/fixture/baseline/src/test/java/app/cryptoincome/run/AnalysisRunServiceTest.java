package app.cryptoincome.run;

import app.cryptoincome.run.application.AnalysisRunService;
import app.cryptoincome.run.domain.AnalysisRun;
import app.cryptoincome.run.domain.AnalysisRunStatus;
import app.cryptoincome.run.domain.AnalysisRunType;
import app.cryptoincome.run.domain.IllegalRunTransitionException;
import app.cryptoincome.run.domain.PortfolioMode;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunMapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AnalysisRunServiceTest {

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

    @Autowired
    AnalysisRunService service;

    @Autowired
    HealthEndpoint healthEndpoint;

    @Test
    void shouldReportApplicationAndDatabaseHealthUp() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void shouldEnforceLifecycleAndIdempotency() {
        String key = "backtest:funding:btc:2026-08-15";

        AnalysisRun created = service.create(key, AnalysisRunType.BACKTEST, PortfolioMode.BACKTEST);
        AnalysisRun sameRun = service.create(key, AnalysisRunType.BACKTEST, PortfolioMode.BACKTEST);

        assertThat(sameRun.id()).isEqualTo(created.id());
        assertThat(created.status()).isEqualTo(AnalysisRunStatus.CREATED);

        AnalysisRun running = service.start(created.id());
        assertThat(running.status()).isEqualTo(AnalysisRunStatus.RUNNING);
        assertThat(running.startedAt()).isNotNull();

        AnalysisRun succeeded = service.succeed(created.id());
        assertThat(succeeded.status()).isEqualTo(AnalysisRunStatus.SUCCEEDED);
        assertThat(succeeded.completedAt()).isNotNull();

        assertThatThrownBy(() -> service.start(created.id()))
                .isInstanceOf(IllegalRunTransitionException.class);
        assertThatThrownBy(() -> service.fail(created.id(), "LATE_FAILURE", "终态不能被覆盖"))
                .isInstanceOf(IllegalRunTransitionException.class);
    }

    @Test
    void shouldRejectIdempotencyKeyReuseAcrossModes() {
        String key = "evaluate:weekly:2026-W33";
        service.create(key, AnalysisRunType.EVALUATE, PortfolioMode.SHADOW);

        assertThatThrownBy(() -> service.create(key, AnalysisRunType.EVALUATE, PortfolioMode.LIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("相同 idempotencyKey");
    }

    @Test
    void mapperMustNotExposeGenericUpdateOrDeleteOperations() {
        assertThat(BaseMapper.class.isAssignableFrom(AnalysisRunMapper.class)).isFalse();
    }

    @Test
    void genericServiceMustRejectMarketSyncCreation() {
        assertThatThrownBy(() -> service.create(
                "sync-bypass", AnalysisRunType.SYNC_MARKET, PortfolioMode.LIVE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("原子 Store");
    }
}
