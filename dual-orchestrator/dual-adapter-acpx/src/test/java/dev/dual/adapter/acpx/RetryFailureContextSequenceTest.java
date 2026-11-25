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
                f.transport.badPath = "secrets.txt/first.txt";
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
                assertTrue(snapshot.get("diagnostic_text").toString().contains("secrets.txt/first.txt"));

                // Historical files are not inputs to the retry snapshot, including after a cold start.
                Files.delete(f.paths.nodeExecutionJson(oldExecution));
                Files.delete(f.root.resolve(failed.result().get("contract_failure_diagnostic_ref").toString()));
                f.ledger = AcpxTestLedger.ledger();
                f.transport.badPath = "";
                f.advance();
                assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.current().status());
                assertEquals("gate.human_" + stage + "_approval", f.active().nodeDefinitionId());
                assertEquals(calls + 2, f.transport.prompts.size(), "one author then one real reviewer node");
                var retryPrompt = f.transport.prompts.get(calls);
                assertTrue(retryPrompt.contains(SECTION));
                assertTrue(retryPrompt.contains("secrets.txt/first.txt"));
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
            assertEquals("gate.contract_review", f.active().nodeDefinitionId());
            f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Approve exact execution scope");
            f.advance();
            assertEquals(WorkflowStatus.COMPLETED, f.current().status());
            assertFalse(f.transport.prompts.getLast().contains(SECTION), "implementation inherits no diagnostic");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void anotherInvalidCandidateIsRejectedAndNextRetryUsesOnlyItsDiagnostic(boolean queued) throws Exception {
        var f = new Fixture(root, queued, "claude");
        f.transport.badPath = "secrets.txt/first.txt";
        f.advance();
        f.ledger.retryActiveNode(f.paths, "Retry");
        f.transport.badPath = "secrets.txt/second.txt";
        f.advance();
        assertEquals(2, f.transport.prompts.size(), "no reviewer or automatic retry");
        var second = f.active();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.current().status());
        assertTrue(second.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
        f.ledger.retryActiveNode(f.paths, "Retry after inspection again");
        var text = ((Map<?, ?>) f.active().result().get(KEY)).get("diagnostic_text").toString();
        assertTrue(text.contains("secrets.txt/second.txt"));
        assertFalse(text.contains("secrets.txt/first.txt"));
        f.transport.badPath = "";
        f.advance();
        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.current().status());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interruptedRetryCompletesItsFixedDecisionBeforeLegitimateContinuation(boolean queued) throws Exception {
        for (var point : List.of("before_manifest", "after_manifest", "after_replace", "before_append", "before_pending_delete")) {
            var f = new Fixture(root.resolve(point), queued, "claude");
            f.transport.badPath = "secrets.txt/first.txt";
            f.advance();
            var failed = f.active();
            var current = f.current();
            var interrupted = dev.dual.ledger.file.InterruptedTestLedger.at(point, Map.of("test_binary", "acpx-tests-v3"));
            assertThrows(java.io.IOException.class, () -> interrupted.retryActiveNode(f.paths, "Retry after inspection"));
            assertEquals(1, f.transport.prompts.size());
            f.ledger = AcpxTestLedger.ledger();
            if (point.equals("before_manifest")) {
                assertEquals(current, f.current());
                assertEquals(NodeStatus.FAILED, f.active().status());
                f.ledger.retryActiveNode(f.paths, "Retry after inspection");
            } else {
                assertThrows(dev.dual.ledger.file.LedgerSession.PendingCommitException.class, f::current);
                try (var write = f.ledger.writeSession(f.paths, "finish fixed retry", "recover")) { write.commit(); }
            }
            assertEquals(NodeStatus.READY, f.active().status());
            assertNotNull(f.active().result().get(KEY));
            assertEquals(failed.inputRefs(), f.active().inputRefs());
            f.transport.badPath = ""; f.advance();
            assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.current().status());
            assertEquals(3, f.transport.prompts.size(), "one deliberate author retry followed by its independent reviewer");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void malformedSnapshotStopsTheWholePipelineBeforeProvider(boolean queued) throws Exception {
        for (var corrupt : List.of("version", "text", "limit", "truncated", "identity", "extra")) {
            var f = new Fixture(root.resolve(corrupt), queued, "claude");
            f.transport.badPath = "secrets.txt/first.txt";
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
            assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", f.active().result().get("reason"));
            assertEquals("RETRY_FAILURE_CONTEXT_INVALID", f.active().result().get("failure_code"));
            assertEquals("input_preparation_failure", RecoveryAssessment.from(f.active(), f.root, f.ws).classification());
            assertFalse(f.active().result().containsKey("parse_error"));
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
        WorkflowFileLedger ledger = AcpxTestLedger.ledger();

        Fixture(Path root, boolean queued, String authorAgent) throws Exception {
            this.root = root.toAbsolutePath();
            this.queued = queued;
            Files.createDirectories(this.root);
            git("init", "-q");
            Files.writeString(this.root.resolve("README.md"), "Document conversion fixture\n");
            git("add", "README.md");
            git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "baseline");
            paths = new WorkstreamPaths(this.root, new DualId(ws));
            var authored = new AuthoredStageSequenceTest.Fixture(this.root, "repo/relative/new.ext",
                    AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, ws, null, ledger);
            transport.bindings = authored.verificationBindings();
            var document = (ObjectNode) JSON.readTree(paths.workflowDefinitionJson().toFile());
            for (var node : document.withArray("nodes")) {
                if (!node.path("handlerType").asText().equals("acpx.agent_run")) continue;
                var id = node.path("id").asText();
                var config = (ObjectNode) node.get("config");
                config.put("agent", id.contains("codex") ? "codex" : authorAgent);
                config.put("session", "retry-fixture"); config.put("permission_profile", "observe");
                config.put("prompt_template", id.startsWith("design") ? (id.contains("codex") ? "design-codex-review" : "design-claude")
                        : id.startsWith("plan") ? (id.contains("codex") ? "plan-codex-review" : "plan-claude")
                        : id.contains("self_test") ? "implement-claude-self-test" : "implement-claude");
            }
            JSON.writeValue(paths.workflowDefinitionJson().toFile(), document);
        }

        HandlerRegistry handlers() {
            return new HandlerRegistry(List.of(new AcpxAgentTaskHandler(transport, false), new ContextBuildPackageHandler(), new ContractCompileHandler()),
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
        com.fasterxml.jackson.databind.JsonNode bindings;
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
                case "implement.claude_self_test_report" -> "SELF_TEST_COMPLETE";
                default -> "READY_FOR_ARCH_REVIEW";
            };
            var example = AgentResultPromptContract.render(snapshot.workstreamId(), snapshot.runId(), snapshot.nodeId(), decision, Set.of(decision));
            var envelope = (ObjectNode) JSON.readTree(example.substring(example.indexOf('{')));
            var result = (ObjectNode) envelope.get("result");
            if (result.has("must_fix")) result.putArray("must_fix");
            if (result.has("plan")) {
                var plan = (ObjectNode) result.get("plan");
                plan.putArray("edit_files").add("repo/relative/new.ext");
                plan.putArray("files").addObject().put("path", "repo/relative/new.ext").put("operation", "CREATE");
                plan.set("verification_bindings", bindings.deepCopy()); plan.putArray("authority_refs");
            }
            if (!badPath.isBlank() && snapshot.nodeId().equals(failNode)) {
                if (result.has("design")) ((ObjectNode) result.at("/design/planned_files/0")).put("path", badPath);
                else {
                    ((ObjectNode) result.get("plan")).putArray("edit_files").add(badPath);
                    ((ObjectNode) result.get("plan")).putArray("files").addObject().put("path", badPath).put("operation", "CREATE");
                }
            }
            var observations = new ArrayList<dev.dual.core.execution.AgentExecutionEvidence.ToolObservation>();
            if (result.has("verification_checks")) {
                var content = Files.readString(request.workdir().resolve("README.md"));
                observations.add(new dev.dual.core.execution.AgentExecutionEvidence.ToolObservation("fixture-session", "read-readme", "fixture.read", "read",
                        JSON.valueToTree(Map.of("path", "README.md")), JSON.valueToTree(Map.of("content", content)),
                        "completed", null, null, "", false, List.of()));
                var checks = result.putArray("verification_checks");
                for (var binding : bindings) {
                    var check = checks.addObject().put("criterion_id", binding.path("criterion_id").asText())
                            .put("assertion", "Inspected fixture README").put("execution_ref", "CURRENT");
                    check.putArray("tool_call_ids").add("read-readme"); check.putArray("material_refs");
                }
            }
            Files.writeString(request.stdoutPath(), "fixture transcript"); Files.writeString(request.stderrPath(), "");
            var now = Instant.now();
            return new AcpxRunResult(now, now.plusMillis(1), List.of("fixture"), List.of(), null,
                    new AcpxPromptCompletion("fixture-request", "end_turn", envelope.toString()),
                    new ProcessResult("fixture", null, 0, ProcessUsability.EXITED_ZERO, 1, "", "", false, false), List.of(), observations);
        }
    }
}
