package app.cryptoincome.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionWorkbenchStaticAssetTest {

    @Test
    void backtestAndShadowConsumeOneSharedCandleStatusAndUseFreshOperationalTimes() throws IOException {
        String html = resource("/static/index.html");
        String javascript = resource("/static/workbench.js");

        assertThat(html)
                .contains("id=\"backtest-sync-candles\"", "id=\"shadow-sync-candles\"", "共享同一份")
                .contains("人工证据，不自动更新")
                .doesNotContain("name=\"from\" type=\"datetime-local\" value=\"2026-",
                        "name=\"to\" type=\"datetime-local\" value=\"2026-",
                        "id=\"shadow-valuation-at\" type=\"datetime-local\" value=\"2026-");
        assertThat(javascript)
                .contains("applyLatestOperationalTimes", "renderSharedCandleStatus")
                .contains("backtestSyncButton.addEventListener('click',()=>refreshMarketData(false))")
                .contains("shadowSyncButton.addEventListener('click',()=>refreshMarketData(false))")
                .contains("const alignOperationalTimes=!automatic||!pageOperationalTimesConfirmed")
                .contains("if(alignOperationalTimes) applyLatestOperationalTimes(finalData)")
                .contains("gridForm.elements.from.value=utc8Input(windowFrom)")
                .contains("document.querySelector('#shadow-valuation-at').value=utc8Input(windowTo)")
                .doesNotContain("async function syncCandles", "simpleQuoteAsOf.value=", "costAsOf.value=",
                        "evidenceAsOf.value=");
    }

    @Test
    void workbenchUsesActualOkxMarketDataAndRefreshesItOncePerMinute() throws IOException {
        String html = resource("/static/index.html");
        String javascript = resource("/static/workbench.js");

        assertThat(html).contains("BTC 实时行情与分钟 K 线", "id=\"market-last-price\"",
                "id=\"market-candle-progress\"", "不需要 API Key");
        assertThat(javascript)
                .contains("/api/workbench/market-data/refresh", "/api/workbench/market-data/refresh/manual",
                        "setInterval(()=>refreshMarketData(true),60000)")
                .contains("candles.actualMinutes", "candles.expectedMinutes", "candles.complete")
                .contains("fullCheckRunning")
                .contains("MARKET_SYNC_POLL_LIMIT_MS=20*60*1000", "data.automaticRetryAt",
                        "marketPollingSuppressedExecutionId")
                .contains("人工刷新可立即重试", "页面已停止持续轮询", "立即更新 OKX 行情")
                .contains("WAITING_LATEST_CANDLE", "不启动90天全量回补")
                .contains("ticker.lastPrice", "ticker.providerTimestamp")
                .doesNotContain("Math.random", "/trade", "/orders");
    }

    @Test
    void uiUsesBackendReturnMetricsReadableShadowSelectorAndSharedCandleSync() throws IOException {
        String html = resource("/static/index.html");
        String javascript = resource("/static/workbench.js");

        assertThat(html)
                .contains("id=\"backtest-sync-candles\"")
                .contains("<select id=\"shadow-resume-id\"")
                .contains("id=\"metric-grid-rate\"")
                .contains("id=\"shadow-candidate-rate\"")
                .contains("SHADOW COMPARISON", "实验对比｜不等于真实组合持仓", "同一笔虚拟资金")
                .contains("候选网格（虚拟实验）", "确认网格（虚拟基准）", "简单赚币（比较基准）")
                .contains("真实组合中比例为 0% 的产品不会在组合结果中展示");
        assertThat(javascript)
                .contains("/api/workbench/shadows")
                .contains("candidateReturns")
                .contains("simpleAnnualizedReturnRate")
                .doesNotContain("netIncome/capital")
                .doesNotContain("netIncome / capital");
    }

    @Test
    void backtestPageShowsTheFourFirstBatchProductsWithoutAddingTradeActions() throws IOException {
        String html = resource("/static/index.html");
        String javascript = resource("/static/workbench.js");

        assertThat(html)
                .contains("90天定期简单赚币", "活期简单赚币估算", "BTC 智能套利", "BTC 固定现货网格")
                .contains("活期利率由程序读取")
                .doesNotContain("创建策略", "申购简单赚币");
        assertThat(javascript)
                .contains("product-fixed-income", "product-flex-income", "product-smart-income", "product-grid-income")
                .doesNotContain("/trade", "/orders");
    }

    @Test
    void separatesProductInputsAndMakesCapitalAllocationAndTimeMeaningsExplicit() throws IOException {
        String html = resource("/static/index.html");
        String javascript = resource("/static/workbench.js");

        assertThat(html)
                .contains("共同评估假设", "产品 A · BTC 固定现货网格", "产品 B · USDT 简单赚币",
                        "产品 C · BTC 智能套利")
                .contains("单产品回测候选资金", "90天收益观察期开始",
                        "本次分析使用的数据版本截止时间", "你确认该定期 APR 的时间",
                        "你确认智能套利成本的时间")
                .contains("真实组合规划总资金", "真实组合比例（可调整）", "真实组合历史回测",
                        "单产品内部资金结构", "allocation-grid-btc", "allocation-smart-btc",
                        "allocation-smart-hedge", "allocation-smart-delta", "allocation-earn-usdt",
                        "portfolio-user-income", "portfolio-baseline-income", "portfolio-incremental");
        assertThat(javascript)
                .contains("gridInitialAllocation", "smart.allocation", "estimatedBtcQuantity",
                        "btcCapitalRatio", "smartReservedMargin", "smartReferenceSpotPrice",
                        "portfolioRecommendation", "portfolioGridRatio", "portfolioSmartRatio",
                        "reservedBuyOrderUsdt", "shortHedgeNotionalUsdt",
                        "proposedProjectedNetIncomeUsdt", "projectedIncrementalVsRecommendedUsdt");
    }

    @Test
    void distinguishesCandidateProductResultsFromPortfolioContributionsAndRestoresLatestSnapshot() throws IOException {
        String html = resource("/static/index.html");
        String javascript = resource("/static/workbench.js");

        assertThat(html)
                .contains("SINGLE PRODUCT BASELINE", "不等于组合投入", "portfolio-equation")
                .contains("保守实盘基线（不参与本次组合计算）", "backtest-result-state");
        assertThat(javascript)
                .contains("incomeWorkbench.latestBacktest.v1", "saveBacktest(snapshot,data)",
                        "restoreLatestBacktest()", "参数已修改：下方仍显示最近一次回测快照")
                .contains("单产品独立回测｜每项", "组合历史净收益")
                .contains("portfolio.allocations.filter(row=>Number(row.proposedRatio)>0)")
                .contains("allocatedRows.map");
    }

    @Test
    void scenarioLabConsumesUnifiedReadModelAndKeepsDemoInputExplicit() throws IOException {
        String workbench = resource("/static/index.html");
        String html = resource("/static/scenario-lab.html");
        String javascript = resource("/static/scenario-lab.js");

        assertThat(workbench)
                .contains("href=\"/scenario-lab.html\"", "进入 AI 情景实验室");
        assertThat(html)
                .contains("BTC 情景与组合实验室", "悲观情景", "震荡情景", "乐观情景")
                .contains("快速体验示例", "使用我的90天假设")
                .contains("01 · 确认本次分析输入", "02 · 设置三种90天行情假设", "03 · 组合建议")
                .contains("教学示例，不代表当前市场", "人工录入，不是实时行情")
                .contains("概率只用于计算达到10%/20%目标的概率，不改变组合比例和方案排序")
                .contains("产品收益与成本证据（人工录入）", "高级价格过程（固定90天）", "组合风险约束")
                .contains("id=\"evidence-observed-at\"")
                .contains("allocation-exclusions", "contribution-check")
                .contains("placeholder=\"可空\"")
                .contains("role=\"group\"")
                .doesNotContain("role=\"tab\"")
                .contains("href=\"/\"")
                .doesNotContain("value=\"64200\"", "后端计算模式", "01 · 冻结输入")
                .doesNotContain("/api/workbench");
        assertThat(javascript)
                .contains("const API_URL = '/api/scenario-evaluations'", "fetch(API_URL")
                .contains("optionSet.options", "pathOutcomes", "targetAttainment", "qualifiedProducts", "excludedProducts")
                .contains("outcome.netIncomeUsdt", "outcome.holdingReturnRate", "outcome.simpleAnnualizedRate")
                .contains("currentReadModel", "results-pending", "教学示例 · 后端计算")
                .contains("clearManualCoreInputs", "markResultsStale", "card.disabled = !currentReadModel")
                .contains("strong.textContent = productMeta[row.productCode].name")
                .contains("paths: [path(regime, 'REPRESENTATIVE'", "path(regime, 'STRESS'")
                .contains("horizon: 'P90D'", "utc8Instant(document.querySelector('#evidence-observed-at').value)")
                .contains("教学示例尚未运行", "singleOption")
                .contains("setMode('demo')")
                .contains("body: JSON.stringify(request)")
                .doesNotContain("sumContributions", "outcomeFor", "spotContribution", "const plans =")
                .doesNotContain("netIncomeUsdt /", "netIncomeUsdt/", "365 / 90", "365/90")
                .doesNotContain("生成3套组合建议", "`${productMeta[row.productCode].name} 0%`")
                .doesNotContain("/trade", "/orders");
    }

    private static String resource(String path) throws IOException {
        try (var stream = DecisionWorkbenchStaticAssetTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("缺少静态资源: " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
