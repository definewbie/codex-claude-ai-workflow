package app.cryptoincome.shadow.application;

import app.cryptoincome.grid.application.FixedGridBacktester;
import app.cryptoincome.grid.application.GridConfigurationSnapshotQuery;
import app.cryptoincome.grid.application.SpotCandleQuery;
import app.cryptoincome.grid.domain.*;
import app.cryptoincome.shadow.*;
import app.cryptoincome.reporting.ReturnMetrics;
import app.cryptoincome.reporting.ReturnMetricsCalculator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class GridShadowStudyService {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal BPS = new BigDecimal("10000");
    private final GridShadowStudyRepository repository;
    private final GridConfigurationSnapshotQuery configurationQuery;
    private final SpotCandleQuery candleQuery;
    private final FixedGridBacktester backtester;
    private final Clock clock;

    @Autowired
    public GridShadowStudyService(GridShadowStudyRepository repository,
                                  GridConfigurationSnapshotQuery configurationQuery,
                                  SpotCandleQuery candleQuery,
                                  FixedGridBacktester backtester) {
        this(repository, configurationQuery, candleQuery, backtester, Clock.systemUTC());
    }

    GridShadowStudyService(GridShadowStudyRepository repository,
                           GridConfigurationSnapshotQuery configurationQuery,
                           SpotCandleQuery candleQuery,
                           FixedGridBacktester backtester, Clock clock) {
        this.repository = repository;
        this.configurationQuery = configurationQuery;
        this.candleQuery = candleQuery;
        this.backtester = backtester;
        this.clock = clock;
    }

    public GridShadowView create(GridShadowCreateCommand command) {
        requireCreateCommand(command);
        GridConfigurationSnapshot configuration = configurationQuery
                .findByIdAsOf(command.configurationSnapshotId(), command.evidenceAsOf())
                .orElseThrow(() -> new IllegalArgumentException("证据时点之前不存在指定人工确认配置"));
        requireSupportedBaseline(configuration);
        GridScenarioParameters baseline = GridScenarioParameters.confirmed(configuration);
        List<SpotCandle> prior = candleQuery.findCompletedAsOf("OKX", "BTC-USDT", CandleBar.M1,
                command.startAt().minusSeconds(60), command.startAt(), command.evidenceAsOf());
        if (prior.size() != 1 || !prior.getFirst().openTime().plusSeconds(60).equals(command.startAt())) {
            throw new IllegalArgumentException("缺少 Shadow 开始前一根已完成分钟 K 线，不能确定入场参考价");
        }
        GridShadowStudy definition = new GridShadowStudy(null, ShadowStudyState.ACTIVE,
                command.configurationSnapshotId(), command.startAt(), command.targetDays(), command.capital(),
                command.simpleEarnApr(), command.makerFeeRate(), command.takerFeeRate(), command.exitSlippageBps(),
                command.maximumDrawdownRate(), command.maximumPathDivergenceRate(),
                command.maximumEndingBtcValueRatio(), command.candidate(), baseline, prior.getFirst().close(),
                command.evidenceAsOf(), command.evidenceReference(), clock.instant(), null);
        long id = repository.create(definition);
        return get(id);
    }

    public GridShadowView refresh(long id, Instant valuationAt, Instant evaluationAsOf) {
        GridShadowStudy study = requireStudy(id);
        if (study.state() != ShadowStudyState.ACTIVE) {
            throw new IllegalStateException("已停止的 Shadow 观察不能刷新或追加事实");
        }
        requireMinute(valuationAt, "valuationAt");
        if (evaluationAsOf == null || evaluationAsOf.isBefore(valuationAt)) {
            throw new IllegalArgumentException("evaluationAsOf 不能早于 valuationAt");
        }
        if (!valuationAt.isAfter(study.startAt())) {
            throw new IllegalArgumentException("valuationAt 必须晚于 Shadow 开始时间");
        }
        if (Duration.between(study.startAt(), valuationAt).toSeconds() % 86_400 != 0) {
            throw new IllegalArgumentException("Shadow 每日估值必须与开始时间保持相同的 UTC+8 时刻");
        }
        Instant targetEnd = study.startAt().plus(Duration.ofDays(study.targetDays()));
        Instant effectiveAt = valuationAt.isAfter(targetEnd) ? targetEnd : valuationAt;
        List<SpotCandle> candles = candleQuery.findCompletedAsOf("OKX", "BTC-USDT", CandleBar.M1,
                study.startAt(), effectiveAt, evaluationAsOf);
        long expected = Duration.between(study.startAt(), effectiveAt).toMinutes();
        if (candles.size() != expected || candles.isEmpty()
                || !candles.getFirst().openTime().equals(study.startAt())
                || !candles.getLast().openTime().plusSeconds(60).equals(effectiveAt)) {
            return view(study, repository.findFacts(id));
        }
        GridShadowDailyFact candidate = evaluate(study, ShadowVariantRole.CANDIDATE, study.candidate(), candles, effectiveAt);
        GridShadowDailyFact baseline = evaluate(study, ShadowVariantRole.BASELINE, study.baseline(), candles, effectiveAt);
        repository.appendFacts(List.of(candidate, baseline));
        return view(study, repository.findFacts(id));
    }

    public GridShadowView get(long id) {
        GridShadowStudy study = requireStudy(id);
        return view(study, repository.findFacts(id));
    }

    public List<GridShadowSummary> list() {
        return repository.findAll().stream().map(study -> {
            GridShadowView view = view(study, repository.findFacts(study.id()));
            return new GridShadowSummary(study.id(), study.state(), view.status(), study.startAt(),
                    study.targetDays(), view.observedMinutes(), study.candidate(), study.baseline(), study.createdAt());
        }).toList();
    }

    public GridShadowView stop(long id, Instant stoppedAt) {
        GridShadowStudy study = requireStudy(id);
        if (stoppedAt == null || stoppedAt.isBefore(study.startAt())) {
            throw new IllegalArgumentException("停止时间不能早于 Shadow 开始时间");
        }
        if (!repository.stop(id, stoppedAt)) throw new IllegalStateException("Shadow 已停止，不能重复停止");
        return get(id);
    }

    private GridShadowDailyFact evaluate(GridShadowStudy study, ShadowVariantRole role,
                                         GridScenarioParameters scenario, List<SpotCandle> candles,
                                         Instant valuationAt) {
        DualPathGridBacktest result = backtester.backtest(new GridBacktestRequest(
                study.capital(), scenario.lowerPrice(), scenario.upperPrice(), scenario.gridCount(),
                study.makerFeeRate(), study.takerFeeRate(), study.exitSlippageBps(),
                scenario.takeProfitPrice(), scenario.stopLossPrice()), candles);
        GridPathResult conservative = result.pathLowThenHigh().totalIncome()
                .compareTo(result.pathHighThenLow().totalIncome()) <= 0
                ? result.pathLowThenHigh() : result.pathHighThenLow();
        BigDecimal simple = max(simpleIncome(study, result.pathLowThenHigh().exitTime()),
                simpleIncome(study, result.pathHighThenLow().exitTime()));
        BigDecimal buyHold = max(buyHoldIncome(study, result.initialPrice(), result.pathLowThenHigh().exitTriggerPrice()),
                buyHoldIncome(study, result.initialPrice(), result.pathHighThenLow().exitTriggerPrice()));
        BigDecimal divergence = result.pathLowThenHigh().totalIncome().subtract(result.pathHighThenLow().totalIncome())
                .abs().divide(study.capital(), MC);
        return new GridShadowDailyFact(null, study.id(), role, valuationAt, candles.size(),
                conservative.totalIncome(), simple, buyHold, conservative.maximumDrawdownRate(), divergence,
                conservative.endingBtcValueRatioBeforeExit(), conservative.completedCycles(),
                conservative.totalFees(), conservative.exitReason());
    }

    private GridShadowView view(GridShadowStudy study, List<GridShadowDailyFact> facts) {
        GridShadowDailyFact candidate = latest(facts, ShadowVariantRole.CANDIDATE);
        GridShadowDailyFact baseline = latest(facts, ShadowVariantRole.BASELINE);
        long minutes = candidate == null ? 0 : Duration.between(study.startAt(), candidate.valuationAt()).toMinutes();
        GridShadowStatus status = study.state() == ShadowStudyState.STOPPED ? GridShadowStatus.STOPPED
                : candidate == null ? GridShadowStatus.WAITING_FOR_DATA
                : minutes >= Duration.ofDays(study.targetDays()).toMinutes()
                ? GridShadowStatus.TARGET_REACHED : GridShadowStatus.OBSERVING;
        Duration observed = minutes == 0 ? null : Duration.ofMinutes(minutes);
        ReturnMetrics candidateReturns = returns(candidate == null ? null : candidate.netIncome(), study, observed);
        ReturnMetrics baselineReturns = returns(baseline == null ? null : baseline.netIncome(), study, observed);
        ReturnMetrics simpleReturns = returns(candidate == null ? null : candidate.simpleEarnIncome(), study, observed);
        ReturnMetrics buyHoldReturns = returns(candidate == null ? null : candidate.buyHoldIncome(), study, observed);
        return new GridShadowView(study, status, minutes, facts, candidate, baseline,
                candidateReturns, baselineReturns, simpleReturns, buyHoldReturns,
                report(study, status, candidate, baseline));
    }

    private static ReturnMetrics returns(BigDecimal income, GridShadowStudy study, Duration observed) {
        return income == null || observed == null ? null
                : ReturnMetricsCalculator.calculate(income, study.capital(), observed, 7);
    }

    private static GridShadowDailyReport report(GridShadowStudy study, GridShadowStatus status,
                                                GridShadowDailyFact candidate, GridShadowDailyFact baseline) {
        List<String> boundaries = List.of("Shadow 不创建或修改 OKX 策略", "样本外结果不代表未来收益", "实盘决策仍需用户确认");
        if (status == GridShadowStatus.WAITING_FOR_DATA) return new GridShadowDailyReport(
                "等待样本外分钟 K 线", "KEEP_FROZEN", "保持候选与基准参数不变。",
                "SHADOW_ONLY", "真实仓位不变。", "NO_CHANGE", "产品组合不变。",
                "K 线入库后刷新", boundaries);
        if (status == GridShadowStatus.STOPPED) return new GridShadowDailyReport(
                "Shadow 观察已停止", "READ_ONLY", "历史参数和事实仅供复盘，不能恢复。",
                "NO_CHANGE", "不会影响真实仓位。", "NO_CHANGE", "不会影响真实产品组合。",
                "如需新参数，创建新的 Shadow 观察", boundaries);
        if (status == GridShadowStatus.OBSERVING) return new GridShadowDailyReport(
                "样本外观察中，今天不调参", "KEEP_FROZEN", "7–14 天窗口完成前禁止追随短期行情改参数。",
                "SHADOW_ONLY", "只观察虚拟资金，真实仓位增量为 0。", "NO_CHANGE", "简单赚币与现有组合保持不变。",
                "下一批完整分钟 K 线入库后刷新", boundaries);
        boolean pass = candidate != null && baseline != null
                && candidate.netIncome().compareTo(baseline.netIncome()) > 0
                && candidate.netIncome().compareTo(candidate.simpleEarnIncome()) > 0
                && candidate.netIncome().compareTo(candidate.buyHoldIncome()) > 0
                && candidate.maximumDrawdownRate().compareTo(study.maximumDrawdownRate()) <= 0
                && candidate.pathDivergenceRate().compareTo(study.maximumPathDivergenceRate()) <= 0
                && candidate.endingBtcValueRatio().compareTo(study.maximumEndingBtcValueRatio()) <= 0;
        return pass ? new GridShadowDailyReport(
                "候选在样本外胜出，可考虑小额人工验证", "KEEP_FROZEN", "保留 80%/30 格，不再回看同一窗口寻优。",
                "MANUAL_MICRO_ONLY", "如决定验证，首次人工实盘不超过 5,000 USDT。",
                "REVIEW", "是否从流动 USDT 划出小额验证资金，由用户决定。",
                "人工核对 OKX 确认页后再决定", boundaries)
                : new GridShadowDailyReport(
                "候选未通过样本外门槛", "REJECT_CANDIDATE", "不继续用同一窗口调参挽救候选。",
                "NO_INCREASE", "网格新增真实仓位为 0。", "KEEP_BASE", "保持简单赚币与流动 USDT 核心。",
                "形成全新市场样本后再提出新候选", boundaries);
    }

    private GridShadowStudy requireStudy(long id) {
        return repository.find(id).orElseThrow(() -> new IllegalArgumentException("Shadow 观察不存在"));
    }

    private static GridShadowDailyFact latest(List<GridShadowDailyFact> facts, ShadowVariantRole role) {
        return facts.stream().filter(f -> f.role() == role)
                .max(Comparator.comparing(GridShadowDailyFact::valuationAt)).orElse(null);
    }

    private static void requireCreateCommand(GridShadowCreateCommand command) {
        if (command == null || command.candidate() == null || command.startAt() == null
                || command.evidenceAsOf() == null || command.evidenceReference() == null
                || command.evidenceReference().isBlank()) throw new IllegalArgumentException("Shadow创建参数不能为空");
        requireMinute(command.startAt(), "startAt");
        if (command.evidenceAsOf().isBefore(command.startAt())) {
            throw new IllegalArgumentException("evidenceAsOf 不能早于 startAt");
        }
    }

    private static void requireSupportedBaseline(GridConfigurationSnapshot configuration) {
        if (!"OKX".equals(configuration.exchange()) || !"BTC-USDT".equals(configuration.instrumentId())
                || configuration.gridType() != GridType.ARITHMETIC) {
            throw new IllegalArgumentException("Shadow 第一期仅支持 OKX BTC-USDT 等差现货网格");
        }
        if (configuration.profitReinvestmentEnabled() || configuration.autoEarnEnabled()
                || configuration.trailingUpEnabled() || configuration.trailingDownEnabled()
                || configuration.takeProfitPrice().isPresent() || configuration.stopLossPrice().isPresent()) {
            throw new IllegalArgumentException("基准必须是复投、自动赚币、移动网格、止盈止损全部关闭的固定网格");
        }
    }

    private static void requireMinute(Instant value, String name) {
        if (value == null || value.getNano() != 0 || value.getEpochSecond() % 60 != 0) {
            throw new IllegalArgumentException(name + " 必须对齐整分钟");
        }
    }

    private static BigDecimal simpleIncome(GridShadowStudy study, Instant exitAt) {
        BigDecimal days = new BigDecimal(Duration.between(study.startAt(), exitAt).toSeconds())
                .divide(new BigDecimal("86400"), MC);
        return study.capital().multiply(study.simpleEarnApr(), MC).multiply(days, MC).divide(DAYS_PER_YEAR, MC);
    }

    private static BigDecimal buyHoldIncome(GridShadowStudy study, BigDecimal entry, BigDecimal terminal) {
        BigDecimal btc = study.capital().divide(entry.multiply(BigDecimal.ONE.add(study.takerFeeRate()), MC), MC);
        BigDecimal exit = terminal.multiply(BigDecimal.ONE.subtract(study.exitSlippageBps().divide(BPS, MC)), MC);
        return btc.multiply(exit, MC).multiply(BigDecimal.ONE.subtract(study.takerFeeRate()), MC).subtract(study.capital());
    }

    private static BigDecimal max(BigDecimal left, BigDecimal right) { return left.max(right); }
}
