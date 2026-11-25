package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed reader for the {@code plan_artifact} envelope emitted by
 * {@code plan.claude_plan}.
 *
 * <p>This is the source of truth for the plan body — it replaces Markdown-section
 * parsing ({@code validateFormalArtifactBody}). The six required fields carry the
 * semantic content that used to live only in Markdown headings; programmatic plan
 * validation reads this typed artifact, not the prose. Markdown may still be
 * written for human reading, but it does not participate in contract / routing /
 * completion decisions.</p>
 */
public record PlanArtifactDocument(
        String artifactType,
        int schemaVersion,
        List<String> implementationSteps,
        List<String> editFiles,
        List<String> referenceFiles,
        List<String> fileLevelChanges,
        List<String> validationPlan,
        String rollbackNotes,
        List<String> knownRisks,
        List<String> humanReviewChecklist,
        boolean editFilesDeclared,
        boolean referenceFilesDeclared,
        List<dev.dual.core.contract.TaskContract.ScopeEntry> files,
        List<dev.dual.core.contract.TaskContract.VerificationBinding> verificationBindings,
        List<dev.dual.core.contract.TaskContract.Reference> authorityRefs
) {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public static PlanArtifactDocument from(Map<String, Object> document) {
        return new PlanArtifactDocument(
                text(document.get("artifact_type")),
                intValue(document.get("schema_version"), 0),
                stringList(document.get("implementation_steps")),
                stringList(document.get("edit_files")),
                stringList(document.get("reference_files")),
                stringList(document.get("file_level_changes")),
                stringList(document.get("validation_plan")),
                text(document.get("rollback_notes")),
                stringList(document.get("known_risks")),
                stringList(document.get("human_review_checklist")),
                document.containsKey("edit_files"),
                document.containsKey("reference_files"),
                typedList(document.get("files"), dev.dual.core.contract.TaskContract.ScopeEntry.class),
                typedList(document.get("verification_bindings"), dev.dual.core.contract.TaskContract.VerificationBinding.class),
                typedList(document.get("authority_refs"), dev.dual.core.contract.TaskContract.Reference.class)
        );
    }

    static Optional<PlanArtifactDocument> read(Path path, ObjectMapper objectMapper) {
        try {
            return Optional.of(from(objectMapper.readValue(path.toFile(), MAP_TYPE)));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Returns the first required field that is empty/blank (using the JSON field
     * name), or empty if the plan body is complete. Drives contract validation:
     * a missing field is a {@code PLAN_ARTIFACT_BODY_MISSING} failure.
     */
    public Optional<String> missingRequiredField() {
        if (schemaVersion != 2) return Optional.of("schema_version");
        if (files.isEmpty()) return Optional.of("files");
        if (verificationBindings.isEmpty()) return Optional.of("verification_bindings");
        if (implementationSteps.isEmpty()) {
            return Optional.of("implementation_steps");
        }
        if (!editFilesDeclared || editFiles.isEmpty()) {
            return Optional.of("edit_files");
        }
        if (!referenceFilesDeclared) {
            return Optional.of("reference_files");
        }
        if (fileLevelChanges.isEmpty()) {
            return Optional.of("file_level_changes");
        }
        if (validationPlan.isEmpty()) {
            return Optional.of("validation_plan");
        }
        if (rollbackNotes.isBlank()) {
            return Optional.of("rollback_notes");
        }
        if (knownRisks.isEmpty()) {
            return Optional.of("known_risks");
        }
        if (humanReviewChecklist.isEmpty()) {
            return Optional.of("human_review_checklist");
        }
        return Optional.empty();
    }

    private static <T> List<T> typedList(Object value, Class<T> type) {
        if (!(value instanceof List<?> values)) throw new IllegalArgumentException("missing typed plan list");
        var mapper = new ObjectMapper();
        return values.stream().map(item -> mapper.convertValue(item, type)).toList();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var item : list) {
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
