package dev.dual.server;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SchedulerService {
    private static final String RUNNING = "RUNNING";

    private final WorkstreamQueryService queryService;
    private final WorkstreamActionService actionService;

    public SchedulerService(WorkstreamQueryService queryService, WorkstreamActionService actionService) {
        this.queryService = queryService;
        this.actionService = actionService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> tick(Path workspaceRoot, int maxWorkstreams, int maxStepsPerWorkstream) throws Exception {
        validatePositive(maxWorkstreams, "maxWorkstreams");
        validatePositive(maxStepsPerWorkstream, "maxStepsPerWorkstream");

        var response = new LinkedHashMap<String, Object>();
        response.put("action", "scheduler.tick");
        response.put("workdir", workspaceRoot.toAbsolutePath().normalize().toString());
        response.put("maxWorkstreams", maxWorkstreams);
        response.put("maxStepsPerWorkstream", maxStepsPerWorkstream);

        var summaries = (java.util.List<Map<String, Object>>) queryService.list(workspaceRoot).get("workstreams");
        var items = new ArrayList<Map<String, Object>>();
        var resumed = 0;
        var skipped = 0;
        for (var summary : summaries) {
            if (items.size() >= maxWorkstreams) {
                break;
            }
            var workstreamId = text(summary.get("workstreamId"));
            var status = text(summary.get("workflowStatus"));
            if (!RUNNING.equals(status)) {
                items.add(skipped(workstreamId, status));
                skipped++;
                continue;
            }
            var runResult = actionService.resume(workspaceRoot, workstreamId, maxStepsPerWorkstream);
            items.add(resumed(workstreamId, runResult));
            resumed++;
        }
        response.put("resumed", resumed);
        response.put("skipped", skipped);
        response.put("workstreams", items);
        return response;
    }

    private static Map<String, Object> resumed(String workstreamId, Map<String, Object> runResult) {
        var item = new LinkedHashMap<String, Object>();
        item.put("workstreamId", workstreamId);
        item.put("result", "resumed");
        item.put("status", runResult.getOrDefault("status", ""));
        item.put("activeNode", runResult.getOrDefault("activeNode", ""));
        item.put("message", runResult.getOrDefault("message", ""));
        return item;
    }

    private static Map<String, Object> skipped(String workstreamId, String reason) {
        var item = new LinkedHashMap<String, Object>();
        item.put("workstreamId", workstreamId);
        item.put("result", "skipped");
        item.put("reason", reason);
        return item;
    }

    private static void validatePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
