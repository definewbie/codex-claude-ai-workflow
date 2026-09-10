package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.*;
import dev.dual.runtime.WorkflowCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredBoundarySequenceTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule())
            .registerModule(new dev.dual.ledger.file.EdgeConditionModule());
    @TempDir Path root;

    @ParameterizedTest
    @CsvSource({"converter/render.py, 0", "converter/render.py, 1", "site/home.html, 0", "site/home.html, 1"})
    void missingResponseCannotPublishOrRouteDespiteProseClaim(String business, int missingIndex) throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, business);
        f.advanceToGate();
        f.revise("Preserve formatting");
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "revise", "Keep relative links");
        f.outputMutation = result -> {
            var responses = (com.fasterxml.jackson.databind.node.ArrayNode) result.get("revision_responses");
            assertEquals(2, responses.size(), "both human notes must be required before removing either response");
            responses.remove(missingIndex);
            result.put("summary", "All human instructions are completely addressed");
        };
        int reviews = java.util.Collections.frequency(f.calls, "design.codex_arch_review");
        f.advanceToGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        var failed = f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        assertEquals("design.claude_design", failed.nodeDefinitionId());
        assertTrue(failed.result().toString().contains("AUTHORED_CONTEXT_INVALID"), failed.toString());
        assertFalse(Files.exists(AgentOutputContractSidecar.pathFor(f.runDir("setup-run-" + f.run).resolve("outputs/result.json"))));
        assertEquals(reviews, java.util.Collections.frequency(f.calls, "design.codex_arch_review"));
        assertFalse(f.calls.contains("implement.claude_implement"));
        var calls = List.copyOf(f.calls);
        f.ledger.abortRecovery(f.paths, "Stop after invalid authored evidence");
        f.runtime.resume(new WorkflowCommand(root, f.ws));
        assertEquals(WorkflowStatus.FAILED, f.ledger.loadCurrent(f.paths).status());
        assertTrue(f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().isEmpty());
        assertEquals(calls, f.calls, "abort must not dispatch or silently repair an authored failure");
    }

    @ParameterizedTest
    @ValueSource(strings = {"converter/render.py", "site/home.html"})
    void approvedInputDamageAndPartialPublicationBlockTheActualConsumer(String business) throws Exception {
        for (var damage : List.of("sidecar", "manifest", "manifest-half", "gate-source", "gate-action",
                "gate-unresolved", "source-incomplete", "missing-execution", "unpublished-sidecar", "missing-route",
                "sidecar-half", "gate-half", "cross-workstream", "cross-iteration")) {
            var f = new AuthoredStageSequenceTest.Fixture(root.resolve(damage), business);
            var context = f.before("implement.claude_implement");
            var input = AuthoredExecutionContext.prepare(context, "inspect").orElseThrow();
            var plan = input.approvedPlan();
            var candidate = plan.candidate();
            var sidecar = f.root.resolve(candidate.sidecar().ref());
            var manifest = f.runDir(candidate.runId()).resolve("prompts/prompt.md.manifest.json");
            var nodePath = f.paths.nodeJson(candidate.nodeInstanceId());
            switch (damage) {
                case "sidecar" -> Files.writeString(sidecar, Files.readString(sidecar) + " ");
                case "sidecar-half" -> Files.writeString(sidecar, "{");
                case "gate-half" -> Files.writeString(f.root.resolve(plan.gate().ref()), "{");
                case "cross-workstream" -> edit(sidecar, tree -> tree.put("workstream_id", "another-workstream"));
                case "cross-iteration" -> edit(nodePath, tree -> tree.put("iterationId", "iter-forged"));
                case "manifest" -> Files.writeString(manifest, Files.readString(manifest) + " ");
                case "manifest-half" -> Files.writeString(manifest, "{\"authoredContext\":");
                case "gate-source" -> edit(f.root.resolve(plan.gate().ref()), tree ->
                        ((ObjectNode) tree.get("result")).put("source_node_instance_id", "forged-source"));
                case "gate-action" -> edit(f.root.resolve(plan.gate().ref()), tree ->
                        ((ObjectNode) tree.get("result")).put("human_action", "revise"));
                case "gate-unresolved" -> edit(f.root.resolve(plan.gate().ref()), tree -> tree.put("status", "open"));
                case "source-incomplete" -> edit(nodePath, tree -> tree.put("status", "RUNNING"));
                case "unpublished-sidecar" -> edit(nodePath, tree -> tree.putArray("outputRefs"));
                case "missing-execution" -> {
                    var execution = f.ledger.latestNodeExecution(f.paths, f.ledger.loadNode(f.paths, candidate.nodeInstanceId())).orElseThrow();
                    try (var files = Files.walk(f.paths.workstreamDir())) {
                        var path = files.filter(p -> p.getFileName().toString().equals(execution.nodeExecutionId() + ".json"))
                                .findFirst().orElseThrow();
                        Files.delete(path);
                    }
                }
                case "missing-route" -> {
                    var lines = new ArrayList<String>();
                    for (var line : Files.readAllLines(f.paths.eventsJsonl())) {
                        var event = JSON.readTree(line);
                        if (event.at("/payload/node_instance_id").asText().equals(context.instance().nodeInstanceId())
                                && event.path("event_type").asText().equals("node_routed")) continue;
                        lines.add(line);
                    }
                    Files.write(f.paths.eventsJsonl(), lines);
                }
                default -> throw new AssertionError(damage);
            }
            if (damage.equals("manifest-half")) {
                var stdout = f.root.resolve("cold-invalid.json");
                var stderr = f.root.resolve("cold-invalid.err");
                var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                        "-cp", System.getProperty("java.class.path"), ColdRead.class.getName(), f.root.toString(),
                        f.ws, context.instance().nodeInstanceId(), "cold-invalid")
                        .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
                try {
                    assertTrue(process.waitFor(20, TimeUnit.SECONDS));
                    assertNotEquals(0, process.exitValue(), "a cold reader must also reject the partial manifest");
                    assertTrue(Files.readString(stderr).contains("AUTHORED_SOURCE_HASH_MISMATCH"), Files.readString(stderr));
                } finally { if (process.isAlive()) process.destroyForcibly(); }
            }
            f.advanceToGate();
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status(), damage);
            assertFalse(f.calls.contains("implement.claude_implement"), damage);
            assertFalse(f.calls.contains("implement.claude_self_test_report"), damage);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"converter/render.py", "site/home.html"})
    void twoValidPlanCandidatesInOneApprovalCannotBeChosenByOrder(String business) throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, business);
        f.advanceToGate(); f.approve();
        var firstGate = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        var first = (AuthoredExecutionContext.Candidate) new AuthoredStageLineageResolver(root, f.ws, Long.MAX_VALUE)
                .gateView(firstGate).projection().get("candidate");
        f.revise("Preserve the approved scope");
        var current = f.ledger.loadCurrent(f.paths);
        var gateNode = current.activeNodeInstanceIds().getFirst();
        var gatePath = f.paths.workstreamDir().resolve("gates/" + current.openGateIds().getFirst() + ".json");
        edit(f.paths.nodeJson(gateNode), tree ->
                ((com.fasterxml.jackson.databind.node.ArrayNode) tree.get("inputRefs")).add(first.sidecar().ref()));
        edit(gatePath, tree ->
                ((com.fasterxml.jackson.databind.node.ArrayNode) tree.at("/result/subject_input_refs")).add(first.sidecar().ref()));
        var error = assertThrows(java.io.IOException.class, () ->
                new AuthoredStageLineageResolver(root, f.ws, Long.MAX_VALUE).gateView(gateNode));
        assertTrue(error.getMessage().contains("AUTHORED_CANDIDATE_NOT_UNIQUE"), error.getMessage());
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Candidate selection must still be verified");
        f.advanceToGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertFalse(f.calls.contains("implement.claude_implement"));
    }

    @Test
    void unchangedRetryAndColdJvmResolveIdenticalObligations() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, "site/home.html");
        f.advanceToGate();
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "revise", "Keep links relative");
        var context = f.before("design.claude_design");
        var snapshot = AuthoredExecutionContext.prepare(context, "cold-run").orElseThrow();
        assertFalse(snapshot.obligations().isEmpty());
        assertEquals(snapshot, AuthoredExecutionContext.prepare(context, "cold-run").orElseThrow());
        var java = Path.of(System.getProperty("java.home"), "bin/java").toString();
        var stdout = root.resolve("cold.json");
        var stderr = root.resolve("cold.err");
        var process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                ColdRead.class.getName(), root.toString(), f.ws, context.instance().nodeInstanceId(), "cold-run")
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "cold reader timed out");
            assertEquals(0, process.exitValue(), Files.readString(stderr));
            assertEquals(snapshot, JSON.readValue(stdout.toFile(), AuthoredExecutionContext.Snapshot.class));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
        f.advanceToGate();
        f.approve(); f.approve();
        assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
        var calls = List.copyOf(f.calls);
        f.runtime.resume(new WorkflowCommand(root, f.ws));
        assertEquals(calls, f.calls, "terminal replay must not dispatch provider");
    }

    public static class ColdRead {
        public static void main(String[] args) throws Exception {
            var ledger = new dev.dual.ledger.file.WorkflowFileLedger();
            var root = Path.of(args[0]);
            var paths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId(args[1]));
            var instance = ledger.loadCoreNode(paths, args[2]);
            var context = new TaskHandler.TaskContext(root, args[1], ledger.loadDefinition(paths).node(instance.nodeDefinitionId()), instance);
            System.out.println(JSON.writeValueAsString(AuthoredExecutionContext.prepare(context, args[3]).orElseThrow()));
        }
    }

    @Test
    void aLaterOpenApprovalBlocksOldRetryAndAResolvedReplacementDoesNotRebindIt() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, "converter/render.py");
        addEdge(f, "plan.claude_plan", "revisit", "design.claude_design");
        var oldPlan = f.before("plan.claude_plan");
        var original = AuthoredExecutionContext.prepare(oldPlan, "retry-run").orElseThrow();
        f.ledger.completeAndRoute(f.paths, f.ledger.loadDefinition(f.paths), oldPlan.instance().nodeInstanceId(),
                NodeStatus.COMPLETED, "revisit", List.of(), Map.of());
        f.advanceToGate();
        assertEquals("gate.human_design_approval", f.currentNode());
        var openFailure = assertThrows(java.io.IOException.class,
                () -> AuthoredExecutionContext.prepare(oldPlan, "retry-run"));
        assertTrue(openFailure.getMessage().contains("AUTHORED_APPROVAL_NOT_COMPLETED"), openFailure.toString());
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Approve replacement design");
        var changed = assertThrows(java.io.IOException.class,
                () -> AuthoredExecutionContext.prepare(oldPlan, "retry-run"));
        assertTrue(changed.getMessage().contains("AUTHORED_NODE_AUTHORITY_CHANGED"), changed.toString());
        var newPlan = f.before("plan.claude_plan");
        var fresh = AuthoredExecutionContext.prepare(newPlan, "new-run").orElseThrow();
        assertNotEquals(original.approvedDesign(), fresh.approvedDesign());
        assertEquals(original.contract(), fresh.contract());
        int calls = f.calls.size();
        assertEquals(NodeStatus.FAILED, f.executeProvider(oldPlan).status());
        assertEquals(calls, f.calls.size(), "old node retry cannot reach the provider substitute");
        f.advanceToGate(); f.approve();
        assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
        assertEquals(fresh.approvedDesign(), f.inputs.get("implement.claude_implement").approvedDesign());
    }

    @Test
    void sameBytesReapprovalRejectsOldAuthoredChainAndFreshChainCompletes() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, "site/home.html");
        addEdge(f, "implement.claude_implement", "requalify", "contract.compile");
        var oldImplement = f.before("implement.claude_implement");
        var old = AuthoredExecutionContext.prepare(oldImplement, "old-run").orElseThrow();
        var oldCompile = f.ledger.loadNode(f.paths, old.contract().compileNodeInstanceId());
        f.ledger.completeAndRoute(f.paths, f.ledger.loadDefinition(f.paths), oldImplement.instance().nodeInstanceId(),
                NodeStatus.COMPLETED, "requalify", List.of(), Map.of());
        var compile = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        // Supply the already verified, identical compile bytes; E3 tests the new approval epoch, not compilation.
        f.ledger.completeAndRoute(f.paths, f.ledger.loadDefinition(f.paths), compile, NodeStatus.COMPLETED,
                "completed", oldCompile.outputRefs(), oldCompile.result());
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "approve", "Reapprove identical contract bytes");
        var qualified = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        assertEquals(old.contract().contract().sha256(), qualified.sha256());
        assertNotEquals(old.contract().compileNodeInstanceId(), qualified.compileNodeInstanceId());
        int calls = f.calls.size();
        assertEquals(NodeStatus.FAILED, f.executeProvider(oldImplement).status());
        assertEquals(calls, f.calls.size());
        f.advanceToGate(); f.approve(); f.approve();
        assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status(), f.calls.toString());
        var fresh = f.inputs.get("implement.claude_implement");
        assertNotEquals(old.contract(), fresh.contract());
        assertEquals(qualified.compileNodeInstanceId(), fresh.contract().compileNodeInstanceId());
        assertEquals(old.contract().contract().sha256(), fresh.contract().contract().sha256());
        assertNotEquals(old.approvedDesign(), fresh.approvedDesign());
        assertNotEquals(old.approvedPlan(), fresh.approvedPlan());
        assertEquals(fresh.approvedPlan(), f.inputs.get("implement.claude_self_test_report").approvedPlan());
    }

    private static void addEdge(AuthoredStageSequenceTest.Fixture f, String from, String on, String to) throws Exception {
        edit(f.paths.workflowDefinitionJson(), tree -> ((com.fasterxml.jackson.databind.node.ArrayNode) tree.get("edges"))
                .addObject().put("from", from).put("on", on).put("to", to));
    }

    @Test
    void everyDeclaredImplementationAndDirectSelfTestEntryConsumesTheApprovedPlan() throws Exception {
        var full = new dev.dual.runtime.lifecycle.WorkflowDefinitionLoader().loadOrDefault(null, "full");
        var entries = full.edges().stream().filter(edge -> edge.to().equals("context.implement_claude")
                || (edge.from().equals("acceptance.classify_failure")
                    && edge.on().equals("REJECT_VERIFICATION_INSUFFICIENT"))).toList();
        assertEquals(8, entries.size(), "seven implementation entries and one direct self-test entry");
        for (var entry : entries) for (boolean damaged : List.of(false, true)) {
            var f = new AuthoredStageSequenceTest.Fixture(root.resolve(entry.from() + "-" + entry.on() + "-" + damaged), "site/home.html");
            edit(f.paths.workflowDefinitionJson(), tree -> {
                var nodes = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("nodes");
                nodes.add(JSON.valueToTree(full.node("context.implement_claude")));
                if (!entry.from().equals("gate.human_plan_approval")) nodes.add(JSON.valueToTree(full.node(entry.from())));
                var edges = (com.fasterxml.jackson.databind.node.ArrayNode) tree.get("edges");
                if (entry.from().equals("gate.human_plan_approval")) {
                    for (int i = edges.size() - 1; i >= 0; i--)
                        if (edges.get(i).path("from").asText().equals(entry.from()) && edges.get(i).path("on").asText().equals("approved")) edges.remove(i);
                }
                edges.add(JSON.valueToTree(entry));
                if (full.node(entry.from()).kind() == NodeKind.GATE) {
                    // Preserve the copied gate's declared but unused collection action in this repair slice.
                    full.edgesFrom(entry.from()).stream().filter(edge -> edge.on().equals("collect_evidence"))
                            .forEach(edge -> edges.add(JSON.valueToTree(edge)));
                }
                if (entry.from().equals("implement.self_test_decision")) {
                    // Keep the unused human branch valid in this implementation-entry slice.
                    edges.add(JSON.valueToTree(new EdgeDefinition(entry.from(), "SELF_TEST_INCOMPLETE",
                            "gate.human_plan_approval", dev.dual.core.workflow.EdgeCondition.configEquals(
                            "continuation_kind", "HUMAN_REVIEW"), TransitionMetadata.normal())));
                }
                edges.addObject().put("from", "context.implement_claude").put("on", "completed").put("to", "implement.claude_implement");
            });
            var initial = f.before("implement.claude_implement");
            var expected = AuthoredExecutionContext.prepare(initial, "inspect").orElseThrow();
            assertNotNull(expected.approvedPlan(), "Every implementation entry requires an approved plan");
            if (!entry.from().equals("gate.human_plan_approval")) {
                addEdge(f, "implement.claude_implement", "fixture_reentry", entry.from());
                f.ledger.completeAndRoute(f.paths, f.ledger.loadDefinition(f.paths), initial.instance().nodeInstanceId(),
                        NodeStatus.COMPLETED, "fixture_reentry", initial.instance().inputRefs(), Map.of());
                var sourceId = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
                if (full.node(entry.from()).kind() == NodeKind.GATE)
                    f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "revise", "Repair within the approved plan");
                else f.ledger.completeAndRoute(f.paths, f.ledger.loadDefinition(f.paths), sourceId,
                        NodeStatus.COMPLETED, entry.on(), initial.instance().inputRefs(),
                        Map.of("decision", entry.on(), "continuation_kind", "IMPLEMENTATION_REPAIR"));
            }
            if (damaged) Files.delete(f.runDir(expected.approvedPlan().candidate().runId()).resolve("prompts/prompt.md.manifest.json"));
            f.advanceToGate();
            var label = entry.from() + ":" + entry.on() + " damaged=" + damaged;
            if (damaged) {
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status(), label);
                assertFalse(f.calls.contains("implement.claude_implement"), label);
                assertFalse(f.calls.contains("implement.claude_self_test_report"), label);
            } else {
                assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status(), label + f.currentDocumentIfActive());
                var consumer = f.inputs.get("implement.claude_self_test_report");
                assertEquals(expected.approvedPlan(), consumer.approvedPlan(), label);
                assertNull(consumer.parent(), "repair is not a design/plan revision");
                assertTrue(consumer.obligations().isEmpty(), label);
                if (!entry.to().equals("implement.claude_self_test_report"))
                    assertEquals(expected.approvedPlan(), f.inputs.get("implement.claude_implement").approvedPlan(), label);
            }
        }
    }

    private static void edit(Path path, java.util.function.Consumer<ObjectNode> mutation) throws Exception {
        var tree = (ObjectNode) JSON.readTree(path.toFile());
        mutation.accept(tree);
        JSON.writeValue(path.toFile(), tree);
    }

    @Test
    void reviewContextCarriesRequiredBodyAfterHumanRevision() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, "converter/render.py");
        f.advanceToGate();
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "revise", "Keep line breaks");
        var reviewer = f.before("design.codex_arch_review");
        var snapshot = AuthoredExecutionContext.prepare(reviewer, "review").orElseThrow();
        var candidate = snapshot.subject();
        var inputs = AuthoredExecutionContext.verifiedCandidateSnapshot(root, f.ws, candidate);
        var packageRef = reviewer.instance().inputRefs().stream().filter(ref -> ref.endsWith(".md")).findFirst().orElseThrow();
        var body = Files.readString(root.resolve(packageRef));
        assertTrue(body.contains(inputs.render(root).stripTrailing()), "required baseline must survive context packaging");
        assertTrue(body.contains(Files.readString(root.resolve(candidate.sidecar().ref()))), "refs alone are insufficient");
        assertTrue(body.contains("Keep line breaks"));
    }

    @Test
    void successiveHumanPacketsExposeTheSameCandidateAndObligationsAsTheirConsumer() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, "site/home.html",
                AuthoredStageSequenceTest.PlanTopology.REVIEWED_HUMAN);
        f.advanceToGate(); f.approve();
        var exports = Path.of("target/authored-packets");
        Files.createDirectories(exports);
        String previousCandidate = "";
        for (int revision = 0; revision < 2; revision++) {
            var id = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
            var packetPath = f.paths.workstreamDir().resolve("reviews/" + id + ".json");
            var markdownPath = f.paths.workstreamDir().resolve("reviews/" + id + ".md");
            var packet = JSON.readTree(packetPath.toFile());
            var view = new AuthoredStageLineageResolver(root, f.ws, Long.MAX_VALUE).gateView(id).projection();
            assertEquals(JSON.readTree(JSON.writeValueAsString(view)), packet.get("authoredStage"));
            var markdown = Files.readString(markdownPath);
            assertTrue(markdown.contains(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(view)));
            var authored = packet.get("authoredStage");
            var candidate = authored.at("/candidate/sidecar/ref").asText();
            assertFalse(candidate.isBlank());
            assertNotEquals(previousCandidate, candidate);
            previousCandidate = candidate;
            assertTrue(authored.at("/plan/edit_files").toString().contains("site/home.html"));
            if (revision == 0) {
                assertEquals(0, authored.path("activeObligations").size());
                assertEquals(0, authored.path("revisionResponses").size());
                assertEquals("plan issue 1", authored.path("newMustFix").get(0).asText());
            } else {
                assertEquals(2, authored.path("activeObligations").size());
                assertEquals(2, authored.path("revisionResponses").size());
                assertTrue(markdown.contains("NOT_ADDRESSED"));
                assertTrue(markdown.contains("<img src=x onerror=alert(1)>"));
            }
            Files.copy(packetPath, exports.resolve(revision + ".json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(markdownPath, exports.resolve(revision + ".md"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (revision == 0) {
                f.outputMutation = result -> {
                    if (result.path("revision_responses").isArray())
                        for (var response : result.path("revision_responses")) ((ObjectNode) response).put("disposition", "NOT_ADDRESSED");
                };
                f.revise("Keep <img src=x onerror=alert(1)> as literal text");
            }
        }
        var candidate = new AuthoredStageLineageResolver(root, f.ws, Long.MAX_VALUE)
                .gateView(f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst()).projection().get("candidate");
        f.approve();
        assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
        assertEquals(candidate, f.inputs.get("implement.claude_implement").approvedPlan().candidate());
    }
}
