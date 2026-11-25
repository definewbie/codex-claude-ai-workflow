package app.cryptoincome.cli;

import app.cryptoincome.grid.application.FixedGridAssessmentService;
import app.cryptoincome.grid.application.SpotCandleBackfillResult;
import app.cryptoincome.grid.application.SpotCandleBackfillService;
import app.cryptoincome.grid.domain.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GridCliRunner implements ApplicationRunner, ExitCodeGenerator {
    public static final String COMMAND = "grid";
    public static final int EXIT_OK = 0;
    public static final int EXIT_INTERNAL = 1;
    public static final int EXIT_ARGS = 2;
    public static final int EXIT_SOURCE = 4;
    private static final Set<String> BACKFILL_OPTIONS = Set.of("from", "to", "limit", "max-pages");
    private static final Set<String> ASSESS_OPTIONS = Set.of(
            "as-of", "from", "to", "config-id", "capital", "simple-apr",
            "maker-fee", "taker-fee", "exit-slippage-bps", "max-drawdown",
            "max-path-divergence", "max-ending-btc-ratio");
    private static final Set<String> SCENARIO_OPTIONS = Set.of(
            "as-of", "from", "to", "config-id", "capital", "simple-apr",
            "maker-fee", "taker-fee", "exit-slippage-bps", "max-drawdown",
            "max-path-divergence", "max-ending-btc-ratio", "lower-price", "upper-price", "grid-count",
            "take-profit", "stop-loss");
    private static final Set<String> SWEEP_OPTIONS = Set.of(
            "as-of", "from", "to", "config-id", "capital", "simple-apr",
            "maker-fee", "taker-fee", "exit-slippage-bps", "max-drawdown",
            "max-path-divergence", "max-ending-btc-ratio", "anchor-price", "range-scales", "grid-counts");

    private final SpotCandleBackfillService backfillService;
    private final FixedGridAssessmentService assessmentService;
    private final PrintStream out;
    private int exitCode;

    @Autowired
    public GridCliRunner(SpotCandleBackfillService backfillService, FixedGridAssessmentService assessmentService) {
        this(backfillService, assessmentService, System.out);
    }

    GridCliRunner(SpotCandleBackfillService backfillService,
                  FixedGridAssessmentService assessmentService, PrintStream out) {
        this.backfillService = backfillService;
        this.assessmentService = assessmentService;
        this.out = out;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String[] raw = arguments.getSourceArgs();
        if (raw.length == 0 || !COMMAND.equals(raw[0])) return;
        exitCode = handle(raw);
    }

    @Override public int getExitCode() { return exitCode; }

    private int handle(String[] raw) {
        try {
            Parsed parsed = parse(raw);
            return switch (parsed.subcommand()) {
                case "backfill" -> backfill(parsed.options());
                case "assess" -> assess(parsed.options());
                case "scenario" -> scenario(parsed.options());
                case "sweep" -> sweep(parsed.options());
                default -> usage();
            };
        } catch (CliUsageException | IllegalArgumentException invalid) {
            out.println("参数错误: " + bounded(invalid.getMessage()));
            return EXIT_ARGS;
        } catch (GridSourceException source) {
            out.println("来源失败: 类别=" + source.category() + " providerCode=" + source.providerCode()
                    + " 可重试=" + source.retryable());
            return EXIT_SOURCE;
        } catch (RuntimeException internal) {
            out.println("内部错误: " + bounded(internal.getMessage()));
            return EXIT_INTERNAL;
        }
    }

    private int backfill(Map<String, String> options) {
        requireOnly(options, BACKFILL_OPTIONS);
        SpotCandleBackfillResult result = backfillService.backfill(
                "BTC-USDT", CandleBar.M1, instant("from", required(options, "from")),
                instant("to", required(options, "to")), integer(options, "limit", 300),
                integer(options, "max-pages", 1000));
        out.println("BTC-USDT 1分钟K线回补完成（OKX公共只读接口）");
        out.println("coverage=" + result.coverage() + "，页数=" + result.pages()
                + "，接收=" + result.received() + "，窗口内完整K线=" + result.completedInWindow());
        out.println("新增事实=" + result.newFacts() + "，重复事实=" + result.duplicateFacts()
                + "，游标=" + result.cursor());
        if (result.coverage() != CandleBackfillCoverage.BOUNDARY_REACHED) {
            out.println("提示: 尚未到达90天窗口边界，请用相同命令继续回补；当前不能生成收益结论。");
        }
        return EXIT_OK;
    }

    private int assess(Map<String, String> options) {
        requireOnly(options, ASSESS_OPTIONS);
        for (String name : ASSESS_OPTIONS) required(options, name);
        FixedGridAssessment result = assessmentService.assess(assessmentRequest(options));
        print(result);
        return EXIT_OK;
    }

    private int scenario(Map<String, String> options) {
        requireOnly(options, SCENARIO_OPTIONS);
        for (String name : ASSESS_OPTIONS) required(options, name);
        required(options, "lower-price");
        required(options, "upper-price");
        required(options, "grid-count");
        GridScenarioParameters scenario = new GridScenarioParameters(
                decimal("lower-price", options.get("lower-price")),
                decimal("upper-price", options.get("upper-price")),
                integer(options, "grid-count", 0), optionalDecimal(options, "take-profit"),
                optionalDecimal(options, "stop-loss"));
        print(assessmentService.assessScenario(assessmentRequest(options), scenario));
        return EXIT_OK;
    }

    private int sweep(Map<String, String> options) {
        requireOnly(options, SWEEP_OPTIONS);
        for (String name : SWEEP_OPTIONS) required(options, name);
        FixedGridAssessmentRequest request = assessmentRequest(options);
        FixedGridAssessment base = assessmentService.assess(request);
        BigDecimal anchor = decimal("anchor-price", options.get("anchor-price"));
        if (anchor.compareTo(base.configurationSnapshot().lowerPrice()) <= 0
                || anchor.compareTo(base.configurationSnapshot().upperPrice()) >= 0) {
            throw new CliUsageException("anchor-price 必须位于原始网格区间内");
        }
        List<BigDecimal> scales = decimalList("range-scales", options.get("range-scales"));
        List<Integer> counts = integerList("grid-counts", options.get("grid-counts"));
        out.println("固定网格参数矩阵（只读情景，不修改 OKX 人工确认配置）");
        out.println("原始配置ID=" + request.configurationSnapshotId() + "，锚点=" + plain(anchor));
        out.println("缩放\t网格\t下限\t上限\t保守净收益\t简单赚币\tBTC持有\t最大回撤\t建议");
        for (BigDecimal scale : scales) {
            if (scale.signum() <= 0 || scale.compareTo(BigDecimal.ONE) > 0) {
                throw new CliUsageException("range-scales 必须在 0 到 1 之间");
            }
            BigDecimal lower = anchor.subtract(anchor.subtract(base.configurationSnapshot().lowerPrice()).multiply(scale));
            BigDecimal upper = anchor.add(base.configurationSnapshot().upperPrice().subtract(anchor).multiply(scale));
            for (int count : counts) {
                FixedGridAssessment result = assessmentService.assessScenario(request,
                        new GridScenarioParameters(lower, upper, count, java.util.Optional.empty(), java.util.Optional.empty()));
                BigDecimal drawdown = result.backtest().pathLowThenHigh().maximumDrawdownRate()
                        .max(result.backtest().pathHighThenLow().maximumDrawdownRate());
                out.println(plain(scale) + "\t" + count + "\t" + money(lower) + "\t" + money(upper)
                        + "\t" + money(result.worsePathIncome()) + "\t" + money(result.simpleEarnIncome())
                        + "\t" + money(result.buyAndHoldIncome()) + "\t" + percent(drawdown)
                        + "\t" + recommendation(result.recommendation()));
            }
        }
        out.println("限制: 矩阵用于敏感度分析，不能把同一历史窗口的最高收益参数直接升级为推荐。");
        return EXIT_OK;
    }

    private static FixedGridAssessmentRequest assessmentRequest(Map<String, String> options) {
        return new FixedGridAssessmentRequest(
                instant("as-of", options.get("as-of")), instant("from", options.get("from")),
                instant("to", options.get("to")), longValue("config-id", options.get("config-id")),
                decimal("capital", options.get("capital")), decimal("simple-apr", options.get("simple-apr")),
                decimal("maker-fee", options.get("maker-fee")), decimal("taker-fee", options.get("taker-fee")),
                decimal("exit-slippage-bps", options.get("exit-slippage-bps")),
                decimal("max-drawdown", options.get("max-drawdown")),
                decimal("max-path-divergence", options.get("max-path-divergence")),
                decimal("max-ending-btc-ratio", options.get("max-ending-btc-ratio")));
    }

    private void print(FixedGridAssessment result) {
        out.println("固定现货网格90天收益评估（历史情景，只读，不会创建策略）");
        out.println("人工确认配置=" + result.request().configurationSnapshotId()
                + (result.scenarioOverride() ? "，评估类型=参数情景（不覆盖原配置）" : "，评估类型=原始配置"));
        out.println("有效参数区间=" + plain(result.scenario().lowerPrice()) + "-" + plain(result.scenario().upperPrice())
                + "，网格数=" + result.scenario().gridCount() + "，模式=等差");
        out.println("OKX推荐期限标签=" + result.configurationSnapshot().recommendationHorizonLabel()
                + "（非精确天数；本次统一按90天历史窗口比较）");
        Duration inactive = Duration.between(result.request().from(), result.backtest().from());
        Duration pathAActive = Duration.between(
                result.backtest().from(), result.backtest().pathLowThenHigh().exitTime());
        Duration pathBActive = Duration.between(
                result.backtest().from(), result.backtest().pathHighThenLow().exitTime());
        out.println("观察窗口=" + utc8(result.request().from()) + " 至 " + utc8(result.request().to()) + "（UTC+8）");
        out.println("历史可运行候选区间=" + utc8(result.backtest().from()) + " 至 " + utc8(result.backtest().toExclusive())
                + "（UTC+8）"
                + "，前置不具备运行条件=" + duration(inactive));
        out.println("复投=关闭，自动赚币=关闭，移动网格=关闭，止盈="
                + result.scenario().takeProfitPrice().map(GridCliRunner::plain).orElse("关闭")
                + "，止损=" + result.scenario().stopLossPrice().map(GridCliRunner::plain).orElse("关闭"));
        out.println("路径A净收益=" + money(result.backtest().pathLowThenHigh().totalIncome())
                + " USDT，路径B净收益=" + money(result.backtest().pathHighThenLow().totalIncome()) + " USDT");
        out.println("路径A退出=" + exitReason(result.backtest().pathLowThenHigh().exitReason())
                + "@" + utc8(result.backtest().pathLowThenHigh().exitTime())
                + "，路径B退出=" + exitReason(result.backtest().pathHighThenLow().exitReason())
                + "@" + utc8(result.backtest().pathHighThenLow().exitTime()));
        out.println("路径A实际运行=" + duration(pathAActive) + "，路径B实际运行=" + duration(pathBActive));
        out.println("保守路径净收益=" + money(result.worsePathIncome()) + " USDT");
        out.println("简单赚币基准=" + money(result.simpleEarnIncome())
                + " USDT，买入持有BTC基准=" + money(result.buyAndHoldIncome()) + " USDT");
        out.println("最大回撤=" + percent(result.backtest().pathLowThenHigh().maximumDrawdownRate()
                .max(result.backtest().pathHighThenLow().maximumDrawdownRate()))
                + "，路径分歧=" + percent(result.pathDivergenceRate()));
        out.println("建议=" + recommendation(result.recommendation()));
        result.maximumFirstLiveCapital().ifPresent(value -> out.println("首次实盘上限=" + money(value) + " USDT"));
        result.reasons().forEach(reason -> out.println("原因: " + reason));
        out.println("限制: 参数由观察窗口结束时的 OKX AI 生成，历史回放存在事后选择；"
                + "分钟K线也无法确定分钟内真实成交顺序，结果不代表未来收益。");
    }

    private int usage() {
        out.println("用法:");
        out.println("  grid backfill --from <UTC> --to <UTC> [--limit 300] [--max-pages 1000]");
        out.println("  grid assess --as-of <UTC> --from <UTC> --to <UTC> --config-id <人工确认配置ID> --capital <USDT>");
        out.println("  grid scenario <同 assess 参数> --lower-price <价格> --upper-price <价格> --grid-count <数量>");
        out.println("    [--take-profit <高于上限的价格>] [--stop-loss <低于下限的价格>]");
        out.println("  grid sweep <同 assess 参数> --anchor-price <当前价> --range-scales 1,0.9,0.8 --grid-counts 30,40,50");
        out.println("    --simple-apr 0.035 --maker-fee 0.0006 --taker-fee 0.0007 --exit-slippage-bps <bps>");
        out.println("    --max-drawdown <小数> --max-path-divergence <小数> --max-ending-btc-ratio <小数>");
        return EXIT_ARGS;
    }

    private static Parsed parse(String[] raw) {
        if (raw.length < 2) throw new CliUsageException("缺少子命令");
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 2; i < raw.length; i++) {
            String token = raw[i];
            if (!token.startsWith("--") || i + 1 >= raw.length) throw new CliUsageException("参数格式错误: " + token);
            String name = token.substring(2);
            String value = raw[++i];
            if (options.putIfAbsent(name, value) != null) throw new CliUsageException("参数重复: --" + name);
        }
        return new Parsed(raw[1], options);
    }

    private static void requireOnly(Map<String, String> options, Set<String> allowed) {
        options.keySet().stream().filter(name -> !allowed.contains(name)).findFirst()
                .ifPresent(name -> { throw new CliUsageException("不支持的参数: --" + name); });
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new CliUsageException("缺少必填参数: --" + name);
        return value;
    }

    private static Instant instant(String name, String value) {
        try { return Instant.parse(value); }
        catch (DateTimeParseException | NullPointerException invalid) {
            throw new CliUsageException(name + " 不是UTC ISO-8601时间");
        }
    }

    private static BigDecimal decimal(String name, String value) {
        try { return new BigDecimal(value); }
        catch (NumberFormatException | NullPointerException invalid) { throw new CliUsageException(name + " 不是数字"); }
    }

    private static int integer(Map<String, String> options, String name, int defaultValue) {
        try { return options.containsKey(name) ? Integer.parseInt(options.get(name)) : defaultValue; }
        catch (NumberFormatException invalid) { throw new CliUsageException(name + " 不是整数"); }
    }

    private static List<BigDecimal> decimalList(String name, String value) {
        List<BigDecimal> result = new ArrayList<>();
        for (String item : value.split(",")) result.add(decimal(name, item.trim()));
        if (result.isEmpty()) throw new CliUsageException(name + " 不能为空");
        return List.copyOf(result);
    }

    private static List<Integer> integerList(String name, String value) {
        List<Integer> result = new ArrayList<>();
        for (String item : value.split(",")) {
            try { result.add(Integer.parseInt(item.trim())); }
            catch (NumberFormatException invalid) { throw new CliUsageException(name + " 包含非法整数"); }
        }
        if (result.isEmpty()) throw new CliUsageException(name + " 不能为空");
        return List.copyOf(result);
    }

    private static long longValue(String name, String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException | NullPointerException invalid) { throw new CliUsageException(name + " 不是整数"); }
    }

    private static java.util.Optional<BigDecimal> optionalDecimal(Map<String, String> options, String name) {
        return options.containsKey(name)
                ? java.util.Optional.of(decimal(name, options.get(name)))
                : java.util.Optional.empty();
    }

    private static String money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private static String percent(BigDecimal value) { return value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%"; }
    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static String duration(Duration value) {
        long minutes = value.toMinutes();
        return (minutes / 1440) + "天" + ((minutes % 1440) / 60) + "小时" + (minutes % 60) + "分钟";
    }
    private static String utc8(Instant value) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value.atZone(ZoneId.of("Asia/Shanghai")));
    }
    private static String recommendation(GridAssessmentRecommendation value) {
        return switch (value) {
            case INSUFFICIENT_EVIDENCE -> "证据不足";
            case PREFER_SIMPLE_EARN -> "优先简单赚币";
            case KEEP_OBSERVING_GRID -> "继续观察现货网格";
            case MICRO_VALIDATE_GRID -> "通过Shadow后仅小额验证";
        };
    }
    private static String exitReason(GridExitReason value) {
        return switch (value) {
            case WINDOW_END -> "窗口结束";
            case TAKE_PROFIT -> "止盈";
            case STOP_LOSS -> "止损";
        };
    }
    private static String bounded(String value) { return value == null ? "未知错误" : value.substring(0, Math.min(200, value.length())); }

    private record Parsed(String subcommand, Map<String, String> options) {}
    private static final class CliUsageException extends RuntimeException { CliUsageException(String message) { super(message); } }
}
