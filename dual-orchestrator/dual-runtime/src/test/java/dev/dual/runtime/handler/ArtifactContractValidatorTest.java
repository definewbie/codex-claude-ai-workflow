package dev.dual.runtime.handler;

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

class ArtifactContractValidatorTest {
    @TempDir
    Path tempDir;

    private final ArtifactContractValidator validator = new ArtifactContractValidator();

    @Test
    void passesValidAgentOutputContract() throws Exception {
        var outputRef = writeOutput("review.md", """
                Review passed.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Architecture is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains(outputRef));
        assertTrue(validated.outputRefs().contains("review.contract.json"));
    }

    @Test
    void writesContractSidecarForValidAgentOutput() throws Exception {
        var outputRef = writeOutput("review.md", """
                Review passed.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Architecture is ready.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        var sidecarRef = "review.contract.json";
        assertTrue(validated.outputRefs().contains(sidecarRef));
        assertEquals(sidecarRef, validated.result().get("contract_sidecar_ref"));

        var sidecar = Files.readString(tempDir.resolve(sidecarRef));
        assertTrue(sidecar.contains("\"artifact_type\" : \"agent_output_contract\""));
        assertTrue(sidecar.contains("\"node_id\" : \"design.codex_arch_review\""));
        assertTrue(sidecar.contains("\"decision\" : \"READY_FOR_ARCH_REVIEW\""));
        assertTrue(sidecar.contains("\"summary\" : \"Architecture is ready.\""));
        assertTrue(sidecar.contains("\"source_output_ref\" : \"review.md\""));
    }

    @Test
    void sidecarIncludesPipelineFieldsFromKnownMarkdownSections() throws Exception {
        var outputRef = writeOutput("acceptance.md", """
                # Acceptance

                ## Gaps / Must Fix

                - Add missing empty-state assertion.
                - Confirm no out-of-scope files changed.

                ## Changed Files

                - src/main/App.java
                - README.md

                ## Verification Adequacy

                Developer self-test is acceptable, but no regression smoke was run.

                ## Residual Risks

                - Manual QA still needed for mobile layout.

                ```dual-control
                decision: ACCEPT_WITH_NOTES
                summary: Accepted with verification notes.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        validator.validate(tempDir, agentNode("acceptance.codex_verifier_review"), result);

        var sidecar = Files.readString(tempDir.resolve("acceptance.contract.json"));
        assertTrue(sidecar.contains("\"must_fix\""));
        assertTrue(sidecar.contains("Add missing empty-state assertion."));
        assertTrue(sidecar.contains("\"changed_files\""));
        assertTrue(sidecar.contains("src/main/App.java"));
        assertTrue(sidecar.contains("\"verification_summary\" : \"Developer self-test is acceptable, but no regression smoke was run.\""));
        assertTrue(sidecar.contains("\"open_risks\""));
        assertTrue(sidecar.contains("Manual QA still needed for mobile layout."));
    }

    @Test
    void sidecarIncludesStructuredVerificationEvidence() throws Exception {
        var outputRef = writeOutput("self-test.md", """
                # Self-Test

                ## Checks Run

                - npm test
                - inspected changed route manually

                ## Checks Skipped

                - device smoke: simulator unavailable

                ## Evidence

                - Test output showed 24 passing tests.

                ## Missing Evidence

                - No physical device validation.

                ## Acceptance Notes

                - Runtime visual check should happen before release.

                ```dual-control
                decision: SELF_TEST_COMPLETE
                summary: Self-test completed with one runtime gap.
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "claude"));
        validator.validate(tempDir, agentNode("implement.claude_self_test_report"), result);

        var sidecar = Files.readString(tempDir.resolve("self-test.contract.json"));
        assertTrue(sidecar.contains("\"checks_run\""));
        assertTrue(sidecar.contains("npm test"));
        assertTrue(sidecar.contains("\"checks_skipped\""));
        assertTrue(sidecar.contains("simulator unavailable"));
        assertTrue(sidecar.contains("\"evidence\""));
        assertTrue(sidecar.contains("24 passing tests"));
        assertTrue(sidecar.contains("\"missing_evidence\""));
        assertTrue(sidecar.contains("No physical device validation."));
        assertTrue(sidecar.contains("\"acceptance_notes\""));
        assertTrue(sidecar.contains("Runtime visual check should happen before release."));
    }

    @Test
    void failsMissingRequiredControlKey() throws Exception {
        var outputRef = writeOutput("review.md", """
                Review passed.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                ```
                """);
        var result = NodeResult.completed("completed", List.of(outputRef), Map.of("agent", "codex"));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.result().get("reason"));
        assertTrue(String.valueOf(validated.result().get("message")).contains("summary"));
    }

    @Test
    void ignoresPreparedAgentRun() throws Exception {
        var result = NodeResult.completed("completed", List.of("prompts/design.md"), Map.of("prepared", true));
        var validated = validator.validate(tempDir, agentNode("design.codex_arch_review"), result);

        assertEquals(result, validated);
    }

    @Test
    void ignoresNonAgentTaskHandler() throws Exception {
        var result = NodeResult.completed("completed", List.of(), Map.of("contract_status", "READY_FOR_REVIEW"));
        var node = new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of());

        assertEquals(result, validator.validate(tempDir, node, result));
    }

    private String writeOutput(String name, String content) throws Exception {
        var path = tempDir.resolve(name);
        Files.writeString(path, content);
        return tempDir.relativize(path).toString();
    }

    private static NodeDefinition agentNode(String nodeId) {
        return new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of());
    }
}
