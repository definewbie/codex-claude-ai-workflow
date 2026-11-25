package app.cryptoincome.cli;

import app.cryptoincome.grid.application.CapturedGridParameter;
import app.cryptoincome.grid.application.GridParameterSnapshotService;
import app.cryptoincome.grid.application.GridConfigurationSnapshotService;
import app.cryptoincome.grid.application.RecordedGridConfiguration;
import app.cryptoincome.grid.domain.GridConfigurationSnapshot;
import app.cryptoincome.grid.domain.GridConfigurationSource;
import app.cryptoincome.grid.domain.GridDuration;
import app.cryptoincome.grid.domain.GridParameterSnapshot;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.grid.domain.GridType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class SyncProductCliRunner implements ApplicationRunner, ExitCodeGenerator {
    public static final String COMMAND = "sync-product";
    public static final int EXIT_OK = 0;
    public static final int EXIT_INTERNAL = 1;
    public static final int EXIT_ARGS = 2;
    public static final int EXIT_SOURCE = 4;
    private static final Set<String> API_OPTIONS = Set.of("instrument", "duration");
    private static final Set<String> UI_OPTIONS = Set.of(
            "instrument", "horizon", "lower-price", "upper-price", "grid-count", "grid-type",
            "displayed-annualized-rate", "displayed-min-per-grid-rate", "displayed-max-per-grid-rate",
            "profit-reinvestment", "auto-earn", "trailing-up", "trailing-down",
            "take-profit", "stop-loss", "observed-at", "source-reference");

    private final GridParameterSnapshotService service;
    private final GridConfigurationSnapshotService configurationService;
    private final PrintStream out;
    private int exitCode = EXIT_OK;

    @Autowired
    public SyncProductCliRunner(
            GridParameterSnapshotService service,
            GridConfigurationSnapshotService configurationService) {
        this(service, configurationService, System.out);
    }

    SyncProductCliRunner(GridParameterSnapshotService service, PrintStream out) {
        this(service, null, out);
    }

    SyncProductCliRunner(GridParameterSnapshotService service,
                         GridConfigurationSnapshotService configurationService, PrintStream out) {
        this.service = service;
        this.configurationService = configurationService;
        this.out = out;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String[] raw = arguments.getSourceArgs();
        if (raw.length == 0 || !COMMAND.equals(raw[0])) return;
        exitCode = handle(raw);
    }

    @Override
    public int getExitCode() { return exitCode; }

    private int handle(String[] raw) {
        try {
            Parsed parsed = parse(raw);
            if ("grid-ui-config".equals(parsed.subcommand())) return recordUiConfiguration(parsed.options());
            if (!"grid-ai-params".equals(parsed.subcommand())) return usage();
            for (String name : parsed.options().keySet()) {
                if (!API_OPTIONS.contains(name)) throw new CliUsageException("不支持的参数: --" + name);
            }
            String instrument = require("instrument", parsed.options());
            GridDuration duration = parseDuration(require("duration", parsed.options()));
            CapturedGridParameter captured = service.capture(instrument, duration);
            print(captured);
            return EXIT_OK;
        } catch (CliUsageException | IllegalArgumentException invalid) {
            out.println("参数错误: " + bounded(invalid.getMessage()));
            return EXIT_ARGS;
        } catch (GridSourceException source) {
            out.println("来源失败: 类别=" + source.category()
                    + " providerCode=" + source.providerCode()
                    + " 可重试=" + source.retryable());
            return EXIT_SOURCE;
        } catch (RuntimeException internal) {
            out.println("内部错误: " + bounded(internal.getMessage()));
            return EXIT_INTERNAL;
        }
    }

    private int recordUiConfiguration(Map<String, String> options) {
        if (configurationService == null) throw new IllegalStateException("人工确认配置服务不可用");
        for (String name : options.keySet()) {
            if (!UI_OPTIONS.contains(name)) throw new CliUsageException("不支持的参数: --" + name);
        }
        for (String required : UI_OPTIONS) require(required, options);
        GridConfigurationSnapshot snapshot = new GridConfigurationSnapshot(
                "OKX", require("instrument", options), GridConfigurationSource.OKX_APP_MANUAL_CONFIRMATION,
                require("horizon", options), decimal("lower-price", options), decimal("upper-price", options),
                integer("grid-count", options), gridType(options.get("grid-type")),
                optionalRate("displayed-annualized-rate", options), optionalRate("displayed-min-per-grid-rate", options),
                optionalRate("displayed-max-per-grid-rate", options),
                bool("profit-reinvestment", options), bool("auto-earn", options),
                bool("trailing-up", options), bool("trailing-down", options),
                optionalPrice("take-profit", options), optionalPrice("stop-loss", options),
                require("source-reference", options), instant("observed-at", options));
        RecordedGridConfiguration recorded = configurationService.record(snapshot);
        print(recorded);
        return EXIT_OK;
    }

    private void print(CapturedGridParameter captured) {
        GridParameterSnapshot value = captured.snapshot();
        out.println("网格参数快照已保存: id=" + captured.id() + "（只读公共接口，不会创建策略）");
        out.println("标的=" + value.instrumentId() + "，OKX回测期限=" + value.duration().providerValue()
                + "，观察时间=" + value.observedAt());
        out.println("区间=" + plain(value.lowerPrice()) + " - " + plain(value.upperPrice()) + " USDT");
        out.println("网格数=" + value.gridCount() + "，模式=" + gridType(value));
        out.println("每格收益率=" + percent(value.minimumPerGridProfitRate())
                + " - " + percent(value.maximumPerGridProfitRate()));
        out.println("OKX展示年化=" + percent(value.annualizedRate())
                + "（外部历史回测报价，不是本系统收益预测）");
        out.println("来源=" + value.sourceEndpoint() + "，旧快照不会被覆盖");
    }

    private void print(RecordedGridConfiguration recorded) {
        GridConfigurationSnapshot value = recorded.snapshot();
        out.println("OKX App 人工确认网格配置已保存: config-id=" + recorded.id());
        out.println("来源=" + value.source() + "，推荐期限标签=" + value.recommendationHorizonLabel()
                + "（非精确天数），观察时间=" + value.observedAt());
        out.println("区间=" + plain(value.lowerPrice()) + " - " + plain(value.upperPrice())
                + " USDT，网格数=" + value.gridCount() + "，模式="
                + (value.gridType() == GridType.ARITHMETIC ? "等差" : "等比"));
        out.println("页面展示年化=" + value.displayedAnnualizedRate().map(SyncProductCliRunner::percent).orElse("未知")
                + "，页面单格收益率=" + value.displayedMinimumPerGridRate().map(SyncProductCliRunner::percent).orElse("未知")
                + " - " + value.displayedMaximumPerGridRate().map(SyncProductCliRunner::percent).orElse("未知")
                + "（仅证据，不参与建议）");
        out.println("利润复投=" + onOff(value.profitReinvestmentEnabled())
                + "，自动赚币=" + onOff(value.autoEarnEnabled())
                + "，上移=" + onOff(value.trailingUpEnabled())
                + "，下移=" + onOff(value.trailingDownEnabled()));
        out.println("止盈=" + value.takeProfitPrice().map(SyncProductCliRunner::plain).orElse("关闭")
                + "，止损=" + value.stopLossPrice().map(SyncProductCliRunner::plain).orElse("关闭"));
        out.println("该配置仅供回测和人工核对，不会创建 OKX 策略");
    }

    private static String gridType(GridParameterSnapshot value) {
        return switch (value.gridType()) {
            case ARITHMETIC -> "等差";
            case GEOMETRIC -> "等比";
        };
    }

    private int usage() {
        out.println("用法:");
        out.println("  sync-product grid-ai-params --instrument BTC-USDT --duration <7D|30D|180D>");
        out.println("  sync-product grid-ui-config --instrument BTC-USDT --horizon '3-6个月' --lower-price <价格>");
        out.println("    --upper-price <价格> --grid-count <数量> --grid-type arithmetic");
        out.println("    --displayed-annualized-rate <小数|unknown> --displayed-min-per-grid-rate <小数|unknown>");
        out.println("    --displayed-max-per-grid-rate <小数|unknown>");
        out.println("    --profit-reinvestment <true|false> --auto-earn <true|false>");
        out.println("    --trailing-up <true|false> --trailing-down <true|false>");
        out.println("    --take-profit <disabled|价格> --stop-loss <disabled|价格>");
        out.println("    --observed-at <UTC时间> --source-reference <截图或人工记录说明>");
        return EXIT_ARGS;
    }

    private static BigDecimal decimal(String name, Map<String, String> options) {
        try { return new BigDecimal(require(name, options)); }
        catch (NumberFormatException invalid) { throw new CliUsageException(name + " 不是合法数字"); }
    }

    private static int integer(String name, Map<String, String> options) {
        try { return Integer.parseInt(require(name, options)); }
        catch (NumberFormatException invalid) { throw new CliUsageException(name + " 不是合法整数"); }
    }

    private static boolean bool(String name, Map<String, String> options) {
        String value = require(name, options);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new CliUsageException(name + " 必须是 true 或 false");
    }

    private static Optional<BigDecimal> optionalPrice(String name, Map<String, String> options) {
        String value = require(name, options);
        if ("disabled".equalsIgnoreCase(value)) return Optional.empty();
        return Optional.of(decimal(name, options));
    }

    private static Optional<BigDecimal> optionalRate(String name, Map<String, String> options) {
        String value = require(name, options);
        if ("unknown".equalsIgnoreCase(value)) return Optional.empty();
        BigDecimal rate = decimal(name, options);
        if (rate.signum() < 0) throw new CliUsageException(name + " 不能为负数");
        return Optional.of(rate);
    }

    private static Instant instant(String name, Map<String, String> options) {
        try { return Instant.parse(require(name, options)); }
        catch (DateTimeParseException invalid) { throw new CliUsageException(name + " 不是 UTC ISO-8601 时间"); }
    }

    private static GridType gridType(String value) {
        return switch (value.toLowerCase()) {
            case "arithmetic" -> GridType.ARITHMETIC;
            case "geometric" -> GridType.GEOMETRIC;
            default -> throw new CliUsageException("grid-type 必须是 arithmetic 或 geometric");
        };
    }

    private static String onOff(boolean value) { return value ? "开启" : "关闭"; }

    private static GridDuration parseDuration(String raw) {
        return switch (raw.toUpperCase()) {
            case "7D" -> GridDuration.D7;
            case "30D" -> GridDuration.D30;
            case "180D" -> GridDuration.D180;
            default -> throw new CliUsageException("duration 只允许 7D、30D、180D");
        };
    }

    private static String require(String name, Map<String, String> options) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new CliUsageException("缺少必填参数: --" + name);
        return value;
    }

    private static Parsed parse(String[] raw) {
        if (raw.length < 2) throw new CliUsageException("缺少子命令");
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 2; i < raw.length; i++) {
            String token = raw[i];
            if (!token.startsWith("--")) throw new CliUsageException("无法识别参数: " + token);
            String name = token.substring(2);
            String value = null;
            int separator = name.indexOf('=');
            if (separator >= 0) {
                value = name.substring(separator + 1);
                name = name.substring(0, separator);
            }
            if (value == null) {
                if (++i >= raw.length) throw new CliUsageException("参数 --" + name + " 缺少值");
                value = raw[i];
            }
            if (options.putIfAbsent(name, value) != null) throw new CliUsageException("参数 --" + name + " 重复提供");
        }
        return new Parsed(raw[1], options);
    }

    private static String percent(BigDecimal value) {
        return value.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String plain(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) return "未知错误";
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private static final class CliUsageException extends RuntimeException {
        CliUsageException(String message) { super(message); }
    }

    private record Parsed(String subcommand, Map<String, String> options) {}
}
