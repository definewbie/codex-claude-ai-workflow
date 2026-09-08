package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.runtime.WorkflowCommand;
import dev.dual.runtime.WorkflowRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AcceptanceRepairInterruptionTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    @TempDir Path root;

    @Test
    void resolvedGateWithUnadvancedCurrentCannotBeReapprovedOrRetried() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "tool.py");
        f.until("gate.human_requirement_review"); f.step();
        var before = Files.readString(f.paths.workflowCurrentJson());
        var current = f.ledger.loadCurrent(f.paths);
        var gateFile = f.paths.workstreamDir().resolve("gates/" + current.openGateIds().getFirst() + ".json");
        var gate = (ObjectNode) JSON.readTree(gateFile.toFile());
        gate.put("status", "resolved");
        ((ObjectNode) gate.get("result")).put("human_action", "approved");
        JSON.writeValue(gateFile.toFile(), gate);
        var cold = new WorkflowFileLedger();
        var calls = List.copyOf(f.calls);
        var runtime = new WorkflowRuntime(cold, new HandlerRegistry(List.of(), List.of(), List.of(new HumanReviewGateHandler())));
        assertThrows(Exception.class, () -> runtime.resume(new WorkflowCommand(root, f.ws)));
        assertThrows(Exception.class, () -> cold.resolveOpenGate(f.paths, cold.loadDefinition(f.paths), "approve", "repeat"));
        assertThrows(Exception.class, () -> cold.retryActiveNode(f.paths, "retry partial gate"));
        assertThrows(Exception.class, () -> cold.abortRecovery(f.paths, "not a recovery state"));
        assertEquals(before, Files.readString(f.paths.workflowCurrentJson()));
        assertFalse(Files.exists(f.paths.workstreamDir().resolve("artifacts/task-contract.json")));
        assertEquals(calls, f.calls);
    }

    @Test
    void completedSourceWithUnadvancedCurrentDoesNotDispatchAnOrphanSuccessor() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "tool.py");
        f.until("acceptance.classify_failure");
        var currentBytes = Files.readAllBytes(f.paths.workflowCurrentJson());
        var source = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        f.step();
        // Reproduce interruption after completed source/successor writes but before advancing current.
        Files.write(f.paths.workflowCurrentJson(), currentBytes);
        var cold = new WorkflowFileLedger();
        var calls = List.copyOf(f.calls);
        var runtime = new WorkflowRuntime(cold, new HandlerRegistry(List.of(), List.of(), List.of()));
        runtime.resume(new WorkflowCommand(root, f.ws));
        assertEquals(List.of(source), cold.loadCurrent(f.paths).activeNodeInstanceIds());
        assertThrows(Exception.class, () -> cold.retryActiveNode(f.paths, "completed source is not retryable"));
        assertThrows(Exception.class, () -> cold.abortRecovery(f.paths, "not recovery"));
        assertEquals(calls, f.calls);
    }

    @Test
    void partialRecompileCannotBeUsedAsNewApprovalOrRescueOldAuthoredInput() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "tool.py");
        f.until("acceptance.classify_failure"); f.step();
        f.until("gate.human_requirement_review"); f.approve();
        f.until("contract.compile");
        var calls = List.copyOf(f.calls);
        var oldInput = f.implementContexts.getFirst();
        var contract = f.paths.workstreamDir().resolve("artifacts/task-contract.json");
        Files.writeString(contract, "{\"id\":");
        assertTrue(TaskContractProjectionVerifier.resolve(root, f.ws).qualified().isEmpty());
        assertThrows(java.io.IOException.class, () -> AuthoredExecutionContext.prepare(oldInput, "retry-after-partial-write"));
        assertEquals(calls, f.calls);
        assertEquals(WorkflowStatus.RUNNING, f.ledger.loadCurrent(f.paths).status(),
                "consumer rejection does not invent a recovery ledger transition");
    }

    @Test
    void validNewContractWithOldResolutionCannotReuseApproval() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "tool.py");
        f.until("acceptance.classify_failure");
        var contract = f.paths.workstreamDir().resolve("artifacts/task-contract.json");
        var resolution = f.paths.workstreamDir().resolve("artifacts/contract-resolution.json");
        var oldResolution = Files.readAllBytes(resolution);
        var changed = (ObjectNode) JSON.readTree(contract.toFile());
        changed.put("objective", "A different but well-formed objective");
        JSON.writeValue(contract.toFile(), changed);
        assertTrue(JSON.readTree(contract.toFile()).isObject());
        assertArrayEquals(oldResolution, Files.readAllBytes(resolution));
        assertTrue(TaskContractProjectionVerifier.resolve(root, f.ws).qualified().isEmpty());
        assertThrows(java.io.IOException.class, () -> AuthoredExecutionContext.prepare(
                f.implementContexts.getFirst(), "retry-mixed-contract-generation"));
    }
}
