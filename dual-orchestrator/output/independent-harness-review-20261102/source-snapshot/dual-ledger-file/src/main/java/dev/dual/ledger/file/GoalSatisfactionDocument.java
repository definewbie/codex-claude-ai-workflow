package dev.dual.ledger.file;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GoalSatisfactionDocument(
        int schemaVersion,
        String workstreamId,
        String workflowInstanceId,
        String nodeInstanceId,
        String nodeDefinitionId,
        String iterationId,
        String outcome,
        GoalDomainOutcome domainOutcome,
        String reason,
        List<String> evidenceRefs,
        Map<String, Object> sourceResult,
        Instant generatedAt
) {
    public GoalSatisfactionDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(workstreamId, "workstreamId");
        requireText(workflowInstanceId, "workflowInstanceId");
        requireText(nodeInstanceId, "nodeInstanceId");
        requireText(nodeDefinitionId, "nodeDefinitionId");
        requireText(iterationId, "iterationId");
        requireText(outcome, "outcome");
        if (domainOutcome == null) {
            throw new IllegalArgumentException("domainOutcome is required");
        }
        reason = reason == null ? "" : reason;
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        evidenceRefs.forEach(value -> requireText(value, "evidenceRefs"));
        sourceResult = copyMapAllowingNullValues(sourceResult);
    }

    private static Map<String, Object> copyMapAllowingNullValues(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("sourceResult key is required");
            }
            copy.put(key, entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
