package dev.dual.runtime.handler;

import dev.dual.core.domain.*;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.core.execution.*;
import dev.dual.ledger.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentRunCommitProtocolTest {
    @TempDir Path root;

    @Test void committedPreparationDoesNotGrantASecondExternalCall() throws Exception {
        var f = fixture();
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), Map.of("role", "review"));
        assertEquals(AgentRunCommitProtocol.Observation.PREPARED, AgentRunCommitProtocol.observe(f.paths(), current(f)));
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        assertEquals(AgentRunCommitProtocol.Observation.EXTERNAL_UNKNOWN, AgentRunCommitProtocol.observe(f.paths(), current(f)));
        assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.beginExternalCall(f.context(), prepared));
        // A reconstructed ledger reading the same preparation has no newly committed send capability either.
        var cold = new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "protocol-v1"));
        var coldContext = new TaskHandler.TaskContext(root, "run-protocol", f.context().definition(), f.context().instance(), cold);
        assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.beginExternalCall(coldContext, prepared));
    }

    @Test void changedPromptRejectsBeforeIntentButRestoringTheBoundBytesIsLegal() throws Exception {
        var f = fixture();
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), Map.of());
        Files.writeString(f.prompt(), "different instructions");
        assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.beginExternalCall(f.context(), prepared));
        assertEquals(AgentRunCommitProtocol.Observation.PREPARED, AgentRunCommitProtocol.observe(f.paths(), current(f)));
        Files.writeString(f.prompt(), "review the approved inputs");
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
    }

    @Test void fixedOutcomeAndCompletionSurviveMutableRunProjectionLoss() throws Exception {
        var f = fixture();
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), Map.of());
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        var output = f.run().resolve("result.json");
        Files.writeString(output, "{\"review\":\"observed fixture result\"}");
        var writer = new AtomicJsonWriter();
        writer.write(f.run().resolve("run.json"), Map.of("schemaVersion", 3, "runId", "one", "workstreamId", "run-protocol",
                "nodeId", "task", "agent", "fixture", "status", "process_exited",
                "processResult", Map.of("exitCode", 0, "usability", "EXITED_ZERO", "durationMs", 1), "adapterSignals", List.of()));
        var candidate = AgentExecutionResultFacts.attach(NodeResult.completed("completed", List.of(root.relativize(output).toString()),
                Map.of("run_id", "one", "run_metadata", root.relativize(f.run().resolve("run.json")).toString())),
                AgentResultContractResult.valid("READY"), List.of(ArtifactContractResult.requiredValid("fixture")), null);
        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(root, candidate);
        assertEquals(NodeStatus.COMPLETED, finalized.status());
        assertEquals(AgentRunCommitProtocol.Observation.OUTCOME_RECORDED, AgentRunCommitProtocol.observe(f.paths(), current(f)));
        Files.writeString(f.run().resolve("run.json"), "projection lost");
        assertEquals(finalized, new AgentRunMetadataFinalizer().finalizeNodeResult(root, candidate));
        var completed = AgentRunCommitProtocol.complete(root, f.ledger(), f.paths(), prepared.execution(), finalized);
        assertEquals(AgentRunCommitProtocol.Observation.COMPLETED, AgentRunCommitProtocol.observe(f.paths(), current(f)));
        try (var snapshot = f.ledger().readSession(f.paths())) {
            assertEquals(completed, AgentRunCommitProtocol.consume(snapshot.ledger(), f.paths(), prepared.execution()));
        }
        Files.writeString(output, "{\"review\":\"replaced output\"}");
        assertThrows(IllegalStateException.class, () -> new AgentRunMetadataFinalizer().finalizeNodeResult(root, candidate));
        try (var snapshot = f.ledger().readSession(f.paths())) {
            assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.consume(snapshot.ledger(), f.paths(), prepared.execution()));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void releasedExecutorCannotUseMutableSuccessOrZeroExitToEraseUnknownIntent(boolean intent) throws Exception {
        var f = fixture();
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), Map.of());
        if (intent) AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        // This projection is deliberately misleading. It is not an R03 completion.
        Files.writeString(f.run().resolve("run.json"), "{\"status\":\"completed\",\"exitCode\":0,\"executionOutcome\":{\"status\":\"SUCCESS\"}}");
        AgentRunCommitProtocol.releaseExecutor(f.ledger(), f.paths(), current(f));
        var result = dev.dual.runtime.RecordedExecutionRecovery.recover(f.ledger(), f.paths(), current(f));
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, result.status());
        var node = f.ledger().loadNode(f.paths(), f.context().instance().nodeInstanceId());
        assertEquals(intent ? "UNKNOWN" : "NOT_STARTED", node.result().get("external_effect"));
        assertEquals(intent ? "UNKNOWN" : "NOT_STARTED", node.result().get("provider_invocation"));
        assertEquals(NodeExecutionStatus.FAILED, current(f).status());
        assertEquals(false, node.result().get("automatic_retry"));
        assertTrue(node.outputRefs().isEmpty());
        if (intent) assertThrows(java.io.IOException.class, () -> f.ledger().retryActiveNode(f.paths(), "retry is not adjudication"));
    }

    @Test void liveExecutorIsNotSupersededByRecovery() throws Exception {
        var f = fixture();
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), Map.of());
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        var before = Files.readAllBytes(f.paths().workflowCurrentJson());
        dev.dual.runtime.RecordedExecutionRecovery.recover(f.ledger(), f.paths(), current(f));
        assertArrayEquals(before, Files.readAllBytes(f.paths().workflowCurrentJson()));
        assertEquals(NodeExecutionStatus.RUNNING, current(f).status());
    }

    @Test void deadExecutorBeforeBoundPreparationHasARealRetryExit() throws Exception {
        var owner = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", System.getProperty("java.class.path"), Owner.class.getName()).start();
        Fixture f;
        try { f = fixture(owner.pid()); }
        finally {
            owner.destroyForcibly();
            assertTrue(owner.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        dev.dual.runtime.RecordedExecutionRecovery.recoverUnprepared(f.ledger(), f.paths(), current(f));
        var node = f.ledger().loadNode(f.paths(), f.context().instance().nodeInstanceId());
        assertEquals("PREPARATION_INTERRUPTED", node.result().get("reason"));
        assertEquals(NodeExecutionStatus.FAILED, current(f).status());
        f.ledger().retryActiveNode(f.paths(), "Retry preparation; no external intent existed");
        assertEquals(WorkflowStatus.RUNNING, f.ledger().loadCurrent(f.paths()).status());
        assertEquals(NodeStatus.READY, f.ledger().loadNode(f.paths(), node.nodeInstanceId()).status());
        assertFalse(Files.exists(f.run().resolve("outputs")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void preGoalUnknownHasExplicitRetryAndAbortWithoutInventingAnApprovedContract(boolean abort) throws Exception {
        var f = fixture();
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()),
                Map.of("run", Map.of("runId", "one", "session", "original-session"), "native_invocation", nativeInvocation(), "allowed_request_options", List.of(List.of("--deny-all"))));
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        AgentRunCommitProtocol.releaseExecutor(f.ledger(), f.paths(), current(f));
        dev.dual.runtime.RecordedExecutionRecovery.recover(f.ledger(), f.paths(), current(f));
        var json = new AtomicJsonWriter().objectMapper();
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(
                ExecutionRecoveryAdjudication.describe(f.ledger(), f.paths()).get("decision_template"));
        assertEquals(0, request.path("input_epoch").asLong(), "no fabricated GOAL epoch");
        request.put("request_id", "pre-goal-decision").put("disposition", abort ? "ABORT" : "AUTHORIZE_NEW_ATTEMPT")
                .put("operator_note", "Native stop observed; keep original external uncertainty")
                .put("accept_duplicate_effect_risk", !abort);
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(),
                json.treeToValue(request, ExecutionRecoveryAdjudication.Request.class)));
        var id = UUID.randomUUID().toString();
        var details = new LinkedHashMap<String, Object>(Map.of("command_id", id, "cleanup", true,
                "purpose", "session_close", "session", "original-session", "invocation", nativeInvocation(), "command", List.of("fixture", "codex", "sessions", "close", "original-session")));
        AgentRunCommitProtocol.commandEvent(f.context(), prepared, "command_intent", details);
        details.put("observation", Map.of("success", true, "timed_out", false, "exit_code", 0));
        AgentRunCommitProtocol.commandEvent(f.context(), prepared, "command_result", details);
        var receipt = prepared.path().getParent().resolve(prepared.execution().nodeExecutionId() + "-command-" + id + "-result.json");
        request.set("evidence_refs", json.valueToTree(List.of(new dev.dual.core.contract.TaskContract.Reference(
                root.relativize(receipt).toString(), TaskContractArtifactWriter.sha256(receipt)))));
        var wrong = request.deepCopy(); wrong.put("input_epoch", 1);
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(),
                json.treeToValue(wrong, ExecutionRecoveryAdjudication.Request.class)));
        var decision = json.treeToValue(request, ExecutionRecoveryAdjudication.Request.class);
        ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(), decision);
        var events = Files.readAllBytes(f.paths().eventsJsonl());
        ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(), decision);
        assertArrayEquals(events, Files.readAllBytes(f.paths().eventsJsonl()));
        var historical = current(f);
        assertEquals("UNKNOWN", historical.result().get("external_effect"));
        assertEquals(abort ? WorkflowStatus.FAILED : WorkflowStatus.RUNNING, f.ledger().loadCurrent(f.paths()).status());
        if (!abort) assertEquals(NodeStatus.READY, f.ledger().loadNode(f.paths(), f.context().instance().nodeInstanceId()).status());
        assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.beginExternalCall(f.context(), prepared));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"before_manifest", "after_replace"})
    void explicitlyAdoptingOriginalLateCompletionIsAtomicAndNeverReopensDispatch(String interruption) throws Exception {
        var f = fixture();
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()),
                Map.of("run", Map.of("runId", "one", "session", "original-session"), "native_invocation", nativeInvocation(), "allowed_request_options", List.of(List.of("--deny-all"))));
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        AgentRunCommitProtocol.releaseExecutor(f.ledger(), f.paths(), current(f));
        dev.dual.runtime.RecordedExecutionRecovery.recover(f.ledger(), f.paths(), current(f));
        var json = new AtomicJsonWriter().objectMapper();
        var request = (com.fasterxml.jackson.databind.node.ObjectNode) json.valueToTree(
                ExecutionRecoveryAdjudication.describe(f.ledger(), f.paths()).get("decision_template"));
        request.put("request_id", "adopt-original").put("disposition", "ADOPT_VERIFIED_RESULT")
                .put("operator_note", "Inspect and adopt the original fixed result; do not run again");
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(),
                json.treeToValue(request, ExecutionRecoveryAdjudication.Request.class)));
        var output = f.run().resolve("result.json"); Files.writeString(output, "original observed result");
        new AtomicJsonWriter().write(f.run().resolve("run.json"), Map.of("schemaVersion", 3, "runId", "one",
                "workstreamId", "run-protocol", "nodeId", "task", "agent", "fixture", "session", "original-session",
                "status", "process_exited", "processResult", Map.of("exitCode", 0, "usability", "EXITED_ZERO", "durationMs", 1), "adapterSignals", List.of()));
        var candidate = AgentExecutionResultFacts.attach(NodeResult.completed("completed", List.of(root.relativize(output).toString()),
                Map.of("run_id", "one", "run_metadata", root.relativize(f.run().resolve("run.json")).toString())),
                AgentResultContractResult.valid("READY"), List.of(ArtifactContractResult.requiredValid("fixture")), null);
        var fixed = AgentRunCommitProtocol.complete(root, f.ledger(), f.paths(), prepared.execution(),
                new AgentRunMetadataFinalizer().finalizeNodeResult(root, candidate));
        request.set("evidence_refs", json.valueToTree(List.of(new dev.dual.core.contract.TaskContract.Reference(
                fixed.result().get("completion_ref").toString(), fixed.result().get("completion_sha256").toString()))));
        var decision = json.treeToValue(request, ExecutionRecoveryAdjudication.Request.class);
        Files.writeString(f.prompt(), "replacement instructions");
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(), decision));
        Files.writeString(f.prompt(), "review the approved inputs");
        var originalOutput = Files.readAllBytes(output); Files.writeString(output, "replacement output");
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(), decision));
        Files.write(output, originalOutput);
        var interrupted = InterruptedTestLedger.at(interruption, f.ledger().frozenRuntimeIdentity());
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(interrupted, f.paths(), decision));
        if (interruption.equals("before_manifest")) assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger().loadCurrent(f.paths()).status());
        else assertThrows(java.io.IOException.class, () -> f.ledger().loadCurrent(f.paths()));
        ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(), decision);
        assertEquals(WorkflowStatus.COMPLETED, f.ledger().loadCurrent(f.paths()).status());
        assertEquals(NodeExecutionStatus.SUCCEEDED, current(f).status());
        assertTrue(current(f).result().containsKey("prior_unknown_observation"));
        assertEquals(true, ((Map<?, ?>) current(f).result().get("permission_reply")).get("closed"));
        var events = Files.readAllBytes(f.paths().eventsJsonl());
        ExecutionRecoveryAdjudication.apply(f.ledger(), f.paths(), decision);
        assertArrayEquals(events, Files.readAllBytes(f.paths().eventsJsonl()));
        assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.beginExternalCall(f.context(), prepared));
    }

    public static final class Owner {
        public static void main(String[] args) throws Exception { System.in.read(); }
    }

    private Map<String, Object> nativeInvocation() {
        return Map.of("schema", "dual.acpx.invocation.v1", "executable", "fixture", "agent", "codex",
                "cwd", root.toString(), "session", "original-session", "launch_environment", Map.of());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"allowed", "correction", "session", "executable", "options", "missing", "cleanup_prompt", "released"})
    void commandDispatchConsumesPreparedInvocationAndCannotBorrowAnotherAuthority(String scenario) throws Exception {
        var f = fixture();
        var binding = scenario.equals("missing") ? Map.<String, Object>of() : Map.<String, Object>of(
                "native_invocation", nativeInvocation(), "allowed_request_options", List.of(List.of("--implement"), List.of("--deny-all")));
        var prepared = AgentRunCommitProtocol.prepare(f.context(), f.run(), List.of(f.prompt()), binding);
        AgentRunCommitProtocol.beginExternalCall(f.context(), prepared);
        if (scenario.equals("released")) AgentRunCommitProtocol.releaseExecutor(f.ledger(), f.paths(), current(f));
        var invocation = new LinkedHashMap<>(nativeInvocation());
        if (scenario.equals("session")) invocation.put("session", "foreign-session");
        var details = new LinkedHashMap<String, Object>(Map.of("command_id", UUID.randomUUID().toString(),
                "purpose", "prompt", "cleanup", scenario.equals("cleanup_prompt"), "session", "original-session",
                "invocation", invocation, "command", List.of(scenario.equals("executable") ? "foreign" : "fixture", "codex", "-f", f.prompt().toString()),
                "request_options", List.of(scenario.equals("options") ? "--approve-all" : scenario.equals("correction") ? "--deny-all" : "--implement")));
        if (scenario.equals("allowed") || scenario.equals("correction")) {
            AgentRunCommitProtocol.commandEvent(f.context(), prepared, "command_intent", details);
            details.put("observation", Map.of("exit_code", 0));
            AgentRunCommitProtocol.commandEvent(f.context(), prepared, "command_result", details);
            // A new reader consumes these fixed command identities, not mutable run.json.
            assertTrue(new AtomicJsonWriter().readTree(prepared.path().getParent().resolve(prepared.execution().nodeExecutionId()
                    + "-command-" + details.get("command_id") + "-result.json")).path("details").path("invocation").isObject());
        } else {
            assertThrows(java.io.IOException.class, () -> AgentRunCommitProtocol.commandEvent(f.context(), prepared, "command_intent", details));
            assertFalse(Files.exists(prepared.path().getParent().resolve(prepared.execution().nodeExecutionId()
                    + "-command-" + details.get("command_id") + "-intent.json")));
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
