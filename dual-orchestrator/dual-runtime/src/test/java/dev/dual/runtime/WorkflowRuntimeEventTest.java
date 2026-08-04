package dev.dual.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.ApprovedTaskContractProposalFixture;
import dev.dual.runtime.handler.TaskHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that WorkflowRuntime emits domain events into events.jsonl
 * after handler execution.
 */
class WorkflowRuntimeEventTest {
    @TempDir
    Path tempDir;

    @Test
    void contractCompile_emitsCompilationCompletedEvent() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-evt-compile"));
        Files.createDirectories(paths.workstreamDir());
        Files.writeString(paths.workstreamDir().resolve("requirement.md"),
                "Add user authentication\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("README.md"), "# Fixture\n", StandardCharsets.UTF_8);
        var reviewRef = ApprovedTaskContractProposalFixture.publish(tempDir, "ws-evt-compile", """
                {
                  "artifact_type": "task_contract_proposal",
                  "schema_version": 3,
                  "objective": "Add user authentication",
                  "scope": {
                    "included": [{"path": "README.md", "operation": "MODIFY"}],
                    "excluded": ["No external dependencies"]
                  },
                  "acceptance_criteria": [
                    {"id": "AC-1", "text": "Authentication behavior is described"},
                    {"id": "AC-2", "text": "Scope remains narrow"}
                  ],
                  "constraints": [{"id": "CONSTRAINT-1", "text": "Keep implementation focused"}],
                  "non_goals": [{"id": "NON_GOAL-1", "text": "No external dependencies"}],
                  "risk_areas": [{"id": "RISK-1", "text": "Authentication flow correctness"}],
                  "test_requirements": [{"id": "TEST-1", "text": "Run relevant tests"}],
                  "evidence_refs": ["README.md"],
                  "open_questions": [],
                  "revision_responses": []
                }
                """);

        var definition = new WorkflowDefinition(
                "test-events", 1, "proposal.seed",
                List.of(
                        new NodeDefinition("proposal.seed", NodeKind.TASK, "proposal.seed", Map.of("review_ref", reviewRef)),
                        new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile",
                                Map.of("required_subject", "approved_task_contract_proposal"))
                ),
                List.of(new EdgeDefinition("proposal.seed", "completed", "contract.compile"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-evt-compile", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(
                        List.of(new ProposalSeedHandler(), new dev.dual.runtime.handler.ContractCompileHandler()),
                        List.of(), List.of())
        );
        runtime.resume(new WorkflowCommand(tempDir, "ws-evt-compile"));
        runtime.resume(new WorkflowCommand(tempDir, "ws-evt-compile"));

        var events = Files.readString(paths.eventsJsonl(), StandardCharsets.UTF_8);
        assertTrue(events.contains("contract_compilation_completed"),
                "events.jsonl should contain contract_compilation_completed event");
        assertTrue(events.contains("handler_type"),
                "event payload should contain common field handler_type");
        assertTrue(events.contains("node_id"),
                "event payload should contain common field node_id");
        assertTrue(events.contains("contract_id"),
                "event payload should contain contract_id");
    }

    @Test
    void failureClassify_emitsFailureClassifiedEvent() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-evt-classify"));
        Files.createDirectories(paths.workstreamDir());

        // Set up task-contract.json (read by FailureClassifyHandler)
        var artifactsDir = paths.workstreamDir().resolve("artifacts");
        Files.createDirectories(artifactsDir);
        var mapper = new ObjectMapper().registerModule(
                new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        var contract = new dev.dual.core.contract.TaskContract(
                "tc-test", 1, java.time.Instant.parse("2026-05-30T10:00:00Z"),
                "Test objective",
                new dev.dual.core.contract.TaskContract.Scope(
                        List.of(new dev.dual.core.contract.TaskContract.ScopeEntry(
                                "src/", dev.dual.core.contract.TaskContract.ScopeOperation.MODIFY)),
                        List.of()),
                List.of("AC1", "AC2"), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(),
                dev.dual.core.contract.ContractStatus.READY_FOR_REVIEW, 1,
                new dev.dual.core.contract.TaskContract.Source("req.md", "test")
        );
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                artifactsDir.resolve("task-contract.json").toFile(), contract);

        // Use the StubClassifyHandler to simulate classify_failure with controlled result
        var definition = new WorkflowDefinition(
                "test-events", 1, "acceptance.classify_failure",
                List.of(new NodeDefinition("acceptance.classify_failure",
                        NodeKind.TASK, "acceptance.failure_classify", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-evt-classify", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(
                        List.of(new StubClassifyHandler()),
                        List.of(), List.of())
        );
        runtime.resume(new WorkflowCommand(tempDir, "ws-evt-classify"));

        var events = Files.readString(paths.eventsJsonl(), StandardCharsets.UTF_8);
        assertTrue(events.contains("failure_classified"),
                "events.jsonl should contain failure_classified event");
        assertTrue(events.contains("handler_type"),
                "event payload should contain common field handler_type");
        assertTrue(events.contains("IMPLEMENTATION_BUG"),
                "event payload should contain category value");
    }

    @Test
    void unknownHandler_noDomainEvent() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-evt-unknown"));
        Files.createDirectories(paths.workstreamDir());

        var definition = new WorkflowDefinition(
                "test-events", 1, "artifact.validate",
                List.of(new NodeDefinition("artifact.validate", NodeKind.TASK, "artifact.validate", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-evt-unknown", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(
                        List.of(new dev.dual.runtime.handler.ArtifactValidateHandler()),
                        List.of(), List.of())
        );
        runtime.resume(new WorkflowCommand(tempDir, "ws-evt-unknown"));

        var events = Files.readString(paths.eventsJsonl(), StandardCharsets.UTF_8);
        // Should have infrastructure events (workflow_started, node_routed, etc.)
        // but NOT domain events
        assertFalse(events.contains("contract_compilation_completed"),
                "artifact.validate should not emit contract_compilation_completed");
        assertFalse(events.contains("failure_classified"),
                "artifact.validate should not emit failure_classified");
    }

    @Test
    void failedHandler_noDomainEvent() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-evt-failed"));
        Files.createDirectories(paths.workstreamDir());

        var definition = new WorkflowDefinition(
                "test-events", 1, "contract.compile",
                List.of(new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-evt-failed", definition);

        var runtime = new WorkflowRuntime(
                ledger,
                new HandlerRegistry(
                        List.of(new FailingContractCompile()),
                        List.of(), List.of())
        );
        runtime.resume(new WorkflowCommand(tempDir, "ws-evt-failed"));

        var events = Files.readString(paths.eventsJsonl(), StandardCharsets.UTF_8);
        assertFalse(events.contains("contract_compilation_completed"),
                "FAILED handler should not emit domain event");
    }

    // --- Test handlers ---

    /** Stub that mimics FailureClassifyHandler output without needing full setup. */
    private static final class StubClassifyHandler implements TaskHandler {
        @Override
        public String type() {
            return "acceptance.failure_classify";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("REJECT_IMPLEMENTATION_BUG", List.of(), Map.of(
                    "category", "IMPLEMENTATION_BUG",
                    "reasoning", "Test classification",
                    "suggested_action", "Fix it",
                    "matched_rule", 3,
                    "review_decision", "REJECT"
            ));
        }
    }

    private static final class ProposalSeedHandler implements TaskHandler {
        @Override
        public String type() {
            return "proposal.seed";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed(
                    "completed",
                    List.of((String) context.definition().config().get("review_ref")),
                    Map.of());
        }
    }

    private static final class FailingContractCompile implements TaskHandler {
        @Override
        public String type() {
            return "contract.compile";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return new NodeResult("failed", NodeStatus.FAILED, List.of(),
                    Map.of("error", "test failure"), "test failure");
        }
    }
}
