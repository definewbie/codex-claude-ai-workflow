package app.cryptoincome.assessment.application;

import app.cryptoincome.assessment.domain.EvidenceStatus;
import app.cryptoincome.assessment.domain.FundingHistoryEvidence;
import app.cryptoincome.assessment.domain.FundingRatePoint;
import app.cryptoincome.assessment.domain.PhaseOneAssessment;
import app.cryptoincome.assessment.domain.PhaseOneAssessmentRequest;
import app.cryptoincome.assessment.domain.Recommendation;
import app.cryptoincome.assessment.domain.TerminalPolicy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class PhaseOneAssessmentService {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365");
    private static final BigDecimal MAXIMUM_FIRST_LIVE_CAPITAL = new BigDecimal("5000");
    private static final String EXCHANGE = "OKX";
    private static final String INSTRUMENT = "BTC-USDT-SWAP";

    private final FundingRateFactQuery fundingRateFactQuery;

    public PhaseOneAssessmentService(FundingRateFactQuery fundingRateFactQuery) {
        this.fundingRateFactQuery = fundingRateFactQuery;
    }

    public PhaseOneAssessment assess(PhaseOneAssessmentRequest request) {
        List<FundingRatePoint> points = fundingRateFactQuery.findRealizedRates(
                EXCHANGE, INSTRUMENT, request.from(), request.to(), request.evaluationAsOf());
        List<FundingRatePoint> ordered = points.stream()
                .sorted(Comparator.comparing(FundingRatePoint::fundingTime))
                .toList();

        BigDecimal holdingReturn = ordered.stream()
                .map(FundingRatePoint::realizedRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fundingIncome = request.hedgedShortNotional().multiply(holdingReturn);
        BigDecimal simpleEarnIncome = request.candidateCapital()
                .multiply(request.simpleEarnQuote().apr())
                .multiply(new BigDecimal(request.simpleEarnQuote().termDays()))
                .divide(DAYS_PER_YEAR, CALCULATION_CONTEXT);
        BigDecimal knownCost = request.costs().knownTotal();

        FundingHistoryEvidence history = new FundingHistoryEvidence(
                holdingReturn,
                fundingIncome,
                ordered.size(),
                (int) ordered.stream().filter(point -> point.realizedRate().signum() > 0).count(),
                (int) ordered.stream().filter(point -> point.realizedRate().signum() < 0).count(),
                ordered.stream().map(FundingRatePoint::fundingTime).min(Instant::compareTo),
                ordered.stream().map(FundingRatePoint::fundingTime).max(Instant::compareTo));

        List<String> blockers = new ArrayList<>();
        List<String> unknowns = new ArrayList<>(request.costs().unknowns());
        List<String> reasons = new ArrayList<>();
        Optional<BigDecimal> netIncome = Optional.empty();
        Optional<BigDecimal> incremental = Optional.empty();
        EvidenceStatus evidenceStatus;
        Recommendation recommendation;

        if (!request.simpleEarnQuote().termsConfirmed()) {
            blockers.add("简单赚币确认页条款尚未人工确认");
        }
        if (ordered.isEmpty()) {
            blockers.add("90 天窗口内没有可用的已实现资金费率事实");
        }
        if (request.terminalPolicy() == TerminalPolicy.KEEP_STRATEGY_OPEN) {
            unknowns.add("期末未退出，无法与到期回到 USDT 的简单赚币同口径比较");
        }

        if (!blockers.isEmpty()) {
            evidenceStatus = EvidenceStatus.INSUFFICIENT;
            recommendation = Recommendation.INSUFFICIENT_EVIDENCE;
            reasons.addAll(blockers);
        } else if (!unknowns.isEmpty()) {
            evidenceStatus = EvidenceStatus.INSUFFICIENT;
            recommendation = Recommendation.WATCH_SMART_ARBITRAGE;
            reasons.add("关键成本或期末状态仍未知，不能把未知按 0 计算");
        } else {
            BigDecimal net = fundingIncome.subtract(knownCost);
            BigDecimal delta = net.subtract(simpleEarnIncome);
            netIncome = Optional.of(net);
            incremental = Optional.of(delta);
            if (delta.signum() <= 0) {
                evidenceStatus = EvidenceStatus.SUFFICIENT_FOR_COMPARISON;
                recommendation = Recommendation.PREFER_SIMPLE_EARN;
                reasons.add("资金费率收入扣除完整已知成本后不高于 90 天简单赚币");
            } else {
                evidenceStatus = EvidenceStatus.SUFFICIENT_FOR_MICRO_VALIDATION;
                recommendation = Recommendation.MICRO_VALIDATE_ARBITRAGE;
                reasons.add("历史情景扣除完整已知成本后高于简单赚币，但尚无真实闭环");
            }
        }

        List<String> checklist = recommendation == Recommendation.MICRO_VALIDATE_ARBITRAGE
                ? List.of(
                        "重新核对 OKX 90 天简单赚币 APR、额度和赎回条款",
                        "重新打开 BTC-USDT 智能套利预览并核对两腿名义和预计开仓成本",
                        "首次只投入 2,000–5,000 USDT",
                        "保存实际成交、资金费、再平衡和退出费用",
                        "退出回 USDT 后复盘预计与实际差异")
                : List.of();

        return new PhaseOneAssessment(
                request, simpleEarnIncome, history, knownCost, netIncome, incremental,
                evidenceStatus, recommendation,
                recommendation == Recommendation.MICRO_VALIDATE_ARBITRAGE
                        ? Optional.of(MAXIMUM_FIRST_LIVE_CAPITAL) : Optional.empty(),
                reasons, blockers, unknowns, checklist);
    }
}
