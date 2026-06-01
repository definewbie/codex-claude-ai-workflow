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

class SchedulerServiceTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void tickOnlyResumesRunningWorkstreams() throws Exception {
        createRunningWorkstream("ws-running");
        createWaitingForHumanWorkstream("ws-human");
        var calls = new AtomicInteger();
        var actionService = new WorkstreamActionService(new WorkflowFileLedger(), () -> command -> {
            calls.incrementAndGet();
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, "gate.review", "paused");
        });
        var scheduler = new SchedulerService(new WorkstreamQueryService(new com.fasterxml.jackson.databind.ObjectMapper()), actionService);

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

    private void createRunningWorkstream(String workstreamId) throws Exception {
        var paths = paths(workstreamId);
        Files.createDirectories(paths.workstreamDir());
        new WorkflowFileLedger().initialize(paths, "wf-" + workstreamId, simpleDefinition());
    }

    private void createWaitingForHumanWorkstream(String workstreamId) throws Exception {
        var paths = paths(workstreamId);
        Files.createDirectories(paths.workstreamDir());
        var definition = gateDefinition();
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-" + workstreamId, definition);
        var first = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.completeAndRoute(paths, definition, first, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
    }

    private WorkstreamPaths paths(String workstreamId) throws Exception {
        var workstream = new DualId(workstreamId);
        var workspace = new DualId("workspace-" + workstreamId);
        var paths = new WorkstreamPaths(tempDir, workstream);
        new FileLedgerStore().initializeWorkstream(
                paths,
                Workstream.created(
                        workstream,
                        workstreamId,
                        new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                        workspace,
                        Instant.parse("2026-06-01T00:00:00Z")
                ),
                Workspace.primaryGitWorktree(
                        workspace,
                        workstream,
                        tempDir.resolve("repo"),
                        tempDir.resolve("repo-agent-runs").resolve(workstreamId),
                        "ai/" + workstreamId,
                        "main",
                        "abc123"
                ),
                "# Requirement\n"
        );
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
