package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.arbitration.FailureCategory;
import dev.dual.core.workflow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FailureClassifyHandlerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WS = "ws-classification";
    private static final String REVIEW = "acceptance.codex_verifier_review";
    @TempDir Path root;

    private ObjectNode envelope(FailureCategory category) throws Exception {
        var prompt = AgentResultPromptContract.render(WS, "run-review", REVIEW, "ACCEPT",
                ContractRegistry.forNode(REVIEW).allowedDecisions());
        for (var value : FailureCategory.values()) assertTrue(prompt.contains(value.name()));
        var tree = (ObjectNode) JSON.readTree(prompt.substring(prompt.indexOf("{")));
        var result = (ObjectNode) tree.get("result");
        result.put("decision", "REJECT");
        result.put("lifecycle_status", "REJECTED");
        var failure = (ObjectNode) result.get("failure");
        failure.put("category_hint", category.name());
        switch (category) {
            case CONTRACT_GAP, CONTRACT_AMBIGUITY -> failure.putArray("contract_gaps").add("Specify the service retention contract");
            case IMPLEMENTATION_BUG -> result.putArray("must_fix").add("Repair site/README.md or service/main.py");
            case VERIFICATION_INSUFFICIENT -> {
                failure.putArray("missing_tests").add("Run the Java tool test suite");
                failure.put("verification_remediation", "EVIDENCE_COLLECTION");
            }
            case CONTEXT_INSUFFICIENT -> failure.putArray("context_gaps").add("Provide the deployment input");
            case TOOL_LIMITATION, REVIEW_NOISE -> failure.put("reason", "Explicit prerequisite or reviewer reevaluation needed");
        }
        return tree;
    }

    private NodeResult publish(ObjectNode tree) throws Exception {
        String ref = ".agent/workstreams/" + WS + "/runs/run-review/outputs/review.json";
        Files.createDirectories(root.resolve(ref).getParent());
        JSON.writeValue(root.resolve(ref).toFile(), tree);
        return new ArtifactContractValidator().validate(root, WS, "node-review", new NodeDefinition(
                REVIEW, NodeKind.TASK, "acpx.agent_run", Map.of()), NodeResult.completed(
                "completed", List.of(ref), Map.of("run_id", "run-review")));
    }

    private Path sidecar() { return root.resolve(".agent/workstreams/" + WS + "/runs/run-review/outputs/review.contract.json"); }
    private String sidecarRef() { return root.relativize(sidecar()).toString(); }
    private Path classification() { return root.resolve(".agent/workstreams/" + WS + "/artifacts/failure-classification.json"); }

    private NodeResult classify() throws Exception {
        var instance = new NodeInstance("node-classify", "acceptance.classify_failure", NodeKind.TASK,
                "acceptance.failure_classify", NodeStatus.READY, "stage-acceptance", "iter-001",
                List.of(sidecarRef()), List.of(), Map.of(), "", Instant.now(), null);
        return new FailureClassifyHandler().execute(new TaskHandler.TaskContext(root, WS,
                new NodeDefinition("acceptance.classify_failure", NodeKind.TASK, "acceptance.failure_classify", Map.of()),
                instance));
    }

    @Test
    void renderParsePublishColdReadClassifyEveryCategory() throws Exception {
        for (var category : FailureCategory.values()) {
            Files.deleteIfExists(classification());
            var tree = envelope(category);
            var parsed = new AgentResultEnvelopeParser().parse(tree.toString());
            assertNotNull(parsed.envelope(), category.name());
            assertTrue(new AgentResultEnvelopeValidator().validate(parsed.envelope(), WS, "run-review",
                    ContractRegistry.forNode(REVIEW)).valid(), category.name());
            var published = publish(tree);
            assertEquals(NodeStatus.COMPLETED, published.status(), published.toString());
            AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
            var cold = AgentEvidenceIndex.latestDecisionFromRefs(root, WS, List.of(sidecarRef()), new ObjectMapper());
            assertNotNull(cold.entry(), cold.toString());
            assertEquals(category.name(), cold.entry().document().failure().categoryHint());
            var classified = classify();
            if (category == FailureCategory.CONTRACT_GAP || category == FailureCategory.CONTRACT_AMBIGUITY) {
                assertEquals("ACCEPTANCE_REWORK_BASELINE_INVALID", classified.outcome());
                assertFalse(Files.exists(classification()), "no contract classification writes without approved baseline");
            } else {
                assertEquals("REJECT_" + category, classified.outcome(), classified.toString());
                assertEquals(category.name(), JSON.readTree(classification().toFile()).path("category").asText());
                assertTrue(classified.outputRefs().contains(sidecarRef()), "retain original mixed evidence");
            }
        }
    }

    @Test
    void mixedProductRepairCannotBeOverriddenByMissingVerification() throws Exception {
        var tree = envelope(FailureCategory.IMPLEMENTATION_BUG);
        var result = (ObjectNode) tree.get("result");
        var failure = (ObjectNode) result.get("failure");
        failure.putArray("missing_tests").add("Collect existing test output");
        failure.put("verification_remediation", "EVIDENCE_COLLECTION");
        result.putArray("missing_evidence").add("Desktop UI record");
        assertEquals(NodeStatus.COMPLETED, publish(tree).status());
        AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
        var classified = classify();
        assertEquals("REJECT_IMPLEMENTATION_BUG", classified.outcome());
        var evidence = JSON.readTree(classification().toFile()).path("evidence").toString();
        assertTrue(evidence.contains("Desktop UI record"));
        assertTrue(evidence.contains("Collect existing test output"));
    }

    @Test
    void mechanicalCombinationMatrixAgreesAtEnvelopePublicationAndColdRead() throws Exception {
        // Columns: category, contract gaps, must-fix, out-of-scope, missing tests,
        // context gaps, reason, missing evidence, remediation, expected validity.
        var rows = List.of(
                "CONTRACT_GAP|1|1|1|1|1|1|1|TEST_CODE_CHANGE|true",
                "CONTRACT_AMBIGUITY|1|1|1|1|1|1|1|EVIDENCE_COLLECTION|true",
                "CONTRACT_GAP|0|1|0|0|0|0|0|NONE|false",
                "IMPLEMENTATION_BUG|0|1|0|0|0|0|0|NONE|true",
                "IMPLEMENTATION_BUG|0|0|1|0|0|0|0|NONE|true",
                "IMPLEMENTATION_BUG|0|0|0|1|0|0|0|TEST_CODE_CHANGE|true",
                "IMPLEMENTATION_BUG|0|1|0|1|0|0|1|EVIDENCE_COLLECTION|true",
                "IMPLEMENTATION_BUG|0|0|0|1|0|1|1|EVIDENCE_COLLECTION|false",
                "IMPLEMENTATION_BUG|1|1|0|0|0|0|0|NONE|false",
                "VERIFICATION_INSUFFICIENT|0|0|0|0|0|0|1|NONE|true",
                "VERIFICATION_INSUFFICIENT|0|1|0|1|0|0|0|EVIDENCE_COLLECTION|true",
                "VERIFICATION_INSUFFICIENT|0|0|0|1|0|0|0|TEST_CODE_CHANGE|false",
                "VERIFICATION_INSUFFICIENT|0|0|1|1|0|0|0|EVIDENCE_COLLECTION|false",
                "VERIFICATION_INSUFFICIENT|0|0|0|1|1|0|0|EVIDENCE_COLLECTION|false",
                "VERIFICATION_INSUFFICIENT|0|0|0|0|0|1|0|NONE|false",
                "CONTEXT_INSUFFICIENT|0|0|0|1|1|0|1|EVIDENCE_COLLECTION|true",
                "CONTEXT_INSUFFICIENT|0|0|0|0|0|1|0|NONE|false",
                "CONTEXT_INSUFFICIENT|0|0|1|0|1|1|0|NONE|false",
                "CONTEXT_INSUFFICIENT|0|0|0|1|1|1|0|TEST_CODE_CHANGE|false",
                "TOOL_LIMITATION|0|0|0|1|0|1|1|EVIDENCE_COLLECTION|true",
                "TOOL_LIMITATION|0|0|0|0|0|0|0|NONE|false",
                "TOOL_LIMITATION|0|0|1|0|0|1|0|NONE|false",
                "TOOL_LIMITATION|0|0|0|1|0|1|0|TEST_CODE_CHANGE|false",
                "REVIEW_NOISE|0|0|0|0|0|1|0|NONE|true",
                "REVIEW_NOISE|0|1|0|0|0|1|0|NONE|false",
                "REVIEW_NOISE|0|0|0|0|0|1|1|NONE|false",
                "REVIEW_NOISE|0|0|0|1|0|1|0|EVIDENCE_COLLECTION|false",
                "REVIEW_NOISE|0|0|0|0|1|1|0|NONE|false",
                "IMPLEMENTATION_BUG|0|1|0|1|0|0|0|NONE|false",
                "IMPLEMENTATION_BUG|0|1|0|0|0|0|0|TEST_CODE_CHANGE|false");
        for (var row : rows) {
            var values = row.split("\\|");
            var tree = envelope(FailureCategory.valueOf(values[0]));
            var result = (ObjectNode) tree.get("result");
            var failure = (ObjectNode) result.get("failure");
            String[] keys = {"contract_gaps", "must_fix", "out_of_scope_files", "missing_tests",
                    "context_gaps", "reason", "missing_evidence"};
            for (int i = 0; i < keys.length; i++) {
                var owner = i == 1 || i == 6 ? result : failure;
                if (i == 5) owner.put(keys[i], values[i + 1].equals("1") ? "Explicit reason" : "");
                else {
                    var list = owner.putArray(keys[i]);
                    if (values[i + 1].equals("1")) list.add("A concrete fact, not a routing keyword");
                }
            }
            failure.put("verification_remediation", values[8]);
            boolean expected = Boolean.parseBoolean(values[9]);
            var parsed = new AgentResultEnvelopeParser().parse(tree.toString());
            assertNotNull(parsed.envelope(), row);
            assertEquals(expected, new AgentResultEnvelopeValidator().validate(parsed.envelope(), WS,
                    "run-review", ContractRegistry.forNode(REVIEW)).valid(), row);
            Files.deleteIfExists(sidecar());
            assertEquals(expected ? NodeStatus.COMPLETED : NodeStatus.FAILED, publish(tree).status(), row);
            if (expected) {
                AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
                assertNotNull(AgentEvidenceIndex.latestDecisionFromRefs(root, WS, List.of(sidecarRef()), JSON).entry(), row);
            } else assertFalse(Files.exists(sidecar()), row);
        }
    }

    @Test
    void legacyUnknownAndContradictoryCategoryFailBeforePublicationAndOnColdReread() throws Exception {
        for (String category : List.of("", "implementation_bug", "IMPLEMENTATION_BUG ", "MISSING_TEST", "UNKNOWN",
                "CONTRACT_GAP", "REVIEW_NOISE", "VERIFICATION_INSUFFICIENT")) {
            var invalid = envelope(FailureCategory.IMPLEMENTATION_BUG);
            ((ObjectNode) invalid.path("result").path("failure")).put("category_hint", category);
            Files.deleteIfExists(sidecar());
            Files.deleteIfExists(classification());
            assertEquals(NodeStatus.FAILED, publish(invalid).status(), category);
            assertFalse(Files.exists(sidecar()), category);

            assertEquals(NodeStatus.COMPLETED, publish(envelope(FailureCategory.IMPLEMENTATION_BUG)).status());
            var persisted = (ObjectNode) JSON.readTree(sidecar().toFile());
            ((ObjectNode) persisted.get("failure")).put("category_hint", category);
            JSON.writeValue(sidecar().toFile(), persisted);
            // Give invalid historical bytes a valid lineage: semantic checks must still reject.
            AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
            assertNull(AgentEvidenceIndex.latestDecisionFromRefs(root, WS, List.of(sidecarRef()), JSON).entry(), category);
            assertEquals(NodeStatus.FAILED, classify().status(), category);
            assertFalse(Files.exists(classification()), category);
        }
    }

    @Test
    void unknownRemediationCannotDisappearDuringSidecarParsing() throws Exception {
        assertEquals(NodeStatus.COMPLETED, publish(envelope(FailureCategory.IMPLEMENTATION_BUG)).status());
        var persisted = (ObjectNode) JSON.readTree(sidecar().toFile());
        ((ObjectNode) persisted.get("failure")).put("verification_remediation", "GUESS");
        JSON.writeValue(sidecar().toFile(), persisted);
        AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
        assertNull(AgentEvidenceIndex.latestDecisionFromRefs(root, WS, List.of(sidecarRef()), JSON).entry());
        assertEquals(NodeStatus.FAILED, classify().status());
        assertFalse(Files.exists(classification()));
    }

    @Test
    void successKeepsExistingStageOutcomeGuardAndRejectsFailureCategory() throws Exception {
        var tree = envelope(FailureCategory.REVIEW_NOISE);
        var result = (ObjectNode) tree.get("result");
        result.put("decision", "ACCEPT_WITH_NOTES");
        result.put("lifecycle_status", "ACCEPTED_WITH_NOTES");
        assertEquals(NodeStatus.FAILED, publish(tree).status());
        ((ObjectNode) result.get("failure")).put("category_hint", "");
        result.put("blocking", true);
        assertEquals(NodeStatus.FAILED, publish(tree).status());
        result.put("blocking", false);
        result.putArray("must_fix").add("Unresolved issue");
        assertEquals(NodeStatus.FAILED, publish(tree).status());
        result.putArray("must_fix");
        result.putArray("missing_evidence").add("Optional follow-up");
        assertEquals(NodeStatus.COMPLETED, publish(tree).status(), "notes may retain optional evidence");
        AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
        assertEquals("ACCEPTANCE_CLASSIFICATION_NOT_REQUIRED", classify().outcome());
    }

    @Test
    void acceptedSidecarAllowsAbsentRemediationButRejectsUnknownRemediation() throws Exception {
        var tree = envelope(FailureCategory.REVIEW_NOISE);
        var result = (ObjectNode) tree.get("result");
        result.put("decision", "ACCEPT");
        result.put("lifecycle_status", "ACCEPTED");
        var failure = (ObjectNode) result.get("failure");
        failure.put("category_hint", "");
        failure.put("reason", "");
        assertEquals(NodeStatus.COMPLETED, publish(tree).status());
        var persisted = (ObjectNode) JSON.readTree(sidecar().toFile());
        ((ObjectNode) persisted.get("failure")).remove("verification_remediation");
        JSON.writeValue(sidecar().toFile(), persisted);
        AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
        assertNotNull(AgentEvidenceIndex.latestDecisionFromRefs(root, WS, List.of(sidecarRef()), JSON).entry());
        assertEquals("ACCEPTANCE_CLASSIFICATION_NOT_REQUIRED", classify().outcome());

        ((ObjectNode) persisted.get("failure")).put("verification_remediation", "UNKNOWN");
        JSON.writeValue(sidecar().toFile(), persisted);
        AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
        assertNull(AgentEvidenceIndex.latestDecisionFromRefs(root, WS, List.of(sidecarRef()), JSON).entry());
        assertEquals(NodeStatus.FAILED, classify().status());
        assertFalse(Files.exists(classification()));
    }

    @Test
    void missingOrTamperedSidecarNeverProducesClassification() throws Exception {
        assertEquals(NodeStatus.FAILED, classify().status());
        assertEquals(NodeStatus.COMPLETED, publish(envelope(FailureCategory.IMPLEMENTATION_BUG)).status());
        AgentOutputLineageFixture.completeAndPublish(root, WS, sidecar());
        Files.writeString(sidecar(), Files.readString(sidecar()) + " ");
        assertEquals(NodeStatus.FAILED, classify().status());
        assertFalse(Files.exists(classification()));
    }
}
