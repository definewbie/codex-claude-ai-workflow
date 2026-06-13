package dev.dual.server;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record SchedulerTickResult(
        Path workdir,
        int maxWorkstreams,
        int maxStepsPerWorkstream,
        boolean autoRecover,
        List<SchedulerWorkstreamResult> workstreams
) {
    int resumed() {
        return count("resumed");
    }

    int autoRecovered() {
        return count("auto_recovered");
    }

    int skipped() {
        return count("skipped");
    }

    Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("action", "scheduler.tick");
        map.put("workdir", workdir.toAbsolutePath().normalize().toString());
        map.put("maxWorkstreams", maxWorkstreams);
        map.put("maxStepsPerWorkstream", maxStepsPerWorkstream);
        map.put("autoRecover", autoRecover);
        map.put("resumed", resumed());
        map.put("autoRecovered", autoRecovered());
        map.put("skipped", skipped());
        map.put("workstreams", workstreams.stream().map(SchedulerWorkstreamResult::toMap).toList());
        return map;
    }

    private int count(String result) {
        return (int) workstreams.stream()
                .filter(item -> result.equals(item.result()))
                .count();
    }
}
