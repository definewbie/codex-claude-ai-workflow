package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractCompileHandlerTest {

    private static final String WORKSTREAM_ID = "ws-test-001";

    @Test
    void handlerTypeIsContractCompile() {
        var handler = new ContractCompileHandler();
        assertEquals("contract.compile", handler.type());
    }

    @Test
    void compileProducesReadyForReviewContract(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        Files.createDirectories(workstreamDir);

        var requirementText = """
                # Feature Request

                Add user authentication with JWT tokens

                Files to modify: `src/auth/Login.java`, `src/auth/AuthService.java`

                Constraints: Max 200 lines per file, no external dependencies
                """;
        Files.writeString(workstreamDir.resolve("requirement.md"), requirementText, StandardCharsets.UTF_8);

        var context = new TaskHandler.TaskContext(
                tempDir,
                WORKSTREAM_ID,
                new NodeDefinition("node-1", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeInstance(
                        "inst-1", "node-1", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-1", "iter-1",
                        List.of(), List.of(), Map.of(), "run-1",
                        Instant.now(), null
                )
        );

        var result = handler.execute(context);

        // Verify result structure
        assertEquals("completed", result.outcome());
        assertEquals(NodeStatus.COMPLETED, result.status());
        assertNotNull(result.result());

        // Verify result fields
        assertEquals("tc-ws-test-001-v1", result.result().get("contract_id"));
        assertEquals(ContractStatus.READY_FOR_REVIEW.name(), result.result().get("contract_status"));
        assertEquals(1, result.result().get("contract_version"));
        assertEquals(1, result.result().get("compilation_pass"));
        assertNotNull(result.result().get("generated_at"));

        // Verify artifacts exist
        var artifactsDir = workstreamDir.resolve("artifacts");
        assertTrue(Files.exists(artifactsDir.resolve("task-contract.json")),
                "task-contract.json should exist");
        assertTrue(Files.exists(artifactsDir.resolve("task-contract.md")),
                "task-contract.md should exist");

        // Verify contract content via JSON
        var json = Files.readString(artifactsDir.resolve("task-contract.json"), StandardCharsets.UTF_8);
        assertTrue(json.contains("READY_FOR_REVIEW"), "Contract should be READY_FOR_REVIEW");
        assertTrue(json.contains("Add user authentication with JWT tokens"),
                "Contract should contain the objective");

        // Verify markdown has sections
        var md = Files.readString(artifactsDir.resolve("task-contract.md"), StandardCharsets.UTF_8);
        assertTrue(md.contains("## Acceptance Criteria"), "Markdown should have AC section");
        assertTrue(md.contains("Implementation achieves:"), "Should have default AC");

        // Verify compile method directly
        var contract = handler.compile(requirementText, WORKSTREAM_ID);
        assertFalse(contract.objective().isBlank(), "Objective should not be blank");
        assertFalse(contract.acceptanceCriteria().isEmpty(), "AC should not be empty");
        assertEquals(ContractStatus.READY_FOR_REVIEW, contract.status());
    }
}
