package app.cryptoincome.cli;

import app.cryptoincome.assessment.application.PhaseOneAssessmentService;
import app.cryptoincome.assessment.domain.OpeningCost;
import app.cryptoincome.assessment.domain.PhaseOneAssessment;
import app.cryptoincome.assessment.domain.PhaseOneAssessmentRequest;
import app.cryptoincome.assessment.domain.Recommendation;
import app.cryptoincome.assessment.domain.SimpleEarnQuote;
import app.cryptoincome.assessment.domain.SmartArbitrageCosts;
import app.cryptoincome.assessment.domain.TerminalPolicy;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 只读收益评估入口；建议来自 PhaseOneAssessment，CLI 不重新判断。 */
@Component
public class AssessIncomeCliRunner implements ApplicationRunner, ExitCodeGenerator {

    public static final String COMMAND = "assess-income";
    public static final int EXIT_OK = 0;
    public static final int EXIT_INTERNAL = 1;
    public static final int EXIT_ARGS = 2;

    private static final Set<String> OPTIONS = Set.of(
            "as-of", "from", "to", "capital", "simple-apr", "simple-quote-as-of",
            "simple-source", "simple-terms-confirmed", "hedged-notional", "cost-as-of", "cost-source", "opening-cost",
            "opening-spread-cost", "spot-entry-fee", "perp-entry-fee",
            "exit-cost", "slippage-cost", "rebalance-cost", "service-cost", "terminal-policy");
    private static final Set<String> REQUIRED = Set.of(
            "as-of", "from", "to", "capital", "simple-apr", "simple-quote-as-of",
            "simple-source", "simple-terms-confirmed", "hedged-notional", "cost-as-of", "cost-source", "terminal-policy");

    private final PhaseOneAssessmentService assessmentService;
    private final PrintStream out;
    private int exitCode = EXIT_OK;

    @Autowired
    public AssessIncomeCliRunner(PhaseOneAssessmentService assessmentService) {
        this(assessmentService, System.out);
    }

    AssessIncomeCliRunner(PhaseOneAssessmentService assessmentService, PrintStream out) {
        this.assessmentService = assessmentService;
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
            if (parsed.positional().size() != 2 || !"phase-one".equals(parsed.positional().get(1))) {
                return usage();
            }
            requireOptions(parsed.options());
            PhaseOneAssessment result = assessmentService.assess(toRequest(parsed.options()));
            print(result);
            return EXIT_OK;
        } catch (CliUsageException | IllegalArgumentException invalid) {
            out.println("参数错误: " + bounded(invalid.getMessage()));
            return EXIT_ARGS;
        } catch (RuntimeException internal) {
            out.println("内部错误: " + bounded(internal.getMessage()));
            return EXIT_INTERNAL;
        }
    }

    private static PhaseOneAssessmentRequest toRequest(Map<String, String> options) {
        Instant asOf = parseInstant("as-of", options.get("as-of"));
        SmartArbitrageCosts costs = new SmartArbitrageCosts(
                parseInstant("cost-as-of", options.get("cost-as-of")),
                requireText("cost-source", options.get("cost-source")),
                parseOpeningCost(options),
                optionalMoney("exit-cost", options),
                optionalMoney("slippage-cost", options),
                optionalMoney("rebalance-cost", options),
                optionalMoney("service-cost", options));
        return new PhaseOneAssessmentRequest(
                asOf,
                parseInstant("from", options.get("from")),
                parseInstant("to", options.get("to")),
                money("capital", options.get("capital")),
                new SimpleEarnQuote(
                        rate("simple-apr", options.get("simple-apr")),
                        90,
                        parseInstant("simple-quote-as-of", options.get("simple-quote-as-of")),
                        requireText("simple-source", options.get("simple-source")),
                        parseBoolean("simple-terms-confirmed", options.get("simple-terms-confirmed"))),
                money("hedged-notional", options.get("hedged-notional")),
                costs,
                parseTerminalPolicy(options.get("terminal-policy")));
    }

    private static Optional<OpeningCost> parseOpeningCost(Map<String, String> options) {
        boolean aggregate = options.containsKey("opening-cost");
        boolean anyComponent = options.containsKey("opening-spread-cost")
                || options.containsKey("spot-entry-fee")
                || options.containsKey("perp-entry-fee");
        if (aggregate && anyComponent) {
            throw new CliUsageException("开仓聚合成本与开仓成本明细不能同时提供");
        }
        if (aggregate) {
            return Optional.of(new OpeningCost.Aggregate(money("opening-cost", options.get("opening-cost"))));
        }
        if (!anyComponent) {
            return Optional.empty();
        }
        for (String required : List.of("opening-spread-cost", "spot-entry-fee", "perp-entry-fee")) {
            if (!options.containsKey(required)) {
                throw new CliUsageException("开仓成本明细必须同时提供 --opening-spread-cost、--spot-entry-fee、--perp-entry-fee");
            }
        }
        return Optional.of(new OpeningCost.Components(
                money("opening-spread-cost", options.get("opening-spread-cost")),
                money("spot-entry-fee", options.get("spot-entry-fee")),
                money("perp-entry-fee", options.get("perp-entry-fee"))));
    }

    private void print(PhaseOneAssessment result) {
        out.println("第一期收益评估（只读，不会创建 OKX 策略）");
        out.println("窗口=" + result.request().from() + " 至 " + result.request().to());
        out.println("候选资金=" + money(result.request().candidateCapital()) + " USDT");
        out.println("简单赚币报价来源=" + result.request().simpleEarnQuote().source()
                + "，时间=" + result.request().simpleEarnQuote().asOf());
        out.println("成本输入来源=" + result.request().costs().source()
                + "，时间=" + result.request().costs().asOf());
        out.println("简单赚币90天收益=" + money(result.simpleEarnIncome()) + " USDT");
        out.println("历史资金费结算=" + result.fundingHistory().settlementCount()
                + " 次（正=" + result.fundingHistory().positiveSettlements()
                + " 负=" + result.fundingHistory().negativeSettlements() + "）");
        out.println("资金费持有收益率=" + percent(result.fundingHistory().holdingReturn())
                + "，按对冲名义估算收入=" + money(result.fundingHistory().fundingIncome()) + " USDT");
        out.println("已知成本=" + money(result.knownCost()) + " USDT");
        out.println("资金费率净收益=" + result.estimatedFundingOnlyNetIncome()
                .map(value -> money(value) + " USDT").orElse("无法精确计算"));
        out.println("相对简单赚币增量=" + result.incrementalIncomeVsSimpleEarn()
                .map(value -> money(value) + " USDT").orElse("无法精确计算"));
        if (!result.unknowns().isEmpty()) {
            out.println("未知项=" + String.join("、", result.unknowns()));
        }
        if (!result.blockers().isEmpty()) {
            out.println("阻断项=" + String.join("、", result.blockers()));
        }
        out.println("证据状态=" + result.evidenceStatus());
        out.println("建议=" + recommendation(result.recommendation()));
        result.maximumFirstLiveCapital().ifPresent(
                value -> out.println("首次实盘上限=" + money(value) + " USDT"));
        for (String reason : result.reasons()) {
            out.println("原因: " + reason);
        }
        if (!result.manualOkxChecklist().isEmpty()) {
            out.println("OKX 人工核对:");
            for (int i = 0; i < result.manualOkxChecklist().size(); i++) {
                out.println("  " + (i + 1) + ". " + result.manualOkxChecklist().get(i));
            }
        }
        out.println("限制: 当前是历史持有期情景分析，区间完整性尚未证明，也不代表未来90天收益。");
    }

    private static String recommendation(Recommendation recommendation) {
        return switch (recommendation) {
            case INSUFFICIENT_EVIDENCE -> "证据不足";
            case PREFER_SIMPLE_EARN -> "优先简单赚币";
            case WATCH_SMART_ARBITRAGE -> "继续观察智能套利";
            case MICRO_VALIDATE_ARBITRAGE -> "仅小额验证智能套利";
        };
    }

    private int usage() {
        out.println("""
                用法:
                  assess-income phase-one --as-of <UTC时间> --from <UTC时间> --to <UTC时间>
                    --capital <USDT> --simple-apr <小数,如0.035> --simple-quote-as-of <UTC时间>
                    --simple-source <来源> --simple-terms-confirmed <true|false> --hedged-notional <USDT>
                    --cost-as-of <UTC时间> --cost-source <来源>
                    [--opening-cost <USDT> | --opening-spread-cost <USDT> --spot-entry-fee <USDT> --perp-entry-fee <USDT>]
                    [--exit-cost <USDT>] [--slippage-cost <USDT>] [--rebalance-cost <USDT>] [--service-cost <USDT>]
                    --terminal-policy <exit-to-usdt|keep-strategy-open>""");
        return EXIT_ARGS;
    }

    private static void requireOptions(Map<String, String> options) {
        for (String name : options.keySet()) {
            if (!OPTIONS.contains(name)) throw new CliUsageException("不支持的参数: --" + name);
        }
        for (String name : REQUIRED) {
            if (!options.containsKey(name)) throw new CliUsageException("缺少必填参数: --" + name);
        }
    }

    private static Parsed parse(String[] raw) {
        Map<String, String> options = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String token = raw[i];
            if (!token.startsWith("--")) {
                positional.add(token);
                continue;
            }
            String name = token.substring(2);
            String value = null;
            int separator = name.indexOf('=');
            if (separator >= 0) {
                value = name.substring(separator + 1);
                name = name.substring(0, separator);
            }
            if (value == null) {
                if (i + 1 >= raw.length) throw new CliUsageException("参数 --" + name + " 缺少值");
                value = raw[++i];
            }
            if (options.putIfAbsent(name, value) != null) {
                throw new CliUsageException("参数 --" + name + " 重复提供");
            }
        }
        return new Parsed(positional, options);
    }

    private static Instant parseInstant(String name, String raw) {
        try {
            Instant value = Instant.parse(raw);
            if (value.getNano() % 1_000_000 != 0) {
                throw new CliUsageException(name + " 精度不能低于 1 毫秒");
            }
            return value;
        } catch (DateTimeParseException | NullPointerException invalid) {
            throw new CliUsageException(name + " 不是 UTC ISO-8601 时间: " + raw);
        }
    }

    private static BigDecimal money(String name, String raw) {
        BigDecimal value = decimal(name, raw);
        if (value.signum() < 0) throw new CliUsageException(name + " 不能为负数");
        return value;
    }

    private static BigDecimal rate(String name, String raw) {
        BigDecimal value = decimal(name, raw);
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new CliUsageException(name + " 必须是 0 到 1 之间的小数");
        }
        return value;
    }

    private static BigDecimal decimal(String name, String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException | NullPointerException invalid) {
            throw new CliUsageException(name + " 不是合法数字: " + raw);
        }
    }

    private static Optional<BigDecimal> optionalMoney(String name, Map<String, String> options) {
        return Optional.ofNullable(options.get(name)).map(value -> money(name, value));
    }

    private static boolean parseBoolean(String name, String raw) {
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        throw new CliUsageException(name + " 必须是 true 或 false");
    }

    private static String requireText(String name, String raw) {
        if (raw == null || raw.isBlank()) throw new CliUsageException(name + " 不能为空");
        return raw;
    }

    private static TerminalPolicy parseTerminalPolicy(String raw) {
        return switch (raw == null ? "" : raw.toLowerCase()) {
            case "exit-to-usdt" -> TerminalPolicy.EXIT_TO_USDT;
            case "keep-strategy-open" -> TerminalPolicy.KEEP_STRATEGY_OPEN;
            default -> throw new CliUsageException("terminal-policy 必须是 exit-to-usdt 或 keep-strategy-open");
        };
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percent(BigDecimal value) {
        return value.multiply(new BigDecimal("100")).setScale(6, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String bounded(String message) {
        if (message == null || message.isBlank()) return "未知错误";
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
