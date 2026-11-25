package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowIterationDocument;
import dev.dual.runtime.WorkflowCommand;
import dev.dual.runtime.WorkflowRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reuses the real runtime fixture; only the reviewer response is substituted. */
class LateContractRevisionContextTest {
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void reviewHumanRevisionAndHistoricalClarifyKeepDistinctParents(@TempDir Path root) throws Exception {
        var f = lateRoot(root);
        var rootIteration = active(f).iterationId();
        var a = context(f, rootIteration).previousReference();
        f.until("intake.codex_product_review");
        rejectReview(f);
        f.step();
        var cIteration = active(f).iterationId();
        var cContext = context(f, cIteration);
        var b = cContext.previousReference();
        assertNotEquals(a, b);
        assertEquals(List.of("define output behavior", "Review B obligation"), texts(cContext));
        f.until("gate.human_requirement_review");
        decide(f, "revise", "Human C note");
        var dIteration = active(f).iterationId();
        var dContext = context(f, dIteration);
        var c = dContext.previousReference();
        assertNotEquals(b, c);
        assertEquals(a, dContext.lateRoot().approvedProposalReference());
        assertEquals(List.of("define output behavior", "Review B obligation", "Human C note"), texts(dContext));
        f.until("gate.human_requirement_review");
        f.approve();
        f.until("gate.contract_review");
        var compileD = String.valueOf(active(f).result().get("source_node_instance_id"));
        var d = new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger()).proposalForCompile(root, f.ws, compileD);
        decide(f, "clarify", "Clarify D note");
        var eIteration = active(f).iterationId();
        var eContext = context(f, eIteration);
        assertEquals(d, eContext.previousReference());
        assertEquals(a, eContext.lateRoot().approvedProposalReference());
        assertEquals(List.of("define output behavior", "Review B obligation", "Human C note", "Clarify D note"), texts(eContext));
        var authorPrompt = AgentResultPromptContract.render(f.ws, "run-final-input", "intake.claude_brainstorm",
                "READY_FOR_PRODUCT_REVIEW", java.util.Set.of("READY_FOR_PRODUCT_REVIEW"),
                AgentResultPromptContract.proposalEmissionContext(eContext));
        assertTrue(authorPrompt.contains("repeat the existing verification"),
                "non-contract evidence from the original acceptance must survive every revision in author input");
        assertTrue(authorPrompt.contains(eContext.lateRoot().acceptanceSha256()));
        assertFalse(texts(eContext).contains("repeat the existing verification"),
                "carried evidence must not become a second contract obligation source");
        f.until("gate.human_requirement_review");
        f.approve();
        f.until("gate.contract_review");
        // A second real compilation has now overwritten the stable resolution.
        assertEquals(d, context(f, eIteration).previousReference());
        assertEquals(eContext.obligations(), context(f, eIteration).obligations());
        var cold = coldResolve(f, eIteration);
        assertTrue(cold.path("valid").asBoolean(), cold.toString());
        assertEquals(d.sha256(), cold.path("parent_sha256").asText());
        assertEquals(JSON.valueToTree(eContext.obligations()), cold.path("obligations"));
        assertEquals(c, context(f, dIteration).previousReference());
        f.approve();
        f.acceptanceCount = 0;
        f.until("acceptance.classify_failure");
        f.step();
        var newContext = context(f, active(f).iterationId());
        assertNotEquals(a, newContext.lateRoot().approvedProposalReference());
        assertEquals(newContext.previousReference(), newContext.lateRoot().approvedProposalReference());
        assertEquals(List.of("define output behavior"), texts(newContext));
        assertNotEquals(eContext.lateRoot().classifierNodeInstanceId(), newContext.lateRoot().classifierNodeInstanceId());
    }

    @Test
    void coldReaderUsesFrozenReferencesAfterStableFilesAreDestroyed(@TempDir Path root) throws Exception {
        var f = lateRoot(root);
        var iteration = active(f).iterationId();
        var expected = context(f, iteration);
        Files.writeString(f.paths.workstreamDir().resolve("artifacts/contract-resolution.json"), "not authority");
        Files.writeString(f.paths.workstreamDir().resolve("artifacts/task-contract.json"), "not authority");
        Files.delete(f.paths.workstreamDir().resolve("artifacts/task-contract.json"));
        assertEquals(expected, context(f, iteration));
        var cold = coldResolve(f, iteration);
        assertTrue(cold.path("valid").asBoolean(), cold.toString());
        assertEquals(expected.previousReference().sha256(), cold.path("parent_sha256").asText());
        assertEquals(expected.lateRoot().approvedProposalReference().sha256(), cold.path("approved_sha256").asText());
        Files.delete(root.resolve(expected.lateRoot().approvedProposalReference().artifactRef()));
        assertFalse(resolve(f, iteration).valid());
        assertFalse(coldResolve(f, iteration).path("valid").asBoolean());
    }

    @Test
    void frozenRootIsMandatoryEvenAfterACompleteDraft(@TempDir Path root) throws Exception {
        var f = lateRoot(root);
        f.until("intake.codex_product_review");
        rejectReview(f);
        f.step();
        var iteration = active(f).iterationId();
        var context = context(f, iteration);
        var classifierPath = f.paths.nodeJson(context.lateRoot().classifierNodeInstanceId());
        var node = (ObjectNode) JSON.readTree(classifierPath.toFile());
        ((ObjectNode) node.get("result")).remove("acceptance_rework_baseline");
        JSON.writeValue(classifierPath.toFile(), node);
        assertFalse(resolve(f, iteration).valid());
    }

    @Test
    void workspaceAndAcceptanceTamperingAreRejected(@TempDir Path root) throws Exception {
        var f = lateRoot(root);
        var iteration = active(f).iterationId();
        var context = context(f, iteration);
        var workspace = f.paths.workstreamDir().resolve("workspaces/" + context.lateRoot().workspaceId() + ".json");
        var saved = Files.readString(workspace);
        var document = (ObjectNode) JSON.readTree(saved);
        document.put("baseCommit", "changed");
        JSON.writeValue(workspace.toFile(), document);
        assertFalse(resolve(f, iteration).valid());
        Files.writeString(workspace, saved);
        var acceptance = root.resolve(context.lateRoot().acceptanceRef());
        Files.writeString(acceptance, "{}");
        assertFalse(resolve(f, iteration).valid());
    }

    @Test
    void historicalClarifyRejectsAmbiguousApproval(@TempDir Path root) throws Exception {
        var f = lateRoot(root);
        f.until("gate.human_requirement_review");
        var requirementNodeId = active(f).nodeInstanceId();
        f.approve();
        f.until("gate.contract_review");
        decide(f, "clarify", "Clarify candidate");
        var iteration = active(f).iterationId();
        assertTrue(resolve(f, iteration).valid());
        var gatePath = f.paths.workstreamDir().resolve("gates/gate-" + requirementNodeId + ".json");
        Files.copy(gatePath, gatePath.resolveSibling("duplicate.json"));
        assertFalse(resolve(f, iteration).valid(), "two matching approvals cannot select a latest winner");
    }

    @Test
    void intermediateCompileInputsAndParentCycleFailClosed(@TempDir Path root) throws Exception {
        var f = lateRoot(root);
        f.until("gate.human_requirement_review");
        f.approve();
        f.until("gate.contract_review");
        var compileId = String.valueOf(active(f).result().get("source_node_instance_id"));
        decide(f, "clarify", "Clarify candidate");
        var iteration = active(f).iterationId();
        var compilePath = f.paths.nodeJson(compileId);
        var saved = Files.readString(compilePath);
        var compile = (ObjectNode) JSON.readTree(saved);
        compile.putArray("inputRefs");
        JSON.writeValue(compilePath.toFile(), compile);
        assertFalse(resolve(f, iteration).valid());
        Files.writeString(compilePath, saved);
        var iterationPath = f.paths.workstreamDir().resolve("iterations/" + iteration + ".json");
        var document = (ObjectNode) JSON.readTree(iterationPath.toFile());
        document.put("parentIterationId", iteration);
        JSON.writeValue(iterationPath.toFile(), document);
        assertFalse(resolve(f, iteration).valid());
    }

    @Test
    void missingRouteAndClassifierStatusCannotBeReconstructedFromFiles(@TempDir Path root) throws Exception {
        var f = lateRoot(root);
        var iteration = active(f).iterationId();
        var context = context(f, iteration);
        var events = f.paths.workstreamDir().resolve("events.jsonl");
        var saved = Files.readString(events);
        var targetId = active(f).nodeInstanceId();
        var filtered = new StringBuilder();
        for (var line : saved.lines().toList()) {
            if (!JSON.readTree(line).path("payload").path("node_instance_id").asText().equals(targetId))
                filtered.append(line).append('\n');
        }
        Files.writeString(events, filtered.toString());
        assertFalse(resolve(f, iteration).valid());
        Files.writeString(events, saved);
        var path = f.paths.nodeJson(context.lateRoot().classifierNodeInstanceId());
        var node = (ObjectNode) JSON.readTree(path.toFile());
        node.put("status", "RUNNING");
        JSON.writeValue(path.toFile(), node);
        assertFalse(resolve(f, iteration).valid());
    }

    @Test
    void initialIterationAndFiveArgumentContextRemainUnchanged(@TempDir Path root) throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "src/main.txt");
        var context = context(f, active(f).iterationId());
        assertFalse(context.revision());
        assertNull(context.lateRoot());
        assertNull(context.approvedScopeBaseline());
        assertNull(new TaskContractRevisionContextResolver.Context(
                WorkflowIterationDocument.initial("intake.claude_brainstorm", Instant.now()), false, null, null, List.of()).approvedScopeBaseline());
    }

    static AcceptanceRepairSequenceTest.Fixture lateRoot(Path root) throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "src/main.txt");
        f.until("acceptance.classify_failure");
        f.step();
        return f;
    }

    static void rejectReview(AcceptanceRepairSequenceTest.Fixture f) throws Exception {
        assertEquals("intake.codex_product_review", f.currentNode());
        var provider = new TaskHandler() {
            public String type() { return "acpx.agent_run"; }
            public NodeResult execute(TaskContext context) throws Exception {
                var output = f.output(context);
                var path = f.root.resolve(output.outputRefs().getFirst());
                var envelope = (ObjectNode) JSON.readTree(path.toFile());
                var result = (ObjectNode) envelope.get("result");
                result.put("decision", "NEEDS_REVISION").put("lifecycle_status", "NEEDS_REVISION").put("blocking", true);
                result.putArray("must_fix").add("Review B obligation");
                result.putArray("contract_obligations").addObject().put("id", "CO-B").put("text", "Review B obligation");
                ((ObjectNode) result.get("failure")).putArray("contract_gaps").add("Review B obligation");
                JSON.writeValue(path.toFile(), envelope);
                return output;
            }
        };
        var runtime = new WorkflowRuntime(f.ledger, new HandlerRegistry(
                List.of(provider, new ContextBuildPackageHandler(), new ContractCompileHandler(), new ArtifactValidateHandler(), new FailureClassifyHandler()),
                List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler())));
        runtime.resume(new WorkflowCommand(f.root, f.ws));
        assertEquals("intake.decision", f.currentNode());
    }

    static NodeInstanceDocument active(AcceptanceRepairSequenceTest.Fixture f) throws Exception {
        return f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
    }

    static void decide(AcceptanceRepairSequenceTest.Fixture f, String action, String note) throws Exception {
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), action, note);
    }

    static TaskContractRevisionContextResolver.Result resolve(AcceptanceRepairSequenceTest.Fixture f, String iteration) {
        return new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger()).resolveForIteration(f.root, f.ws, iteration);
    }

    static TaskContractRevisionContextResolver.Context context(AcceptanceRepairSequenceTest.Fixture f, String iteration) {
        var result = resolve(f, iteration);
        assertTrue(result.valid(), result.errors().toString());
        return result.context();
    }

    static List<String> texts(TaskContractRevisionContextResolver.Context context) {
        return context.obligations().stream().map(AgentResultEnvelope.ReviewObligation::text).toList();
    }

    private static com.fasterxml.jackson.databind.JsonNode coldResolve(
            AcceptanceRepairSequenceTest.Fixture f, String iteration) throws Exception {
        var output = Files.createTempFile("late-contract-cold-", ".json");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                LateContractRevisionContextTest.class.getName(), f.root.toString(), f.ws, iteration)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "cold reader timed out");
            var text = Files.readString(output);
            assertEquals(0, process.exitValue(), text);
            return JSON.readTree(text);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(output);
        }
    }

    public static void main(String[] args) throws Exception {
        var result = new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger()).resolveForIteration(Path.of(args[0]), args[1], args[2]);
        var document = JSON.createObjectNode().put("valid", result.valid());
        document.set("errors", JSON.valueToTree(result.errors()));
        if (result.valid()) {
            document.put("parent_sha256", result.context().previousReference().sha256());
            document.put("approved_sha256", result.context().lateRoot().approvedProposalReference().sha256());
            document.set("obligations", JSON.valueToTree(result.context().obligations()));
        }
        System.out.println(JSON.writeValueAsString(document));
    }
}
