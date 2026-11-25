package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class FixedGridAssessmentService {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal BPS = new BigDecimal("10000");
    private static final BigDecimal FIRST_LIVE_LIMIT = new BigDecimal("5000");

    private final GridConfigurationSnapshotQuery configurationQuery;
    private final SpotCandleQuery candleQuery;
    private final FixedGridBacktester backtester;

    public FixedGridAssessmentService(
            GridConfigurationSnapshotQuery configurationQuery,
            SpotCandleQuery candleQuery,
            FixedGridBacktester backtester) {
        this.configurationQuery = configurationQuery;
        this.candleQuery = candleQuery;
        this.backtester = backtester;
    }

    public FixedGridAssessment assess(FixedGridAssessmentRequest request) {
        return assess(request, Optional.empty());
    }

    public FixedGridAssessment assessScenario(
            FixedGridAssessmentRequest request, GridScenarioParameters scenario) {
        return assess(request, Optional.of(scenario));
    }

    private FixedGridAssessment assess(
            FixedGridAssessmentRequest request, Optional<GridScenarioParameters> scenarioOverride) {
        GridConfigurationSnapshot configuration = configurationQuery
                .findByIdAsOf(request.configurationSnapshotId(), request.evaluationAsOf())
                .orElseThrow(() -> new IllegalArgumentException("评估时点之前不存在指定人工确认配置"));
        validateConfiguration(configuration, request);
        GridScenarioParameters scenario = scenarioOverride.orElseGet(() -> GridScenarioParameters.confirmed(configuration));
        List<SpotCandle> candles = candleQuery.findCompletedAsOf(
                "OKX", "BTC-USDT", CandleBar.M1,
                request.from(), request.to(), request.evaluationAsOf());
        requireCompleteWindow(candles, request);
        List<SpotCandle> activeCandles = eligibleCandles(candles, scenario);

        DualPathGridBacktest base = run(request, scenario, activeCandles, scenario.gridCount());
        BigDecimal simpleIncome = max(
                simpleEarnIncome(request, base.from(), base.pathLowThenHigh().exitTime()),
                simpleEarnIncome(request, base.from(), base.pathHighThenLow().exitTime()));
        BigDecimal buyHoldIncome = max(
                buyAndHoldIncome(request, activeCandles.getFirst().open(),
                        base.pathLowThenHigh().exitTriggerPrice()),
                buyAndHoldIncome(request, activeCandles.getFirst().open(),
                        base.pathHighThenLow().exitTriggerPrice()));
        BigDecimal worseIncome = min(base.pathLowThenHigh().totalIncome(), base.pathHighThenLow().totalIncome());
        BigDecimal divergence = base.pathLowThenHigh().totalIncome()
                .subtract(base.pathHighThenLow().totalIncome()).abs().divide(request.capital(), MC);

        List<GridSensitivityResult> sensitivity = new ArrayList<>();
        boolean sensitivityPasses = true;
        for (int count : sensitivityCounts(scenario.gridCount())) {
            DualPathGridBacktest variant = run(request, scenario, activeCandles, count);
            BigDecimal variantWorse = min(
                    variant.pathLowThenHigh().totalIncome(), variant.pathHighThenLow().totalIncome());
            sensitivity.add(new GridSensitivityResult(count, variantWorse));
            sensitivityPasses &= variantWorse.compareTo(simpleIncome) > 0
                    && variantWorse.compareTo(buyHoldIncome) > 0;
        }

        BigDecimal worstDrawdown = max(base.pathLowThenHigh().maximumDrawdownRate(),
                base.pathHighThenLow().maximumDrawdownRate());
        BigDecimal worstEndingBtcRatio = max(base.pathLowThenHigh().endingBtcValueRatioBeforeExit(),
                base.pathHighThenLow().endingBtcValueRatioBeforeExit());
        List<String> reasons = new ArrayList<>();
        boolean beatsSimple = worseIncome.compareTo(simpleIncome) > 0;
        boolean beatsBuyHold = worseIncome.compareTo(buyHoldIncome) > 0;
        boolean drawdownPasses = worstDrawdown.compareTo(request.maximumDrawdownRate()) <= 0;
        boolean divergencePasses = divergence.compareTo(request.maximumPathDivergenceRate()) <= 0;
        boolean exposurePasses = worstEndingBtcRatio.compareTo(request.maximumEndingBtcValueRatio()) <= 0;
        if (!beatsSimple) reasons.add("保守路径净收益未超过同期简单赚币");
        if (!beatsBuyHold) reasons.add("保守路径净收益未超过同期买入并持有 BTC");
        if (!drawdownPasses) reasons.add("最大回撤超过用户阈值");
        if (!divergencePasses) reasons.add("分钟内两种价格路径分歧超过阈值");
        if (!exposurePasses) reasons.add("期末退出前 BTC 价值占比超过阈值");
        if (!sensitivityPasses) reasons.add("网格数量上下扰动后不能稳定超过两个基准");

        boolean allPass = beatsSimple && beatsBuyHold && drawdownPasses
                && divergencePasses && exposurePasses && sensitivityPasses;
        GridAssessmentRecommendation recommendation;
        Optional<BigDecimal> liveLimit;
        if (allPass) {
            recommendation = GridAssessmentRecommendation.MICRO_VALIDATE_GRID;
            liveLimit = Optional.of(request.capital().min(FIRST_LIVE_LIMIT));
            reasons.add("仅达到小额 Shadow 后实盘验证门槛，不代表未来收益");
        } else if (!beatsSimple) {
            recommendation = GridAssessmentRecommendation.PREFER_SIMPLE_EARN;
            liveLimit = Optional.empty();
        } else {
            recommendation = GridAssessmentRecommendation.KEEP_OBSERVING_GRID;
            liveLimit = Optional.empty();
        }
        return new FixedGridAssessment(
                request, configuration, scenario, scenarioOverride.isPresent(), base,
                simpleIncome, buyHoldIncome, worseIncome, divergence,
                List.copyOf(sensitivity), recommendation, liveLimit, List.copyOf(reasons));
    }

    private DualPathGridBacktest run(FixedGridAssessmentRequest request, GridScenarioParameters scenario,
                                     List<SpotCandle> candles, int gridCount) {
        return backtester.backtest(new GridBacktestRequest(
                request.capital(), scenario.lowerPrice(), scenario.upperPrice(), gridCount,
                request.makerFeeRate(), request.takerFeeRate(), request.exitSlippageBps(),
                scenario.takeProfitPrice(), scenario.stopLossPrice()), candles);
    }

    private static void validateConfiguration(GridConfigurationSnapshot configuration, FixedGridAssessmentRequest request) {
        if (!"OKX".equals(configuration.exchange()) || !"BTC-USDT".equals(configuration.instrumentId())) {
            throw new IllegalArgumentException("第一期仅支持 OKX BTC-USDT 人工确认配置");
        }
        if (configuration.gridType() != GridType.ARITHMETIC) {
            throw new IllegalArgumentException("第一期仅支持等差固定现货网格");
        }
        if (configuration.observedAt().isAfter(request.evaluationAsOf())) {
            throw new IllegalArgumentException("人工确认配置晚于评估时点");
        }
        if (configuration.profitReinvestmentEnabled() || configuration.autoEarnEnabled()
                || configuration.trailingUpEnabled() || configuration.trailingDownEnabled()
                || configuration.takeProfitPrice().isPresent() || configuration.stopLossPrice().isPresent()) {
            throw new IllegalArgumentException("当前回测只支持复投、自动赚币、移动网格、止盈止损全部关闭的固定配置");
        }
    }

    private static void requireCompleteWindow(List<SpotCandle> candles, FixedGridAssessmentRequest request) {
        if (candles.size() != 129_600
                || !candles.getFirst().openTime().equals(request.from())
                || !candles.getLast().openTime().plusSeconds(60).equals(request.to())) {
            throw new IllegalArgumentException("90 天分钟 K 线不完整，禁止生成网格收益结论");
        }
    }

    private static List<SpotCandle> eligibleCandles(
            List<SpotCandle> observationCandles, GridScenarioParameters scenario) {
        for (int index = 0; index < observationCandles.size(); index++) {
            BigDecimal open = observationCandles.get(index).open();
            if (open.compareTo(scenario.lowerPrice()) >= 0
                    && open.compareTo(scenario.upperPrice()) <= 0) {
                return observationCandles.subList(index, observationCandles.size());
            }
        }
        throw new IllegalArgumentException("90 天观察窗口内价格从未进入人工确认的网格区间");
    }

    private static BigDecimal simpleEarnIncome(
            FixedGridAssessmentRequest request, java.time.Instant from, java.time.Instant to) {
        BigDecimal activeDays = new BigDecimal(Duration.between(from, to).toSeconds())
                .divide(new BigDecimal("86400"), MC);
        return request.capital().multiply(request.simpleEarnApr(), MC)
                .multiply(activeDays, MC).divide(DAYS_PER_YEAR, MC);
    }

    private static BigDecimal buyAndHoldIncome(
            FixedGridAssessmentRequest request, BigDecimal entry, BigDecimal terminalPrice) {
        BigDecimal btc = request.capital().divide(
                entry.multiply(BigDecimal.ONE.add(request.takerFeeRate()), MC), MC);
        BigDecimal exitPrice = terminalPrice
                .multiply(BigDecimal.ONE.subtract(request.exitSlippageBps().divide(BPS, MC)), MC);
        BigDecimal proceeds = btc.multiply(exitPrice, MC)
                .multiply(BigDecimal.ONE.subtract(request.takerFeeRate()), MC);
        return proceeds.subtract(request.capital());
    }

    private static Set<Integer> sensitivityCounts(int base) {
        int delta = Math.max(1, new BigDecimal(base).multiply(new BigDecimal("0.10"))
                .setScale(0, RoundingMode.HALF_UP).intValue());
        Set<Integer> counts = new LinkedHashSet<>();
        counts.add(Math.max(2, base - delta));
        counts.add(base);
        counts.add(Math.min(500, base + delta));
        return counts;
    }

    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        return left.min(right);
    }

    private static BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.max(right);
    }
}
