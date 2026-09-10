package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.flow.liteflow.LiteFlowPrototypeKernel;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SelfTestContinuationSequenceTest {
    @TempDir Path temp;

    @Test
    void missingOrUnknownDispositionIsRejectedBeforeAnyLedgerWrite() throws Exception {
        var loaded = new WorkflowDefinitionLoader().loadOrDefault(null, "terminal-delivery");
        var definition = new WorkflowDefinition(loaded.workflowId(), loaded.version(),
                "implement.self_test_decision", loaded.nodes(), loaded.edges());
        var paths = new WorkstreamPaths(temp.resolve("invalid"), new DualId("ws-test"));
        try (var kernel = new LiteFlowPrototypeKernel()) {
            var ledger = new WorkflowFileLedger(kernel);
            ledger.initialize(paths, "workflow-test", definition);
            var current = ledger.loadCurrent(paths);
            var id = current.activeNodeInstanceIds().getFirst();
            var before = Files.readString(paths.nodeJson(id));
            var events = Files.readString(paths.eventsJsonl());
            for (var facts : List.of(Map.<String, Object>of(), Map.<String, Object>of("continuation_kind", "UNKNOWN"))) {
                assertThrows(IllegalArgumentException.class, () -> ledger.completeAndRoute(paths, definition,
                        id, NodeStatus.COMPLETED, "SELF_TEST_INCOMPLETE", List.of(), facts));
                assertEquals(before, Files.readString(paths.nodeJson(id)));
                assertEquals(events, Files.readString(paths.eventsJsonl()));
                assertEquals(current, ledger.loadCurrent(paths));
            }
        }
    }

    @Test
    void deletedBranchOrConditionCannotLoadAsAValidDefinition() throws Exception {
        var loaded = new WorkflowDefinitionLoader().loadOrDefault(null, "terminal-delivery");
        var edges = new java.util.ArrayList<>(loaded.edges());
        var index = java.util.stream.IntStream.range(0, edges.size()).filter(i ->
                edges.get(i).from().equals("implement.self_test_decision")
                        && edges.get(i).on().equals("SELF_TEST_INCOMPLETE")).findFirst().orElseThrow();
        var edge = edges.remove(index);
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(loaded.workflowId(),
                loaded.version(), loaded.entryNodeId(), loaded.nodes(), edges));
        edges.add(index, new dev.dual.core.workflow.EdgeDefinition(edge.from(), edge.on(), edge.to(), null, edge.metadata()));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(loaded.workflowId(),
                loaded.version(), loaded.entryNodeId(), loaded.nodes(), edges));
    }

    @Test
    void realLiteFlowRoutesValidatedSelfTestAndColdLoadsTheCorrectSuccessorAcrossSiblings() throws Exception {
        for (var variant : List.of("contract", "full", "terminal-delivery")) {
            for (var mode : List.of("complete", "evidence", "mixed", "blocked")) {
                var workspace = temp.resolve(variant + "-" + mode);
                var paths = new WorkstreamPaths(workspace, new DualId("ws-test"));
                var loaded = new WorkflowDefinitionLoader().loadOrDefault(null, variant);
                // Enter at the affected stage; upstream qualification is not bypassed by a provider run.
                var definition = new WorkflowDefinition(loaded.workflowId(), loaded.version(),
                        "implement.claude_self_test_report", loaded.nodes(), loaded.edges());
                try (var kernel = new LiteFlowPrototypeKernel()) {
                    var ledger = new WorkflowFileLedger(kernel);
                    ledger.initialize(paths, "workflow-test", definition);
                    var sourceId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
                    var decision = mode.equals("complete") ? "SELF_TEST_COMPLETE"
                            : mode.equals("blocked") ? "BLOCKED" : "SELF_TEST_INCOMPLETE";
                    var mustFix = mode.equals("mixed") ? List.of("Correct rollback behavior") : List.<String>of();
                    var missing = mode.equals("complete") ? List.<String>of() : List.of("Device verification record");
                    var payload = new AgentResultEnvelope.SelfTestResult("self_test_report", decision,
                            "Verification result", mode.equals("complete") ? "READY"
                                    : mode.equals("blocked") ? "BLOCKED" : "INSUFFICIENT_EVIDENCE",
                            List.of("local test"), List.of(), List.of("local result"), missing, mustFix,
                            List.of(), "Recorded actual evidence");
                    var envelope = new AgentResultEnvelope(1, "ws-test", "run-test",
                            "implement.claude_self_test_report", payload);
                    var contract = ContractRegistry.forNode(envelope.nodeId());
                    var validation = new AgentResultEnvelopeValidator().validate(envelope, "ws-test", "run-test", contract);
                    assertTrue(validation.valid(), validation.errors().toString());
                    var ref = ".agent/workstreams/ws-test/runs/run-test/outputs/self-test.contract.json";
                    var sidecar = new AgentResultDocumentProjector().outputContract(envelope, contract,
                            "acpx.agent_run", sourceId,
                            ".agent/workstreams/ws-test/runs/run-test/outputs/self-test.json");
                    Files.createDirectories(workspace.resolve(ref).getParent());
                    new ObjectMapper().writeValue(workspace.resolve(ref).toFile(), sidecar);
                    AgentOutputLineageFixture.publish(workspace, "ws-test", workspace.resolve(ref), "iter-001");
                    var published = ledger.loadNode(paths, sourceId);
                    ledger.completeAndRoute(paths, definition, sourceId, NodeStatus.COMPLETED,
                            "completed", published.outputRefs(), published.result());
                    var decisionNode = ledger.loadCoreNode(paths,
                            ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
                    assertEquals("implement.self_test_decision", decisionNode.nodeDefinitionId());
                    var result = new ReviewDecisionHandler().evaluate(new DecisionHandler.DecisionContext(
                            workspace, "ws-test", definition.node(decisionNode.nodeDefinitionId()), decisionNode));
                    assertEquals(NodeStatus.COMPLETED, result.status(), result.toString());
                    ledger.completeAndRoute(paths, definition, decisionNode.nodeInstanceId(), result.status(),
                            result.outcome(), result.outputRefs(), result.result());
                }
                try (var coldKernel = new LiteFlowPrototypeKernel()) {
                    var cold = new WorkflowFileLedger(coldKernel);
                    var current = cold.loadCurrent(paths);
                    assertEquals(1, current.activeNodeInstanceIds().size());
                    var next = cold.loadNode(paths, current.activeNodeInstanceIds().getFirst());
                    var expected = mode.equals("complete") ? "implement.artifact_validation"
                            : mode.equals("mixed") ? "context.implement_claude" : "gate.human_implementation_repair";
                    assertEquals(expected, next.nodeDefinitionId(), variant + ":" + mode);
                    assertEquals(List.of(".agent/workstreams/ws-test/runs/run-test/outputs/self-test.json",
                                    ".agent/workstreams/ws-test/runs/run-test/outputs/self-test.contract.json"),
                            next.inputRefs());
                    if (mode.equals("evidence") || mode.equals("blocked")) {
                        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, current.status());
                        assertThrows(Exception.class, () -> cold.resolveOpenGate(paths, cold.loadDefinition(paths),
                                "revise", ""));
                        assertEquals(current, cold.loadCurrent(paths), "invalid human action must not alter state");
                    }
                }
            }
        }
    }
}
