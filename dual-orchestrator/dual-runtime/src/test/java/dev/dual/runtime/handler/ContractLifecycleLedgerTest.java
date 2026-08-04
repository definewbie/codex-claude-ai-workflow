package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContractLifecycleLedgerTest {

    private static final String WORKSTREAM_ID = "ws-lifecycle-001";

    @Test
    void contractCompileEmitsEventMetadata(@TempDir Path tempDir) throws Exception {
        var handler = new ContractCompileHandler();
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve(WORKSTREAM_ID);
        Files.createDirectories(workstreamDir);

        var requirementText = """
                # Feature Request

                Add user authentication with JWT tokens

                Files to modify: `src/auth/Login.java`

                Constraints: Max 200 lines per file
                """;
        Files.writeString(workstreamDir.resolve("requirement.md"), requirementText, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("README.md"), "# Fixture\n", StandardCharsets.UTF_8);
        var reviewRef = ApprovedTaskContractProposalFixture.publish(tempDir, WORKSTREAM_ID, """
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 3,
                  "objective": "Add user authentication with JWT tokens",
                  "scope": {
                    "included": [{"path": "README.md", "operation": "MODIFY"}],
                    "excluded": ["No external dependencies"]
                  },
                  "acceptance_criteria": [
                    {"id": "AC-1", "text": "Authentication behavior is described"},
                    {"id": "AC-2", "text": "Scope remains narrow"}
                  ],
                  "constraints": [{"id": "CONSTRAINT-1", "text": "Max 200 lines per file"}],
                  "non_goals": [{"id": "NON_GOAL-1", "text": "No external dependencies"}],
                  "risk_areas": [{"id": "RISK-1", "text": "Authentication flow correctness"}],
                  "test_requirements": [{"id": "TEST-1", "text": "Run relevant tests"}],
                  "evidence_refs": ["README.md"],
                  "open_questions": [],
                  "revision_responses": []
                }
                """);

        var context = new TaskHandler.TaskContext(
                tempDir,
                WORKSTREAM_ID,
                new NodeDefinition("node-1", NodeKind.TASK, "contract.compile",
                        Map.of("required_subject", "approved_task_contract_proposal")),
                new NodeInstance(
                        "inst-1", "node-1", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-1", "iter-001",
                        List.of(reviewRef), List.of(), Map.of(), "run-1",
                        Instant.now(), null
                )
        );

        var result = handler.execute(context);

        // Verify result contains contract metadata fields
        assertNotNull(result.result().get("contract_id"),
                "result should contain contract_id");
        assertNotNull(result.result().get("contract_status"),
                "result should contain contract_status");
        assertNotNull(result.result().get("generated_at"),
                "result should contain generated_at");
    }

    @Test
    void contractEventTypesExistAsConstants() {
        // Emitted events
        assertEquals("contract_compilation_completed", ContractEventTypes.COMPILATION_COMPLETED);
        assertEquals("failure_classified", ContractEventTypes.FAILURE_CLASSIFIED);

        // Reserved for future gate-layer emission (not yet wired)
        assertEquals("contract_review_completed", ContractEventTypes.REVIEW_COMPLETED);
        assertEquals("contract_approved", ContractEventTypes.APPROVED);
        assertEquals("contract_requires_clarification", ContractEventTypes.REQUIRES_CLARIFICATION);
        assertEquals("contract_scope_reduction_requested", ContractEventTypes.SCOPE_REDUCTION_REQUESTED);
        assertEquals("contract_rejected", ContractEventTypes.REJECTED);
    }

}
