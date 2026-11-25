package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ExternalVerificationEvidenceTest {
    @TempDir Path temp;
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final WorkflowFileLedger ledger = dev.dual.runtime.RuntimeTestLedger.ledger();

    @Test void realRuntimePublishesSelfTestAndAcceptanceSidecarsWithExactMaterialRefs() throws Exception {
        for (var queued : List.of(false, true)) for (var mode : List.of("valid", "summary", "suffix")) {
            temp = temp.toRealPath();
            var root = temp.resolve("runtime-" + queued + "-" + mode);
            Files.createDirectories(root.resolve("src"));
            GitTrackedPathInventory.command(root, "init");
            Files.writeString(root.resolve("src/Main.java"), "class Main {}");
            GitTrackedPathInventory.command(root, "add", ".");
            GitTrackedPathInventory.command(root, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-m", "baseline");
            var fixture = new AuthoredStageSequenceTest.Fixture(root, "src/Main.java",
                    AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, "ws-test", null, ledger, null, true);
            var prior = fixture.before("implement.claude_self_test_report");
            var paths = new WorkstreamPaths(root, new dev.dual.core.id.DualId("ws-test"));
            var oldDefinition = ledger.loadDefinition(paths);
            var nodes = new ArrayList<>(oldDefinition.nodes());
            var acceptanceContext = new dev.dual.runtime.lifecycle.WorkflowDefinitionLoader().loadOrDefault(null, "full")
                    .node("context.acceptance_codex_verifier_review");
            nodes.add(acceptanceContext);
            nodes.add(new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of("gate_purpose", "implementation_repair", "allowed_actions", List.of("submit_evidence"))));
            nodes.add(new NodeDefinition("acceptance.codex_verifier_review", NodeKind.TASK, "acpx.agent_run", Map.of("expected_artifact", "acceptance_review", "required_contract_phase", "EXECUTION")));
            var edges = new ArrayList<>(oldDefinition.edges());
            edges.add(new EdgeDefinition("implement.claude_self_test_report", "incomplete", "repair"));
            edges.add(new EdgeDefinition("repair", "submit_evidence", "implement.claude_self_test_report"));
            edges.add(new EdgeDefinition("implement.claude_self_test_report", "completed", acceptanceContext.id()));
            edges.add(new EdgeDefinition(acceptanceContext.id(), "completed", "acceptance.codex_verifier_review"));
            var definition = new WorkflowDefinition(oldDefinition.workflowId(), oldDefinition.version(), oldDefinition.entryNodeId(), nodes, edges);
            new AtomicJsonWriter().write(paths.workflowDefinitionJson(), new WorkflowDefinitionDocument(2, definition.workflowId(), definition.version(), definition.entryNodeId(), nodes, edges));
            ledger.completeAndRoute(paths, definition, prior.instance().nodeInstanceId(), NodeStatus.COMPLETED, "incomplete", List.of(), Map.of());
            var preparation = ExternalVerificationEvidence.prepare(ledger, paths);
            ExternalVerificationEvidence.submit(ledger, paths, preparation, "runtime", List.of(report()), declaration(), "verify");
            var sidecars = new ArrayList<String>();
            var allRefs = new ArrayList<List<String>>();
            var handler = new TaskHandler() {
                public String type() { return "acpx.agent_run"; }
                public NodeResult execute(TaskContext context) throws Exception {
                    var nodeId = context.instance().nodeInstanceId();
                    var runId = "run-" + nodeId;
                    var run = OfflineAgentRun.start(context, runId);
                    var refs = EvidenceCollection.requiredReferences(dev.dual.runtime.RuntimeTestLedger.ledger(), context.workspaceRoot(), context.workstreamId(), nodeId);
                    allRefs.add(refs);
                    assertTrue(EvidenceCollection.promptInstruction(dev.dual.runtime.RuntimeTestLedger.ledger(), context.workspaceRoot(), context.workstreamId(), nodeId)
                            .orElseThrow().contains(refs.getLast()));
                    var decision = context.definition().id().equals("implement.claude_self_test_report") ? "SELF_TEST_COMPLETE" : "ACCEPT";
                    var prompt = AgentResultPromptContract.render(context.workstreamId(), runId, context.definition().id(), decision, Set.of(decision));
                    var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(prompt.substring(prompt.indexOf('{')));
                    var evidence = ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("result")).putArray("evidence");
                    if (mode.equals("summary")) evidence.add("all external checks passed");
                    else if (mode.equals("suffix")) refs.forEach(ref -> evidence.add(ref + ".old"));
                    else refs.forEach(evidence::add);
                    // Approved operator evidence: references the submitted manifest and copied material;
                    // no synthetic tool observation or measured process exit is produced.
                    var material = new dev.dual.core.contract.TaskContract.Reference(refs.getLast(),
                            TaskContractArtifactWriter.sha256(context.workspaceRoot().resolve(refs.getLast())));
                    ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.get("result")).set("verification_checks", json.valueToTree(
                            run.governance.qualifiedTaskContract().orElseThrow().verificationBindings().stream().map(binding ->
                                    new AgentResultEnvelope.VerificationCheck(binding.criterionId(), "Review the submitted operator report", refs.getFirst(),
                                            List.<String>of(), List.of(material))).toList()));
                    var output = paths.workstreamDir().resolve("runs/" + runId + "/outputs/result.json");
                    Files.createDirectories(output.getParent()); json.writeValue(output.toFile(), envelope);
                    sidecars.add(paths.workspaceRoot().relativize(AgentOutputContractSidecar.pathFor(output)).toString());
                    return run.exited(List.of(paths.workspaceRoot().relativize(output).toString()));
                }
            };
            var registry = new HandlerRegistry(List.of(handler, new ContextBuildPackageHandler()), List.of(), List.of());
            var runtime = new dev.dual.runtime.WorkflowRuntime(ledger, registry, queued ? dev.dual.runtime.AgentTaskDispatchMode.WORKER : dev.dual.runtime.AgentTaskDispatchMode.INLINE);
            var command = new dev.dual.runtime.WorkflowCommand(paths.workspaceRoot(), paths.workstreamId().value());
            for (int i = 0; i < 3; i++) {
                runtime.resume(command);
                if (queued) new dev.dual.runtime.AgentWorker(ledger, registry).runOne(command);
                if (!mode.equals("valid")) {
                    assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status());
                    assertEquals(EvidenceCollection.FAILURE, ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()).result().get("failure_code"));
                    assertFalse(Files.exists(paths.workspaceRoot().resolve(sidecars.getFirst())), "no sidecar may be published");
                    break;
                }
                assertNotEquals(WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status(),
                        () -> { try { return Files.readString(paths.currentFailureMarkdown()); } catch (Exception e) { return e.toString(); } });
            }
            if (!mode.equals("valid")) continue;
            assertEquals(2, sidecars.size());
            assertEquals(allRefs.getFirst(), allRefs.getLast());
            for (var ref : sidecars) {
                var document = json.readTree(paths.workspaceRoot().resolve(ref).toFile());
                assertEquals(allRefs.getFirst(), json.convertValue(document.path("evidence"), List.class));
                var typed = AgentOutputContractDocument.read(paths.workspaceRoot().resolve(ref), json).orElseThrow();
                VerificationEvidence.verifyPublished(ledger, root, "ws-test", typed);
                var material = root.resolve(allRefs.getFirst().getLast());
                var original = Files.readAllBytes(material);
                Files.writeString(material, "modified operator report");
                assertThrows(java.io.IOException.class, () -> VerificationEvidence.verifyPublished(ledger, root, "ws-test", typed));
                Files.write(material, original);
                VerificationEvidence.verifyPublished(ledger, root, "ws-test", typed);
            }
        }
    }

    @Test void prepareSubmitSelfTestAndAcceptanceConsumeTheSameCopiedBytesAfterColdRead() throws Exception {
        var paths = waiting("whole");
        var before = ledger.loadCurrent(paths);
        var preparation = ExternalVerificationEvidence.prepare(ledger, paths);
        assertEquals(before, ledger.loadCurrent(paths));
        var original = report();
        var declaration = declaration();
        var submission = ExternalVerificationEvidence.submit(ledger, paths, preparation, "request-1", List.of(original), declaration, "Verify this evidence");
        assertEquals(WorkflowStatus.RUNNING, ledger.loadCurrent(paths).status());
        var collection = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        assertEquals("implement.claude_self_test_report", collection.nodeDefinitionId());
        var events = Files.readAllBytes(paths.eventsJsonl());
        assertEquals(submission, ExternalVerificationEvidence.submit(dev.dual.runtime.RuntimeTestLedger.ledger(), paths, preparation,
                "request-1", List.of(original), declaration, "Verify this evidence"));
        assertArrayEquals(events, Files.readAllBytes(paths.eventsJsonl()), "idempotent submission must not route twice");
        Files.delete(original);
        var refs = EvidenceCollection.requiredReferences(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), collection.nodeInstanceId());
        assertEquals(2, refs.size());
        assertEquals(submission.manifestRef(), refs.getFirst());
        assertEquals("device check passed", Files.readString(paths.workspaceRoot().resolve(refs.getLast())));
        assertTrue(EvidenceCollection.promptInstruction(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), collection.nodeInstanceId())
                .orElseThrow().contains(refs.getLast()));
        var calls = new AtomicInteger();
        var result = EvidenceCollection.execute(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), collection, () -> {
            calls.incrementAndGet();
            EvidenceCollection.verifyResult(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), collection.nodeInstanceId(), self(refs));
            return NodeResult.completed("completed", refs, Map.of());
        });
        assertEquals(1, calls.get());
        ledger.completeAndRoute(paths, ledger.loadDefinition(paths), collection.nodeInstanceId(), result.status(), result.outcome(), result.outputRefs(), result.result());
        var acceptance = dev.dual.runtime.RuntimeTestLedger.ledger().loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        assertEquals("verify", acceptance.nodeDefinitionId());
        assertEquals(refs, EvidenceCollection.requiredReferences(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), acceptance.nodeInstanceId()));
        EvidenceCollection.verifyResult(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), acceptance.nodeInstanceId(), acceptance(refs));
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.verifyResult(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(),
                acceptance.nodeInstanceId(), acceptance(List.of("all checks passed"))));
        Files.writeString(paths.workspaceRoot().resolve(refs.getLast()), "tampered");
        assertEquals(EvidenceCollection.FAILURE, EvidenceCollection.execute(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), acceptance,
                () -> { fail("acceptance must not run on tampered materials"); return null; }).outcome());
    }

    @Test void preparationTamperingAndSamePorcelainSourceChangesKeepGateOpen() throws Exception {
        for (var mode : List.of("preparation", "sha", "source", "untracked")) {
            var paths = waiting(mode);
            Files.writeString(paths.workspaceRoot().resolve("source.txt"), "dirty A");
            Files.writeString(paths.workspaceRoot().resolve("untracked.txt"), "A");
            var preparation = ExternalVerificationEvidence.prepare(ledger, paths);
            var current = ledger.loadCurrent(paths);
            var events = Files.readAllBytes(paths.eventsJsonl());
            if (mode.equals("preparation")) Files.writeString(paths.workspaceRoot().resolve(preparation.ref()), "{}");
            if (mode.equals("sha")) preparation = new ExternalVerificationEvidence.Reference(preparation.ref(), "0".repeat(64));
            if (mode.equals("source")) Files.writeString(paths.workspaceRoot().resolve("source.txt"), "dirty B");
            if (mode.equals("untracked")) Files.writeString(paths.workspaceRoot().resolve("untracked.txt"), "B");
            var ref = preparation;
            assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, ref, "r", List.of(report()), declaration(), "note"));
            assertEquals(current, ledger.loadCurrent(paths));
            assertArrayEquals(events, Files.readAllBytes(paths.eventsJsonl()));
            assertEquals("open", ledger.loadOpenGateForNode(paths, current.activeNodeInstanceIds().getFirst()).status());
        }
    }

    @Test void exactRefsAreRequiredAndLateSourceOrMaterialChangesPreventPublication() throws Exception {
        var paths = waiting("publication");
        var preparation = ExternalVerificationEvidence.prepare(ledger, paths);
        ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", List.of(report()), declaration(), "note");
        var id = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        var refs = EvidenceCollection.requiredReferences(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), id);
        for (var evidence : List.of(List.of("summary only"), List.of(String.join(" ", refs)), refs.stream().map(ref -> ref + ".old").toList()))
            assertThrows(java.io.IOException.class, () -> EvidenceCollection.verifyResult(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), id, self(evidence)));
        EvidenceCollection.verifyResult(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), id, self(refs));
        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "late edit");
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.verifyResult(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), id, self(refs)));
    }

    @Test void rawActionMissingNoteOldPreparationSymlinkAndLimitsCannotResolveGate() throws Exception {
        var paths = waiting("reject");
        var preparation = ExternalVerificationEvidence.prepare(ledger, paths);
        var current = ledger.loadCurrent(paths);
        assertThrows(java.io.IOException.class, () -> ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "submit_evidence", "fake ref in note"));
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", List.of(report()), declaration(), " "));
        var link = temp.resolve("link"); Files.createSymbolicLink(link, report());
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", List.of(link), declaration(), "note"));
        var large = temp.resolve("large"); Files.write(large, new byte[8 * 1024 * 1024 + 1]);
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", List.of(large), declaration(), "note"));
        Files.write(large, new byte[8 * 1024 * 1024]);
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", Collections.nCopies(5, large), declaration(), "note"));
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", Collections.nCopies(33, report()), declaration(), "note"));
        var old = new ExternalVerificationEvidence.Declaration("test", "host", Instant.EPOCH, Instant.EPOCH, 0, "verify");
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", List.of(report()), old, "note"));
        assertEquals(current, ledger.loadCurrent(paths));
        ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", "repair instead");
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", List.of(report()), declaration(), "note"));
    }

    @Test void submissionInterruptionReplaysTheFixedMaterialWithoutANewDecision() throws Exception {
        for (var point : List.of("before_manifest", "after_replace")) {
            var paths = waiting("partial-" + point);
            var preparation = ExternalVerificationEvidence.prepare(ledger, paths);
            var current = ledger.loadCurrent(paths);
            var material = report(); var declared = declaration();
            assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.submit(InterruptedTestLedger.at(point),
                    paths, preparation, "r", List.of(material), declared, "note"));
            var cold = dev.dual.runtime.RuntimeTestLedger.ledger();
            if (point.equals("before_manifest")) assertEquals(current, cold.loadCurrent(paths));
            else {
                assertThrows(java.io.IOException.class, () -> cold.loadCurrent(paths));
                try (var session = cold.writeSession(paths, "finish fixed evidence submission", "recover")) { session.commit(); }
            }
            var submitted = ExternalVerificationEvidence.submit(cold, paths, preparation, "r", List.of(material), declared, "note");
            var events = Files.readAllBytes(paths.eventsJsonl());
            assertEquals(submitted, ExternalVerificationEvidence.submit(cold, paths, preparation, "r", List.of(material), declared, "note"));
            assertArrayEquals(events, Files.readAllBytes(paths.eventsJsonl()));
            assertEquals("implement.claude_self_test_report", cold.loadNode(paths, cold.loadCurrent(paths).activeNodeInstanceIds().getFirst()).nodeDefinitionId());
        }
    }

    @Test void storageSymlinkIsRejectedBeforeCreatingDirectoriesOutsideTheWorkstream() throws Exception {
        var paths = waiting("storage-link");
        var outside = temp.resolve("outside"); Files.createDirectory(outside);
        Files.createSymbolicLink(paths.workstreamDir().resolve("artifacts/verification"), outside);
        var current = ledger.loadCurrent(paths);
        assertThrows(java.io.IOException.class, () -> ExternalVerificationEvidence.prepare(ledger, paths));
        assertEquals(current, ledger.loadCurrent(paths));
        try (var entries = Files.list(outside)) { assertEquals(0, entries.count()); }
    }

    @Test void coldJvmRequiresPreparationAndMaterialAndExecutionRechecksAfterTheTool() throws Exception {
        var paths = waiting("cold-jvm");
        var preparation = ExternalVerificationEvidence.prepare(ledger, paths);
        ExternalVerificationEvidence.submit(ledger, paths, preparation, "r", List.of(report()), declaration(), "note");
        var id = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        assertEquals(0, cold(paths, id));
        var original = Files.readAllBytes(paths.workspaceRoot().resolve(preparation.ref()));
        Files.delete(paths.workspaceRoot().resolve(preparation.ref()));
        assertNotEquals(0, cold(paths, id));
        Files.write(paths.workspaceRoot().resolve(preparation.ref()), original);
        var refs = EvidenceCollection.requiredReferences(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), id);
        var node = ledger.loadNode(paths, id);
        assertEquals(EvidenceCollection.FAILURE, EvidenceCollection.execute(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), node,
                () -> {
                    Files.delete(paths.workspaceRoot().resolve(refs.getLast()));
                    return NodeResult.completed("completed", List.of(), Map.of());
                }).outcome());
    }

    private int cold(WorkstreamPaths paths, String id) throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
                System.getProperty("java.class.path"), ColdReader.class.getName(), paths.workspaceRoot().toString(), paths.workstreamId().value(), id)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); fail("cold evidence read timed out"); }
        return process.exitValue();
    }
    public static final class ColdReader {
        public static void main(String[] args) throws Exception {
            if (EvidenceCollection.requiredReferences(dev.dual.runtime.RuntimeTestLedger.ledger(), Path.of(args[0]), args[1], args[2]).isEmpty()) throw new IllegalStateException("binding lost");
        }
    }

    private WorkstreamPaths waiting(String name) throws Exception {
        temp = temp.toRealPath();
        var fixture = new EvidenceCollectionSequenceTest(); fixture.temp = temp;
        return fixture.waiting(name);
    }
    private Path report() throws Exception {
        var path = Files.createTempFile(temp, "device-report-", ".txt");
        Files.writeString(path, "device check passed"); return path;
    }
    private ExternalVerificationEvidence.Declaration declaration() {
        var now = Instant.now(); return new ExternalVerificationEvidence.Declaration("device verify", "operator host", now, now, 0, "device evidence");
    }
    private AgentResultEnvelope.SelfTestResult self(List<String> refs) {
        return new AgentResultEnvelope.SelfTestResult("self_test_report", "SELF_TEST_COMPLETE", "verified", "IMPLEMENTED",
                List.of("device verification"), List.of(), refs, List.of(), List.of(), List.of(), "verified material", List.of());
    }
    private AgentResultEnvelope.AcceptanceReviewResult acceptance(List<String> refs) {
        return new AgentResultEnvelope.AcceptanceReviewResult("acceptance_review", "ACCEPT", "verified", "ACCEPTED", false,
                List.of("device evidence"), refs, List.of(), List.of(), List.of(), List.of(), List.of(), "verified", null, List.of());
    }
}
