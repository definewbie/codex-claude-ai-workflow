package dev.dual.runtime.handler;
import dev.dual.core.domain.*;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativePermissionSequenceTest {
    @TempDir Path root;

    private AgentRunCommitProtocol.Prepared prepare(Fixture f) throws Exception {
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), Map.of("native_permission_budget_ms", 60000));
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        return prepared;
    }
    private Map<String, Object> request(String rpc) throws Exception {
        var raw = new AtomicJsonWriter().objectMapper().writeValueAsString(Map.of("sessionId", "native-session", "toolCall", Map.of("toolCallId", rpc, "kind", "edit", "title", "Edit approved product"),
                "options", List.of(Map.of("optionId", "exact-allow", "kind", "allow_once", "name", "Once"), Map.of("optionId", "exact-deny", "kind", "reject_once", "name", "Deny"))));
        return Map.of("schema", "dual.native.permission.request.v1", "requestKey", UUID.randomUUID().toString(), "sessionId", "native-session", "promptId", "queue-prompt",
                "ownerGeneration", 2, "rpcId", rpc, "rawJson", raw, "rawSha256", AgentRunCommitProtocol.hash(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "deadlineMs", System.currentTimeMillis() + 40000);
    }
    private String key(Map<String, Object> request) { return (String) request.get("requestKey"); }

    @Test void savedHumanChoicesHaveZeroAuthorityThenEachExactRequestCommitsOnce() throws Exception {
        var f = fixture(); var prepared = prepare(f); var a = request("A"); var b = request("B");
        NativePermissionRequests.record(f.context(), prepared, a); NativePermissionRequests.record(f.context(), prepared, b);
        assertEquals(2, ((List<?>) NativePermissionRequests.view(f.ledger(), f.paths()).get("requests")).size());
        assertTrue(NativePermissionRequests.commit(f.context(), prepared, key(a)).isEmpty());
        var saved = NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-allow", "Choose offered once option");
        assertEquals(false, saved.get("send_authority"));
        assertFalse(current(f).result().containsKey("permission_reply"));
        NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(b), "exact-deny", "Deny B only");
        var allow = NativePermissionRequests.commit(f.context(), prepared, key(a)).orElseThrow();
        var deny = NativePermissionRequests.commit(f.context(), prepared, key(b)).orElseThrow();
        assertEquals("exact-allow", allow.get("optionId")); assertEquals("exact-deny", deny.get("optionId"));
        assertEquals("A", allow.get("rpcId")); assertEquals("B", deny.get("rpcId"));
        assertTrue(NativePermissionRequests.commit(f.context(), prepared, key(a)).isEmpty());
        var events = Files.readString(f.paths().eventsJsonl());
        assertEquals(2, events.lines().filter(line -> line.contains("native_permission_reply_committed")).count());
        NativePermissionRequests.observe(f.context(), prepared, key(a), "send-intent", allow);
        NativePermissionRequests.observe(f.context(), prepared, key(a), "send-result", Map.of("consumed", true, "business_completed", false));
        assertTrue(NativePermissionRequests.commit(f.context(), prepared, key(a)).isEmpty());
    }

    @Test void wrongChoiceDriftAndConflictingHumanDecisionsRefuseWithoutPoisoningALegalChoice() throws Exception {
        var f = fixture(); var prepared = prepare(f); var a = request("A"); NativePermissionRequests.record(f.context(), prepared, a);
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "allow-from-somewhere-else", ""));
        var saved = NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-allow", "one");
        assertEquals(saved, NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-allow", "one"));
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-deny", "two"));
        Files.writeString(f.prompt(), "changed approved inputs");
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(f.context(), prepared, key(a)));
        assertFalse(current(f).result().containsKey("permission_reply"));
        Files.writeString(f.prompt(), "review the approved inputs");
        assertEquals("exact-allow", NativePermissionRequests.commit(f.context(), prepared, key(a)).orElseThrow().get("optionId"));
    }

    @Test void XBeforeCRejectsAndCAfterXIsNeverAReplayedPermissionButCompletionPreservesFacts() throws Exception {
        var f = fixture(); var prepared = prepare(f); var a = request("A"); NativePermissionRequests.record(f.context(), prepared, a);
        NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-allow", "one");
        NativePermissionRequests.close(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), "operator cancelled");
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(f.context(), prepared, key(a)));
        assertEquals(true, ((Map<?, ?>) current(f).result().get("permission_reply")).get("closed"));
        assertTrue(NativePermissionRequests.closed(f.context(), prepared));
        f.ledger().failNodeExecution(f.paths(), current(f), new java.io.IOException("original stop must be observed"));
        assertEquals(true, ((Map<?, ?>) current(f).result().get("permission_reply")).get("closed"));
    }

    @Test void CBeforeXAllowsOnlyThatOriginalSingleReturnedPayloadAndKeepsCommitOnFailure() throws Exception {
        var f = fixture(); var prepared = prepare(f); var a = request("A"); NativePermissionRequests.record(f.context(), prepared, a);
        NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-allow", "one");
        var committed = NativePermissionRequests.commit(f.context(), prepared, key(a)).orElseThrow();
        NativePermissionRequests.close(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), "after C");
        assertEquals("exact-allow", committed.get("optionId"), "one already committed reply may be in flight; accepted product semantics");
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(f.context(), prepared, key(a)));
        f.ledger().failNodeExecution(f.paths(), current(f), new java.io.IOException("unknown delivery, no resend"));
        var reply = (Map<?, ?>) current(f).result().get("permission_reply");
        assertEquals(true, reply.get("closed")); assertTrue(((Map<?, ?>) reply.get("commits")).containsKey(key(a)));
    }

    @ParameterizedTest @ValueSource(strings={"before_manifest","after_manifest","before_replace","after_replace","before_append","after_append","before_pending_delete","after_pending_delete"})
    void everyCWritePrefixGrantsZeroReplyAndFixedReplayNeverGrantsNewlyCommitted(String point) throws Exception {
        var f = fixture(); var prepared = prepare(f); var a = request("A"); NativePermissionRequests.record(f.context(), prepared, a);
        NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-allow", "one");
        var interrupted = dev.dual.ledger.file.InterruptedTestLedger.at(point, f.ledger().frozenRuntimeIdentity());
        var context = new TaskHandler.TaskContext(root, f.context().workstreamId(), f.context().definition(), f.context().instance(), interrupted);
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(context, prepared, key(a)));
        assertFalse(Files.exists(prepared.path().getParent().resolve(prepared.execution().nodeExecutionId()+"-permission-"+key(a)+"-send-intent.json")));
        if (!point.equals("before_manifest")) {
            try (var write = f.ledger().writeSession(f.paths(), "finish fixed local bytes", "fixed")) { write.commit(); }
            assertTrue(NativePermissionRequests.commit(f.context(), prepared, key(a)).isEmpty());
        }
        NativePermissionRequests.close(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), "failed C is not resend authority");
    }

    @Test void requestAndDecisionWriteFailuresGiveNoCAndRetainALegalLocalRecovery() throws Exception {
        var f=fixture(); var prepared=prepare(f); var a=request("A");
        var directory=prepared.path().getParent();
        var prefix=prepared.execution().nodeExecutionId()+"-permission-"+key(a);
        var requestPath=directory.resolve(prefix+"-request.json"); Files.createDirectory(requestPath);
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.record(f.context(),prepared,a));
        assertFalse(current(f).result().containsKey("permission_reply")); Files.delete(requestPath);
        NativePermissionRequests.record(f.context(),prepared,a);
        var decisionPath=directory.resolve(prefix+"-decision.json"); Files.createDirectory(decisionPath);
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.decide(f.ledger(),f.paths(),prepared.execution().nodeExecutionId(),key(a),"exact-allow","one"));
        assertFalse(current(f).result().containsKey("permission_reply")); Files.delete(decisionPath);
        assertTrue(NativePermissionRequests.commit(f.context(),prepared,key(a)).isEmpty());
        NativePermissionRequests.decide(f.ledger(),f.paths(),prepared.execution().nodeExecutionId(),key(a),"exact-allow","one");
        assertTrue(NativePermissionRequests.commit(f.context(),prepared,key(a)).isPresent());
    }

    @Test void expiredOrUnbudgetedRequestsCannotCreateAnActionableHumanRequest() throws Exception {
        var f=fixture(); var prepared=prepare(f); var expired=new LinkedHashMap<>(request("expired")); expired.put("deadlineMs",System.currentTimeMillis()-1);
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.record(f.context(),prepared,expired));
        assertTrue(((List<?>)NativePermissionRequests.view(f.ledger(),f.paths()).get("requests")).isEmpty());
        var legal=request("legal"); NativePermissionRequests.record(f.context(),prepared,legal);
        assertEquals(1,((List<?>)NativePermissionRequests.view(f.ledger(),f.paths()).get("requests")).size());
    }

    @Test void unbudgetedRequestCannotCreateAHumanDecision() throws Exception {
        var unbudgeted=fixture();
        var noBudget=AgentRunCommitProtocol.prepare(unbudgeted.context(),unbudgeted.run(),List.of(unbudgeted.prompt()),Map.of());
        AgentRunCommitProtocol.beginExternalCall(unbudgeted.context(),noBudget);
        var a=request("A");
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.record(unbudgeted.context(),noBudget,a));
        assertFalse(current(unbudgeted).result().containsKey("permission_reply"));
    }

    @ParameterizedTest @ValueSource(strings={"before_manifest","after_manifest","before_replace","after_replace","before_append","after_append","before_pending_delete","after_pending_delete"})
    void XFailurePrefixesCannotInventCancellationAndFixedCompletionClosesNewReplies(String point) throws Exception {
        var f=fixture(); var prepared=prepare(f); var a=request("A");
        NativePermissionRequests.record(f.context(),prepared,a);
        NativePermissionRequests.decide(f.ledger(),f.paths(),prepared.execution().nodeExecutionId(),key(a),"exact-allow","one");
        var interrupted=dev.dual.ledger.file.InterruptedTestLedger.at(point,f.ledger().frozenRuntimeIdentity());
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.close(interrupted,f.paths(),prepared.execution().nodeExecutionId(),"stop"));
        if(point.equals("before_manifest")) {
            assertFalse(current(f).result().containsKey("permission_reply"),"failed before durable X does not invent a closed fact");
            NativePermissionRequests.close(f.ledger(),f.paths(),prepared.execution().nodeExecutionId(),"retry local close");
        } else {
            try(var write=f.ledger().writeSession(f.paths(),"finish fixed X","fixed")){write.commit();}
        }
        assertTrue(NativePermissionRequests.closed(f.context(),prepared));
        assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(f.context(),prepared,key(a)));
    }

    @Test void anotherJvmCannotUseSavedDecisionEvenWhileTheOriginalExecutorIsAlive() throws Exception {
        var f = fixture(); var prepared = prepare(f); var a = request("A"); NativePermissionRequests.record(f.context(), prepared, a);
        NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key(a), "exact-allow", "one");
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp", System.getProperty("java.class.path"), ColdReader.class.getName(), root.toString(), key(a))
                .redirectErrorStream(true).start();
        var output = new String(child.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(child.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, child.exitValue(), output); assertTrue(output.contains("COLD_ZERO_SEND"), output);
        assertFalse(current(f).result().containsKey("permission_reply"));
        assertTrue(NativePermissionRequests.commit(f.context(), prepared, key(a)).isPresent(), "legal original executor is still usable");
    }
    @Test void actualCommitBarrierKeepsXOutOfTheQualificationToCWindow() throws Exception {
        var f=fixture(); var prepared=prepare(f); var a=request("A");
        NativePermissionRequests.record(f.context(),prepared,a);
        NativePermissionRequests.decide(f.ledger(),f.paths(),prepared.execution().nodeExecutionId(),key(a),"exact-allow", "one");
        var reached=new java.util.concurrent.CountDownLatch(1); var resume=new java.util.concurrent.CountDownLatch(1);
        var paused=dev.dual.ledger.file.InterruptedTestLedger.pauseAt("before_manifest", f.ledger().frozenRuntimeIdentity(),reached,resume);
        var context=new TaskHandler.TaskContext(root, f.context().workstreamId(), f.context().definition(), f.context().instance(),paused);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var c=executor.submit(() -> NativePermissionRequests.commit(context,prepared,key(a)));
            assertTrue(reached.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(WorkstreamLockedException.class, () -> NativePermissionRequests.close(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), "concurrent X"));
            resume.countDown(); assertTrue(c.get(10, java.util.concurrent.TimeUnit.SECONDS).isPresent());
            NativePermissionRequests.close(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), "X after C");
            assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(f.context(), prepared,key(a)));
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @ParameterizedTest @ValueSource(strings={"retry","abort","close_iteration","revise","readiness","gate","replace_claim"})
    void siblingMutationsCannotReplaceALiveApprovedRequest(String action) throws Exception {
        var f=fixture(); var prepared=prepare(f); var a=request("A");
        NativePermissionRequests.record(f.context(),prepared,a);
        NativePermissionRequests.decide(f.ledger(),f.paths(),prepared.execution().nodeExecutionId(),key(a),"exact-allow","one");
        assertThrows(java.io.IOException.class, () -> {
            try(var write=f.ledger().writeSession(f.paths(), "illegal live mutation",UUID.randomUUID().toString())) {
                var scoped=write.ledger();
                switch(action) {
                    case "retry" -> scoped.retryActiveNode(f.paths(),"retry live");
                    case "abort" -> scoped.abortRecovery(f.paths(),"abort live");
                    case "close_iteration" -> scoped.closeMistakenIteration(f.paths(),"close live");
                    case "revise" -> scoped.reviseReadinessFailure(f.paths(),prepared.execution().nodeInstanceId(),"revise",Map.of(),List.of());
                    case "readiness" -> scoped.recordReadiness(f.paths(),prepared.execution().nodeInstanceId(),new dev.dual.core.contract.TaskContract.Reference(".agent/workstreams/run-protocol/artifacts/verification-readiness/test.json","a".repeat(64)),new byte[0]);
                    case "gate" -> scoped.resolveOpenGate(f.paths(),scoped.loadDefinition(f.paths()),"approve","borrow");
                    case "replace_claim" -> scoped.startNodeExecution(f.paths(),prepared.execution().workflowExecutionId(),scoped.loadNode(f.paths(),prepared.execution().nodeInstanceId()),scoped.readAgentTask(f.paths(),prepared.execution().agentTaskId()).orElseThrow(),ProcessHandle.current().pid());
                }
                write.commit();
            }
        });
        assertTrue(NativePermissionRequests.commit(f.context(),prepared,key(a)).isPresent(),"illegal rejected mutation must not poison lawful continuation");
    }

    public static final class ColdReader {
        public static void main(String[] args) throws Exception {
            var ledger = new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "protocol-v1"));
            var paths = new WorkstreamPaths(Path.of(args[0]), new DualId("run-protocol"));
            var node = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            var instance = ledger.loadCoreNode(paths, node);
            var execution = ledger.latestNodeExecution(paths, ledger.loadNode(paths, node)).orElseThrow();
            var prepared = AgentRunCommitProtocol.boundPreparation(paths, execution);
            var context = new TaskHandler.TaskContext(Path.of(args[0]), "run-protocol", ledger.loadDefinition(paths).nodes().getFirst(), instance, ledger);
            assertEquals("DECISION_SAVED", ((Map<?, ?>) ((List<?>) NativePermissionRequests.view(ledger, paths).get("requests")).getFirst()).get("status"));
            assertThrows(java.io.IOException.class, () -> NativePermissionRequests.commit(context, prepared, args[1]));
            System.out.println("COLD_ZERO_SEND");
        }
    }
    private NodeExecutionDocument current(Fixture f) throws Exception {
        var node = f.ledger().loadNode(f.paths(), f.context().instance().nodeInstanceId());
        return f.ledger().latestNodeExecution(f.paths(), node).orElseThrow();
    }

    private Fixture fixture() throws Exception { return fixture(ProcessHandle.current().pid()); }

    private Fixture fixture(long ownerPid) throws Exception {
        var ledger = new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "protocol-v1"));
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
