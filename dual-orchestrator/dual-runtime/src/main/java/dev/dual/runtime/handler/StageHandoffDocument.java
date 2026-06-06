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
 * Typed boundary for artifacts/next-stage/handoff.json.
 *
 * Markdown handoff text is still flexible, but once it becomes JSON the runtime
 * should consume this record instead of spreading hand-written map lookups.
 */
record StageHandoffDocument(
        int schemaVersion,
        String workstreamId,
        String stageId,
        String iterationId,
        String sourceHandoffPath,
        List<String> completedWork,
        List<String> changedFiles,
        List<String> openRisks,
        String verificationState,
        String generatedAt
) {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    static Optional<StageHandoffDocument> read(Path path, ObjectMapper objectMapper) {
        try {
            return Optional.of(from(objectMapper.readValue(path.toFile(), MAP_TYPE)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    static StageHandoffDocument from(Map<String, Object> document) {
        return new StageHandoffDocument(
                intValue(document.get("schemaVersion"), 1),
                text(document.get("workstreamId")),
                text(document.get("stageId")),
                text(document.get("iterationId")),
                text(document.get("sourceHandoffPath")),
                stringList(document.get("completedWork")),
                stringList(document.get("changedFiles")),
                stringList(document.get("openRisks")),
                text(document.get("verificationState")),
                text(document.get("generatedAt"))
        );
    }

    Map<String, Object> toMap() {
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", schemaVersion);
        document.put("workstreamId", workstreamId);
        document.put("stageId", stageId);
        document.put("iterationId", iterationId);
        document.put("sourceHandoffPath", sourceHandoffPath);
        document.put("completedWork", completedWork);
        document.put("changedFiles", changedFiles);
        document.put("openRisks", openRisks);
        document.put("verificationState", verificationState);
        document.put("generatedAt", generatedAt);
        return document;
    }

    boolean hasCompletedWork() {
        return !completedWork.isEmpty();
    }

    boolean hasChangedFiles() {
        return !changedFiles.isEmpty();
    }

    boolean hasOpenRisks() {
        return !openRisks.isEmpty();
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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
