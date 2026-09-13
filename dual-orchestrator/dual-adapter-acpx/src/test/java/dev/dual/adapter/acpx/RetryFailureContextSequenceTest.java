package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.*;
import dev.dual.runtime.handler.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RetryFailureContextSequenceTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String KEY = "retry_failure_context";
    private static final String SECTION = "Previous rejected execution";
    @TempDir Path root;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectedAuthorRetriesWithDiagnosticAndStillTraversesReviewAndApproval(boolean queued) throws Exception {
        for (var agent : List.of("claude", "codex")) {
            var f = new Fixture(root.resolve(agent), queued, agent);
            for (var stage : List.of("design", "plan")) {
                f.transport.failNode = stage + ".claude_" + stage;
                f.transport.badPath = "unapproved/first.txt";
                f.advance();
                var failed = f.active();
                assertEquals(NodeStatus.FAILED, failed.status());
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.current().status());
                assertFalse(f.transport.prompts.getLast().contains(SECTION));
                var oldExecution = failed.latestExecutionId();
                var inputRefs = failed.inputRefs();
                var iteration = failed.iterationId();
                var runId = failed.result().get("run_id");
                int calls = f.transport.prompts.size();

                f.ledger.retryActiveNode(f.paths, "Retry the same approved task");
                var ready = f.active();
                var snapshot = (Map<?, ?>) ready.result().get(KEY);
                assertNotNull(snapshot, "retry must preserve the failed diagnostic before discarding result");
                assertEquals(oldExecution, snapshot.get("source_execution_id"));
                assertEquals(runId, snapshot.get("source_run_id"));
                assertEquals(iteration, ready.iterationId());
                assertEquals(inputRefs, ready.inputRefs());
                assertTrue(ready.outputRefs().isEmpty());
                assertFalse(ready.result().containsKey("permission_approval"));
                assertTrue(snapshot.get("diagnostic_text").toString().contains("unapproved/first.txt"));

                // Historical files are not inputs to the retry snapshot, including after a cold start.
                Files.delete(f.paths.nodeExecutionJson(oldExecution));
                Files.delete(f.root.resolve(failed.result().get("contract_failure_diagnostic_ref").toString()));
                f.ledger = new WorkflowFileLedger();
                f.transport.badPath = "";
                f.advance();
                assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.current().status());
                assertEquals("gate.human_" + stage + "_approval", f.active().nodeDefinitionId());
                assertEquals(calls + 2, f.transport.prompts.size(), "one author then one real reviewer node");
                var retryPrompt = f.transport.prompts.get(calls);
                assertTrue(retryPrompt.contains(SECTION));
                assertTrue(retryPrompt.contains("unapproved/first.txt"));
                assertTrue(retryPrompt.contains(oldExecution));
                assertFalse(f.transport.prompts.get(calls + 1).contains(SECTION), "no retry section in reviewer");
                var published = f.ledger.loadNode(f.paths, failed.nodeInstanceId());
                assertEquals(NodeStatus.COMPLETED, published.status());
                assertFalse(published.result().containsKey(KEY));
                assertNotEquals(oldExecution, published.latestExecutionId());
                assertTrue(published.outputRefs().stream().anyMatch(ref -> ref.endsWith(".contract.json")));
                var promptPath = f.transport.promptPaths.get(calls);
                assertEquals(retryPrompt, Files.readString(promptPath));
                assertTrue(Files.readString(PromptManifestWriter.manifestPathFor(promptPath)).contains(SECTION));
                assertEquals(0, f.transport.corrections, "node retry does not spend schema-correction attempts");
                f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Accept reviewed result");
            }
            f.advance();
            assertEquals(WorkflowStatus.COMPLETED, f.current().status());
            assertFalse(f.transport.prompts.getLast().contains(SECTION), "implementation inherits no diagnostic");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anotherInvalidCandidateIsRejectedAndNextRetryUsesOnlyItsDiagnostic(boolean queued) throws Exception {
        var f = new Fixture(root, queued, "claude");
        f.transport.badPath = "unapproved/first.txt";
        f.advance();
        f.ledger.retryActiveNode(f.paths, "Retry");
        f.transport.badPath = "unapproved/second.txt";
        f.advance();
        assertEquals(2, f.transport.prompts.size(), "no reviewer or automatic retry");
        var second = f.active();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.current().status());
        assertTrue(second.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
        f.ledger.retryActiveNode(f.paths, "Retry after inspection again");
        var text = ((Map<?, ?>) f.active().result().get(KEY)).get("diagnostic_text").toString();
        assertTrue(text.contains("unapproved/second.txt"));
        assertFalse(text.contains("unapproved/first.txt"));
        f.transport.badPath = "";
        f.advance();
        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.current().status());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void incompleteRetryWritesCannotLaunchAfterRestart(boolean queued) throws Exception {
        for (var cut : List.of("event", "current")) {
            var f = new Fixture(root.resolve(cut), queued, "claude");
            f.transport.badPath = "unapproved/first.txt";
            f.advance();
            var failurePath = cut.equals("event") ? f.paths.eventsJsonl()
                    : f.paths.workflowCurrentJson().resolveSibling("current.json.tmp");
            var backup = failurePath.resolveSibling("retry-fault-backup");
            if (cut.equals("event")) Files.move(failurePath, backup);
            Files.createDirectory(failurePath);
            try {
                assertThrows(Exception.class, () -> f.ledger.retryActiveNode(f.paths, "Retry"));
            } finally {
                Files.delete(failurePath);
                if (cut.equals("event")) Files.move(backup, failurePath);
            }
            assertEquals(NodeStatus.READY, f.active().status());
            assertNotNull(f.active().result().get(KEY));
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.current().status());
            f.ledger = new WorkflowFileLedger();
            f.runtime().resume(new WorkflowCommand(f.root, f.ws));
            if (queued) new AgentWorker(f.ledger, f.handlers()).runOne(new WorkflowCommand(f.root, f.ws));
            assertEquals(1, f.transport.prompts.size(), "partial retry must not spend another provider turn");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void malformedSnapshotStopsTheWholePipelineBeforeProvider(boolean queued) throws Exception {
        for (var corrupt : List.of("version", "text", "limit", "truncated", "identity", "extra")) {
            var f = new Fixture(root.resolve(corrupt), queued, "claude");
            f.transport.badPath = "unapproved/first.txt";
            f.advance();
            f.ledger.retryActiveNode(f.paths, "Retry");
            var node = f.active();
            var document = (ObjectNode) JSON.readTree(f.paths.nodeJson(node.nodeInstanceId()).toFile());
            var snapshot = (ObjectNode) document.path("result").path(KEY);
            assertNotNull(snapshot);
            switch (corrupt) {
                case "version" -> snapshot.put("schema_version", 2);
                case "text" -> snapshot.put("diagnostic_text", 42);
                case "limit" -> snapshot.put("diagnostic_text", "x".repeat(8193));
                case "truncated" -> snapshot.put("truncated", "false");
                case "identity" -> snapshot.putArray("source_execution_id");
                case "extra" -> snapshot.put("approve_all", true);
            }
            JSON.writeValue(f.paths.nodeJson(node.nodeInstanceId()).toFile(), document);
            f.advance();
            assertEquals(1, f.transport.prompts.size());
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.current().status());
            assertEquals(NodeStatus.FAILED, f.active().status());
            assertTrue(f.active().outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
            f.runtime().resume(new WorkflowCommand(f.root, f.ws));
            assertEquals(1, f.transport.prompts.size(), "invalid context must not trigger automatic retry");
        }
    }

    private static final class Fixture {
        final Path root;
        final boolean queued;
        final String ws = "ws-retry-diagnostic";
        final WorkstreamPaths paths;
        final Transport transport = new Transport();
        WorkflowFileLedger ledger = new WorkflowFileLedger();

        Fixture(Path root, boolean queued, String authorAgent) throws Exception {
            this.root = root.toAbsolutePath();
            this.queued = queued;
            Files.createDirectories(this.root);
            git("init", "-q");
            Files.writeString(this.root.resolve("README.md"), "Document conversion fixture\n");
            git("add", "README.md");
            git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "baseline");
            paths = new WorkstreamPaths(this.root, new DualId(ws));
            var nodes = new ArrayList<NodeDefinition>();
            var edges = new ArrayList<EdgeDefinition>();
            for (var stage : List.of("design", "plan")) {
                var author = stage + ".claude_" + stage;
                var reviewer = stage + ".codex_" + (stage.equals("design") ? "arch" : "plan") + "_review";
                nodes.add(agent(author, authorAgent, stage + "-claude", stage + "_result"));
                nodes.add(new NodeDefinition("ctx." + stage, NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", reviewer, "profile", stage.equals("design") ? "architecture_review" : "plan_review",
                        "requires_approved_task_contract", true)));
                nodes.add(agent(reviewer, "codex", stage + "-codex-review", "agent_output_contract"));
                nodes.add(new NodeDefinition(stage + ".decision", NodeKind.DECISION, "review.decision", Map.of()));
                nodes.add(gate("gate.human_" + stage + "_approval", stage));
                edges.add(new EdgeDefinition(author, "completed", "ctx." + stage));
                edges.add(new EdgeDefinition("ctx." + stage, "completed", reviewer));
                edges.add(new EdgeDefinition(reviewer, "completed", stage + ".decision"));
                edges.add(new EdgeDefinition(stage + ".decision", stage.equals("design") ? "READY_FOR_ARCH_REVIEW" : "READY_TO_IMPLEMENT",
                        "gate.human_" + stage + "_approval"));
                edges.add(new EdgeDefinition("gate.human_" + stage + "_approval", "revise", author));
            }
            nodes.add(new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()));
            nodes.add(gate("gate.contract_review", "contract"));
            nodes.add(agent("implement.claude_implement", "claude", "implement-claude", "agent_output_contract"));
            edges.add(new EdgeDefinition("contract.compile", "completed", "gate.contract_review"));
            edges.add(new EdgeDefinition("gate.contract_review", "approved", "design.claude_design"));
            edges.add(new EdgeDefinition("gate.human_design_approval", "approved", "plan.claude_plan"));
            edges.add(new EdgeDefinition("gate.human_plan_approval", "approved", "implement.claude_implement"));
            ledger.initialize(paths, ws, new WorkflowDefinition("retry-diagnostic-fixture", 1, "design.claude_design", nodes, edges));
            DesignContractFixture.approve(this.root, ws);
            Files.writeString(paths.workstreamDir().resolve("requirement.md"), "Design a document conversion tool");
        }

        NodeDefinition agent(String id, String agent, String template, String artifact) {
            return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of("agent", agent, "session", "retry-fixture",
                    "prompt_template", template, "permission_profile", "observe", "expected_artifact", artifact,
                    "requires_approved_task_contract", true));
        }
        NodeDefinition gate(String id, String purpose) {
            return new NodeDefinition(id, NodeKind.GATE, "human.review", Map.of("gate_purpose", purpose,
                    "allowed_actions", List.of("approved", "revise")));
        }
        HandlerRegistry handlers() {
            return new HandlerRegistry(List.of(new AcpxAgentTaskHandler(transport, false), new ContextBuildPackageHandler()),
                    List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler()));
        }
        WorkflowRuntime runtime() {
            return new WorkflowRuntime(ledger, handlers(), queued ? AgentTaskDispatchMode.WORKER : AgentTaskDispatchMode.INLINE);
        }
        WorkflowCurrentDocument current() throws Exception { return ledger.loadCurrent(paths); }
        NodeInstanceDocument active() throws Exception { return ledger.loadNode(paths, current().activeNodeInstanceIds().getFirst()); }
        void advance() throws Exception {
            for (int i = 0; i < 25; i++) {
                if (current().status() != WorkflowStatus.RUNNING) return;
                var command = new WorkflowCommand(root, ws);
                runtime().resume(command);
                if (queued && current().status() == WorkflowStatus.RUNNING) new AgentWorker(ledger, handlers()).runOne(command);
            }
            fail("fixture did not pause");
        }
        void git(String... args) throws Exception {
            var command = new ArrayList<>(List.of("git", "-C", root.toString())); command.addAll(List.of(args));
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), output);
        }
    }

    private static final class Transport extends AcpxAgentTransport {
        final List<String> prompts = new ArrayList<>();
        final List<Path> promptPaths = new ArrayList<>();
        String failNode = "design.claude_design";
        String badPath = "";
        int corrections;
        @Override public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException { return run(request); }
        @Override public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws java.io.IOException { return run(request); }
        @Override AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request) {
            corrections++;
            throw new AssertionError("valid schema / invalid scope must not use schema correction");
        }
        private AcpxRunResult run(AcpxRunRequest request) throws java.io.IOException {
            var manifest = JSON.readTree(PromptManifestWriter.manifestPathFor(request.promptPath()).toFile());
            var snapshot = JSON.treeToValue(manifest.get("authoredContext"), AuthoredExecutionContext.Snapshot.class);
            prompts.add(Files.readString(request.promptPath()));
            promptPaths.add(request.promptPath());
            var decision = switch (snapshot.nodeId()) {
                case "plan.claude_plan" -> "READY_FOR_PLAN_REVIEW";
                case "plan.codex_plan_review" -> "READY_TO_IMPLEMENT";
                case "implement.claude_implement" -> "IMPLEMENTED";
                default -> "READY_FOR_ARCH_REVIEW";
            };
            var example = AgentResultPromptContract.render(snapshot.workstreamId(), snapshot.runId(), snapshot.nodeId(), decision, Set.of(decision));
            var envelope = (ObjectNode) JSON.readTree(example.substring(example.indexOf('{')));
            var result = (ObjectNode) envelope.get("result");
            if (result.has("must_fix")) result.putArray("must_fix");
            if (result.has("plan")) ((ObjectNode) result.get("plan")).putArray("edit_files").add("repo/relative/new.ext");
            if (!badPath.isBlank() && snapshot.nodeId().equals(failNode)) {
                if (result.has("design")) ((ObjectNode) result.at("/design/planned_files/0")).put("path", badPath);
                else ((ObjectNode) result.get("plan")).putArray("edit_files").add(badPath);
            }
            Files.writeString(request.stdoutPath(), "fixture transcript"); Files.writeString(request.stderrPath(), "");
            var now = Instant.now();
            return new AcpxRunResult(now, now.plusMillis(1), List.of("fixture"), List.of(), null,
                    new AcpxPromptCompletion("fixture-request", "end_turn", envelope.toString()),
                    new ProcessResult("fixture", null, 0, ProcessUsability.EXITED_ZERO, 1, "", "", false, false), List.of());
        }
    }
}
