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
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        var current = ledger.loadCurrent(paths);
        var definition = ledger.loadDefinition(paths);
        assertThrows(java.io.IOException.class, () -> ledger.resolveOpenGate(paths, definition, "collect_evidence", "yes"));
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.resolveGate(ledger, paths, definition, "collect_evidence", " "));
        Files.delete(paths.workspaceRoot().resolve(TaskContractProjectionVerifier.resolve(ledger, paths.workspaceRoot(),
                paths.workstreamId().value()).qualified().orElseThrow().contractRef()));
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.resolveGate(ledger, paths, definition, "collect_evidence", "yes"));
        assertEquals(current, ledger.loadCurrent(paths));
        assertEquals("open", ledger.loadOpenGateForNode(paths, current.activeNodeInstanceIds().getFirst()).status());
    }

    @Test void realInlineAndWorkerConsumersRejectBeforeAndAfterExecutionIncludingThrownFailure() throws Exception {
        for (var queued : List.of(false, true)) for (var mode : List.of("unchanged", "before", "after", "exception")) {
            var paths = waiting(queued + "-" + mode);
            var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
            approve(paths, ledger);
            var collectionNode = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            var calls = new AtomicInteger();
            var handlers = new HandlerRegistry(List.of(new TaskHandler() {
                public String type() { return "acpx.agent_run"; }
                public NodeResult execute(TaskContext context) throws Exception {
                    var run = OfflineAgentRun.start(context, "collection-" + context.instance().nodeInstanceId());
                    calls.incrementAndGet();
                    if (mode.equals("after") || mode.equals("exception"))
                        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "changed by tool");
                    if (mode.equals("exception")) throw new java.io.IOException("tool interrupted after writing");
                    var rendered = AgentResultPromptContract.render(context.workstreamId(), run.runId,
                            context.definition().id(), "SELF_TEST_COMPLETE", Set.of("SELF_TEST_COMPLETE"));
                    var output = run.dir.resolve("outputs/result.json");
                    var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(rendered.substring(rendered.indexOf('{')));
                    run.observeRead((com.fasterxml.jackson.databind.node.ObjectNode) envelope.get("result"), "source.txt");
                    mapper.writeValue(output.toFile(), envelope);
                    return run.exited(List.of(paths.workspaceRoot().relativize(output).toString()));
                }
            }), List.of(), List.of());
            if (mode.equals("before")) Files.writeString(paths.workspaceRoot().resolve("source.txt"), "changed after approval");
            var runtime = new WorkflowRuntime(ledger, handlers,
                    queued ? AgentTaskDispatchMode.WORKER : AgentTaskDispatchMode.INLINE);
            var command = new WorkflowCommand(paths.workspaceRoot(), paths.workstreamId().value());
            if (mode.equals("exception")) {
                if (queued) {
                    runtime.resume(command);
                    assertThrows(Exception.class, () -> new AgentWorker(ledger, handlers).runOne(command));
                } else assertThrows(Exception.class, () -> runtime.resume(command));
                // An unknown external result cannot be converted to an ordinary input rejection.
                var execution = ledger.latestNodeExecution(paths, ledger.loadNode(paths, collectionNode)).orElseThrow();
                assertTrue(execution.result().containsKey("prepared_run"));
                continue;
            }
            runtime.resume(command);
            if (queued) new AgentWorker(ledger, handlers).runOne(command);
            var current = dev.dual.runtime.RuntimeTestLedger.ledger().loadCurrent(paths);
            assertEquals(mode.equals("before") ? 0 : 1, calls.get());
            if (mode.equals("unchanged")) {
                assertEquals(WorkflowStatus.RUNNING, current.status());
                var next = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
                assertEquals("verify", next.nodeDefinitionId());
                assertTrue(next.inputTransition().containsKey("evidence_collection_gate"));
                // A change between stages is also rejected before the next consumer.
                Files.writeString(paths.workspaceRoot().resolve("source.txt"), "late change");
                var result = EvidenceCollection.execute(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), next,
                        () -> { fail("verification must not consume changed source"); return null; });
                assertEquals(EvidenceCollection.FAILURE, result.outcome());
            } else {
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
                var stopped = ledger.loadNode(paths, collectionNode);
                assertEquals(NodeStatus.FAILED, stopped.status());
                assertEquals(EvidenceCollection.FAILURE, stopped.result().get("failure_code"));
                assertTrue(stopped.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")),
                        "failed evidence must not publish a usable sidecar; raw diagnostics may remain");
                assertEquals(List.of(collectionNode), current.activeNodeInstanceIds());
                assertNotEquals("original", Files.readString(paths.workspaceRoot().resolve("source.txt")), "never silently revert source");
            }
        }
    }

    @Test void coldRetryCannotRecaptureBaselineOrLoseBindingAndApprovalTamperingIsRejected() throws Exception {
        var paths = waiting("cold");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        var gateId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        approve(paths, ledger);
        var nodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        var node = ledger.loadNode(paths, nodeId);
        var approval = Files.readAllBytes(paths.nodeJson(gateId));
        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "modified");
        for (int retry = 0; retry < 2; retry++) {
            var cold = dev.dual.runtime.RuntimeTestLedger.ledger().loadNode(paths, nodeId);
            assertEquals(EvidenceCollection.FAILURE, EvidenceCollection.execute(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(),
                    paths.workstreamId().value(), cold, () -> { fail("must not retry changed source"); return null; }).outcome());
            assertArrayEquals(approval, Files.readAllBytes(paths.nodeJson(gateId)));
        }
        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "original");
        var erased = new NodeInstanceDocument(node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(),
                node.handlerType(), node.status(), node.stageId(), node.iterationId(), node.inputRefs(), Map.of(), node.outputRefs(),
                node.result(), node.runRef(), node.latestExecutionId(), node.startedAt(), node.finishedAt());
        mapper.writeValue(paths.nodeJson(nodeId).toFile(), erased);
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.open(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), erased));
        mapper.writeValue(paths.nodeJson(nodeId).toFile(), node);
        Files.writeString(paths.nodeJson(gateId), "{} ");
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.open(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), node));
        Files.delete(paths.nodeJson(gateId));
        assertThrows(java.io.IOException.class, () -> EvidenceCollection.open(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), node));
    }

    @Test void directPublicationRejectsChangedSourceAndOrdinaryRunsRemainUnaffected() throws Exception {
        var paths = waiting("publish");
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        approve(paths, ledger);
        var id = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        var node = ledger.loadNode(paths, id);
        Files.writeString(paths.workspaceRoot().resolve("source.txt"), "changed");
        var result = new ArtifactContractValidator(dev.dual.runtime.RuntimeTestLedger.ledger()).validate(paths.workspaceRoot(), paths.workstreamId().value(), id,
                ledger.loadDefinition(paths).node(node.nodeDefinitionId()), node.inputRefs(),
                NodeResult.completed("completed", List.of(), Map.of()));
        assertEquals(EvidenceCollection.FAILURE, result.outcome());
        var ordinary = new NodeInstanceDocument(1, "ordinary", "ordinary", NodeKind.TASK, "noop.task", NodeStatus.READY,
                "implement", "iter-001", List.of(), Map.of(), List.of(), Map.of(), null, "", null, null);
        mapper.writeValue(paths.nodeJson(ordinary.nodeInstanceId()).toFile(), ordinary);
        assertEquals("completed", EvidenceCollection.execute(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), ordinary,
                () -> NodeResult.completed("completed", List.of(), Map.of())).outcome());
    }

    @Test void approvalInterruptionKeepsOldStateOrCompletesOnlyTheFixedDecision() throws Exception {
        for (var point : List.of("before_manifest", "after_replace")) {
            var paths = waiting("partial-" + point);
            var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
            var current = ledger.loadCurrent(paths);
            var gateId = current.activeNodeInstanceIds().getFirst();
            assertThrows(java.io.IOException.class, () -> approve(paths, InterruptedTestLedger.at(point)));
            var cold = dev.dual.runtime.RuntimeTestLedger.ledger();
            if (point.equals("before_manifest")) {
                assertEquals(current, cold.loadCurrent(paths));
                assertEquals("open", cold.loadOpenGateForNode(paths, gateId).status());
                approve(paths, cold);
            } else {
                assertThrows(java.io.IOException.class, () -> cold.loadCurrent(paths), "pending writes are not a readable approval");
                // Recovery must reuse the approved source identity, even if source changes meanwhile.
                Files.writeString(paths.workspaceRoot().resolve("source.txt"), "changed during interrupted commit");
                try (var session = cold.writeSession(paths, "finish fixed evidence approval", "recover")) { session.commit(); }
            }
            var next = cold.loadNode(paths, cold.loadCurrent(paths).activeNodeInstanceIds().getFirst());
            assertEquals("implement.claude_self_test_report", next.nodeDefinitionId());
            assertTrue(cold.loadNode(paths, gateId).result().containsKey("evidence_collection_baseline"));
            var events = Files.readAllBytes(paths.eventsJsonl());
            assertThrows(Exception.class, () -> approve(paths, cold));
            assertArrayEquals(events, Files.readAllBytes(paths.eventsJsonl()));
            if (!point.equals("before_manifest"))
                assertEquals(EvidenceCollection.FAILURE, EvidenceCollection.execute(cold, paths.workspaceRoot(),
                        paths.workstreamId().value(), next, () -> { fail("must not recapture source during recovery"); return null; }).outcome());
        }
    }

    @Test void evidenceOnlyLoopKeepsOriginalApprovalButImplementationRepairDoesNot() throws Exception {
        for (var outcome : List.of("more_evidence", "repair_product")) {
            var paths = waiting(outcome);
            var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
            approve(paths, ledger);
            var definition = ledger.loadDefinition(paths);
            var collecting = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
            var approvalId = collecting.inputTransition().get("evidence_collection_gate");
            ledger.completeAndRoute(paths, definition, collecting.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());
            var verifying = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            ledger.completeAndRoute(paths, definition, verifying, NodeStatus.COMPLETED, outcome, List.of(), Map.of());
            var next = dev.dual.runtime.RuntimeTestLedger.ledger().loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
            if (outcome.equals("more_evidence")) {
                assertEquals(approvalId, next.inputTransition().get("evidence_collection_gate"));
                assertNotNull(EvidenceCollection.open(dev.dual.runtime.RuntimeTestLedger.ledger(), paths.workspaceRoot(), paths.workstreamId().value(), next));
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
        ReviewGateActions.resolve(ledger, paths, "collect_evidence", "Collect missing evidence only");
    }

    WorkstreamPaths waiting(String name) throws Exception {
        var root = temp.resolve(name);
        Files.createDirectories(root);
        GitTrackedPathInventory.command(root, "init");
        Files.writeString(root.resolve("source.txt"), "original");
        GitTrackedPathInventory.command(root, "add", ".");
        GitTrackedPathInventory.command(root, "-c", "user.name=Test", "-c", "user.email=test@example.invalid", "commit", "-m", "baseline");
        var fixture = new AuthoredStageSequenceTest.Fixture(root, "source.txt", AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, "ws-test");
        var prior = fixture.before("implement.claude_self_test_report");
        var paths = fixture.paths;
        var ledger = fixture.ledger;
        var old = ledger.loadDefinition(paths);
        var nodes = new ArrayList<>(old.nodes());
        nodes.addAll(List.of(
                new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of("gate_purpose", "implementation_repair",
                        "allowed_actions", List.of("revise", "collect_evidence", "submit_evidence"))),
                new NodeDefinition("verify", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("implementation", NodeKind.TASK, "noop.task", Map.of())));
        var edges = new ArrayList<>(old.edges().stream().filter(e -> !e.from().equals("implement.claude_self_test_report")).toList());
        edges.addAll(List.of(new EdgeDefinition("implement.claude_self_test_report", "incomplete", "repair"),
                new EdgeDefinition("repair", "collect_evidence", "implement.claude_self_test_report"), new EdgeDefinition("repair", "submit_evidence", "implement.claude_self_test_report"),
                new EdgeDefinition("repair", "revise", "verify"), new EdgeDefinition("implement.claude_self_test_report", "completed", "verify"),
                new EdgeDefinition("verify", "more_evidence", "implement.claude_self_test_report", null, TransitionMetadata.loop("collect more evidence")),
                new EdgeDefinition("verify", "repair_product", "implementation", null, TransitionMetadata.loop("specific product repair"))));
        var definition = new WorkflowDefinition(old.workflowId(), old.version(), old.entryNodeId(), nodes, edges);
        new AtomicJsonWriter().write(paths.workflowDefinitionJson(), new WorkflowDefinitionDocument(2, definition.workflowId(), definition.version(), definition.entryNodeId(), nodes, edges));
        var evidence = paths.workstreamDir().resolve("artifacts/prior-evidence.txt");
        Files.writeString(evidence, "Original verification was incomplete; collect missing evidence against the approved source.");
        ledger.completeAndRoute(paths, definition, prior.instance().nodeInstanceId(), NodeStatus.COMPLETED, "incomplete",
                List.of(paths.workspaceRoot().relativize(evidence).toString()), Map.of());
        return paths;
    }
}
