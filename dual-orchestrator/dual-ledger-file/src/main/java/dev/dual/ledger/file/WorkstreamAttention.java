package dev.dual.ledger.file;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WorkstreamAttention(
        WorkstreamAttentionCategory category,
        String severity,
        String title,
        String detail,
        String nextAction,
        String command,
        boolean retrySafe,
        String workflowStatus,
        String currentNodeDefinitionId,
        String sourceNodeInstanceId,
        String domainOutcome,
        List<String> evidenceRefs
) {
    public WorkstreamAttention {
        if (category == null) {
            category = WorkstreamAttentionCategory.UNKNOWN;
        }
        severity = clean(severity);
        title = clean(title);
        detail = clean(detail);
        nextAction = clean(nextAction);
        command = clean(command);
        workflowStatus = clean(workflowStatus);
        currentNodeDefinitionId = clean(currentNodeDefinitionId);
        sourceNodeInstanceId = clean(sourceNodeInstanceId);
        domainOutcome = clean(domainOutcome);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("category", category.name());
        map.put("severity", severity);
        map.put("title", title);
        map.put("detail", detail);
        map.put("nextAction", nextAction);
        map.put("command", command);
        map.put("retrySafe", retrySafe);
        map.put("workflowStatus", workflowStatus);
        map.put("currentNodeDefinitionId", currentNodeDefinitionId);
        map.put("sourceNodeInstanceId", sourceNodeInstanceId);
        map.put("domainOutcome", domainOutcome);
        map.put("evidenceRefs", evidenceRefs);
        return map;
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
