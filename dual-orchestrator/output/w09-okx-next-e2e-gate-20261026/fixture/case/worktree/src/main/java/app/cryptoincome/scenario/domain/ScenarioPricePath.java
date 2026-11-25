package app.cryptoincome.scenario.domain;

import java.time.Duration;
import java.util.List;

public record ScenarioPricePath(String scenarioCode, String pathCode, List<ScenarioPricePoint> points) {
    public ScenarioPricePath {
        if (scenarioCode == null || scenarioCode.isBlank() || pathCode == null || pathCode.isBlank()) {
            throw new IllegalArgumentException("行情与路径代码不能为空");
        }
        if (points == null || points.size() < 2) {
            throw new IllegalArgumentException("情景路径至少需要起点和终点");
        }
        points = List.copyOf(points);
        for (int i = 1; i < points.size(); i++) {
            if (!points.get(i).at().isAfter(points.get(i - 1).at())) {
                throw new IllegalArgumentException("情景路径时间必须严格递增");
            }
        }
    }

    public Duration horizon() {
        return Duration.between(points.getFirst().at(), points.getLast().at());
    }
}
