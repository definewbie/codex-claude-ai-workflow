package app.cryptoincome.web;

import app.cryptoincome.assessment.application.PhaseOneAssessmentService;
import app.cryptoincome.assessment.application.SmartArbitrageAllocationCalculator;
import app.cryptoincome.assessment.domain.*;
import app.cryptoincome.grid.application.FixedGridAssessmentService;
import app.cryptoincome.grid.domain.*;
import app.cryptoincome.grid.sync.CandleSyncExecution;
import app.cryptoincome.grid.sync.CandleSyncExecutionService;
import app.cryptoincome.income.SimpleEarnIncomeService;
import app.cryptoincome.income.SimpleEarnSourceException;
import app.cryptoincome.portfolio.application.PhaseOnePortfolioRecommendationService;
import app.cryptoincome.portfolio.domain.PortfolioRecommendation;
import app.cryptoincome.shadow.application.*;
import app.cryptoincome.reporting.ReturnMetrics;
import app.cryptoincome.reporting.ReturnMetricsCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/workbench")
public class DecisionWorkbenchController {
    private final FixedGridAssessmentService assessmentService;
    private final GridShadowStudyService shadowService;
    private final CandleSyncExecutionService candleSyncService;
    private final PhaseOneAssessmentService smartArbitrageService;
    private final SimpleEarnIncomeService simpleEarnIncomeService;
    private final SmartArbitrageAllocationCalculator smartAllocationCalculator;
    private final PhaseOnePortfolioRecommendationService portfolioRecommendationService;

    public DecisionWorkbenchController(FixedGridAssessmentService assessmentService,
                                       GridShadowStudyService shadowService,
                                       CandleSyncExecutionService candleSyncService,
                                       PhaseOneAssessmentService smartArbitrageService,
                                       SimpleEarnIncomeService simpleEarnIncomeService,
                                       SmartArbitrageAllocationCalculator smartAllocationCalculator,
                                       PhaseOnePortfolioRecommendationService portfolioRecommendationService) {
        this.assessmentService = assessmentService;
        this.shadowService = shadowService;
        this.candleSyncService = candleSyncService;
        this.smartArbitrageService = smartArbitrageService;
        this.simpleEarnIncomeService = simpleEarnIncomeService;
        this.smartAllocationCalculator = smartAllocationCalculator;
        this.portfolioRecommendationService = portfolioRecommendationService;
    }

    @GetMapping("/capabilities")
    public CapabilityView capabilities() {
        return new CapabilityView(List.of(
                new ModeCapability("BACKTEST", "可用", true,
                        "使用数据库中的90天分钟K线，支持区间、网格数和止盈止损情景。"),
                new ModeCapability("SHADOW", "可用", true,
                        "冻结候选30格与已确认50格基准，并行记录7–14天样本外每日事实。"),
                new ModeCapability("LIVE", "等待账户事实", false,
                        "尚未接入只读账户或人工持仓快照，不能计算真实收益或建议实际调仓。")),
                List.of("页面不调用OKX交易接口", "AI不能修改金额或建议", "所有时间以UTC+8展示"));
    }

    @PostMapping("/shadows/grid-pairs")
    public GridShadowView createShadow(@RequestBody GridShadowCreateRequest input) {
        return shadowService.create(new GridShadowCreateCommand(
                input.configurationSnapshotId(), input.startAt(), input.targetDays(), input.capital(),
                input.simpleEarnApr(), input.makerFeeRate(), input.takerFeeRate(), input.exitSlippageBps(),
                input.maximumDrawdownRate(), input.maximumPathDivergenceRate(), input.maximumEndingBtcValueRatio(),
                new GridScenarioParameters(input.candidateLowerPrice(), input.candidateUpperPrice(),
                        input.candidateGridCount(), Optional.ofNullable(input.candidateTakeProfitPrice()),
                        Optional.ofNullable(input.candidateStopLossPrice())),
                input.evidenceAsOf(), input.evidenceReference()));
    }

    @GetMapping("/shadows/{id}")
    public GridShadowView getShadow(@PathVariable long id) {
        return shadowService.get(id);
    }

    @GetMapping("/shadows")
    public List<GridShadowSummary> listShadows() {
        return shadowService.list();
    }

    @PostMapping("/shadows/{id}/refresh")
    public GridShadowView refreshShadow(@PathVariable long id, @RequestBody ShadowRefreshRequest input) {
        return shadowService.refresh(id, input.valuationAt(), input.evaluationAsOf());
    }

    @PostMapping("/candles/sync")
    public CandleSyncView syncCandles(@RequestBody CandleSyncRequest input) {
        if (input == null) throw new IllegalArgumentException("K线同步请求不能为空");
        return candleSyncView(candleSyncService.start(input.to()));
    }

    @GetMapping("/candles/sync/{id}")
    public CandleSyncView candleSync(@PathVariable long id) {
        return candleSyncView(candleSyncService.get(id));
    }

    private static CandleSyncView candleSyncView(CandleSyncExecution execution) {
        return new CandleSyncView(execution.id(), execution.state().name(), execution.instrumentId(), execution.bar(),
                execution.from(), execution.to(), execution.startedAt(), execution.completedAt(), execution.updatedAt(),
                execution.coverage(), Math.toIntExact(Duration.ofDays(90).toMinutes()), execution.pages(),
                execution.received(), execution.completedInWindow(), execution.newFacts(), execution.duplicateFacts(),
                execution.cursor(), execution.failureCategory(), execution.failureMessage());
    }

    @PostMapping("/shadows/{id}/stop")
    public GridShadowView stopShadow(@PathVariable long id, @RequestBody ShadowStopRequest input) {
        return shadowService.stop(id, input.stoppedAt());
    }

    @PostMapping("/backtests/grid")
    public GridEvaluationView evaluateGrid(@RequestBody GridEvaluationRequest input) {
        portfolioRecommendationService.validatePlan(
                input.portfolioCapital(), input.capital(), input.portfolioFixedEarnRatio(),
                input.portfolioLiquidRatio(), input.portfolioSmartRatio(), input.portfolioGridRatio());
        SmartArbitrageAllocation smartAllocation = smartAllocationCalculator.calculate(
                input.capital(), input.hedgedNotional(), input.smartReferenceSpotPrice(),
                input.smartReservedMargin());
        FixedGridAssessmentRequest request = new FixedGridAssessmentRequest(
                input.evaluationAsOf(), input.from(), input.to(), input.configurationSnapshotId(),
                input.capital(), input.simpleEarnApr(), input.makerFeeRate(), input.takerFeeRate(),
                input.exitSlippageBps(), input.maximumDrawdownRate(),
                input.maximumPathDivergenceRate(), input.maximumEndingBtcValueRatio());
        FixedGridAssessment assessment = assessmentService.assessScenario(request,
                new GridScenarioParameters(input.lowerPrice(), input.upperPrice(), input.gridCount(),
                        Optional.ofNullable(input.takeProfitPrice()), Optional.ofNullable(input.stopLossPrice())));
        SimpleEarnIncomeService.SimpleEarnIncomeAssessment earn = simpleEarnIncomeService.assess(
                input.capital(), input.simpleEarnApr(), input.simpleQuoteAsOf());
        PhaseOneAssessment smart = smartArbitrageService.assess(new PhaseOneAssessmentRequest(
                input.evaluationAsOf(), input.from(), input.to(), input.capital(),
                new SimpleEarnQuote(input.simpleEarnApr(), 90, input.simpleQuoteAsOf(),
                        "OKX 90天定期确认页（人工录入）", true),
                input.hedgedNotional(), new SmartArbitrageCosts(input.costAsOf(),
                        "OKX智能套利预览与人工成本假设", optionalOpening(input.openingCost()),
                        Optional.ofNullable(input.exitCost()), Optional.ofNullable(input.slippageCost()),
                        Optional.ofNullable(input.rebalanceCost()), Optional.ofNullable(input.serviceCost())),
                TerminalPolicy.EXIT_TO_USDT));
        PortfolioRecommendation portfolio = portfolioRecommendationService.recommend(
                input.portfolioCapital(), input.capital(), input.portfolioFixedEarnRatio(),
                input.portfolioLiquidRatio(), input.portfolioSmartRatio(), input.portfolioGridRatio(),
                assessment.recommendation(), smart.recommendation(), Duration.between(input.from(), input.to()),
                earn.fixed90Days().income90Days(), earn.flexible().income90Days(),
                smart.estimatedFundingOnlyNetIncome().orElse(null), assessment.worsePathIncome());
        return toView(assessment, earn, smart, smartAllocation, portfolio);
    }

    @PostMapping("/income/simple-earn")
    public SimpleEarnIncomeService.SimpleEarnIncomeAssessment simpleEarn(@RequestBody SimpleEarnRequest input) {
        if (input == null) throw new IllegalArgumentException("简单赚币请求不能为空");
        return simpleEarnIncomeService.assess(input.capital(), input.fixed90DayApr(), input.fixedQuoteAt());
    }

    private static Optional<OpeningCost> optionalOpening(BigDecimal value) {
        return Optional.ofNullable(value).map(OpeningCost.Aggregate::new);
    }

    private static GridEvaluationView toView(FixedGridAssessment assessment,
                                             SimpleEarnIncomeService.SimpleEarnIncomeAssessment earn,
                                             PhaseOneAssessment smart,
                                             SmartArbitrageAllocation smartAllocation,
                                             PortfolioRecommendation portfolio) {
        DailyDecisionReport report = report(assessment);
        Duration observed = Duration.between(assessment.backtest().from(), assessment.backtest().toExclusive());
        ReturnMetrics gridReturns = ReturnMetricsCalculator.calculate(
                assessment.worsePathIncome(), assessment.request().capital(), observed, 7);
        ReturnMetrics simpleReturns = ReturnMetricsCalculator.calculate(
                assessment.simpleEarnIncome(), assessment.request().capital(), observed, 7);
        ReturnMetrics buyHoldReturns = ReturnMetricsCalculator.calculate(
                assessment.buyAndHoldIncome(), assessment.request().capital(), observed, 7);
        return new GridEvaluationView(
                "BACKTEST", assessment.request().evaluationAsOf(), assessment.request().from(),
                assessment.request().to(), assessment.backtest().from(), assessment.backtest().toExclusive(),
                assessment.scenarioOverride(), assessment.scenario(),
                assessment.worsePathIncome(), assessment.simpleEarnIncome(), assessment.buyAndHoldIncome(),
                gridReturns, simpleReturns, buyHoldReturns,
                assessment.pathDivergenceRate(), path(assessment.backtest().pathLowThenHigh()),
                path(assessment.backtest().pathHighThenLow()), assessment.sensitivity(),
                assessment.recommendation(), assessment.maximumFirstLiveCapital().orElse(null),
                assessment.reasons(), report, assessment.backtest().initialAllocation(), earn,
                smartView(smart, smartAllocation), portfolio);
    }

    private static SmartArbitrageView smartView(PhaseOneAssessment value,
                                                SmartArbitrageAllocation allocation) {
        return new SmartArbitrageView(value.fundingHistory().settlementCount(),
                value.fundingHistory().positiveSettlements(), value.fundingHistory().negativeSettlements(),
                value.fundingHistory().holdingReturn(), value.fundingHistory().fundingIncome(), value.knownCost(),
                value.estimatedFundingOnlyNetIncome().orElse(null), value.incrementalIncomeVsSimpleEarn().orElse(null),
                value.evidenceStatus().name(), value.recommendation().name(), value.maximumFirstLiveCapital().orElse(null),
                value.reasons(), value.unknowns(), value.manualOkxChecklist(), allocation);
    }

    private static PathView path(GridPathResult path) {
        return new PathView(path.totalIncome(), path.completedGridProfit(), path.inventoryPnl(),
                path.totalFees(), path.maximumDrawdownRate(), path.completedCycles(),
                path.endingBtcValueRatioBeforeExit(), path.tradeCount(), path.exitReason(),
                path.exitTime(), path.exitTriggerPrice());
    }

    private static DailyDecisionReport report(FixedGridAssessment assessment) {
        return switch (assessment.recommendation()) {
            case INSUFFICIENT_EVIDENCE -> new DailyDecisionReport(
                    "证据不足，今天不调整", "NO_CHANGE", "参数", "缺少可靠证据，不继续调参。",
                    "BLOCKED", "仓位", "没有Live持仓事实，禁止生成实际调仓金额。",
                    "BLOCKED", "产品组合", "保持现状，先补齐阻断证据。",
                    "补齐数据后重新评估", List.of("回测不等于Shadow或Live"));
            case PREFER_SIMPLE_EARN -> new DailyDecisionReport(
                    "网格未胜出，今天不增加网格", "REJECT", "参数",
                    "当前情景不值得通过继续拟合历史数据来挽救；保留结果作为反例。",
                    "NO_INCREASE", "仓位", "网格新增仓位为0；没有Live事实时不判断现有仓位。",
                    "PREFER_SIMPLE_EARN", "产品组合",
                    "在本次候选比较中简单赚币更优；定期与流动USDT的具体比例仍需人工决定。",
                    "新数据窗口形成后再评估", List.of("不自动申购简单赚币", "不修改OKX策略"));
            case KEEP_OBSERVING_GRID -> new DailyDecisionReport(
                    "继续观察，今天不动实盘", "KEEP", "参数",
                    "冻结本次参数进入样本外观察，不做每日追涨式调参。",
                    "SHADOW_ONLY", "仓位", "仅记录虚拟仓位，实盘增量为0。",
                    "KEEP", "产品组合", "简单赚币或流动USDT保持核心，网格只占Shadow观察位。",
                    "创建并完成7–14天Shadow后复核", List.of("本次回测结果尚未自动创建Shadow", "无Live账户事实"));
            case MICRO_VALIDATE_GRID -> new DailyDecisionReport(
                    "历史情景通过，先进入Shadow", "FREEZE_FOR_SHADOW", "参数",
                    "冻结当前区间、网格数和止盈止损，避免在同一历史窗口继续寻优。",
                    "SHADOW_ONLY", "仓位",
                    "今天不增加实盘；Shadow通过后首次人工验证上限为"
                            + assessment.maximumFirstLiveCapital().map(BigDecimal::toPlainString).orElse("0") + " USDT。",
                    "NO_LIVE_REALLOCATION", "产品组合",
                    "暂不改变真实产品组合；Shadow只用于验证网格相对简单赚币的样本外表现。",
                    "连续观察7–14天后复核", List.of("历史收益不代表未来", "实盘必须由用户在OKX手工执行"));
        };
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorView invalid(IllegalArgumentException error) {
        String message = error.getMessage() == null ? "参数不合法" : error.getMessage();
        return new ErrorView("INVALID_REQUEST", message.substring(0, Math.min(200, message.length())));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorView conflict(IllegalStateException error) {
        String message = error.getMessage() == null ? "状态冲突" : error.getMessage();
        return new ErrorView("STATE_CONFLICT", message.substring(0, Math.min(200, message.length())));
    }

    @ExceptionHandler(GridSourceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorView sourceFailure(GridSourceException error) {
        return new ErrorView("SOURCE_FAILURE",
                "OKX K线来源暂时不可用，类别=" + error.category() + "，可重试=" + error.retryable());
    }

    @ExceptionHandler(SimpleEarnSourceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorView simpleEarnSourceFailure(SimpleEarnSourceException error) {
        return new ErrorView("SIMPLE_EARN_SOURCE_FAILURE",
                "OKX 活期利率暂时不可用，可重试=" + error.retryable());
    }

    public record GridEvaluationRequest(
            Instant evaluationAsOf, Instant from, Instant to, long configurationSnapshotId,
            BigDecimal capital, BigDecimal simpleEarnApr, BigDecimal makerFeeRate,
            BigDecimal takerFeeRate, BigDecimal exitSlippageBps, BigDecimal maximumDrawdownRate,
            BigDecimal maximumPathDivergenceRate, BigDecimal maximumEndingBtcValueRatio,
            BigDecimal lowerPrice, BigDecimal upperPrice, int gridCount,
            BigDecimal takeProfitPrice, BigDecimal stopLossPrice,
            Instant simpleQuoteAsOf, BigDecimal hedgedNotional, Instant costAsOf,
            BigDecimal openingCost, BigDecimal exitCost, BigDecimal slippageCost,
            BigDecimal rebalanceCost, BigDecimal serviceCost,
            BigDecimal smartReferenceSpotPrice, BigDecimal smartReservedMargin,
            BigDecimal portfolioCapital, BigDecimal portfolioFixedEarnRatio,
            BigDecimal portfolioLiquidRatio, BigDecimal portfolioSmartRatio,
            BigDecimal portfolioGridRatio) {}
    public record SimpleEarnRequest(BigDecimal capital, BigDecimal fixed90DayApr, Instant fixedQuoteAt) {}
    public record GridShadowCreateRequest(
            long configurationSnapshotId, Instant startAt, int targetDays, BigDecimal capital,
            BigDecimal simpleEarnApr, BigDecimal makerFeeRate, BigDecimal takerFeeRate,
            BigDecimal exitSlippageBps, BigDecimal maximumDrawdownRate,
            BigDecimal maximumPathDivergenceRate, BigDecimal maximumEndingBtcValueRatio,
            BigDecimal candidateLowerPrice, BigDecimal candidateUpperPrice, int candidateGridCount,
            BigDecimal candidateTakeProfitPrice, BigDecimal candidateStopLossPrice,
            Instant evidenceAsOf, String evidenceReference) {}
    public record ShadowRefreshRequest(Instant valuationAt, Instant evaluationAsOf) {}
    public record ShadowStopRequest(Instant stoppedAt) {}
    public record CandleSyncRequest(Instant to) {}
    public record CandleSyncView(
            long id, String state, String instrumentId, String bar, Instant from, Instant to,
            Instant startedAt, Instant completedAt, Instant updatedAt,
            CandleBackfillCoverage coverage, int expectedMinutes, int pages, int received, int completedInWindow,
            int newFacts, int duplicateFacts, Instant cursor, String failureCategory, String failureMessage) {}

    public record CapabilityView(List<ModeCapability> modes, List<String> boundaries) {}
    public record ModeCapability(String mode, String status, boolean available, String explanation) {}
    public record GridEvaluationView(
            String mode, Instant asOf, Instant windowFrom, Instant windowTo,
            Instant activeFrom, Instant activeTo, boolean scenarioOverride,
            GridScenarioParameters parameters, BigDecimal conservativeNetIncome,
            BigDecimal simpleEarnIncome, BigDecimal buyAndHoldIncome,
            ReturnMetrics gridReturns, ReturnMetrics simpleEarnReturns, ReturnMetrics buyHoldReturns,
            BigDecimal pathDivergenceRate,
            PathView pathLowThenHigh, PathView pathHighThenLow,
            List<GridSensitivityResult> sensitivity, GridAssessmentRecommendation recommendation,
            BigDecimal maximumFirstLiveCapital, List<String> reasons, DailyDecisionReport dailyReport,
            GridInitialAllocation gridInitialAllocation,
            SimpleEarnIncomeService.SimpleEarnIncomeAssessment simpleEarn,
            SmartArbitrageView smartArbitrage,
            PortfolioRecommendation portfolioRecommendation) {}
    public record SmartArbitrageView(
            int settlementCount, int positiveSettlements, int negativeSettlements,
            BigDecimal holdingReturn, BigDecimal fundingIncome, BigDecimal knownCost,
            BigDecimal netIncome, BigDecimal incrementalVsSimpleEarn, String evidenceStatus,
            String recommendation, BigDecimal maximumFirstLiveCapital, List<String> reasons,
            List<String> unknowns, List<String> manualOkxChecklist,
            SmartArbitrageAllocation allocation) {}
    public record PathView(
            BigDecimal netIncome, BigDecimal completedGridProfit, BigDecimal inventoryPnl,
            BigDecimal totalFees, BigDecimal maximumDrawdownRate, int completedCycles,
            BigDecimal endingBtcRatio, int tradeCount, GridExitReason exitReason,
            Instant exitTime, BigDecimal exitTriggerPrice) {}
    public record DailyDecisionReport(
            String headline, String parameterAction, String parameterSubject, String parameterAdvice,
            String positionAction, String positionSubject, String positionAdvice,
            String portfolioAction, String portfolioSubject, String portfolioAdvice,
            String nextReview, List<String> boundaries) {}
    public record ErrorView(String code, String message) {}
}
