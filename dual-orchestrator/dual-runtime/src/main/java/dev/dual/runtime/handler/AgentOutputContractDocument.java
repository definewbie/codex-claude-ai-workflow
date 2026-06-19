package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed reader for agent output sidecars.
 *
 * Map-based JSON handling is kept at this boundary. Runtime routing,
 * classification, and human guidance should consume this record instead of
 * repeatedly reading stringly-typed fields from raw maps.
 */
record AgentOutputContractDocument(
        String nodeId,
        String stageOutcome,
        String lifecycleStatus,
        String decision,
        String summary,
        String sourceArtifactType,
        boolean blocking,
        List<String> checks,
        List<String> evidence,
        List<String> mustFix,
        List<String> missingEvidence,
        List<String> acceptanceNotes,
        List<String> openRisks,
        List<String> changedFiles,
        String verificationSummary,
        List<String> completedWork,
        List<String> importantDecisions,
        String recommendedNextStage,
        String suggestedNextPrompt,
        Map<String, String> control,
        FailureFields failure
) {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    static Optional<AgentOutputContractDocument> read(Path path, ObjectMapper objectMapper) {
        try {
            return Optional.of(from(objectMapper.readValue(path.toFile(), MAP_TYPE)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    static AgentOutputContractDocument from(Map<String, Object> document) {
        return new AgentOutputContractDocument(
                text(document.get("node_id")),
                firstNonBlank(document.get("stage_outcome"), document.get("lifecycle_status")),
                text(document.get("lifecycle_status")),
                text(document.get("decision")),
                text(document.get("summary")),
                text(document.get("source_artifact_type")),
                booleanValue(document.get("blocking")),
                firstNonEmptyList(document.get("checks"), document.get("checks_run")),
                stringList(document.get("evidence")),
                stringList(document.get("must_fix")),
                stringList(document.get("missing_evidence")),
                stringList(document.get("acceptance_notes")),
                stringList(document.get("open_risks")),
                stringList(document.get("changed_files")),
                text(document.get("verification_summary")),
                stringList(document.get("completed_work")),
                stringList(document.get("important_decisions")),
                text(document.get("recommended_next_stage")),
                text(document.get("suggested_next_prompt")),
                stringMap(document.get("control")),
                FailureFields.from(objectMap(document.get("failure")))
        );
    }

    boolean hasDecision() {
        return !decision.isBlank();
    }

    String canonicalStageOutcome() {
        return stageOutcome.isBlank() ? lifecycleStatus : stageOutcome;
    }

    boolean isStageHandoffArtifact() {
        return "stage_handoff".equals(sourceArtifactType);
    }

    record FailureFields(
            String categoryHint,
            List<String> contractGaps,
            List<String> outOfScopeFiles,
            List<String> missingTests,
            List<String> contextGaps,
            String reason
    ) {
        static FailureFields from(Map<String, Object> document) {
            return new FailureFields(
                    text(document.get("category_hint")),
                    stringList(document.get("contract_gaps")),
                    stringList(document.get("out_of_scope_files")),
                    stringList(document.get("missing_tests")),
                    stringList(document.get("context_gaps")),
                    text(document.get("reason"))
            );
        }

        static FailureFields empty() {
            return new FailureFields("", List.of(), List.of(), List.of(), List.of(), "");
        }

        Map<String, Object> toMap() {
            var document = new LinkedHashMap<String, Object>();
            document.put("category_hint", categoryHint);
            document.put("contract_gaps", contractGaps);
            document.put("out_of_scope_files", outOfScopeFiles);
            document.put("missing_tests", missingTests);
            document.put("context_gaps", contextGaps);
            document.put("reason", reason);
            return document;
        }

        boolean isEmpty() {
            return categoryHint.isBlank()
                    && contractGaps.isEmpty()
                    && outOfScopeFiles.isEmpty()
                    && missingTests.isEmpty()
                    && contextGaps.isEmpty()
                    && reason.isBlank();
        }
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

    private static List<String> firstNonEmptyList(Object first, Object second) {
        var firstList = stringList(first);
        return firstList.isEmpty() ? stringList(second) : firstList;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, String>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return copy;
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

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(text(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(Object first, Object second) {
        var firstText = text(first);
        return firstText.isBlank() ? text(second) : firstText;
    }
}
