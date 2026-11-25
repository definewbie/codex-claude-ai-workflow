package app.cryptoincome.portfolio.application;

import app.cryptoincome.assessment.domain.Recommendation;
import app.cryptoincome.grid.domain.GridAssessmentRecommendation;
import app.cryptoincome.portfolio.domain.PortfolioAllocationLine;
import app.cryptoincome.portfolio.domain.PortfolioRecommendation;
import app.cryptoincome.reporting.ReturnMetricsCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Duration;
import java.util.List;

@Service
public class PhaseOnePortfolioRecommendationService {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal RECOMMENDED_FIXED = new BigDecimal("0.80");
    private static final BigDecimal RECOMMENDED_LIQUID = new BigDecimal("0.20");

    public PortfolioRecommendation recommend(BigDecimal totalCapital,
                                             BigDecimal candidateCapital,
                                             BigDecimal proposedFixedRatio,
                                             BigDecimal proposedLiquidRatio,
                                             BigDecimal proposedSmartRatio,
                                             BigDecimal proposedGridRatio,
                                             GridAssessmentRecommendation gridRecommendation,
                                             Recommendation smartRecommendation,
                                             Duration holdingPeriod,
                                             BigDecimal fixedCandidateNetIncome,
                                             BigDecimal liquidCandidateNetIncome,
                                             BigDecimal smartCandidateNetIncome,
                                             BigDecimal gridCandidateNetIncome) {
        validatePlan(totalCapital, candidateCapital, proposedFixedRatio, proposedLiquidRatio,
                proposedSmartRatio, proposedGridRatio);
        if (holdingPeriod == null || holdingPeriod.isZero() || holdingPeriod.isNegative()) {
            throw new IllegalArgumentException("组合回测持有期必须大于0");
        }

        List<PortfolioAllocationLine> allocations = List.of(
                line("FIXED_EARN_90D", "90天定期简单赚币", totalCapital, candidateCapital,
                        proposedFixedRatio, RECOMMENDED_FIXED, fixedCandidateNetIncome,
                        "已确认报价按回测窗口折算"),
                line("LIQUID_USDT", "活期简单赚币 / 流动USDT", totalCapital, candidateCapital,
                        proposedLiquidRatio, RECOMMENDED_LIQUID, liquidCandidateNetIncome,
                        "活期APR是当前估算，不代表锁定收益"),
                line("SMART_ARBITRAGE", "BTC智能套利", totalCapital, candidateCapital,
                        proposedSmartRatio, BigDecimal.ZERO, smartCandidateNetIncome,
                        smartReason(smartRecommendation)),
                line("SPOT_GRID", "BTC固定现货网格", totalCapital, candidateCapital,
                        proposedGridRatio, BigDecimal.ZERO, gridCandidateNetIncome,
                        gridReason(gridRecommendation)));

        BigDecimal proposedAllocated = sumAmounts(allocations, true);
        BigDecimal recommendedAllocated = sumAmounts(allocations, false);
        BigDecimal proposedIncome = sumIncome(allocations, true);
        BigDecimal recommendedIncome = sumIncome(allocations, false);
        BigDecimal proposedReturn = rate(proposedIncome, totalCapital);
        BigDecimal recommendedReturn = rate(recommendedIncome, totalCapital);
        BigDecimal proposedAnnualized = proposedIncome == null ? null
                : ReturnMetricsCalculator.calculate(proposedIncome, totalCapital, holdingPeriod, 7)
                .simpleAnnualizedReturnRate();
        BigDecimal incremental = proposedIncome == null || recommendedIncome == null ? null
                : proposedIncome.subtract(recommendedIncome);
        String status = proposedIncome == null ? "PARTIAL" : "COMPLETE";

        return new PortfolioRecommendation(totalCapital, "第一期赚钱优先·保守运营基线",
                false, false, candidateCapital, allocations, proposedAllocated, recommendedAllocated,
                proposedIncome, proposedReturn, proposedAnnualized, recommendedIncome, recommendedReturn,
                incremental, status,
                List.of("你的组合收益已按各产品分配金额计算，不再只是展示比例",
                        "分项收益使用单产品候选情景净收益率线性缩放；尚未模拟容量、成交量与滑点的非线性变化",
                        "红色提示表示尚未通过实盘准入门槛，不表示该分项被排除在本次历史回测之外",
                        "这不是基于未来收益预测求出的数学最优解",
                        "没有OKX Live余额与持仓事实，不生成真实调仓指令"));
    }

    public void validatePlan(BigDecimal totalCapital,
                             BigDecimal candidateCapital,
                             BigDecimal proposedFixedRatio,
                             BigDecimal proposedLiquidRatio,
                             BigDecimal proposedSmartRatio,
                             BigDecimal proposedGridRatio) {
        positive(totalCapital, "portfolioCapital");
        positive(candidateCapital, "candidateCapital");
        if (candidateCapital.compareTo(totalCapital) > 0) {
            throw new IllegalArgumentException("单产品候选资金不能超过真实组合规划资金");
        }
        ratio(proposedFixedRatio, "portfolioFixedEarnRatio");
        ratio(proposedLiquidRatio, "portfolioLiquidRatio");
        ratio(proposedSmartRatio, "portfolioSmartRatio");
        ratio(proposedGridRatio, "portfolioGridRatio");
        BigDecimal proposedTotal = proposedFixedRatio.add(proposedLiquidRatio)
                .add(proposedSmartRatio).add(proposedGridRatio);
        if (proposedTotal.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("真实组合的四项比例合计必须等于100%");
        }
    }

    private static PortfolioAllocationLine line(String code, String name,
                                                BigDecimal totalCapital, BigDecimal candidateCapital,
                                                BigDecimal proposedRatio, BigDecimal recommendedRatio,
                                                BigDecimal candidateIncome, String reason) {
        BigDecimal proposedAmount = totalCapital.multiply(proposedRatio);
        BigDecimal recommendedAmount = totalCapital.multiply(recommendedRatio);
        BigDecimal proposedIncome = scale(candidateIncome, candidateCapital, proposedAmount);
        BigDecimal recommendedIncome = scale(candidateIncome, candidateCapital, recommendedAmount);
        BigDecimal productReturn = rate(candidateIncome, candidateCapital);
        String status;
        if (proposedRatio.signum() == 0) {
            status = "未分配";
        } else if (candidateIncome == null) {
            status = "收益未知·组合不完整";
        } else if (recommendedRatio.signum() == 0) {
            status = "已纳入回测·未通过实盘门槛";
        } else {
            status = "已纳入组合回测";
        }
        return new PortfolioAllocationLine(code, name, proposedAmount, proposedRatio,
                recommendedAmount, recommendedRatio, candidateIncome, proposedIncome, productReturn,
                recommendedIncome, status, reason);
    }

    private static BigDecimal scale(BigDecimal candidateIncome, BigDecimal candidateCapital,
                                    BigDecimal allocatedCapital) {
        if (allocatedCapital.signum() == 0) return BigDecimal.ZERO;
        if (candidateIncome == null) return null;
        return candidateIncome.multiply(allocatedCapital, MC).divide(candidateCapital, MC);
    }

    private static BigDecimal sumAmounts(List<PortfolioAllocationLine> lines, boolean proposed) {
        return lines.stream().map(line -> proposed ? line.proposedAmountUsdt() : line.recommendedAmountUsdt())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumIncome(List<PortfolioAllocationLine> lines, boolean proposed) {
        BigDecimal total = BigDecimal.ZERO;
        for (PortfolioAllocationLine line : lines) {
            BigDecimal income = proposed ? line.proposedProjectedNetIncomeUsdt()
                    : line.recommendedProjectedNetIncomeUsdt();
            if (income == null) return null;
            total = total.add(income);
        }
        return total;
    }

    private static BigDecimal rate(BigDecimal income, BigDecimal capital) {
        return income == null ? null : income.divide(capital, MC);
    }

    private static String smartReason(Recommendation recommendation) {
        return switch (recommendation) {
            case PREFER_SIMPLE_EARN -> "历史保守净收益不高于90天定期；仍计算你的分配，但当前不建议进入实盘";
            case WATCH_SMART_ARBITRAGE -> "仍处观察阶段；已知净收益可计入历史组合，实盘建议保持0";
            case MICRO_VALIDATE_ARBITRAGE -> "仅达到未来小额验证门槛；历史组合可计算，实盘建议仍为0";
            case INSUFFICIENT_EVIDENCE -> "关键收益或成本证据不足；非零分配会使组合总收益暂时不可计算";
        };
    }

    private static String gridReason(GridAssessmentRecommendation recommendation) {
        return switch (recommendation) {
            case MICRO_VALIDATE_GRID -> "历史情景通过并可计入组合回测，但真实资金仍应先完成Shadow";
            case KEEP_OBSERVING_GRID -> "历史收益已计入组合回测，尚未通过样本外观察";
            case PREFER_SIMPLE_EARN -> "历史网格未胜过简单赚币；仍按用户比例计入本次组合回测";
            case INSUFFICIENT_EVIDENCE -> "网格证据不足；非零分配会使组合总收益暂时不可计算";
        };
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于0");
    }

    private static void ratio(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(name + " 必须在0到1之间");
        }
    }
}
