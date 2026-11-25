package app.cryptoincome.cli;

import app.cryptoincome.funding.application.FundingRateBackfillService;
import app.cryptoincome.funding.application.FundingRateSyncService;
import app.cryptoincome.funding.domain.DerivativeInstrumentType;
import app.cryptoincome.funding.domain.FundingFormulaType;
import app.cryptoincome.funding.domain.FundingRateHistoryPager;
import app.cryptoincome.funding.domain.FundingRateHistorySource;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSyncStore;
import app.cryptoincome.funding.domain.FundingSettlementMethod;
import app.cryptoincome.funding.infrastructure.mybatis.FundingRateFactMapper;
import app.cryptoincome.funding.infrastructure.mybatis.FundingSyncRequestMapper;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SyncMarketCliRunnerTest {

    private static final AtomicInteger SOURCE_CALLS = new AtomicInteger();
    private static final AtomicInteger BACKFILL_PAGER_CALLS = new AtomicInteger();
    /** after 毫秒值 → 返回页 */
    static final Map<Long, List<FundingRateObservation>> BACKFILL_PAGES = new ConcurrentHashMap<>();

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

    @TestConfiguration
    static class StubSourceConfig {
        @Bean
        @Primary
        FundingRateHistorySource stubFundingRateHistorySource() {
            return (instrument, limit) -> {
                SOURCE_CALLS.incrementAndGet();
                return List.of(observation("0.000118", "c"));
            };
        }

        @Bean
        @Primary
        FundingRateHistoryPager stubFundingRateHistoryPager() {
            return (instrument, limit, after) -> {
                BACKFILL_PAGER_CALLS.incrementAndGet();
                return BACKFILL_PAGES.getOrDefault(after.toEpochMilli(), List.of());
            };
        }
    }

    @Autowired FundingRateSyncService syncService;
    @Autowired FundingRateBackfillService backfillService;
    @Autowired FundingRateSyncStore store;
    @Autowired AnalysisRunMapper runMapper;
    @Autowired FundingSyncRequestMapper requestMapper;
    @Autowired FundingRateFactMapper factMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void latestPersistsOnePageAndReplaysWithoutSecondSourceCall() {
        SOURCE_CALLS.set(0);
        // 观察关系按 run 记账, 与其他测试是否先插入同一内容事实无关, 避免顺序依赖
        int observationsBefore = factMapper.countAllObservations();

        CliInvocation first = invoke("sync-market", "latest",
                "--instrument", "btc-usdt-swap", "--limit", "10", "--request-key", "cli-replay");
        CliInvocation replay = invoke("sync-market", "latest",
                "--instrument", "BTC-USDT-SWAP", "--limit", "10", "--request-key", "cli-replay");

        assertThat(first.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_OK);
        assertThat(first.output()).contains("同步完成").contains("接收=1");
        assertThat(replay.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_OK);
        assertThat(replay.output()).contains("同步回放").contains("未再次请求 OKX");
        assertThat(SOURCE_CALLS).hasValue(1);
        assertThat(factMapper.countAllObservations()).isEqualTo(observationsBefore + 1);
    }

    @Test
    void argumentErrorExitsNonZeroAndCreatesNoExecution() {
        Integer runsBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_run", Integer.class);

        CliInvocation missingLimit = invoke("sync-market", "latest",
                "--instrument", "BTC-USDT-SWAP", "--request-key", "cli-args-missing-limit");
        CliInvocation badDuration = invoke("sync-market", "stale-list", "--older-than", "15-minutes");

        assertThat(missingLimit.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_ARGS);
        assertThat(missingLimit.output()).contains("参数错误").doesNotContain("Exception");
        assertThat(badDuration.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_ARGS);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_run", Integer.class))
                .isEqualTo(runsBefore);
    }

    @Test
    void inProgressRequestIsRefusedWithBusyExitCode() {
        // 通过 Store 占用逻辑请求但不完成, 模拟另一进程正在执行
        acquireDirectly(requestKey("cli-busy", 1));

        CliInvocation busy = invoke("sync-market", "latest",
                "--instrument", "BTC-USDT-SWAP", "--limit", "1", "--request-key", "cli-busy");

        assertThat(busy.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_IN_PROGRESS);
        assertThat(busy.output()).contains("正在执行中").doesNotContain("Exception");
    }

    @Test
    void staleListIsReadOnlyAndReportsOnlyAgedRunningExecutions() {
        String agedKey = requestKey("cli-stale-aged", 1);
        String freshKey = requestKey("cli-stale-fresh", 1);
        var aged = acquireDirectly(agedKey);
        acquireDirectly(freshKey);
        ageRunByOneHour(aged.run().id());

        CliInvocation listing = invoke("sync-market", "stale-list", "--older-than", "PT15M");

        assertThat(listing.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_OK);
        assertThat(listing.output()).contains(agedKey).doesNotContain(freshKey);
        assertThat(listing.output()).contains("只读");
        assertThat(requestMapper.selectByKey(freshKey).getState()).isEqualTo("RUNNING");
        assertThat(requestMapper.selectByKey(agedKey).getState()).isEqualTo("RUNNING");
    }

    @Test
    void staleRecoverRequiresMatchingExpectedRunIdAndRefusesChangedCurrentRun() {
        String key = requestKey("cli-stale-conflict", 1);
        var first = acquireDirectly(key);
        ageRunByOneHour(first.run().id());

        CliInvocation wrongId = invoke("sync-market", "stale-recover",
                "--request-key", key, "--expected-run-id", String.valueOf(first.run().id() + 999),
                "--older-than", "PT15M");
        assertThat(wrongId.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_RECOVERY_REFUSED);
        assertThat(runMapper.selectById(first.run().id()).getStatus()).isEqualTo("RUNNING");

        CliInvocation correct = invoke("sync-market", "stale-recover",
                "--request-key", key, "--expected-run-id", String.valueOf(first.run().id()),
                "--older-than", "PT15M");
        assertThat(correct.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_OK);
        assertThat(runMapper.selectById(first.run().id()).getStatus()).isEqualTo("FAILED");

        var second = acquireDirectly(key);
        ageRunByOneHour(second.run().id());
        CliInvocation oldExpectation = invoke("sync-market", "stale-recover",
                "--request-key", key, "--expected-run-id", String.valueOf(first.run().id()),
                "--older-than", "PT15M");
        assertThat(oldExpectation.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_RECOVERY_REFUSED);
        assertThat(runMapper.selectById(second.run().id()).getStatus()).isEqualTo("RUNNING");
        assertThat(oldExpectation.output()).contains("拒绝恢复").doesNotContain("Exception");
    }

    @Test
    void staleRecoverRefusesExecutionYoungerThanThreshold() {
        String key = requestKey("cli-stale-young", 1);
        var acquired = acquireDirectly(key);

        CliInvocation refused = invoke("sync-market", "stale-recover",
                "--request-key", key, "--expected-run-id", String.valueOf(acquired.run().id()),
                "--older-than", "PT1H");

        assertThat(refused.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_RECOVERY_REFUSED);
        assertThat(refused.output()).contains("未超过阈值");
        assertThat(runMapper.selectById(acquired.run().id()).getStatus()).isEqualTo("RUNNING");
        assertThat(requestMapper.selectByKey(key).getState()).isEqualTo("RUNNING");
    }

    @Test
    void cliRunnerMustNotReachInfrastructureMappersDirectly() {
        assertThat(Arrays.stream(SyncMarketCliRunner.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .noneMatch(type -> type.contains("Mapper") || type.contains("infrastructure"));
    }

    @Test
    void illegalStaleWindowsAndUnsafeKeysAreRejectedWithoutSideEffects() {
        int runsBefore = runCount();
        String key = requestKey("cli-policy", 1);
        var acquired = acquireDirectly(key);

        CliInvocation shortWindow = invoke("sync-market", "stale-list", "--older-than", "PT30S");
        CliInvocation longWindow = invoke("sync-market", "stale-recover",
                "--request-key", key, "--expected-run-id", String.valueOf(acquired.run().id()),
                "--older-than", "P31D");
        CliInvocation unsafeKey = invoke("sync-market", "latest",
                "--instrument", "BTC-USDT-SWAP", "--limit", "1", "--request-key", "bad\nkey");

        assertThat(shortWindow.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_ARGS);
        assertThat(shortWindow.output()).contains("olderThan").doesNotContain("Exception");
        assertThat(longWindow.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_ARGS);
        assertThat(unsafeKey.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_ARGS);
        assertThat(runMapper.selectById(acquired.run().id()).getStatus()).isEqualTo("RUNNING");
        assertThat(requestMapper.selectByKey(key).getState()).isEqualTo("RUNNING");
        assertThat(runCount()).isEqualTo(runsBefore + 1);
        store.fail(acquired.requestId(), acquired.run().id(), "TEST_CLEANUP", "cli policy rejection left state untouched");
    }

    @Test
    void cliNormalizesRequestKeyCaseForReplay() {
        SOURCE_CALLS.set(0);
        invoke("sync-market", "latest",
                "--instrument", "BTC-USDT-SWAP", "--limit", "1", "--request-key", "Manual-Cli-Key");

        CliInvocation replay = invoke("sync-market", "latest",
                "--instrument", "BTC-USDT-SWAP", "--limit", "1", "--request-key", "manual-cli-key");

        assertThat(replay.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_OK);
        assertThat(replay.output()).contains("同步回放");
        assertThat(SOURCE_CALLS).hasValue(1);
    }

    @Test
    void backfillCliReportsCoverageAndReplaysCompleted() {
        BACKFILL_PAGES.clear();
        BACKFILL_PAGES.put(Instant.parse("2026-08-01T00:00:00Z").toEpochMilli(), List.of(
                stubBackfillObservation(Instant.parse("2026-07-31T16:00:00Z")),
                stubBackfillObservation(Instant.parse("2026-07-30T00:00:00Z"))));

        CliInvocation first = invoke("sync-market", "backfill",
                "--instrument", "BTC-USDT-SWAP",
                "--from", "2026-07-30T00:00:00Z", "--to", "2026-08-01T00:00:00Z",
                "--limit", "100", "--max-pages", "5", "--request-key", "cli-backfill");
        CliInvocation replay = invoke("sync-market", "backfill",
                "--instrument", "BTC-USDT-SWAP",
                "--from", "2026-07-30T00:00:00Z", "--to", "2026-08-01T00:00:00Z",
                "--limit", "100", "--max-pages", "5", "--request-key", "cli-backfill");

        assertThat(first.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_OK);
        assertThat(first.output())
                .contains("coverage=COMPLETE(已到达目标边界, 区间完整性尚未检查)")
                .contains("页数=1")
                .contains("窗口内=2")
                .contains("新增全局事实=")
                .contains("当前cursor=2026-07-30T00:00:00Z")
                .doesNotContain("Exception");
        assertThat(replay.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_OK);
        assertThat(replay.output()).contains("回补回放").contains("未再次请求 OKX");
        assertThat(BACKFILL_PAGER_CALLS).hasValue(1);
    }

    @Test
    void backfillCliRejectsIllegalWindowBeforeAnySideEffect() {
        int runsBefore = runCount();

        CliInvocation reversed = invoke("sync-market", "backfill",
                "--instrument", "BTC-USDT-SWAP",
                "--from", "2026-08-01T00:00:00Z", "--to", "2026-07-30T00:00:00Z",
                "--limit", "100", "--max-pages", "5", "--request-key", "cli-backfill-bad");
        CliInvocation subMillis = invoke("sync-market", "backfill",
                "--instrument", "BTC-USDT-SWAP",
                "--from", "2026-07-30T00:00:00.0000005Z", "--to", "2026-08-01T00:00:00Z",
                "--limit", "100", "--max-pages", "5", "--request-key", "cli-backfill-bad");

        assertThat(reversed.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_ARGS);
        assertThat(subMillis.exitCode()).isEqualTo(SyncMarketCliRunner.EXIT_ARGS);
        assertThat(runCount()).isEqualTo(runsBefore);
    }

    private static FundingRateObservation stubBackfillObservation(Instant fundingTime) {
        String hash = (Long.toHexString(fundingTime.toEpochMilli()) + "0".repeat(64)).substring(0, 64);
        return new FundingRateObservation(
                "OKX", DerivativeInstrumentType.SWAP, "BTC-USDT-SWAP",
                FundingFormulaType.WITH_RATE, new BigDecimal("0.0001"), new BigDecimal("0.0001"),
                fundingTime, FundingSettlementMethod.CURRENT_PERIOD,
                "/api/v5/public/funding-rate-history", "{\"fixture\":true}", hash,
                Instant.parse("2026-08-15T00:00:00Z"));
    }

    private int runCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_run", Integer.class);
        return count == null ? 0 : count;
    }

    private record CliInvocation(int exitCode, String output) {
    }

    private CliInvocation invoke(String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        SyncMarketCliRunner runner = new SyncMarketCliRunner(
                syncService, backfillService, new PrintStream(buffer, true, StandardCharsets.UTF_8));
        runner.run(new DefaultApplicationArguments(args));
        return new CliInvocation(runner.getExitCode(), buffer.toString(StandardCharsets.UTF_8));
    }

    private app.cryptoincome.funding.domain.FundingSyncAcquisition acquireDirectly(String requestKey) {
        return store.acquire(requestKey, "BTC-USDT-SWAP", 1);
    }

    private void ageRunByOneHour(long runId) {
        jdbcTemplate.update(
                "UPDATE analysis_run SET started_at = DATE_SUB(started_at, INTERVAL 1 HOUR) WHERE id = ?",
                runId);
    }

    private static String requestKey(String syncKey, int limit) {
        return "sync-funding:BTC-USDT-SWAP:" + limit + ":" + syncKey;
    }

    private static FundingRateObservation observation(String realizedRate, String hashSeed) {
        return new FundingRateObservation(
                "OKX", DerivativeInstrumentType.SWAP, "BTC-USDT-SWAP",
                FundingFormulaType.WITH_RATE, new BigDecimal("0.000123"), new BigDecimal(realizedRate),
                Instant.parse("2026-01-01T00:00:00Z"), FundingSettlementMethod.CURRENT_PERIOD,
                "/api/v5/public/funding-rate-history", "{\"fixture\":true}", hashSeed.repeat(64),
                Instant.parse("2026-08-15T00:00:00Z"));
    }
}
