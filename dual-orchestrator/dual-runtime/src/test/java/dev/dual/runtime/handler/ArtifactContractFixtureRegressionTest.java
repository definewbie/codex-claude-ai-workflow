package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContractFixtureRegressionTest {
    private static final String FIXTURE_ROOT = "dev/dual/runtime/handler/artifacts/";

    @TempDir
    Path tempDir;

    private final ArtifactContractValidator validator = new ArtifactContractValidator();

    @Test
    void acceptsPublicAcceptanceWithNotesFixture() throws Exception {
        var sidecar = validateFixture(
                "acceptance-with-notes.md",
                "acceptance.codex_verifier_review",
                "acceptance-with-notes.contract.json"
        );

        assertTrue(sidecar.contains("\"decision\" : \"ACCEPT_WITH_NOTES\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"ACCEPTED_WITH_NOTES\""));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 0"));
        assertTrue(sidecar.contains("\"must_fix_count\" : 0"));
        assertTrue(sidecar.contains("\"acceptance_note_count\" : 1"));
    }

    @Test
    void acceptsPublicRejectedFixtureWithBlockingEvidence() throws Exception {
        var sidecar = validateFixture(
                "acceptance-rejected-missing-evidence.md",
                "acceptance.codex_verifier_review",
                "acceptance-rejected-missing-evidence.contract.json"
        );

        assertTrue(sidecar.contains("\"decision\" : \"REJECT\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"REJECTED\""));
        assertTrue(sidecar.contains("\"blocking\" : true"));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 1"));
        assertTrue(sidecar.contains("\"must_fix_count\" : 1"));
    }

    @Test
    void acceptsPublicSelfTestInsufficientEvidenceFixture() throws Exception {
        var sidecar = validateFixture(
                "self-test-insufficient-evidence.md",
                "implement.claude_self_test_report",
                "self-test-insufficient-evidence.contract.json"
        );

        assertTrue(sidecar.contains("\"decision\" : \"SELF_TEST_INCOMPLETE\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"INSUFFICIENT_EVIDENCE\""));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 1"));
    }

    @Test
    void acceptsRealPublicSelfTestCompleteFixture() throws Exception {
        var sidecar = validateFixture(
                "public-real-self-test-complete.md",
                "implement.claude_self_test_report",
                "public-real-self-test-complete.contract.json"
        );

        assertTrue(sidecar.contains("\"source_artifact_type\" : \"self_test_report\""));
        assertTrue(sidecar.contains("\"decision\" : \"SELF_TEST_COMPLETE\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"READY\""));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 0"));
        assertTrue(sidecar.contains("\"acceptance_note_count\" : 1"));
        assertTrue(sidecar.contains("node --test"));
    }

    @Test
    void acceptsRealPublicAcceptanceWithNotesFixture() throws Exception {
        var sidecar = validateFixture(
                "public-real-acceptance-with-notes.md",
                "acceptance.codex_verifier_review",
                "public-real-acceptance-with-notes.contract.json"
        );

        assertTrue(sidecar.contains("\"source_artifact_type\" : \"acceptance_review\""));
        assertTrue(sidecar.contains("\"decision\" : \"ACCEPT_WITH_NOTES\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"ACCEPTED_WITH_NOTES\""));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 0"));
        assertTrue(sidecar.contains("\"must_fix_count\" : 0"));
        assertTrue(sidecar.contains("\"acceptance_note_count\" : 2"));
        assertTrue(sidecar.contains("src/issueWorkflow.js"));
        assertTrue(sidecar.contains("test/issueWorkflow.test.js"));
    }

    @Test
    void acceptsRealPublicStageHandoffFixture() throws Exception {
        var sidecar = validateFixture(
                "public-real-stage-handoff-ready.md",
                "stage.claude_handoff",
                "public-real-stage-handoff-ready.contract.json"
        );

        assertTrue(sidecar.contains("\"source_artifact_type\" : \"stage_handoff\""));
        assertTrue(sidecar.contains("\"decision\" : \"HANDOFF_READY\""));
        assertTrue(sidecar.contains("\"lifecycle_status\" : \"READY\""));
        assertTrue(sidecar.contains("\"completed_work\""));
        assertTrue(sidecar.contains("\"recommended_next_stage\" : \"Merge/close"));
        assertTrue(sidecar.contains("README.md"));
    }

    private String validateFixture(String fixtureName, String nodeId, String sidecarName) throws Exception {
        var outputRef = fixtureName;
        Files.writeString(tempDir.resolve(outputRef), loadFixture(fixtureName), StandardCharsets.UTF_8);

        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("fixture", fixtureName));
        var validated = validator.validate(tempDir, agentNode(nodeId), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains(sidecarName));
        return Files.readString(tempDir.resolve(sidecarName), StandardCharsets.UTF_8);
    }

    private static String loadFixture(String name) {
        var resource = FIXTURE_ROOT + name;
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("missing fixture: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static NodeDefinition agentNode(String nodeId) {
        return new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of());
    }
}
