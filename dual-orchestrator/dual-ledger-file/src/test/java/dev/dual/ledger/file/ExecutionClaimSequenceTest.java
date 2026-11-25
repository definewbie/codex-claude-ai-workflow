package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionClaimSequenceTest {
    @TempDir Path root;

    @Test void preparedBindingIsUniqueAndLegalCompletionRetainsIt() throws Exception {
        var paths = initialize();
        var ledger = LedgerTestFixture.ledger();
        var claim = claim(ledger, paths);
        var ref = Map.of("ref", ".agent/workstreams/claim/runs/one/outputs/prepared.json", "sha256", "a".repeat(64));
        try (var write = ledger.writeSession(paths, "bind prepared run", "prepare")) {
            write.ledger().bindPreparedRun(paths, claim, ref);
            write.commit();
        }
        try (var write = ledger.writeSession(paths, "repeat preparation binding", "repeat")) {
            write.ledger().bindPreparedRun(paths, claim, ref);
            assertThrows(java.io.IOException.class, () -> write.ledger().bindPreparedRun(paths, claim,
                    Map.of("ref", ref.get("ref"), "sha256", "b".repeat(64))));
        }
        try (var write = ledger.writeSession(paths, "complete current execution", "complete")) {
            var scoped = write.ledger();
            scoped.verifyExecutionClaim(paths, claim);
            var result = NodeResult.completed("done", List.of(), Map.of("actual_result", "verified"));
            var completed = scoped.finishNodeExecution(paths, claim, result);
            assertEquals(ref, completed.result().get("prepared_run"));
            assertTrue(completed.result().containsKey("claim"));
            scoped.completeAndRoute(paths, scoped.loadDefinition(paths), claim.nodeInstanceId(), result.status(),
                    result.outcome(), result.outputRefs(), result.result());
            write.commit();
        }
        assertEquals(WorkflowStatus.COMPLETED, ledger.loadCurrent(paths).status());
        try (var read = ledger.readSession(paths)) {
            assertThrows(java.io.IOException.class, () -> read.ledger().verifyExecutionClaim(paths, claim));
        }
    }

    @Test void changedInputsOrTerminalWorkflowCannotConsumeAnOldClaim() throws Exception {
        var paths = initialize();
        var ledger = LedgerTestFixture.ledger();
        var claim = claim(ledger, paths);
        var writer = new AtomicJsonWriter();
        var original = writer.readBytes(paths.nodeJson(claim.nodeInstanceId()));
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) writer.readTree(paths.nodeJson(claim.nodeInstanceId()));
        node.putArray("inputRefs").add("different-approved-input");
        writer.write(paths.nodeJson(claim.nodeInstanceId()), node);
        try (var read = ledger.readSession(paths)) {
            assertThrows(java.io.IOException.class, () -> read.ledger().verifyExecutionClaim(paths, claim));
        }
        writer.writeBytes(paths.nodeJson(claim.nodeInstanceId()), original);
        try (var read = ledger.readSession(paths)) { read.ledger().verifyExecutionClaim(paths, claim); }
        var current = (com.fasterxml.jackson.databind.node.ObjectNode) writer.readTree(paths.workflowCurrentJson());
        current.put("status", "COMPLETED");
        writer.write(paths.workflowCurrentJson(), current);
        try (var read = ledger.readSession(paths)) {
            assertThrows(java.io.IOException.class, () -> read.ledger().verifyExecutionClaim(paths, claim));
        }
    }

    private WorkstreamPaths initialize() throws Exception {
        var paths = new WorkstreamPaths(root, new DualId("claim"));
        var definition = new WorkflowDefinition("claim", 1, "task",
                List.of(new NodeDefinition("task", NodeKind.TASK, "noop", Map.of())), List.of());
        LedgerTestFixture.initialize(LedgerTestFixture.ledger(), paths, "wf-claim", definition);
        return paths;
    }

    private NodeExecutionDocument claim(WorkflowFileLedger ledger, WorkstreamPaths paths) throws Exception {
        try (var write = ledger.writeSession(paths, "claim ready task", "claim")) {
            var scoped = write.ledger();
            var current = scoped.loadCurrent(paths);
            var workflow = scoped.startWorkflowExecution(paths, current, "test");
            var node = scoped.loadNode(paths, current.activeNodeInstanceIds().getFirst());
            var task = scoped.createAgentTask(paths, workflow.workflowExecutionId(), scoped.loadDefinition(paths).node("task"), node);
            var execution = scoped.startNodeExecution(paths, workflow.workflowExecutionId(), node, task, ProcessHandle.current().pid());
            scoped.markAgentTaskRunning(paths, task, execution.nodeExecutionId());
            scoped.markRunning(paths, node.nodeInstanceId(), ProcessHandle.current().pid());
            write.commit();
            return execution;
        }
    }
}
