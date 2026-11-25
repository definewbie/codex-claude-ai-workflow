package dev.dual.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StaleRunningRecoveryTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path root;

    @Test void liveOriginalExecutorCannotBeTakenOverOrKilledByStaleCommand() throws Exception {
        var f = fixture();
        var before = Files.readString(f.paths.nodeJson(f.context.instance().nodeInstanceId()));
        var failure = assertThrows(IllegalStateException.class, () -> new StaleRunningRecovery(f.ledger).terminateAndRecover(f.paths, "stop"));
        assertTrue(failure.getMessage().contains("original executor"));
        assertTrue(ProcessHandle.current().isAlive());
        assertEquals(before, Files.readString(f.paths.nodeJson(f.context.instance().nodeInstanceId())));
        assertEquals(NodeStatus.RUNNING, f.ledger.loadNode(f.paths, f.context.instance().nodeInstanceId()).status());
    }

    @Test void releasedIntentBecomesUnknownWithoutInferringOutcomeOrKillingMutableRunPid() throws Exception {
        var f = fixture();
        var process = new ProcessBuilder("sleep", "30").start();
        try {
            var run = f.prepared.path().getParent().getParent().resolve("run.json");
            JSON.writeValue(run.toFile(), Map.of("status", "running", "executionTimeline",
                    List.of(Map.of("kind", "process_started", "details", Map.of("pid", process.pid())))));
            var original = Files.readString(run);
            AgentRunCommitProtocol.releaseExecutor(f.ledger, f.paths, f.prepared.execution());
            var result = new StaleRunningRecovery(f.ledger).terminateAndRecover(f.paths, "operator wants to stop");
            assertEquals(0, result.terminatedProcessTrees());
            assertEquals(0, result.reconciledRuns());
            assertTrue(process.isAlive(), "mutable telemetry cannot authorize killing a process");
            assertEquals(original, Files.readString(run), "no synthesized exit status or usage");
            var node = f.ledger.loadNode(f.paths, f.context.instance().nodeInstanceId());
            assertEquals("UNKNOWN", node.result().get("external_effect"));
            assertEquals(false, node.result().get("automatic_retry"));
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
            assertThrows(Exception.class, () -> f.ledger.retryActiveNode(f.paths, "must not redispatch unknown effects"));
        } finally { process.destroyForcibly(); process.waitFor(); }
    }

    @Test void absentNativeStopObservationCannotBeReplacedByStaleRecoveryOrRepeatedRecovery() throws Exception {
        var f = fixture();
        AgentRunCommitProtocol.releaseExecutor(f.ledger, f.paths, f.prepared.execution());
        new StaleRunningRecovery(f.ledger).recover(f.paths, "owner stopped");
        var before = Files.readString(f.paths.nodeJson(f.context.instance().nodeInstanceId()));
        assertThrows(IllegalStateException.class, () -> new StaleRunningRecovery(f.ledger).recover(f.paths, "retry stale classification"));
        assertEquals(before, Files.readString(f.paths.nodeJson(f.context.instance().nodeInstanceId())));
        assertEquals("UNKNOWN", f.ledger.loadNode(f.paths, f.context.instance().nodeInstanceId()).result().get("external_effect"));
    }

    private Fixture fixture() throws Exception {
        var ledger = RuntimeTestLedger.ledger(); var paths = new WorkstreamPaths(root, new DualId("ws-stale"));
        var definition = new NodeDefinition("agent.node", NodeKind.TASK, "acpx.agent_run", Map.of());
        RuntimeTestLedger.initialize(ledger, paths, "wf-stale", new WorkflowDefinition("stale", 1, definition.id(), List.of(definition), List.of()));
        var context = RuntimeTestLedger.claim(new TaskHandler.TaskContext(root, "ws-stale", definition,
                ledger.loadCoreNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()), ledger));
        var dir = paths.runsDir().resolve("one"); Files.createDirectories(dir);
        var input = dir.resolve("prompt.md"); Files.writeString(input, "offline recovery fixture");
        var prepared = AgentRunCommitProtocol.prepare(context, dir, List.of(input), Map.of());
        AgentRunCommitProtocol.qualifyDispatch(context, prepared);
        AgentRunCommitProtocol.beginExternalCall(context, prepared);
        return new Fixture(ledger, paths, context, prepared);
    }
    private record Fixture(WorkflowFileLedger ledger, WorkstreamPaths paths, TaskHandler.TaskContext context, AgentRunCommitProtocol.Prepared prepared) {}
}
