package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.*;
import dev.dual.runtime.handler.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredReferenceCorrectionSequenceTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    @TempDir Path root;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void humanRevisionCorrectionsPublishAndFlowThroughReviewForDesignAndPlan(boolean queued) throws Exception {
        var f = new Fixture(root, queued);
        f.advance();
        assertEquals("gate.human_design_approval", f.currentNode());
        for (var stage : List.of("design", "plan")) {
            f.revise("Keep the " + stage + " obligations explicit");
            var run = f.transport.revisedRun;
            var acq = JSON.readTree(run.toFile()).path("agentResultAcquisition");
            assertTrue(acq.path("correctionAttempted").asBoolean());
            assertEquals("SCHEMA_FAILURE", acq.path("correctionReason").asText());
            assertEquals(1, acq.path("maxCorrectionAttempts").asInt());
            assertEquals(2, acq.path("occurrences").size());
            assertFalse(acq.at("/occurrences/0/contractSchemaValid").asBoolean());
            assertTrue(acq.at("/occurrences/1/contractSchemaValid").asBoolean());
            assertTrue(acq.at("/occurrences/0/contractValidationErrors/0").asText().contains("index=0"));
            var original = root.resolve(acq.at("/occurrences/0/outputPath").asText());
            var corrected = root.resolve(acq.at("/occurrences/1/outputPath").asText());
            assertTrue(Files.readString(original).contains(".old"));
            assertFalse(Files.readString(corrected).contains(".old"));
            assertEquals("gate.human_" + stage + "_approval", f.currentNode());
            assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.ledger.loadCurrent(f.paths).status());
            assertTrue(f.transport.calls.stream().filter(id -> id.equals(stage + ".codex_" + (stage.equals("design") ? "arch" : "plan") + "_review")).count() >= 2);
            var node = f.ledger.loadNode(f.paths, f.transport.revisedNode);
            assertEquals(NodeStatus.COMPLETED, node.status());
            var sidecarRef = node.outputRefs().stream().filter(ref -> ref.endsWith(".contract.json")).findFirst().orElseThrow();
            var sidecar = JSON.readTree(root.resolve(sidecarRef).toFile());
            assertEquals(f.transport.activeId, sidecar.at("/revision_responses/0/obligation_id").asText(), sidecar.toPrettyString());
            assertEquals("Frozen response", sidecar.at("/revision_responses/0/evidence").asText());
            // A new ledger/runtime instance consumes the published binding, not the failed first candidate.
            f.runtime = f.runtime(new WorkflowFileLedger());
            f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Accept reviewed candidate");
            f.advance();
        }
        assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
        assertEquals(2, f.transport.corrections);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void shapeValidCorrectionThatChangesEvidenceMustNotPublishOrRoute(boolean queued) throws Exception {
        assertRejected("evidence", queued);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void stillMalformedCorrectionStopsAfterOneAttemptWithoutSidecar(boolean queued) throws Exception {
        assertRejected("malformed", queued);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void changedAuthoredSourceBeforePublicationStillFailsClosed(boolean queued) throws Exception {
        assertRejected("source", queued);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ordinarySchemaCorrectionAlsoCannotPublishChangedSemanticContent(boolean queued) throws Exception {
        for (var stage : List.of("design", "plan")) {
            var f = new Fixture(root.resolve(stage), queued);
            if (stage.equals("plan")) {
                f.advance();
                f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Accept design");
            }
            f.transport.mutation = "schema";
            f.advance();
            assertEquals(1, f.transport.corrections);
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
            var node = f.ledger.loadNode(f.paths, f.transport.revisedNode);
            assertEquals(NodeStatus.FAILED, node.status());
            assertTrue(node.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
            var run = JSON.readTree(f.transport.revisedRun.toFile());
            assertFalse(run.at("/agentResultAcquisition/occurrences/1/contractSchemaValid").asBoolean());
            assertTrue(node.result().get("agent_execution_result_contract") instanceof Map<?, ?>);
            assertEquals(false, ((Map<?, ?>) node.result().get("agent_execution_result_contract")).get("valid"));
        }
    }

    private void assertRejected(String mutation, boolean queued) throws Exception {
        for (var stage : List.of("design", "plan")) {
            var f = new Fixture(root.resolve(stage), queued);
            f.advance();
            if (stage.equals("plan")) {
                f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Accept design");
                f.advance();
            }
            var callsBefore = f.transport.calls.size();
            assertEquals("gate.human_" + stage + "_approval", f.currentNode(),
                    f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst()).toString());
            f.transport.mutation = mutation;
            f.revise("Retain the response content");
            assertEquals(1, f.transport.corrections, mutation);
            assertEquals(callsBefore + 1, f.transport.calls.size(), "no later reviewer or third prompt");
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status(), mutation);
            var node = f.ledger.loadNode(f.paths, f.transport.revisedNode);
            assertEquals(NodeStatus.FAILED, node.status());
            assertTrue(node.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
            var run = JSON.readTree(f.transport.revisedRun.toFile());
            var acq = run.path("agentResultAcquisition");
            assertTrue(acq.path("correctionAttempted").asBoolean());
            assertTrue(Files.exists(f.root.resolve(acq.at("/occurrences/0/outputPath").asText())));
            if (!mutation.equals("source")) assertFalse(acq.at("/occurrences/1/contractSchemaValid").asBoolean());
            if (!mutation.equals("source")) {
                var persisted = new WorkflowFileLedger().loadNode(f.paths, node.nodeInstanceId());
                var replay = new ArtifactContractValidator().validate(f.root, f.ws, persisted.nodeInstanceId(),
                        f.ledger.loadDefinition(f.paths).node(persisted.nodeDefinitionId()), persisted.inputRefs(),
                        new NodeResult("AGENT_OUTPUT_CONTRACT_VIOLATION", persisted.status(), persisted.outputRefs(),
                                persisted.result(), "cold replay"));
                assertEquals(NodeStatus.FAILED, replay.status(), "cold read cannot erase the persisted rejection");
                assertTrue(replay.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
            }
            try (var files = Files.walk(f.transport.revisedRun.getParent())) {
                assertTrue(files.noneMatch(path -> path.toString().endsWith(".contract.json")));
            }
        }
    }

    private static final class Fixture {
        final Path root;
        final String ws = "ws-reference-correction";
        final WorkstreamPaths paths;
        final WorkflowFileLedger ledger = new WorkflowFileLedger();
        final Transport transport = new Transport();
        final boolean queued;
        WorkflowRuntime runtime;

        Fixture(Path root, boolean queued) throws Exception {
            this.root = root.toAbsolutePath();
            this.queued = queued;
            Files.createDirectories(this.root);
            git("init", "-q");
            Files.writeString(this.root.resolve("README.md"), "File conversion fixture\n");
            git("add", "README.md");
            git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "baseline");
            paths = new WorkstreamPaths(this.root, new DualId(ws));
            var nodes = new ArrayList<NodeDefinition>();
            var edges = new ArrayList<EdgeDefinition>();
            for (var stage : List.of("design", "plan")) {
                var author = stage + ".claude_" + stage;
                var reviewer = stage + ".codex_" + (stage.equals("design") ? "arch" : "plan") + "_review";
                nodes.add(agent(author, stage + "-claude", stage + "_result"));
                nodes.add(new NodeDefinition("ctx." + stage, NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", reviewer, "profile", stage.equals("design") ? "architecture_review" : "plan_review",
                        "requires_approved_task_contract", true)));
                nodes.add(agent(reviewer, stage + "-codex-review", "agent_output_contract"));
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
            nodes.add(agent("implement.claude_implement", "implement-claude", "agent_output_contract"));
            edges.add(new EdgeDefinition("contract.compile", "completed", "gate.contract_review"));
            edges.add(new EdgeDefinition("gate.contract_review", "approved", "design.claude_design"));
            edges.add(new EdgeDefinition("gate.human_design_approval", "approved", "plan.claude_plan"));
            edges.add(new EdgeDefinition("gate.human_plan_approval", "approved", "implement.claude_implement"));
            var definition = new WorkflowDefinition("reference-correction-fixture", 1, "design.claude_design", nodes, edges);
            ledger.initialize(paths, ws, definition);
            // Reuse the approved-contract pre-state. All subsequent authors/reviews/gates use normal runtime transitions.
            DesignContractFixture.approve(this.root, ws);
            Files.writeString(paths.workstreamDir().resolve("requirement.md"), "Design a file conversion tool");
            runtime = runtime(ledger);
        }

        WorkflowRuntime runtime(WorkflowFileLedger ledger) {
            return new WorkflowRuntime(ledger, handlers(), queued ? AgentTaskDispatchMode.WORKER : AgentTaskDispatchMode.INLINE);
        }

        HandlerRegistry handlers() {
            return new HandlerRegistry(List.of(new AcpxAgentTaskHandler(transport, false),
                    new ContextBuildPackageHandler()), List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler()));
        }

        void advance() throws Exception {
            for (int i = 0; i < 30; i++) {
                if (ledger.loadCurrent(paths).status() != WorkflowStatus.RUNNING) return;
                var command = new WorkflowCommand(root, ws);
                runtime.resume(command);
                if (queued && ledger.loadCurrent(paths).status() == WorkflowStatus.RUNNING)
                    new AgentWorker(ledger, handlers()).runOne(command);
            }
            fail("fixture did not pause");
        }

        void revise(String note) throws Exception {
            ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", note);
            advance();
        }

        String currentNode() throws Exception {
            return ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()).nodeDefinitionId();
        }

        void git(String... args) throws Exception {
            var command = new ArrayList<>(List.of("git", "-C", root.toString())); command.addAll(List.of(args));
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), output);
        }

        NodeDefinition agent(String id, String template, String artifact) {
            return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of("agent", id.contains("codex") ? "codex" : "claude",
                    "session", "authored-fixture", "prompt_template", template, "permission_profile", "observe",
                    "expected_artifact", artifact, "requires_approved_task_contract", true));
        }

        NodeDefinition gate(String id, String purpose) {
            return new NodeDefinition(id, NodeKind.GATE, "human.review", Map.of("gate_purpose", purpose,
                    "allowed_actions", List.of("approved", "revise")));
        }
    }

    private static final class Transport extends AcpxAgentTransport {
        final List<String> calls = new ArrayList<>();
        final Set<String> correctedSessions = new java.util.HashSet<>();
        int corrections;
        String mutation = "";
        String activeId;
        String revisedNode;
        Path revisedRun;
        ObjectNode valid;
        AcpxRunRequest initial;
        AuthoredExecutionContext.Snapshot snapshot;

        @Override public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException { return initial(request); }
        @Override public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws java.io.IOException { return initial(request); }

        private AcpxRunResult initial(AcpxRunRequest request) throws java.io.IOException {
            var manifest = JSON.readTree(PromptManifestWriter.manifestPathFor(request.promptPath()).toFile());
            snapshot = JSON.treeToValue(manifest.get("authoredContext"), AuthoredExecutionContext.Snapshot.class);
            calls.add(snapshot.nodeId());
            var decision = snapshot.nodeId().equals("plan.claude_plan") ? "READY_FOR_PLAN_REVIEW"
                    : snapshot.nodeId().equals("plan.codex_plan_review") ? "READY_TO_IMPLEMENT"
                    : snapshot.nodeId().equals("implement.claude_implement") ? "IMPLEMENTED" : "READY_FOR_ARCH_REVIEW";
            var prompt = AgentResultPromptContract.render(snapshot.workstreamId(), snapshot.runId(), snapshot.nodeId(), decision, Set.of(decision));
            valid = (ObjectNode) JSON.readTree(prompt.substring(prompt.indexOf('{')));
            var result = (ObjectNode) valid.get("result");
            if (result.has("must_fix")) result.putArray("must_fix");
            if (result.has("plan")) ((ObjectNode) result.get("plan")).putArray("edit_files").add("repo/relative/new.ext");
            if (mutation.equals("schema") && result.has("revision_responses")) {
                revisedNode = snapshot.nodeInstanceId();
                revisedRun = request.promptPath().getParent().getParent().resolve("run.json");
                initial = request;
                var invalid = valid.deepCopy();
                ((ObjectNode) invalid.get("result")).remove("checks");
                return output(request, invalid.toString());
            }
            if (result.has("revision_responses")) {
                var responses = result.putArray("revision_responses");
                snapshot.obligations().forEach(obligation -> responses.addObject().put("obligation_id", obligation.id())
                        .put("disposition", "ADDRESSED").put("evidence", "Frozen response"));
                if (!snapshot.obligations().isEmpty()) {
                    activeId = snapshot.obligations().getFirst().id();
                    revisedNode = snapshot.nodeInstanceId();
                    revisedRun = request.promptPath().getParent().getParent().resolve("run.json");
                    initial = request;
                    var invalid = valid.deepCopy();
                    ((ObjectNode) invalid.at("/result/revision_responses/0")).put("obligation_id", activeId + ".old");
                    return output(request, invalid.toString());
                }
            }
            return output(request, valid.toString());
        }

        @Override AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request) throws java.io.IOException {
            corrections++;
            assertTrue(correctedSessions.add(request.sessionName()), "at most one correction for this occurrence");
            assertEquals(initial.sessionName(), request.sessionName());
            assertTrue(request.permissionProfile().denyAll());
            assertFalse(request.permissionProfile().terminalEnabled());
            if (activeId != null) assertTrue(Files.readString(request.promptPath()).contains(activeId));
            if (mutation.equals("schema")) ((ObjectNode) valid.get("result")).put("summary", "Changed conclusion");
            if (mutation.equals("evidence")) ((ObjectNode) valid.at("/result/revision_responses/0")).put("evidence", "Changed response");
            if (mutation.equals("malformed")) ((ObjectNode) valid.at("/result/revision_responses/0")).put("obligation_id", activeId + ".old");
            if (mutation.equals("source")) Files.writeString(request.workdir().resolve(snapshot.trigger().ref()), "{}");
            return output(request, valid.toString());
        }

        private AcpxRunResult output(AcpxRunRequest request, String value) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "fixture transcript"); Files.writeString(request.stderrPath(), "");
            var now = Instant.now();
            return new AcpxRunResult(now, now.plusMillis(1), List.of("fixture"), List.of(), null,
                    new AcpxPromptCompletion("fixture-request", "end_turn", value),
                    new ProcessResult("fixture", null, 0, ProcessUsability.EXITED_ZERO, 1, "", "", false, false), List.of());
        }
    }
}
