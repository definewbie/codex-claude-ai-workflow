package dev.dual.adapter.acpx;
import dev.dual.core.domain.*;
import dev.dual.core.execution.*;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Local fake acpx executable and real Dual L/R owners; never a native/provider call. */
class NativePermissionTransportSequenceTest {
    @TempDir Path root;
    private final com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
    @ParameterizedTest @ValueSource(strings={"allow", "deny", "unknown", "cancel", "busy-before-send", "busy-after-send"})
    void savedDecisionCommitsExactlyOnceAndContinuesOrStopsWithoutRetry(String scenario) throws Exception {
        var f = fixture();
        var transport = new AcpxAgentTransport(executable().toString(), Duration.ofMillis(100), Duration.ofMillis(400), Duration.ofSeconds(2), Duration.ofSeconds(15));
        var binding = Map.<String,Object>of("native_permission_budget_ms", 15000, "native_invocation", transport.invocationInputs("codex", root, "exact-session"), "allowed_request_options", List.of(transport.requestOptions("codex", root, AcpxPermissionProfile.implement(), null, true)));
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), binding);
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        var raw = json.writeValueAsString(Map.of("sessionId", "native-one", "toolCall", Map.of("toolCallId", "tool-A", "kind", "edit", "title", "approved product edit"),
                "options", List.of(Map.of("optionId", "exact-allow", "kind", "allow_once", "name", "Once"), Map.of("optionId", "exact-deny", "kind", "reject_once", "name", "Deny"))));
        var key = UUID.randomUUID().toString();
        var nativeRequest = Map.<String,Object>of("schema", "dual.native.permission.request.v1", "requestKey", key, "sessionId", "native-one", "promptId", "prompt-one", "ownerGeneration", 4,
                "rpcId", "rpc-A", "rawJson", raw, "rawSha256", java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))), "deadlineMs", System.currentTimeMillis()+10000);
        json.writeValue(root.resolve("ask.json").toFile(), nativeRequest);
        json.writeValue(root.resolve("session.json").toFile(), Map.of("schema", "acpx.session.v1", "acpxRecordId", "record", "acpSessionId", "native-one", "agentSessionId", "native-one", "cwd", root.toString(), "name", "exact-session", "closed", false, "protocolVersion", 1, "agentCapabilities", Map.of()));
        Files.writeString(root.resolve("scenario"), scenario);
        var events = new ArrayList<AcpxExecutionEvent>();
        var announced = new java.util.concurrent.CountDownLatch(1);
        var sendIntent = new java.util.concurrent.atomic.AtomicBoolean();
        var busyInjected = new java.util.concurrent.atomic.AtomicBoolean();
        var externalReturned = new java.util.concurrent.atomic.AtomicBoolean();
        var qualificationAfterExternal = new java.util.concurrent.atomic.AtomicInteger();
        var sink = new AcpxExecutionEventSink() {
            public void accept(AcpxExecutionEvent event) {
                events.add(event);
                try { AgentRunCommitProtocol.commandEvent(f.context(), prepared, event.kind().wireName(), event.details()); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
            public boolean permissionRepliesEnabled() { return true; }
            public void permissionRequest(Map<String,Object> request) throws java.io.IOException { NativePermissionRequests.record(f.context(), prepared, request); announced.countDown(); }
            public Optional<Map<String,Object>> permissionReply(String k) throws java.io.IOException {
                if(externalReturned.get()) qualificationAfterExternal.incrementAndGet();
                return NativePermissionRequests.commit(f.context(), prepared, k);
            }
            public void permissionObservation(String k, String phase, Map<String,Object> observation) throws java.io.IOException {
                if(phase.equals("send-intent")) sendIntent.set(true);
                if(phase.equals("send-result")) {
                    externalReturned.set(true);
                    if(scenario.equals("busy-after-send") && busyInjected.compareAndSet(false,true)) throw new WorkstreamLockedException(f.paths().workstreamDir());
                }
                NativePermissionRequests.observe(f.context(), prepared, k, phase, observation);
            }
            public boolean permissionRepliesClosed() throws java.io.IOException {
                if(scenario.equals("busy-before-send") && sendIntent.get() && busyInjected.compareAndSet(false,true)) throw new WorkstreamLockedException(f.paths().workstreamDir());
                return NativePermissionRequests.closed(f.context(), prepared);
            }
        };
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var human = executor.submit(() -> {
            try {
                assertTrue(announced.await(8, java.util.concurrent.TimeUnit.SECONDS));
                long limit = System.currentTimeMillis()+8000;
                while (System.currentTimeMillis()<limit) {
                    var rows = (List<?>) NativePermissionRequests.view(f.ledger(), f.paths()).get("requests");
                    if (!rows.isEmpty()) {
                        if (scenario.equals("cancel")) NativePermissionRequests.cancel(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), "stop now");
                        else NativePermissionRequests.decide(f.ledger(), f.paths(), prepared.execution().nodeExecutionId(), key, scenario.equals("deny") ? "exact-deny" : "exact-allow", "human exact choice");
                        return;
                    }
                    Thread.sleep(20);
                }
                throw new AssertionError("legal pending request never reached human read model");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
        AcpxRunResult result;
        try {
            var request = new AcpxRunRequest(root, "exact-session", f.prompt(), root.resolve("stdout"), root.resolve("stderr"), AcpxPermissionProfile.implement(), sink);
            result = transport.runCodexDesignReview(request);
            assertTrue(Files.exists(root.resolve("stdout")), result.toString());
            try { human.get(10, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception e) { throw new AssertionError(result.toString()+"\nOUT:"+Files.readString(root.resolve("stdout"))+"\nERR:"+Files.readString(root.resolve("stderr")), e); }
        } finally { executor.shutdownNow(); }
        assertTrue(events.stream().anyMatch(e -> e.kind()==AgentExecutionTimelineEventKind.COMMAND_INTENT && e.details().get("purpose").equals("prompt") && ((List<?>) e.details().get("command")).contains("--permission-replies")), "actual prompt intent, not synthetic failure summary");
        var replies = Files.exists(root.resolve("replies.jsonl")) ? Files.readAllLines(root.resolve("replies.jsonl")) : List.<String>of();
        assertEquals(scenario.equals("cancel")?0:1, replies.size());
        if (!scenario.equals("cancel")) {
            var actual = json.readTree(replies.getFirst());
            assertEquals(key, actual.path("requestKey").asText()); assertEquals("rpc-A", actual.path("rpcId").asText());
            assertEquals(scenario.equals("deny")?"exact-deny":"exact-allow", actual.path("optionId").asText());
            assertTrue(NativePermissionRequests.commit(f.context(), prepared, key).isEmpty(), "no resend even after uncertain receipt");
        }
        boolean failure = scenario.equals("unknown") || scenario.equals("cancel") || scenario.equals("busy-after-send");
        assertEquals(failure, result.adapterSignals().stream().anyMatch(s -> s.type()==AdapterSignalType.NATIVE_PERMISSION_INTERRUPTED));
        assertEquals(!failure, result.exitCode()==0);
        assertEquals(!scenario.equals("cancel") && !scenario.equals("deny"), Files.exists(root.resolve("product-change")));
        assertEquals(0, qualificationAfterExternal.get(), "post-IPC uncertainty must not re-enter qualification or resend");
        assertEquals(1, Files.readAllLines(root.resolve("prompt-count")).size(), "no automatic prompt retry or correction");
        assertTrue(NativePermissionRequests.view(f.ledger(), f.paths()).containsKey("requests"));
        f.ledger().failNodeExecution(f.paths(), f.ledger().latestNodeExecution(f.paths(), f.ledger().loadNode(f.paths(), f.context().instance().nodeInstanceId())).orElseThrow(), new java.io.IOException("test finalization, not business approval"));
        assertEquals(true, ((Map<?,?>) f.ledger().latestNodeExecution(f.paths(), f.ledger().loadNode(f.paths(), f.context().instance().nodeInstanceId())).orElseThrow().result().get("permission_reply")).get("closed"));
    }
    private Path executable() throws Exception {
        var executable = root.resolve("fake-acpx.py");
        Files.writeString(executable, """
#!/usr/bin/python3
import sys,json,time,pathlib
p=pathlib.Path('.')
a=sys.argv[1:]
if 'sessions' in a and 'show' in a:
 print(p.joinpath('session.json').read_text());sys.exit(0)
if 'permission-reply' in a:
 reply=json.loads(a[a.index('--reply')+1])
 with p.joinpath('replies.jsonl').open('a') as f:f.write(json.dumps(reply)+'\\n')
 if reply['optionId']=='exact-allow':p.joinpath('product-change').write_text('approved local fixture change')
 if p.joinpath('scenario').read_text()=='unknown':sys.exit(4)
 print(json.dumps({'schema':'dual.native.permission.reply.v1','requestKey':reply['requestKey'],'ownerGeneration':reply['ownerGeneration'],'consumed':True}));sys.exit(0)
if '-f' in a:
 with p.joinpath('prompt-count').open('a') as f:f.write('prompt\\n')
 print(json.dumps({'jsonrpc':'2.0','method':'dual/permission/request','params':json.loads(p.joinpath('ask.json').read_text())}),flush=True)
 until=time.time()+12
 while time.time()<until and not p.joinpath('replies.jsonl').exists():time.sleep(.02)
 if p.joinpath('replies.jsonl').exists(): print('[done] end_turn',flush=True);sys.exit(0)
 sys.exit(3)
print('[done] end_turn',flush=True)
""");
        assertTrue(executable.toFile().setExecutable(true)); return executable;
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
