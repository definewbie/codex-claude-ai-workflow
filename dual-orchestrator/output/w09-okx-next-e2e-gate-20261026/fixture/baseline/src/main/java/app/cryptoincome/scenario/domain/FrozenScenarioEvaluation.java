package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public record FrozenScenarioEvaluation(
        String evaluationId,
        Instant asOf,
        Duration horizon,
        BigDecimal totalCapitalUsdt,
        CapitalContext capitalContext,
        BigDecimal startingBtcPrice,
        List<MarketScenario> scenarios,
        PortfolioConstraintSnapshot constraints,
        String calculationVersion
) {
    public FrozenScenarioEvaluation {
        if (evaluationId == null || evaluationId.isBlank() || asOf == null
                || !Duration.ofDays(90).equals(horizon)
                || totalCapitalUsdt == null || totalCapitalUsdt.signum() <= 0
                || capitalContext != CapitalContext.NEW_MONEY_USDT
                || startingBtcPrice == null || startingBtcPrice.signum() <= 0
                || scenarios == null || constraints == null
                || calculationVersion == null || calculationVersion.isBlank()) {
            throw new IllegalArgumentException("冻结情景输入不完整或不属于第一批支持范围");
        }
        scenarios = List.copyOf(scenarios);
        if (scenarios.size() != 3
                || !scenarios.stream().map(MarketScenario::regime).collect(
                        java.util.stream.Collectors.toSet()).equals(EnumSet.allOf(MarketRegime.class))) {
            throw new IllegalArgumentException("必须且只能提供 BEAR、RANGE、BULL 三类行情");
        }
        validateProbabilities(scenarios);
        List<Instant> expectedTimeline = scenarios.getFirst().paths().getFirst().points().stream()
                .map(ScenarioPricePoint::at).toList();
        if (!expectedTimeline.getFirst().equals(asOf)) {
            throw new IllegalArgumentException("情景路径起点必须等于评估时点");
        }
        for (MarketScenario scenario : scenarios) {
            for (ScenarioPricePath path : scenario.paths()) {
                List<Instant> timeline = path.points().stream().map(ScenarioPricePoint::at).toList();
                if (!timeline.equals(expectedTimeline) || !path.horizon().equals(horizon)
                        || path.points().getFirst().btcUsdtPrice().compareTo(startingBtcPrice) != 0) {
                    throw new IllegalArgumentException("所有情景路径必须共享起点价格、90天期限和时间轴");
                }
            }
        }
    }

    private static void validateProbabilities(List<MarketScenario> scenarios) {
        long present = scenarios.stream().map(MarketScenario::probability).filter(Optional::isPresent).count();
        if (present != 0 && present != scenarios.size()) {
            throw new IllegalArgumentException("行情概率必须全部为空或全部填写");
        }
        if (present == scenarios.size()) {
            BigDecimal total = scenarios.stream().map(s -> s.probability().orElseThrow())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("行情概率合计必须等于1");
            }
        }
    }
}
