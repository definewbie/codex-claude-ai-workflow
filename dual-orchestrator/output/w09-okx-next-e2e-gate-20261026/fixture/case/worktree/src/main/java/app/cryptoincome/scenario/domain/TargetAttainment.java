package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.util.Optional;

public record TargetAttainment(
        int lowTargetScenarioCount,
        int highTargetScenarioCount,
        int scenarioCount,
        Optional<BigDecimal> lowTargetProbability,
        Optional<BigDecimal> highTargetProbability
) {
    public TargetAttainment {
        if (scenarioCount <= 0 || lowTargetScenarioCount < 0 || highTargetScenarioCount < 0
                || lowTargetScenarioCount > scenarioCount || highTargetScenarioCount > scenarioCount
                || highTargetScenarioCount > lowTargetScenarioCount
                || lowTargetProbability == null || highTargetProbability == null
                || lowTargetProbability.isPresent() != highTargetProbability.isPresent()) {
            throw new IllegalArgumentException("目标达成统计不合法");
        }
        lowTargetProbability.ifPresent(TargetAttainment::probability);
        highTargetProbability.ifPresent(TargetAttainment::probability);
    }

    private static void probability(BigDecimal value) {
        if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("目标达成概率必须在0到1之间");
        }
    }
}
