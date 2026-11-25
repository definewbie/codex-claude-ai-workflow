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
        var cold = dev.dual.runtime.RuntimeTestLedger.ledger();
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
        var cold = dev.dual.runtime.RuntimeTestLedger.ledger();
        var calls = List.copyOf(f.calls);
        var runtime = new WorkflowRuntime(cold, new HandlerRegistry(List.of(), List.of(), List.of()));
        assertThrows(Exception.class, () -> runtime.resume(new WorkflowCommand(root, f.ws)));
        assertArrayEquals(currentBytes, Files.readAllBytes(f.paths.workflowCurrentJson()));
        assertThrows(Exception.class, () -> cold.retryActiveNode(f.paths, "completed source is not retryable"));
        assertThrows(Exception.class, () -> cold.abortRecovery(f.paths, "not recovery"));
        assertEquals(calls, f.calls);
    }

    @Test
    void partialRecompileCannotBeUsedAsNewApprovalOrRescueOldAuthoredInput() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "tool.py");
        f.until("acceptance.classify_failure"); f.step();
        f.until("gate.human_requirement_review"); f.approve();
        f.until("gate.contract_review");
        var calls = List.copyOf(f.calls);
        var oldInput = f.implementContexts.getFirst();
        var gate = f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        var compile = f.ledger.loadNode(f.paths, String.valueOf(gate.inputTransition().get("source_node_instance_id")));
        var contract = f.root.resolve(compile.outputRefs().stream().filter(r -> r.endsWith("/task-contract.json")).findFirst().orElseThrow());
        Files.writeString(contract, "{\"id\":");
        assertTrue(TaskContractProjectionVerifier.resolve(dev.dual.runtime.RuntimeTestLedger.ledger(), root, f.ws).qualified().isEmpty());
        assertThrows(java.io.IOException.class, () -> AuthoredExecutionContext.prepare(oldInput, "retry-after-partial-write"));
        assertEquals(calls, f.calls);
        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.ledger.loadCurrent(f.paths).status(),
                "consumer rejection does not invent a recovery ledger transition");
    }

    @Test
    void validNewContractWithOldResolutionCannotReuseApproval() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "tool.py");
        f.until("acceptance.classify_failure");
        var approved = TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().orElseThrow();
        var contract = f.root.resolve(approved.contractRef());
        var resolution = f.root.resolve(approved.resolutionRef());
        var oldResolution = Files.readAllBytes(resolution);
        var changed = (ObjectNode) JSON.readTree(contract.toFile());
        changed.put("objective", "A different but well-formed objective");
        JSON.writeValue(contract.toFile(), changed);
        assertTrue(JSON.readTree(contract.toFile()).isObject());
        assertArrayEquals(oldResolution, Files.readAllBytes(resolution));
        assertTrue(TaskContractProjectionVerifier.resolve(dev.dual.runtime.RuntimeTestLedger.ledger(), root, f.ws).qualified().isEmpty());
        assertThrows(java.io.IOException.class, () -> AuthoredExecutionContext.prepare(
                f.implementContexts.getFirst(), "retry-mixed-contract-generation"));
    }
}
