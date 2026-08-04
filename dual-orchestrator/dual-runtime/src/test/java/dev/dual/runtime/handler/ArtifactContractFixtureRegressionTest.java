package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContractFixtureRegressionTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @TempDir
    Path root;

    private final ArtifactContractValidator validator = new ArtifactContractValidator();

    @Test
    void acceptsAcceptanceWithNotes() throws Exception {
        var result = new AgentResultEnvelope.AcceptanceReviewResult(
                "acceptance_review", "ACCEPT_WITH_NOTES", "accepted with notes", "ACCEPTED_WITH_NOTES",
                false, List.of("reviewed diff"), List.of("tests passed"), List.of(), List.of(),
                List.of("minor note"), List.of(), List.of("src/A.java"), "verified", emptyFailure());

        var sidecar = validate("acceptance.codex_verifier_review", result);

        assertTrue(sidecar.contains("\"decision\" : \"ACCEPT_WITH_NOTES\""));
        assertTrue(sidecar.contains("\"acceptance_note_count\" : 1"));
    }

    @Test
    void acceptsRejectedReviewWithBlockingEvidence() throws Exception {
        var result = new AgentResultEnvelope.AcceptanceReviewResult(
                "acceptance_review", "REJECT", "evidence missing", "REJECTED", true,
                List.of("reviewed tests"), List.of(), List.of("test output"), List.of("run tests"),
                List.of(), List.of("unverified behavior"), List.of(), "not verified", emptyFailure());

        var sidecar = validate("acceptance.codex_verifier_review", result);

        assertTrue(sidecar.contains("\"blocking\" : true"));
        assertTrue(sidecar.contains("\"missing_evidence_count\" : 1"));
        assertTrue(sidecar.contains("\"must_fix_count\" : 1"));
    }

    @Test
    void acceptsSelfTestResult() throws Exception {
        var result = new AgentResultEnvelope.SelfTestResult(
                "self_test_report", "SELF_TEST_COMPLETE", "tests complete", "READY",
                List.of("mvn test"), List.of(), List.of("all tests passed"), List.of(),
                List.of("focused suite"), "adequate");

        var sidecar = validate("implement.claude_self_test_report", result);

        assertTrue(sidecar.contains("\"source_artifact_type\" : \"self_test_report\""));
        assertTrue(sidecar.contains("mvn test"));
    }

    @Test
    void acceptsStageHandoffResult() throws Exception {
        var result = new AgentResultEnvelope.StageHandoffResult(
                "stage_handoff", "HANDOFF_READY", "handoff ready", "READY",
                List.of("implemented feature"), List.of("kept API stable"), List.of("README.md"),
                "tests passed", List.of(), "delivery", "review and deliver");

        var sidecar = validate("stage.claude_handoff", result);

        assertTrue(sidecar.contains("\"source_artifact_type\" : \"stage_handoff\""));
        assertTrue(sidecar.contains("README.md"));
    }

    private String validate(String nodeId, AgentResultEnvelope.AgentResult result) throws Exception {
        var output = root.resolve(nodeId.replace('.', '-') + ".json");
        var envelope = new AgentResultEnvelope(1, "ws-fixture", "run-fixture", nodeId, result);
        Files.writeString(output, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
        var nodeResult = NodeResult.completed("completed", List.of(output.getFileName().toString()),
                Map.of("run_id", "run-fixture"));

        var validated = validator.validate(root, "ws-fixture", "node-fixture", agentNode(nodeId), nodeResult);

        assertEquals(NodeStatus.COMPLETED, validated.status(), validated.result().toString());
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        assertTrue(validated.outputRefs().contains(sidecar.getFileName().toString()));
        return Files.readString(sidecar);
    }

    private static NodeDefinition agentNode(String nodeId) {
        return new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of());
    }

    private static AgentResultEnvelope.Failure emptyFailure() {
        return new AgentResultEnvelope.Failure("", AgentResultEnvelope.VerificationRemediation.NONE,
                List.of(), List.of(), List.of(), List.of(), "");
    }
}
