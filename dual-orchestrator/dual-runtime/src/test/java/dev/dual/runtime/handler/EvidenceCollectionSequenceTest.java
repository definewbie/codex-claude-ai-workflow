package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceCollectionSequenceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void approvalRequiresNoteQualificationAndCannotUseBareLedgerDecision() throws Exception {
        var paths = waiting("reject");
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        var definition = ledger.loadDefinition(paths);
        assertThrows(java.io.IOException.class, () -> ledger.resolveOpenGate(paths, definition, "collect_evidence", "yes"));
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.resolveGate(ledger, paths, definition, "collect_evidence", " "));
        Files.delete(paths.workstreamDir().resolve("artifacts/task-contract.json"));
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.resolveGate(ledger, paths, definition, "collect_evidence", "yes"));
        assertEquals(current, ledger.loadCurrent(paths));
        assertEquals("open", ledger.loadOpenGateForNode(paths, current.activeNodeInstanceIds().getFirst()).status());
    }

    @Test void realInlineAndWorkerConsumersRejectBeforeAndAfterExecutionIncludingThrownFailure() throws Exception {
        for (var queued : List.of(false, true)) for (var mode : List.of("unchanged", "before", "after", "exception")) {
            var paths = waiting(queued + "-" + mode);
            var ledger = new WorkflowFileLedger();
            approve(paths, ledger);
            var collectionNode = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            var calls = new AtomicInteger();
            var handlers = new HandlerRegistry(List.of(new TaskHandler() {
                public String type() { return "acpx.agent_run"; }
                public NodeResult execute(TaskContext context) throws Exception {
                    calls.incrementAndGet();
                    if (mode.equals("after") || mode.equals("exception"))
                        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "changed by tool");
                    if (mode.equals("exception")) throw new java.io.IOException("tool interrupted after writing");
                    return NodeResult.completed("completed", List.of(), Map.of());
                }
            }), List.of(), List.of());
            if (mode.equals("before")) Files.writeString(paths.workspaceRoot().resolve("source.txt"), "changed after approval");
            var runtime = new WorkflowRuntime(ledger, handlers,
                    queued ? AgentTaskDispatchMode.WORKER : AgentTaskDispatchMode.INLINE);
            var command = new WorkflowCommand(paths.workspaceRoot(), paths.workstreamId().value());
            runtime.resume(command);
            if (queued) new AgentWorker(ledger, handlers).runOne(command);
            var current = new WorkflowFileLedger().loadCurrent(paths);
            assertEquals(mode.equals("before") ? 0 : 1, calls.get());
            if (mode.equals("unchanged")) {
                assertEquals(WorkflowStatus.RUNNING, current.status());
                var next = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
                assertEquals("verify", next.nodeDefinitionId());
                assertTrue(next.inputTransition().containsKey("evidence_collection_gate"));
                // A change between stages is also rejected before the next consumer.
                Files.writeString(paths.workspaceRoot().resolve("source.txt"), "late change");
                var result = EvidenceCollection.execute(paths.workspaceRoot(), paths.workstreamId().value(), next,
                        () -> { fail("verification must not consume changed source"); return null; });
                assertEquals(EvidenceCollection.FAILURE, result.outcome());
            } else {
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
                var stopped = ledger.loadNode(paths, collectionNode);
                assertEquals(NodeStatus.FAILED, stopped.status());
                assertEquals(EvidenceCollection.FAILURE, stopped.result().get("failure_code"));
                assertTrue(stopped.outputRefs().isEmpty());
                assertEquals(List.of(collectionNode), current.activeNodeInstanceIds());
                assertNotEquals("original", Files.readString(paths.workspaceRoot().resolve("source.txt")), "never silently revert source");
            }
        }
    }

    @Test void coldRetryCannotRecaptureBaselineOrLoseBindingAndApprovalTamperingIsRejected() throws Exception {
        var paths = waiting("cold");
        var ledger = new WorkflowFileLedger();
        var gateId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        approve(paths, ledger);
        var nodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        var node = ledger.loadNode(paths, nodeId);
        var approval = Files.readAllBytes(paths.nodeJson(gateId));
        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "modified");
        for (int retry = 0; retry < 2; retry++) {
            var cold = new WorkflowFileLedger().loadNode(paths, nodeId);
            assertEquals(EvidenceCollection.FAILURE, EvidenceCollection.execute(paths.workspaceRoot(),
                    paths.workstreamId().value(), cold, () -> { fail("must not retry changed source"); return null; }).outcome());
            assertArrayEquals(approval, Files.readAllBytes(paths.nodeJson(gateId)));
        }
        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "original");
        var erased = new NodeInstanceDocument(node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(),
                node.handlerType(), node.status(), node.stageId(), node.iterationId(), node.inputRefs(), Map.of(), node.outputRefs(),
                node.result(), node.runRef(), node.latestExecutionId(), node.startedAt(), node.finishedAt());
        mapper.writeValue(paths.nodeJson(nodeId).toFile(), erased);
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.open(paths.workspaceRoot(), paths.workstreamId().value(), erased));
        mapper.writeValue(paths.nodeJson(nodeId).toFile(), node);
        Files.writeString(paths.nodeJson(gateId), "{} ");
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.open(paths.workspaceRoot(), paths.workstreamId().value(), node));
        Files.delete(paths.nodeJson(gateId));
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.open(paths.workspaceRoot(), paths.workstreamId().value(), node));
    }

    @Test void directPublicationRejectsChangedSourceAndOrdinaryRunsRemainUnaffected() throws Exception {
        var paths = waiting("publish");
        var ledger = new WorkflowFileLedger();
        approve(paths, ledger);
        var id = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        var node = ledger.loadNode(paths, id);
        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "changed");
        var result = new ArtifactContractValidator().validate(paths.workspaceRoot(), paths.workstreamId().value(), id,
                ledger.loadDefinition(paths).node(node.nodeDefinitionId()), node.inputRefs(),
                NodeResult.completed("completed", List.of(), Map.of()));
        assertEquals(EvidenceCollection.FAILURE, result.outcome());
        var ordinary = new NodeInstanceDocument(1, "ordinary", "ordinary", NodeKind.TASK, "noop.task", NodeStatus.READY,
                "implement", "iter-001", List.of(), Map.of(), List.of(), Map.of(), null, "", null, null);
        assertEquals("completed", EvidenceCollection.execute(paths.workspaceRoot(), paths.workstreamId().value(), ordinary,
                () -> NodeResult.completed("completed", List.of(), Map.of())).outcome());
    }

    @Test void partialApprovalWritesNeverAuthorizeASecondCaptureOrResume() throws Exception {
        for (var afterNodeWrite : List.of(false, true)) {
            var paths = waiting("partial-" + afterNodeWrite);
            var ledger = new WorkflowFileLedger();
            var current = ledger.loadCurrent(paths);
            var gateId = current.activeNodeInstanceIds().getFirst();
            if (afterNodeWrite) {
                ledger = new WorkflowFileLedger(new dev.dual.core.flow.FlowOrchestrationKernel() {
                    public dev.dual.core.flow.FlowValidationResult validate(WorkflowDefinition definition) {
                        return new dev.dual.core.flow.BuiltInFlowOrchestrationKernel().validate(definition);
                    }
                    public dev.dual.core.flow.FlowAdvanceResult advance(WorkflowDefinition definition,
                            dev.dual.core.flow.FlowExecutionSnapshot snapshot, dev.dual.core.flow.FlowSignal signal) {
                        throw new IllegalStateException("simulated stop after approved node write");
                    }
                });
            } else {
                Files.createDirectory(paths.nodeJson(gateId).resolveSibling(gateId + ".json.tmp"));
            }
            var failing = ledger;
            assertThrows(Exception.class, () -> approve(paths, failing));
            var cold = new WorkflowFileLedger();
            assertEquals(current, cold.loadCurrent(paths));
            var gate = mapper.readValue(paths.gateJson("gate-" + gateId).toFile(), GateDocument.class);
            assertEquals("resolved", gate.status());
            assertEquals(afterNodeWrite, cold.loadNode(paths, gateId).result().containsKey("evidence_collection_baseline"));
            assertThrows(Exception.class, () -> approve(paths, cold), "cannot silently recapture a partially resolved approval");
        }
    }

    @Test void evidenceOnlyLoopKeepsOriginalApprovalButImplementationRepairDoesNot() throws Exception {
        for (var outcome : List.of("more_evidence", "repair_product")) {
            var paths = waiting(outcome);
            var ledger = new WorkflowFileLedger();
            approve(paths, ledger);
            var definition = ledger.loadDefinition(paths);
            var collecting = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
            var approvalId = collecting.inputTransition().get("evidence_collection_gate");
            ledger.completeAndRoute(paths, definition, collecting.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());
            var verifying = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            ledger.completeAndRoute(paths, definition, verifying, NodeStatus.COMPLETED, outcome, List.of(), Map.of());
            var next = new WorkflowFileLedger().loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
            if (outcome.equals("more_evidence")) {
                assertEquals(approvalId, next.inputTransition().get("evidence_collection_gate"));
                assertNotNull(EvidenceCollection.open(paths.workspaceRoot(), paths.workstreamId().value(), next));
            } else assertFalse(next.inputTransition().containsKey("evidence_collection_gate"));
        }
    }

    @Test void allWorkflowSiblingsDeclareOnlyASelfTestCollectionRoute() throws Exception {
        var loader = new dev.dual.runtime.lifecycle.WorkflowDefinitionLoader();
        for (var variant : List.of("full", "contract", "terminal-delivery", "small-plan-human-review-experiment")) {
            var definition = variant.equals("small-plan-human-review-experiment")
                    ? loader.load(Path.of(getClass().getResource("/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json").toURI()))
                    : loader.loadOrDefault(null, variant);
            var gate = definition.node("gate.human_implementation_repair");
          for (var action : List.of("collect_evidence", "submit_evidence")) {
            var collection = definition.edgesFrom(gate.id()).stream().filter(edge -> edge.on().equals(action)).toList();
            assertEquals(1, collection.size());
            assertEquals("self_test_report", definition.node(collection.getFirst().to()).config().get("expected_artifact"));
            assertFalse(HumanGateActionSemantics.allowedActions(gate, "CHANGE_SCOPE_VIOLATION").contains(action));
            for (var changed : List.of(new EdgeDefinition(gate.id(), action, "context.implement_claude"),
                    new EdgeDefinition(gate.id(), action, collection.getFirst().to(), null, TransitionMetadata.loop("bad")))) {
                var edges = new ArrayList<>(definition.edges());
                edges.remove(collection.getFirst());
                edges.add(changed);
                assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(definition.workflowId(), definition.version(),
                        definition.entryNodeId(), definition.nodes(), edges));
            }
          }
        }
    }

    private void approve(WorkstreamPaths paths, WorkflowFileLedger ledger) throws Exception {
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(paths.workstreamId().value(), "test approval"))) {
            EvidenceCollection.resolveGate(ledger, paths, ledger.loadDefinition(paths), "collect_evidence", "Collect missing evidence only");
        }
    }

    WorkstreamPaths waiting(String name) throws Exception {
        var root = temp.resolve(name);
        Files.createDirectories(root);
        GitTrackedPathInventory.command(root, "init");
        Files.writeString(root.resolve("source.txt"), "original");
        GitTrackedPathInventory.command(root, "add", ".");
        GitTrackedPathInventory.command(root, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-m", "baseline");
        var paths = new WorkstreamPaths(root, new DualId("ws-test"));
        var definition = new WorkflowDefinition("collection", 1, "start", List.of(
                new NodeDefinition("start", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of("gate_purpose", "implementation_repair",
                        "allowed_actions", List.of("revise", "collect_evidence", "submit_evidence"))),
                new NodeDefinition("collect", NodeKind.TASK, "acpx.agent_run", Map.of("expected_artifact", "self_test_report")),
                new NodeDefinition("verify", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("implementation", NodeKind.TASK, "noop.task", Map.of())), List.of(
                new EdgeDefinition("start", "completed", "repair"), new EdgeDefinition("repair", "collect_evidence", "collect"),
                new EdgeDefinition("repair", "submit_evidence", "collect"),
                new EdgeDefinition("repair", "revise", "verify"), new EdgeDefinition("collect", "completed", "verify"),
                new EdgeDefinition("verify", "more_evidence", "collect", null, TransitionMetadata.loop("collect more evidence")),
                new EdgeDefinition("verify", "repair_product", "implementation", null, TransitionMetadata.loop("specific product repair"))));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-test", definition);
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of("prior-evidence.json"), Map.of());
        Files.createDirectories(paths.workstreamDir().resolve("artifacts"));
        Files.writeString(paths.workstreamDir().resolve("artifacts/task-contract.json"), """
                {"id":"tc","version":1,"created_at":"2026-08-26T00:00:00Z","objective":"Verify product",
                 "status":"READY_FOR_REVIEW","scope":{"included":[{"path":"source.txt","operation":"MODIFY"}],"excluded":[]},
                 "acceptance_criteria":["works","evidence is reproducible"],"test_requirements":["verify"],"constraints":["Keep product scope"],"non_goals":[],"risk_areas":[],
                 "evidence_refs":["source.txt"],"ambiguity_notes":[],"human_questions":[],"compilation_pass":1,
                 "source":{"raw_request_ref":"requirement.md","created_by":"contract_compiler"}}
                """);
        TaskContractProjectionFixture.seedCompileBinding(root, "ws-test");
        var current = ledger.loadCurrent(paths);
        var last = mapper.readTree(Files.readAllLines(paths.eventsJsonl()).getLast()).path("event_id").asText();
        mapper.writeValue(paths.workflowCurrentJson().toFile(), new WorkflowCurrentDocument(current.schemaVersion(),
                current.workstreamId(), current.workflowInstanceId(), current.workflowId(), current.definitionVersion(), current.status(),
                current.activeNodeInstanceIds(), current.openGateIds(), last, current.updatedAt()));
        return paths;
    }
}
