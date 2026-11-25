package app.cryptoincome.funding;

import app.cryptoincome.funding.application.FundingRateBackfillService;
import app.cryptoincome.funding.application.FundingRateBackfillServiceTestSupport.ScriptedPager;
import app.cryptoincome.funding.domain.BackfillCoverage;
import app.cryptoincome.funding.domain.DerivativeInstrumentType;
import app.cryptoincome.funding.domain.FundingBackfillCursorStalledException;
import app.cryptoincome.funding.domain.FundingFormulaType;
import app.cryptoincome.funding.domain.FundingBackfillStore;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSourceException;
import app.cryptoincome.funding.domain.FundingRateSyncInProgressException;
import app.cryptoincome.funding.domain.FundingSettlementMethod;
import app.cryptoincome.funding.domain.StaleExecution;
import app.cryptoincome.funding.domain.StaleRecoveryOutcome;
import app.cryptoincome.funding.infrastructure.mybatis.FundingBackfillPageMapper;
import app.cryptoincome.funding.infrastructure.mybatis.FundingRateFactMapper;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunEntity;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunMapper;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FundingRateBackfillServiceTest {

    private static final Instant FROM = Instant.parse("2026-07-30T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant T_31_16 = Instant.parse("2026-07-31T16:00:00Z");
    private static final Instant T_31_08 = Instant.parse("2026-07-31T08:00:00Z");
    private static final Instant T_31_00 = Instant.parse("2026-07-31T00:00:00Z");
    private static final Instant T_30_16 = Instant.parse("2026-07-30T16:00:00Z");
    private static final Instant T_30_08 = Instant.parse("2026-07-30T08:00:00Z");
    private static final Instant BEFORE_FROM = Instant.parse("2026-07-29T16:00:00Z");

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

    @Autowired FundingBackfillStore store;
    @Autowired AnalysisRunMapper runMapper;
    @Autowired FundingBackfillPageMapper pageMapper;
    @Autowired FundingRateFactMapper factMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void backfillsAcrossPagesUntilWindowBoundaryAndProvesComplete() {
        ScriptedPager pager = ScriptedPager.pagesOver(TO, 2, T_31_16, T_31_08, T_31_00, T_30_16, T_30_08, FROM);
        int factsBefore = factMapper.countFacts();

        var result = service(pager).backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "multi-page");

        assertThat(result.replayed()).isFalse();
        assertThat(result.coverage()).isEqualTo(BackfillCoverage.COMPLETE);
        assertThat(result.pagesThisRun()).isEqualTo(3);
        assertThat(result.receivedThisRun()).isEqualTo(6);
        assertThat(result.newGlobalFactsThisRun()).isEqualTo(6);
        assertThat(result.newObservationLinksThisRun()).isEqualTo(6);
        assertThat(result.duplicateObservationLinksThisRun()).isZero();
        assertThat(result.earliestFactTime()).isEqualTo(FROM);
        assertThat(result.latestFactTime()).isEqualTo(T_31_16);
        assertThat(pager.calls).hasValue(3);
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore + 6);
        assertThat(pageMapper.countPagesByRequest(requestIdOf("multi-page"))).isEqualTo(3);
        AnalysisRunEntity run = runMapper.selectById(result.runId());
        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(run.getAttemptNo()).isEqualTo(1);
        assertThat(backfillRowState("multi-page")).isEqualTo("COMPLETE");
    }

    @Test
    void toleratesBoundaryDuplicatesAcrossPagesWithoutDuplicateFacts() {
        // 页边界重复: 下一页重复包含上一页最旧记录(同一 run 内重复事实身份必须去重)
        String salt = "b";
        ScriptedPager pager = new ScriptedPager(TO,
                page(salt, T_31_16, T_31_08),
                page(salt, T_31_08, T_31_00, T_30_16),
                page(salt, T_30_16, FROM));
        int factsBefore = factMapper.countFacts();

        var result = service(pager).backfill("BTC-USDT-SWAP", FROM, TO, 3, 30, "boundary-dup");

        assertThat(result.coverage()).isEqualTo(BackfillCoverage.COMPLETE);
        assertThat(result.receivedThisRun()).isEqualTo(7);
        assertThat(result.newGlobalFactsThisRun()).isEqualTo(5);
        assertThat(result.newObservationLinksThisRun()).isEqualTo(5);
        assertThat(result.duplicateObservationLinksThisRun()).isEqualTo(2);
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore + 5);
        assertThat(pager.calls).hasValue(3);
    }

    @Test
    void separatesGlobalFactsFromObservationLinksWhenFactAlreadyExists() {
        // 第一个回补请求(另一个 run)先写入事实
        ScriptedPager firstPager = new ScriptedPager(TO, page("x", T_31_16));
        int factsBefore = factMapper.countFacts();
        var first = service(firstPager).backfill("BTC-USDT-SWAP", FROM, TO, 1, 1, "cross-run-a");
        assertThat(first.newGlobalFactsThisRun()).isEqualTo(1);
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore + 1);

        // 第二个回补请求获取同一事实: 观察关系新增, 全局事实不增加
        ScriptedPager secondPager = new ScriptedPager(TO, page("x", T_31_16));
        var second = service(secondPager).backfill("BTC-USDT-SWAP", FROM, TO, 1, 30, "cross-run-b");

        assertThat(second.receivedThisRun()).isEqualTo(1);
        assertThat(second.newGlobalFactsThisRun()).isZero();
        assertThat(second.newObservationLinksThisRun()).isEqualTo(1);
        assertThat(second.duplicateObservationLinksThisRun()).isZero();
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore + 1);
    }

    @Test
    void pageCrossingFromBoundarySplitsWindowCountsAndKeepsAllFacts() {
        ScriptedPager pager = new ScriptedPager(TO, page("w", T_31_08, BEFORE_FROM));
        int factsBefore = factMapper.countFacts();

        var result = service(pager).backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "cross-window");

        assertThat(result.coverage()).isEqualTo(BackfillCoverage.COMPLETE);
        assertThat(result.receivedThisRun()).isEqualTo(2);
        assertThat(result.inWindowReceivedThisRun()).isEqualTo(1);
        assertThat(result.outOfWindowReceivedThisRun()).isEqualTo(1);
        assertThat(result.newGlobalFactsThisRun()).isEqualTo(2);
        assertThat(factMapper.countFacts()).isEqualTo(factsBefore + 2);

        Long requestId = requestIdOf("cross-window");
        Integer inWindow = jdbcTemplate.queryForObject(
                "SELECT in_window_received FROM funding_backfill_page WHERE backfill_request_id = ? AND page_no = 1",
                Integer.class, requestId);
        Integer outOfWindow = jdbcTemplate.queryForObject(
                "SELECT out_of_window_received FROM funding_backfill_page WHERE backfill_request_id = ? AND page_no = 1",
                Integer.class, requestId);
        Integer globalFacts = jdbcTemplate.queryForObject(
                "SELECT new_global_facts FROM funding_backfill_page WHERE backfill_request_id = ? AND page_no = 1",
                Integer.class, requestId);
        assertThat(inWindow).isEqualTo(1);
        assertThat(outOfWindow).isEqualTo(1);
        assertThat(globalFacts).isEqualTo(2);
    }

    @Test
    void legacyPageRowsKeepUnknownSplitAndGlobalFactsAsNull() {
        FundingRateBackfillService backfillService = service(ScriptedPager.empty());
        String requestKey = backfillService.requestKeyFor("BTC-USDT-SWAP", FROM, TO, 2, "legacy-null");
        var acquisition = store.begin(requestKey, "BTC-USDT-SWAP", FROM, TO, 2);

        // 模拟 V4 时代的账本行: 只有旧口径列有值, 新口径必须保持 NULL(UNKNOWN), 不得伪造
        jdbcTemplate.update(
                "INSERT INTO funding_backfill_page (backfill_request_id, run_id, page_no, after_cursor, "
                        + "received, new_observation_links) VALUES (?, ?, 1, ?, 3, 3)",
                acquisition.requestId(), acquisition.run().id(), java.sql.Timestamp.from(TO));

        Integer globalFacts = jdbcTemplate.queryForObject(
                "SELECT new_global_facts FROM funding_backfill_page WHERE backfill_request_id = ? AND page_no = 1",
                Integer.class, acquisition.requestId());
        Integer inWindow = jdbcTemplate.queryForObject(
                "SELECT in_window_received FROM funding_backfill_page WHERE backfill_request_id = ? AND page_no = 1",
                Integer.class, acquisition.requestId());
        assertThat(globalFacts).isNull();
        assertThat(inWindow).isNull();

        store.fail(acquisition.requestId(), acquisition.run().id(), "TEST_CLEANUP", "legacy row verified");
    }

    @Test
    void replaysCompletedRequestWithoutRefetching() {
        ScriptedPager pager = ScriptedPager.pagesOver(TO, 6, T_31_16, T_31_08, T_31_00, T_30_16, T_30_08, FROM);
        FundingRateBackfillService service = service(pager);
        int runsBefore = analysisRunCount();
        var first = service.backfill("BTC-USDT-SWAP", FROM, TO, 6, 30, "replay-done");

        var replay = service.backfill("BTC-USDT-SWAP", FROM, TO, 6, 30, "replay-done");

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.runId()).isEqualTo(first.runId());
        assertThat(replay.coverage()).isEqualTo(BackfillCoverage.COMPLETE);
        assertThat(pager.calls).hasValue(1);
        assertThat(analysisRunCount()).isEqualTo(runsBefore + 1);
    }

    @Test
    void resumesAfterBudgetExhaustionWithNewAttemptAndContinuousLedger() {
        ScriptedPager pager = ScriptedPager.pagesOver(TO, 2, T_31_16, T_31_08, T_31_00, T_30_16, T_30_08, FROM);
        FundingRateBackfillService service = service(pager);

        var first = service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 1, "budget-resume");
        assertThat(first.coverage()).isEqualTo(BackfillCoverage.BUDGET_EXHAUSTED);
        assertThat(first.pagesThisRun()).isEqualTo(1);
        assertThat(backfillRowState("budget-resume")).isEqualTo("IDLE");

        var resumed = service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "budget-resume");
        assertThat(resumed.coverage()).isEqualTo(BackfillCoverage.COMPLETE);
        assertThat(resumed.pagesThisRun()).isEqualTo(2);
        assertThat(resumed.runId()).isNotEqualTo(first.runId());
        assertThat(pageMapper.countPagesByRequest(requestIdOf("budget-resume"))).isEqualTo(3);
        assertThat(runMapper.selectById(resumed.runId()).getAttemptNo()).isEqualTo(2);
    }

    @Test
    void keepsCheckpointAfterHttpFailureAndResumesFromCursor() {
        ScriptedPager pager = ScriptedPager.pagesOver(TO, 2, T_31_16, T_31_08, T_31_00, T_30_16, T_30_08, FROM);
        pager.failAfterCalls(1, new FundingRateSourceException(
                FundingRateSourceException.Category.NETWORK, null, true, "OKX 网络请求失败"));
        FundingRateBackfillService service = service(pager);

        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "fail-resume"))
                .isInstanceOf(FundingRateSourceException.class);
        long failedRunId = onlyRunIdOf("fail-resume");
        assertThat(runMapper.selectById(failedRunId).getStatus()).isEqualTo("FAILED");
        assertThat(runMapper.selectById(failedRunId).getFailureCode()).isEqualTo("SOURCE_NETWORK");
        assertThat(backfillRowState("fail-resume")).isEqualTo("IDLE");
        assertThat(pageMapper.countPagesByRequest(requestIdOf("fail-resume"))).isEqualTo(1);

        pager.clearFailure();
        var resumed = service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "fail-resume");
        assertThat(resumed.coverage()).isEqualTo(BackfillCoverage.COMPLETE);
        assertThat(resumed.pagesThisRun()).isEqualTo(2);
        // 1 次成功 + 1 次失败 + 恢复后 2 页
        assertThat(pager.calls).hasValue(4);
    }

    @Test
    void emptyPageBeforeBoundaryIsSourceExhaustedNotComplete() {
        ScriptedPager pager = new ScriptedPager(TO, List.of(page("e", T_31_16, T_31_08)));

        var result = service(pager).backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "empty-tail");

        assertThat(result.coverage()).isEqualTo(BackfillCoverage.SOURCE_EXHAUSTED);
        assertThat(result.coverage()).isNotEqualTo(BackfillCoverage.COMPLETE);
        assertThat(backfillRowState("empty-tail")).isEqualTo("IDLE");
        // 游标仍指向已见最旧记录, 未到达 FROM
        assertThat(result.cursor()).isEqualTo(T_31_08);
    }

    @Test
    void stalledCursorFailsFastInsteadOfLoopingForever() {
        ScriptedPager pager = new ScriptedPager(TO,
                page("s", T_31_16, T_31_08),
                page("s", T_31_16, T_31_08));
        // 第二页最旧值等于游标 → 不前进
        FundingRateBackfillService service = service(pager);

        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "stalled"))
                .isInstanceOf(FundingBackfillCursorStalledException.class);

        long runId = onlyRunIdOf("stalled");
        assertThat(runMapper.selectById(runId).getStatus()).isEqualTo("FAILED");
        assertThat(runMapper.selectById(runId).getFailureCode()).isEqualTo("CURSOR_STALLED");
        assertThat(pageMapper.countPagesByRequest(requestIdOf("stalled"))).isEqualTo(1);
        assertThat(pager.calls).hasValue(2);
        assertThat(backfillRowState("stalled")).isEqualTo("IDLE");
    }

    @Test
    void rejectsIllegalArgumentsBeforeAnySideEffect() {
        int runsBefore = analysisRunCount();
        int requestsBefore = backfillRequestCount();
        FundingRateBackfillService service = service(ScriptedPager.empty());

        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP", TO, FROM, 2, 30, "bad-window"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP", FROM, TO, 0, 30, "bad-limit"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 0, "bad-budget"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "bad key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP",
                Instant.parse("2026-07-30T00:00:00.0000005Z"), TO, 2, 30, "sub-millis"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.backfill("BTC-SPOT", FROM, TO, 2, 30, "bad-instrument"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(analysisRunCount()).isEqualTo(runsBefore);
        assertThat(backfillRequestCount()).isEqualTo(requestsBefore);
    }

    @Test
    void recoveredOldExecutionCannotCommitPagesAndStaleListSeesBackfillOrphans() {
        ScriptedPager pager = ScriptedPager.pagesOver(TO, 2, T_31_16, T_31_08, T_31_00, T_30_16, T_30_08, FROM);
        FundingRateBackfillService service = service(pager);
        String requestKey = service.requestKeyFor("BTC-USDT-SWAP", FROM, TO, 2, "recover-fence");
        var acquisition = store.begin(requestKey, "BTC-USDT-SWAP", FROM, TO, 2);
        ageRunByOneHour(acquisition.run().id());

        List<StaleExecution> stale = service.listStale(Duration.ofMinutes(15));
        assertThat(stale).extracting(StaleExecution::requestKey).contains(requestKey);

        assertThat(service.recoverStale(requestKey, acquisition.run().id(), Duration.ofMinutes(15)))
                .isEqualTo(StaleRecoveryOutcome.RECOVERED);

        assertThatThrownBy(() -> store.commitPage(
                acquisition.requestId(), acquisition.run().id(), 1, TO,
                page("f", T_31_16, T_31_08), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("执行权");

        var resumed = service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "recover-fence");
        assertThat(resumed.coverage()).isEqualTo(BackfillCoverage.COMPLETE);
        assertThat(runMapper.selectById(resumed.runId()).getAttemptNo()).isEqualTo(2);
    }

    @Test
    void rejectsUnorderedPageAsInvalidSourceWithoutSideEffects() {
        ScriptedPager pager = new ScriptedPager(TO, List.of(page("u", T_31_08, T_31_16)));
        int pagesBefore = pageMapper.countAllPages();

        assertThatThrownBy(() -> service(pager).backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "unordered"))
                .isInstanceOf(FundingRateSourceException.class)
                .isInstanceOfSatisfying(FundingRateSourceException.class, failure ->
                        assertThat(failure.category())
                                .isEqualTo(FundingRateSourceException.Category.INVALID_RESPONSE));

        long runId = onlyRunIdOf("unordered");
        assertThat(runMapper.selectById(runId).getStatus()).isEqualTo("FAILED");
        assertThat(pageMapper.countAllPages()).isEqualTo(pagesBefore);
    }

    @Test
    void refusesConcurrentExecutionOfSameBackfillRequest() {
        FundingRateBackfillService service = service(ScriptedPager.empty());
        var acquisition = store.begin(
                service.requestKeyFor("BTC-USDT-SWAP", FROM, TO, 2, "concurrent-bf"),
                "BTC-USDT-SWAP", FROM, TO, 2);

        assertThatThrownBy(() -> service.backfill("BTC-USDT-SWAP", FROM, TO, 2, 30, "concurrent-bf"))
                .isInstanceOf(FundingRateSyncInProgressException.class);

        store.fail(acquisition.requestId(), acquisition.run().id(), "TEST_CLEANUP", "cleanup");
    }

    private FundingRateBackfillService service(ScriptedPager pager) {
        return new FundingRateBackfillService(pager, store);
    }

    private static List<FundingRateObservation> page(String salt, Instant... times) {
        List<FundingRateObservation> observations = new ArrayList<>();
        for (Instant time : times) {
            String hash = (Long.toHexString(time.toEpochMilli()) + salt + "0".repeat(64)).substring(0, 64);
            observations.add(new FundingRateObservation(
                    "OKX", DerivativeInstrumentType.SWAP, "BTC-USDT-SWAP",
                    FundingFormulaType.WITH_RATE, new BigDecimal("0.0001"), new BigDecimal("0.0001"),
                    time, FundingSettlementMethod.CURRENT_PERIOD,
                    "/api/v5/public/funding-rate-history", "{\"fixture\":true}",
                    hash, TO));
        }
        return observations;
    }

    private long requestIdOf(String syncKey) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM funding_backfill_request WHERE request_key = ?",
                Long.class, requestKeyOf(syncKey));
    }

    private String backfillRowState(String syncKey) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM funding_backfill_request WHERE request_key = ?",
                String.class, requestKeyOf(syncKey));
    }

    private long onlyRunIdOf(String syncKey) {
        return jdbcTemplate.queryForObject(
                "SELECT a.id FROM analysis_run a JOIN funding_backfill_request r ON r.id = a.backfill_request_id "
                        + "WHERE r.request_key = ? ORDER BY a.id LIMIT 1",
                Long.class, requestKeyOf(syncKey));
    }

    private String requestKeyOf(String syncKey) {
        return "backfill:BTC-USDT-SWAP:2:" + FROM.toEpochMilli() + ":" + TO.toEpochMilli() + ":" + syncKey;
    }

    private int analysisRunCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM analysis_run", Integer.class);
        return count == null ? 0 : count;
    }

    private int backfillRequestCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM funding_backfill_request", Integer.class);
        return count == null ? 0 : count;
    }

    private void ageRunByOneHour(long runId) {
        jdbcTemplate.update(
                "UPDATE analysis_run SET started_at = DATE_SUB(started_at, INTERVAL 1 HOUR) WHERE id = ?", runId);
    }
}
