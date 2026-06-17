package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed reader for JSON inside <dual-artifact> envelopes.
 *
 * The validator still accepts flexible artifact types, but all map lookups stay
 * at this boundary instead of leaking into runtime contract decisions.
 */
record AgentStructuredArtifactDocument(
        String artifactType,
        int schemaVersion,
        String lifecycleStatus,
        String decision,
        String summary,
        boolean blocking,
        List<String> checks,
        List<String> checksRun,
        List<String> checksSkipped,
        List<String> evidence,
        List<String> missingEvidence,
        List<String> mustFix,
        List<String> acceptanceNotes,
        List<String> openRisks,
        List<String> changedFiles,
        String verificationSummary,
        List<String> completedWork,
        List<String> importantDecisions,
        String verificationState,
        String recommendedNextStage,
        String suggestedNextPrompt,
        AgentOutputContractDocument.FailureFields failure
) {
    static AgentStructuredArtifactDocument from(Map<String, Object> document) {
        return new AgentStructuredArtifactDocument(
                text(document.get("artifact_type")),
                intValue(document.get("schema_version"), 1),
                firstNonBlank(document.get("lifecycle_status"), document.get("status")),
                text(document.get("decision")),
                text(document.get("summary")),
                booleanValue(document.get("blocking")),
                stringList(document.get("checks")),
                stringList(document.get("checks_run")),
                stringList(document.get("checks_skipped")),
                stringList(document.get("evidence")),
                stringList(document.get("missing_evidence")),
                stringList(document.get("must_fix")),
                stringList(document.get("acceptance_notes")),
                stringList(document.get("open_risks")),
                stringList(document.get("changed_files")),
                text(document.get("verification_summary")),
                stringList(document.get("completed_work")),
                stringList(document.get("important_decisions")),
                text(document.get("verification_state")),
                text(document.get("recommended_next_stage")),
                text(document.get("suggested_next_prompt")),
                AgentOutputContractDocument.FailureFields.from(objectMap(document.get("failure")))
        );
    }

    private static String firstNonBlank(Object first, Object second) {
        var firstText = text(first);
        return firstText.isBlank() ? text(second) : firstText;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var item : items) {
            var text = text(item);
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(text(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
