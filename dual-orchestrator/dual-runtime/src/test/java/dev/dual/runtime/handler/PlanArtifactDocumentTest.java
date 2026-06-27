package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the typed {@code plan_artifact} model that replaces Markdown-section
 * parsing as the source of truth for the plan body (Step 1: additive, no
 * workflow behavior change yet).
 */
class PlanArtifactDocumentTest {

    private static Map<String, Object> fullMap() {
        var m = new LinkedHashMap<String, Object>();
        m.put("artifact_type", "plan_artifact");
        m.put("schema_version", 1);
        m.put("implementation_steps", List.of("step 1", "step 2"));
        m.put("file_level_changes", List.of("src/a.js"));
        m.put("validation_plan", List.of("npm test"));
        m.put("rollback_notes", "revert src/a.js");
        m.put("known_risks", List.of("flaky test"));
        m.put("human_review_checklist", List.of("confirm scope"));
        return m;
    }

    @Test
    void fromReadsAllFields() {
        var doc = PlanArtifactDocument.from(fullMap());
        assertEquals("plan_artifact", doc.artifactType());
        assertEquals(1, doc.schemaVersion());
        assertEquals(List.of("step 1", "step 2"), doc.implementationSteps());
        assertEquals(List.of("src/a.js"), doc.fileLevelChanges());
        assertEquals(List.of("npm test"), doc.validationPlan());
        assertEquals("revert src/a.js", doc.rollbackNotes());
        assertEquals(List.of("flaky test"), doc.knownRisks());
        assertEquals(List.of("confirm scope"), doc.humanReviewChecklist());
    }

    @Test
    void missingRequiredFieldEmptyWhenComplete() {
        assertTrue(PlanArtifactDocument.from(fullMap()).missingRequiredField().isEmpty());
    }

    @Test
    void missingRequiredFieldReportsEachMissingField() {
        for (String key : List.of(
                "implementation_steps", "file_level_changes", "validation_plan",
                "rollback_notes", "known_risks", "human_review_checklist")) {
            var m = fullMap();
            m.put(key, m.get(key) instanceof List ? List.of() : "");
            var missing = PlanArtifactDocument.from(m).missingRequiredField();
            assertTrue(missing.isPresent(), "expected missing for " + key);
            assertEquals(key, missing.get(), "reported field should match missing key " + key);
        }
    }

    @Test
    void fromToleratesMissingKeysAsEmptyAndReportsFirstMissing() {
        var doc = PlanArtifactDocument.from(Map.of("artifact_type", "plan_artifact"));
        assertEquals("plan_artifact", doc.artifactType());
        assertTrue(doc.implementationSteps().isEmpty());
        assertTrue(doc.rollbackNotes().isBlank());
        var missing = doc.missingRequiredField();
        assertTrue(missing.isPresent());
        assertEquals("implementation_steps", missing.get());
    }
}
