package dev.dual.server;

import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.WorkflowRuntimeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerServiceTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void tickOnlyResumesRunningWorkstreams() throws Exception {
        createRunningWorkstream("ws-running");
        createWaitingForHumanWorkstream("ws-human");
        var calls = new AtomicInteger();
        var actionService = new WorkstreamActionService(ServerLedgerFactory.create(), () -> command -> {
            calls.incrementAndGet();
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused");
        });
        var scheduler = new SchedulerService(queryService(), actionService);

        var response = scheduler.tick(tempDir, 10, 1);

        assertEquals("scheduler.tick", response.get("action"));
        assertEquals(1, response.get("resumed"));
        assertEquals(1, response.get("skipped"));
        assertEquals(1, calls.get());
        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertEquals("ws-human", workstreams.get(0).get("workstreamId"));
        assertEquals("skipped", workstreams.get(0).get("result"));
        assertEquals("WAITING_FOR_HUMAN", workstreams.get(0).get("reason"));
        assertEquals("ws-running", workstreams.get(1).get("workstreamId"));
        assertEquals("resumed", workstreams.get(1).get("result"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickSkipsRecoveryByDefault() throws Exception {
        createWaitingForRecoveryWorkstream("ws-recovery", Map.of(
                "acpx_failure.recoverable", true,
                "acpx_failure.reason", "quota"
        ));
        var calls = new AtomicInteger();
        var actionService = new WorkstreamActionService(ServerLedgerFactory.create(), () -> command -> {
            calls.incrementAndGet();
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused");
        });
        var scheduler = new SchedulerService(queryService(), actionService);

        var response = scheduler.tick(tempDir, 10, 1);

        assertEquals(0, response.get("autoRecovered"));
        assertEquals(1, response.get("skipped"));
        assertEquals(0, calls.get());
        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertEquals("skipped", workstreams.get(0).get("result"));
        assertEquals("WAITING_FOR_RECOVERY", workstreams.get(0).get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickAutoRecoversRetryReadyRuntimeFailureWhenEnabled() throws Exception {
        createWaitingForRecoveryWorkstream("ws-recovery", Map.of(
                "acpx_failure.recoverable", true,
                "acpx_failure.reason", "quota"
        ));
        var calls = new AtomicInteger();
        var actionService = new WorkstreamActionService(ServerLedgerFactory.create(), () -> command -> {
            calls.incrementAndGet();
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused");
        });
        var scheduler = new SchedulerService(queryService(), actionService);

        var response = scheduler.tick(tempDir, 10, 1, true);

        assertEquals(1, response.get("autoRecovered"));
        assertEquals(0, response.get("skipped"));
        assertEquals(1, calls.get());
        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertEquals("auto_recovered", workstreams.get(0).get("result"));
        assertEquals("recoverable_agent_runtime", workstreams.get(0).get("recoveryClassification"));
        assertEquals("RETRY_AFTER_EXTERNAL_FIX", workstreams.get(0).get("retryReadiness"));
        var recoveryPaths = new WorkstreamPaths(tempDir, new DualId("ws-recovery"));
        assertTrue(Files.readString(recoveryPaths.eventsJsonl()).contains("node_retry_requested"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickDoesNotAutoRecoverArtifactContractFailure() throws Exception {
        createWaitingForRecoveryWorkstream("ws-contract", Map.of(
                "reason", "AGENT_OUTPUT_CONTRACT_VIOLATION"
        ));
        var calls = new AtomicInteger();
        var actionService = new WorkstreamActionService(ServerLedgerFactory.create(), () -> command -> {
            calls.incrementAndGet();
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused");
        });
        var scheduler = new SchedulerService(queryService(), actionService);

        var response = scheduler.tick(tempDir, 10, 1, true);

        assertEquals(0, response.get("autoRecovered"));
        assertEquals(1, response.get("skipped"));
        assertEquals(0, calls.get());
        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertEquals("skipped", workstreams.get(0).get("result"));
        assertEquals("recovery:artifact_contract_failure/RETRY_AFTER_OUTPUT_CONTRACT_FIX", workstreams.get(0).get("reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickDoesNotAutoRecoverTypedReconnectFailure() throws Exception {
        createWaitingForRecoveryWorkstream("ws-reconnect", Map.of(
                "agent", "codex",
                "session", "codex-reviewer",
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "agent_reconnect_required",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "The ACP session needs reconnect."
                )
        ));
        var calls = new AtomicInteger();
        var actionService = new WorkstreamActionService(ServerLedgerFactory.create(), () -> command -> {
            calls.incrementAndGet();
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused");
        });
        var scheduler = new SchedulerService(queryService(), actionService);

        var response = scheduler.tick(tempDir, 10, 1, true);

        assertEquals(0, response.get("autoRecovered"));
        assertEquals(1, response.get("skipped"));
        assertEquals(0, calls.get(), "typed reconnect must not invoke resume");
        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertEquals("skipped", workstreams.get(0).get("result"));
        assertEquals(
                "recovery:agent_session_recovery/REBUILD_ACPX_SESSION_THEN_RETRY",
                workstreams.get(0).get("reason")
        );
        var recoveryPaths = new WorkstreamPaths(tempDir, new DualId("ws-reconnect"));
        assertFalse(
                Files.readString(recoveryPaths.eventsJsonl()).contains("node_retry_requested"),
                "typed reconnect must not request retry"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void tickDoesNotScheduleFromLegacyInitialStatus() throws Exception {
        paths("ws-legacy");
        var calls = new AtomicInteger();
        var actionService = new WorkstreamActionService(ServerLedgerFactory.create(), () -> command -> {
            calls.incrementAndGet();
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused");
        });
        var scheduler = new SchedulerService(queryService(), actionService);

        var response = scheduler.tick(tempDir, 10, 1, true);

        assertEquals(0, calls.get());
        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertTrue(workstreams.isEmpty(), "invalid legacy records are not executable scheduler candidates");

    }

    private void createRunningWorkstream(String workstreamId) throws Exception {
        var paths = paths(workstreamId);
        Files.createDirectories(paths.workstreamDir());
        ServerTestLedger.initialize(ServerLedgerFactory.create(), paths, "wf-" + workstreamId, simpleDefinition());
    }

    private WorkstreamQueryService queryService() {
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return new WorkstreamQueryService(objectMapper, new DeliveryViewService(objectMapper));
    }

    private void createWaitingForHumanWorkstream(String workstreamId) throws Exception {
        var paths = paths(workstreamId);
        Files.createDirectories(paths.workstreamDir());
        var definition = gateDefinition();
        var ledger = ServerLedgerFactory.create();
        ServerTestLedger.initialize(ledger, paths, "wf-" + workstreamId, definition);
        var first = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, first, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
    }

    private void createWaitingForRecoveryWorkstream(String workstreamId, Map<String, Object> result) throws Exception {
        var paths = paths(workstreamId);
        Files.createDirectories(paths.workstreamDir());
        var definition = simpleDefinition();
        var ledger = ServerLedgerFactory.create();
        ServerTestLedger.initialize(ledger, paths, "wf-" + workstreamId, definition);
        var first = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, first, NodeStatus.FAILED, "failed", List.of("stderr.log"), result);
    }

    private WorkstreamPaths paths(String workstreamId) throws Exception {
        var workstream = new DualId(workstreamId);
        var workspace = new DualId("workspace-" + workstreamId);
        var paths = new WorkstreamPaths(tempDir, workstream);

        return paths;
    }

    private static WorkflowDefinition simpleDefinition() {
        return new WorkflowDefinition(
                "scheduler-test",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
    }

    private static WorkflowDefinition gateDefinition() {
        return new WorkflowDefinition(
                "scheduler-test",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "gate.review"))
        );
    }
}
