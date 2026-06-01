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
