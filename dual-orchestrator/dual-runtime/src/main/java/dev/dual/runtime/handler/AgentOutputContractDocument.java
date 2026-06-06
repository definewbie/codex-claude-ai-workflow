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
        String decision,
        String summary,
        boolean blocking,
        List<String> mustFix,
        List<String> missingEvidence,
        List<String> openRisks,
        List<String> changedFiles,
        String verificationSummary,
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
                text(document.get("decision")),
                text(document.get("summary")),
                booleanValue(document.get("blocking")),
                meaningfulList(document.get("must_fix")),
                meaningfulList(document.get("missing_evidence")),
                meaningfulList(document.get("open_risks")),
                stringList(document.get("changed_files")),
                text(document.get("verification_summary")),
                stringMap(document.get("control")),
                FailureFields.from(objectMap(document.get("failure")))
        );
    }

    boolean hasDecision() {
        return !decision.isBlank();
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
    }

    private static List<String> meaningfulList(Object value) {
        return stringList(value).stream()
                .filter(item -> {
                    var normalized = item.trim().toLowerCase();
                    return !normalized.isBlank()
                            && !normalized.equals("none")
                            && !normalized.equals("none.")
                            && !normalized.equals("none blocking")
                            && !normalized.equals("none blocking.")
                            && !normalized.equals("no blocking issues")
                            && !normalized.equals("no blocking issues.")
                            && !normalized.equals("n/a")
                            && !normalized.equals("na")
                            && !normalized.equals("无")
                            && !normalized.equals("无。");
                })
                .toList();
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
}
