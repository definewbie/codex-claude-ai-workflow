package dev.dual.server;
import dev.dual.core.domain.*;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class NativePermissionApiTest {
    @TempDir Path root;
    @Test void apiAndOverviewUseTheSameProjectionButOnlyTheOriginalExecutorCanCommit() throws Exception {
        var f=fixture();
        var prepared=AgentRunCommitProtocol.prepare(f.context(), f.run(),List.of(f.prompt()),Map.of("native_permission_budget_ms",60000));
        AgentRunCommitProtocol.beginExternalCall(f.context(),prepared);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var raw=json.writeValueAsString(Map.of("sessionId","native", "toolCall",Map.of("toolCallId","edit","title","product"),"options",List.of(Map.of("optionId","specific","kind","allow_once","name","Once"))));
        var key=UUID.randomUUID().toString();
        var request=Map.<String,Object>of("schema","dual.native.permission.request.v1","requestKey",key,"sessionId","native","promptId","p","rpcId","rpc", "ownerGeneration",2,"rawJson",raw,"rawSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))),"deadlineMs",System.currentTimeMillis()+40000);
        NativePermissionRequests.record(f.context(),prepared,request);
        var query=new WorkstreamQueryService(json,new DeliveryViewService(json));
        var actions=new WorkstreamActionService(f.ledger(), () -> command -> { throw new AssertionError("human action cannot dispatch runtime"); });
        var api=new WorkstreamApiController(query, actions,new WorkstreamStreamService(query,json),new DeliveryViewService(json));
        var view=api.nativePermissions("run-protocol",root.toString());
        assertEquals("REQUEST_PENDING", ((Map<?,?>)((List<?>)view.get("requests")).getFirst()).get("status"));
        assertEquals(view, api.overview("run-protocol",root.toString()).get("nativePermissions"));
        try(var held=f.ledger().readSession(f.paths())) {
            var busy=api.nativePermissions("run-protocol",root.toString());
            assertEquals("BUSY",busy.get("status")); assertTrue(((List<?>)busy.get("requests")).isEmpty());
        }
        assertThrows(java.io.IOException.class, () -> api.nativePermissionDecision("run-protocol",root.toString(),new WorkstreamApiController.NativePermissionDecision(prepared.execution().nodeExecutionId(),key,"missing","wrong")));
        var saved=api.nativePermissionDecision("run-protocol",root.toString(),new WorkstreamApiController.NativePermissionDecision(prepared.execution().nodeExecutionId(),key,"specific","human"));
        assertEquals(false,saved.get("send_authority"));
        assertEquals("DECISION_SAVED", ((Map<?,?>)((List<?>)api.nativePermissions("run-protocol",root.toString()).get("requests")).getFirst()).get("status"));
        assertTrue(NativePermissionRequests.commit(f.context(),prepared,key).isPresent());
        var interrupted=dev.dual.ledger.file.InterruptedTestLedger.at("after_manifest",f.ledger().frozenRuntimeIdentity());
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.close(interrupted,f.paths(),prepared.execution().nodeExecutionId(),"interrupted close"));
        var pending=api.nativePermissions("run-protocol",root.toString());
        assertEquals("PENDING",pending.get("status")); assertTrue(((List<?>)pending.get("requests")).isEmpty());
        try(var recovery=f.ledger().writeSession(f.paths(),"finish fixed close","fixed")){recovery.commit();}
        var stop=api.nativePermissionCancel("run-protocol",root.toString(),new WorkstreamApiController.NativePermissionCancel(prepared.execution().nodeExecutionId(),"stop"));
        assertEquals("STOP_UNCONFIRMED",stop.get("status"));
        assertEquals(false, ((Map<?,?>)((List<?>)api.nativePermissions("run-protocol",root.toString()).get("requests")).getFirst()).get("can_decide"));
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(f.context(),prepared,key));
    }
    private Fixture fixture() throws Exception { return fixture(ProcessHandle.current().pid()); }

    private Fixture fixture(long ownerPid) throws Exception {
        var ledger = ServerLedgerFactory.create();
        var paths = new WorkstreamPaths(root, new DualId("run-protocol"));
        var workspace = new DualId("workspace-run-protocol");
        var taskDefinition = new NodeDefinition("task", NodeKind.TASK, "acpx.agent_run", Map.of());
        var definition = new WorkflowDefinition("protocol", 1, "task", List.of(taskDefinition), List.of());
        ledger.initialize(paths, Workstream.created(paths.workstreamId(), "run protocol", new Workstream.RepositoryRef(root, root), workspace, Instant.now()),
                Workspace.primaryGitWorktree(workspace, paths.workstreamId(), root, root, "test", "main", "base"),
                "review and continue", WorkstreamSettingsDocument.of("auto"), "wf-protocol", definition);
        String nodeId;
        try (var write = ledger.writeSession(paths, "claim", "claim")) {
            var scoped = write.ledger();
            var current = scoped.loadCurrent(paths);
            nodeId = current.activeNodeInstanceIds().getFirst();
            var node = scoped.loadNode(paths, nodeId);
            var workflow = scoped.startWorkflowExecution(paths, current, "test");
            var task = scoped.createAgentTask(paths, workflow.workflowExecutionId(), taskDefinition, node);
            var execution = scoped.startNodeExecution(paths, workflow.workflowExecutionId(), node, task, ownerPid);
            scoped.markAgentTaskRunning(paths, task, execution.nodeExecutionId());
            scoped.markRunning(paths, nodeId, ownerPid);
            write.commit();
        }
        var run = paths.runsDir().resolve("one"); Files.createDirectories(run);
        var prompt = run.resolve("prompt.md"); Files.writeString(prompt, "review the approved inputs");
        return new Fixture(ledger, paths, new TaskHandler.TaskContext(root, "run-protocol", taskDefinition,
                ledger.loadCoreNode(paths, nodeId), ledger), run, prompt);
    }

    private record Fixture(WorkflowFileLedger ledger, WorkstreamPaths paths, TaskHandler.TaskContext context, Path run, Path prompt) {}
}
