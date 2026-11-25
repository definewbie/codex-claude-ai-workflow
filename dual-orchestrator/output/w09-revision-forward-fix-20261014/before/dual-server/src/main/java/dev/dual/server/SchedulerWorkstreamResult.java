package dev.dual.server;

import java.util.LinkedHashMap;
import java.util.Map;

record SchedulerWorkstreamResult(
        String workstreamId,
        String result,
        String status,
        String activeNode,
        String message,
        String reason,
        String retryAction,
        String recoveryClassification,
        String retryReadiness
) {
    static SchedulerWorkstreamResult resumed(String workstreamId, Map<String, Object> runResult) {
        return new SchedulerWorkstreamResult(
                workstreamId,
                "resumed",
                text(runResult.get("status")),
                text(runResult.get("activeNode")),
                text(runResult.get("message")),
                "",
                "",
                "",
                ""
        );
    }

    static SchedulerWorkstreamResult autoRecovered(
            String workstreamId,
            SchedulerDecision decision,
            Map<String, Object> retryResult,
            Map<String, Object> runResult
    ) {
        return new SchedulerWorkstreamResult(
                workstreamId,
                "auto_recovered",
                text(runResult.get("status")),
                text(runResult.get("activeNode")),
                text(runResult.get("message")),
                "",
                text(retryResult.get("action")),
                decision.recoveryClassification(),
                decision.retryReadiness()
        );
    }

    static SchedulerWorkstreamResult skipped(String workstreamId, String reason) {
        return new SchedulerWorkstreamResult(
                workstreamId,
                "skipped",
                "",
                "",
                "",
                reason,
                "",
                "",
                ""
        );
    }

    Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("workstreamId", workstreamId);
        map.put("result", result);
        putIfPresent(map, "status", status);
        putIfPresent(map, "activeNode", activeNode);
        putIfPresent(map, "message", message);
        putIfPresent(map, "reason", reason);
        putIfPresent(map, "retryAction", retryAction);
        putIfPresent(map, "recoveryClassification", recoveryClassification);
        putIfPresent(map, "retryReadiness", retryReadiness);
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
