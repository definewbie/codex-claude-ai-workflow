package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record MarketScenario(
        MarketRegime regime,
        Optional<BigDecimal> probability,
        List<ScenarioPricePath> paths,
        String sourceReference
) {
    public MarketScenario {
        if (regime == null || probability == null || paths == null || sourceReference == null
                || sourceReference.isBlank()) {
            throw new IllegalArgumentException("行情定义字段不能为空");
        }
        probability.ifPresent(value -> {
            if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("行情概率必须在0到1之间");
            }
        });
        paths = List.copyOf(paths);
        Set<String> pathCodes = paths.stream().map(ScenarioPricePath::pathCode).collect(Collectors.toSet());
        if (pathCodes.size() != paths.size()) {
            throw new IllegalArgumentException("同一行情内路径代码不能重复");
        }
        if (paths.size() < 2 || !pathCodes.contains("REPRESENTATIVE") || !pathCodes.contains("STRESS")) {
            throw new IllegalArgumentException("每类行情必须至少包含 REPRESENTATIVE 和 STRESS 路径");
        }
        if (paths.stream().anyMatch(path -> !path.scenarioCode().equals(regime.name()))) {
            throw new IllegalArgumentException("路径行情代码必须与行情定义一致");
        }
    }
}
