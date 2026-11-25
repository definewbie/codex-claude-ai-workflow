package app.cryptoincome.cli;

import app.cryptoincome.funding.application.BackfillResult;
import app.cryptoincome.funding.application.FundingRateBackfillService;
import app.cryptoincome.funding.application.FundingRateSyncResult;
import app.cryptoincome.funding.application.FundingRateSyncService;
import app.cryptoincome.funding.domain.BackfillCoverage;
import app.cryptoincome.funding.domain.FundingBackfillCursorStalledException;
import app.cryptoincome.funding.domain.FundingRateSourceException;
import app.cryptoincome.funding.domain.FundingRateSyncInProgressException;
import app.cryptoincome.funding.domain.StaleExecution;
import app.cryptoincome.funding.domain.StaleRecoveryOutcome;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * sync-market 手动入口: 只调用 application 层 API, 不触碰 Mapper, 不开启外层事务。
 * 数据库事务边界由 FundingRateSyncStore 的 acquire/complete/fail/recoverStale 各自持有,
 * OKX HTTP 请求发生在事务之外。
 */
@Component
public class SyncMarketCliRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String COMMAND = "sync-market";

    public static final int EXIT_OK = 0;
    public static final int EXIT_INTERNAL = 1;
    public static final int EXIT_ARGS = 2;
    public static final int EXIT_IN_PROGRESS = 3;
    public static final int EXIT_SOURCE = 4;
    public static final int EXIT_RECOVERY_REFUSED = 5;

    private static final Set<String> LATEST_OPTIONS = Set.of("instrument", "limit", "request-key");
    private static final Set<String> BACKFILL_OPTIONS =
            Set.of("instrument", "from", "to", "limit", "max-pages", "request-key");
    private static final Set<String> STALE_LIST_OPTIONS = Set.of("older-than");
    private static final Set<String> STALE_RECOVER_OPTIONS = Set.of("request-key", "expected-run-id", "older-than");

    private final FundingRateSyncService syncService;
    private final FundingRateBackfillService backfillService;
    private final PrintStream out;
    private int exitCode = EXIT_OK;

    @Autowired
    public SyncMarketCliRunner(FundingRateSyncService syncService, FundingRateBackfillService backfillService) {
        this(syncService, backfillService, System.out);
    }

    SyncMarketCliRunner(FundingRateSyncService syncService, FundingRateBackfillService backfillService, PrintStream out) {
        this.syncService = syncService;
        this.backfillService = backfillService;
        this.out = out;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String[] raw = arguments.getSourceArgs();
        if (raw.length == 0 || !COMMAND.equals(raw[0])) {
            return;
        }
        exitCode = handle(raw);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private int handle(String[] raw) {
        try {
            Parsed parsed = parse(raw);
            if (parsed.positional().size() != 2) {
                return usage();
            }
            return switch (parsed.positional().get(1)) {
                case "latest" -> latest(parsed.options(), LATEST_OPTIONS);
                case "backfill" -> backfill(parsed.options(), BACKFILL_OPTIONS);
                case "stale-list" -> staleList(parsed.options(), STALE_LIST_OPTIONS);
                case "stale-recover" -> staleRecover(parsed.options(), STALE_RECOVER_OPTIONS);
                default -> usage();
            };
        } catch (CliUsageException usage) {
            out.println("参数错误: " + usage.getMessage());
            return EXIT_ARGS;
        } catch (FundingRateSyncInProgressException inProgress) {
            out.println("拒绝执行: 相同逻辑请求正在执行中 (runId=" + inProgress.runId() + "), 不会并发请求 OKX");
            return EXIT_IN_PROGRESS;
        } catch (FundingBackfillCursorStalledException stalled) {
            out.println("游标停滞: " + bounded(stalled.getMessage())
                    + "; 本次 execution 已标记 FAILED(CURSOR_STALLED), 游标与账本保留, 可用相同 request-key 重试; 若持续停滞请核对来源分页行为");
            return EXIT_SOURCE;
        } catch (FundingRateSourceException sourceFailure) {
            out.println("来源失败: 类别=" + sourceFailure.category()
                    + " providerCode=" + sourceFailure.providerCode()
                    + " 可重试=" + sourceFailure.retryable()
                    + "; 本次 execution 已标记 FAILED, 已提交的页与游标保留, 可用相同 request-key 从 checkpoint 继续");
            return EXIT_SOURCE;
        } catch (IllegalArgumentException badArguments) {
            out.println("参数错误: " + bounded(badArguments.getMessage()));
            return EXIT_ARGS;
        } catch (RuntimeException internal) {
            out.println("内部错误: " + bounded(internal.getMessage()));
            return EXIT_INTERNAL;
        }
    }

    private int latest(Map<String, String> options, Set<String> expected) {
        requireExpected(options, expected);
        String instrument = options.get("instrument");
        int limit = parseLimit(options.get("limit"));
        String requestKey = options.get("request-key");

        FundingRateSyncResult result = syncService.syncLatest(instrument, limit, requestKey);
        if (result.replayed()) {
            out.println("同步回放: 状态=" + result.status() + " runId=" + result.analysisRunId()
                    + " 已关联观察=" + result.newObservationLinks()
                    + " (该 request-key 已成功过, 未再次请求 OKX)");
        } else {
            out.println("同步完成: 状态=" + result.status() + " runId=" + result.analysisRunId()
                    + " 接收=" + result.received() + " 新观察=" + result.newObservationLinks()
                    + " 重复观察=" + result.duplicateObservationLinks());
            out.println("注意: 这是最近一页增量, 不代表历史数据已经完整回补");
        }
        return EXIT_OK;
    }

    private int backfill(Map<String, String> options, Set<String> expected) {
        requireExpected(options, expected);
        String instrument = options.get("instrument");
        Instant from = parseInstant("from", options.get("from"));
        Instant to = parseInstant("to", options.get("to"));
        int limit = parseLimit(options.get("limit"));
        int maxPages = parseMaxPages(options.get("max-pages"));
        String requestKey = options.get("request-key");

        BackfillResult result = backfillService.backfill(instrument, from, to, limit, maxPages, requestKey);
        if (result.replayed()) {
            out.println("回补回放: coverage=" + result.coverage() + coverageNote(result.coverage())
                    + " 累计页数=" + result.totalPages()
                    + " (该 request-key 已完成, 未再次请求 OKX)");
            return EXIT_OK;
        }
        out.println("回补执行完成: runId=" + result.runId()
                + " attempt=" + result.attemptNo()
                + " coverage=" + result.coverage() + coverageNote(result.coverage()));
        out.println("  本次页数=" + result.pagesThisRun()
                + " 累计页数=" + result.totalPages()
                + " 本次接收=" + result.receivedThisRun()
                + " (窗口内=" + result.inWindowReceivedThisRun()
                + " 窗口外=" + result.outOfWindowReceivedThisRun() + ")");
        out.println("  新增全局事实=" + result.newGlobalFactsThisRun()
                + " 新增观察关系=" + result.newObservationLinksThisRun()
                + " 重复观察关系=" + result.duplicateObservationLinksThisRun());
        out.println("  最早fundingTime=" + result.earliestFactTime()
                + " 最晚fundingTime=" + result.latestFactTime()
                + " 当前cursor=" + result.cursor());
        out.println("  下一步: " + nextAction(result.coverage()));
        return EXIT_OK;
    }

    /** coverage=COMPLETE 仅代表 BOUNDARY_REACHED: 分页游标到达 from 边界, 区间完整性尚未检查。 */
    private static String coverageNote(BackfillCoverage coverage) {
        return coverage == BackfillCoverage.COMPLETE ? "(已到达目标边界, 区间完整性尚未检查)" : "";
    }

    private static String nextAction(BackfillCoverage coverage) {
        return switch (coverage) {
            case COMPLETE -> "分页游标已到达 from 边界; 区间完整性尚未检查(缺口检测属后续工作), "
                    + "不要据此声称区间无缺失; COMPLETE 也不代表 OKX 全部历史完整(公共窗口约三个月)";
            case BUDGET_EXHAUSTED -> "本次页数预算用尽, 使用相同 request-key 重新执行可从 checkpoint 续传";
            case SOURCE_EXHAUSTED -> "OKX 在当前游标已无更旧数据, 未到达 from 边界(公共历史窗口约三个月); 请核对窗口后决定是否重试";
            case IN_PROGRESS -> "执行仍在进行中";
        };
    }

    private int staleList(Map<String, String> options, Set<String> expected) {
        requireExpected(options, expected);
        Duration olderThan = parseDuration(options.get("older-than"));

        List<StaleExecution> stale = new ArrayList<>(syncService.listStale(olderThan));
        stale.addAll(backfillService.listStale(olderThan));
        stale.sort(Comparator.comparing(StaleExecution::startedAt));
        if (stale.isEmpty()) {
            out.println("未发现超过阈值(" + olderThan + ")的 RUNNING execution(含单页同步与历史回补)");
            return EXIT_OK;
        }
        out.println("发现 " + stale.size() + " 个超过阈值(" + olderThan + ")的 RUNNING execution:");
        for (StaleExecution execution : stale) {
            out.println("request-key=" + execution.requestKey()
                    + " runId=" + execution.runId()
                    + " instrument=" + execution.instrumentId()
                    + " limit=" + execution.pageLimit()
                    + " startedAt=" + execution.startedAt());
        }
        out.println("以上为只读发现结果; 如需恢复, 请人工核对后执行 sync-market stale-recover");
        return EXIT_OK;
    }

    private int staleRecover(Map<String, String> options, Set<String> expected) {
        requireExpected(options, expected);
        String requestKey = options.get("request-key");
        long expectedRunId = parseExpectedRunId(options.get("expected-run-id"));
        Duration olderThan = parseDuration(options.get("older-than"));

        StaleRecoveryOutcome outcome = requestKey.startsWith(FundingRateBackfillService.REQUEST_KEY_PREFIX)
                ? backfillService.recoverStale(requestKey, expectedRunId, olderThan)
                : syncService.recoverStale(requestKey, expectedRunId, olderThan);
        return switch (outcome) {
            case RECOVERED -> {
                out.println("恢复完成: runId=" + expectedRunId
                        + " 已标记 FAILED(STALE_RECOVERED), 逻辑请求已回到 READY/IDLE, 旧 execution 已失去执行权;"
                        + " 请核对后用相同 request-key 重新执行对应命令继续");
                yield EXIT_OK;
            }
            case NOT_FOUND -> refused("未找到该 request-key 对应的逻辑请求");
            case NOT_RUNNING -> refused("该逻辑请求当前不是 RUNNING 状态, 无需恢复(可能已成功或已被恢复)");
            case RUN_MISMATCH -> refused("current run 已变化, 拒绝恢复; 请先用 stale-list 确认当前 runId");
            case NOT_STALE -> refused("该 execution 开始时间未超过阈值, 拒绝恢复");
        };
    }

    private int refused(String reason) {
        out.println("拒绝恢复: " + reason);
        return EXIT_RECOVERY_REFUSED;
    }

    private int usage() {
        out.println("""
                用法:
                  sync-market latest --instrument <永续合约> --limit <1-400> --request-key <幂等键>
                  sync-market backfill --instrument <永续合约> --from <UTC时间> --to <UTC时间> --limit <1-400> --max-pages <1-1000> --request-key <幂等键>
                  sync-market stale-list --older-than <ISO-8601时长, 例如 PT15M>
                  sync-market stale-recover --request-key <幂等键> --expected-run-id <runId> --older-than <ISO-8601时长>""");
        return EXIT_ARGS;
    }

    /**
     * 解析原始参数: 支持 "--name value" 与 "--name=value" 两种形式。
     * Spring 标准 ApplicationArguments 只认 "--name=value", 而本工具约定空格形式,
     * 因此这里基于 sourceArgs 自行解析, 不引入新依赖。
     */
    private static Parsed parse(String[] raw) {
        Map<String, String> options = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String token = raw[i];
            if (token.startsWith("--")) {
                String name = token.substring(2);
                String inlineValue = null;
                int separator = name.indexOf('=');
                if (separator >= 0) {
                    inlineValue = name.substring(separator + 1);
                    name = name.substring(0, separator);
                }
                String value = inlineValue;
                if (value == null) {
                    if (i + 1 >= raw.length) {
                        throw new CliUsageException("参数 --" + name + " 缺少值");
                    }
                    value = raw[++i];
                }
                if (options.containsKey(name)) {
                    throw new CliUsageException("参数 --" + name + " 重复提供");
                }
                options.put(name, value);
            } else {
                positional.add(token);
            }
        }
        return new Parsed(positional, options);
    }

    private static void requireExpected(Map<String, String> options, Set<String> expected) {
        for (String name : options.keySet()) {
            if (!expected.contains(name)) {
                throw new CliUsageException("不支持的参数: --" + name);
            }
        }
        for (String required : expected) {
            if (!options.containsKey(required)) {
                throw new CliUsageException("缺少必填参数: --" + required);
            }
        }
    }

    private static int parseLimit(String raw) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException notAnInt) {
            throw new CliUsageException("limit 不是整数: " + raw);
        }
        if (value < 1 || value > 400) {
            throw new CliUsageException("limit 必须在 1 到 400 之间");
        }
        return value;
    }

    private static long parseExpectedRunId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException notALong) {
            throw new CliUsageException("expected-run-id 不是整数: " + raw);
        }
    }

    private static int parseMaxPages(String raw) {
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException notAnInt) {
            throw new CliUsageException("max-pages 不是整数: " + raw);
        }
        if (value < 1 || value > 1_000) {
            throw new CliUsageException("max-pages 必须在 1 到 1000 之间");
        }
        return value;
    }

    private static Instant parseInstant(String name, String raw) {
        Instant value;
        try {
            value = Instant.parse(raw);
        } catch (DateTimeParseException | NullPointerException invalid) {
            throw new CliUsageException(name + " 不是 UTC ISO-8601 时间(例如 2026-05-01T00:00:00Z): " + raw);
        }
        if (value.getNano() % 1_000_000 != 0) {
            throw new CliUsageException(name + " 精度不能低于 1 毫秒: " + raw);
        }
        return value;
    }

    private static Duration parseDuration(String raw) {
        try {
            return Duration.parse(raw);
        } catch (DateTimeParseException invalid) {
            throw new CliUsageException("older-than 不是 ISO-8601 时长(例如 PT15M): " + raw);
        }
    }

    private static String bounded(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        return message.length() <= 200 ? message : message.substring(0, 200);
    }

    private static final class CliUsageException extends RuntimeException {
        CliUsageException(String message) {
            super(message);
        }
    }

    private record Parsed(List<String> positional, Map<String, String> options) {
    }
}
