package dev.dual.core.workflow;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionTest {
    @Test
    void acceptsDefinitionWithEntryNodeAndEdges() {
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(
                        new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of()),
                        new NodeDefinition("gate.human_requirement_review", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("intake.claude_brainstorm", "completed", "gate.human_requirement_review"))
        );

        assertEquals(NodeKind.TASK, definition.node("intake.claude_brainstorm").kind());
        assertEquals(1, definition.edgesFrom("intake.claude_brainstorm").size());
    }

    @Test
    void rejectsGateEdgeThatNoVisibleActionCanTrigger() {
        var exception = assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "hidden-gate-action",
                1,
                "gate.review",
                List.of(
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of(
                                "allowed_actions", List.of("approved"))),
                        new NodeDefinition("next", NodeKind.TASK, "noop.task", Map.of())),
                List.of(new EdgeDefinition("gate.review", "approve", "next"))
        ));

        assertTrue(exception.getMessage().contains("gate.review"));
        assertTrue(exception.getMessage().contains("approve"));
        assertTrue(exception.getMessage().contains("approved"));
    }

    @Test
    void acceptsDirectionalApprovedCompatibilityAndAllowedOnlyTerminalActions() {
        var routed = new WorkflowDefinition(
                "canonical-gate-action",
                1,
                "gate.review",
                List.of(
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of(
                                "allowed_actions", List.of("approve", "reject"))),
                        new NodeDefinition("next", NodeKind.TASK, "noop.task", Map.of())),
                List.of(new EdgeDefinition("gate.review", "approved", "next"))
        );
        var terminal = new WorkflowDefinition(
                "terminal-gate-action",
                1,
                "gate.review",
                List.of(new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of(
                        "allowed_actions", List.of("reject", "complete")))),
                List.of()
        );

        assertEquals("approved", routed.edgesFrom("gate.review").getFirst().on());
        assertTrue(terminal.edgesFrom("gate.review").isEmpty());
    }

    @Test
    void rejectsMissingEntryNode() {
        var exception = assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "missing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        ));

        assertTrue(exception.getMessage().contains("entry node"));
    }

    @Test
    void rejectsInvalidWorkflowIdAndVersion() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "",
                1,
                "existing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "agent-lifecycle",
                0,
                "existing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        ));
    }

    @Test
    void rejectsUnknownNodeLookup() {
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "existing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var exception = assertThrows(IllegalArgumentException.class, () -> definition.node("missing"));

        assertTrue(exception.getMessage().contains("unknown node"));
    }

    @Test
    void rejectsDuplicateNodeId() {
        var exception = assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "same",
                List.of(
                        new NodeDefinition("same", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("same", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of()
        ));

        assertTrue(exception.getMessage().contains("duplicate node id"));
        assertTrue(exception.getMessage().contains("same"));
    }

    @Test
    void rejectsEdgeWithMissingFromOrToNode() {
        var missingFrom = assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "existing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                List.of(new EdgeDefinition("missing-from", "completed", "existing"))
        ));
        var missingTo = assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "existing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                List.of(new EdgeDefinition("existing", "completed", "missing-to"))
        ));

        assertTrue(missingFrom.getMessage().contains("edge from"));
        assertTrue(missingFrom.getMessage().contains("missing-from"));
        assertTrue(missingTo.getMessage().contains("edge to"));
        assertTrue(missingTo.getMessage().contains("missing-to"));
    }

    @Test
    void rejectsCollectionContainingNull() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "existing",
                Collections.singletonList(null),
                List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "existing",
                List.of(new NodeDefinition("existing", NodeKind.TASK, "noop.task", Map.of())),
                Collections.singletonList(null)
        ));
    }

    @Test
    void nodeDefinitionValidatesRequiredFieldsAndCopiesConfig() {
        var config = new HashMap<String, Object>();
        config.put("prompt", "review");

        var node = new NodeDefinition("review", NodeKind.GATE, "human.review", config);
        config.put("prompt", "changed");

        assertEquals("review", node.config().get("prompt"));
        assertThrows(UnsupportedOperationException.class, () -> node.config().put("prompt", "again"));
        assertThrows(IllegalArgumentException.class, () -> new NodeDefinition("", NodeKind.TASK, "handler", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new NodeDefinition("id", null, "handler", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new NodeDefinition("id", NodeKind.TASK, "", Map.of()));
    }

    @Test
    void mapsPreserveNullValuesAndRejectNullKeysWithFieldName() {
        var config = new HashMap<String, Object>();
        config.put("optional", null);
        var instanceResult = new HashMap<String, Object>();
        instanceResult.put("artifact", null);
        var resultPayload = new HashMap<String, Object>();
        resultPayload.put("decision", null);

        var node = new NodeDefinition("review", NodeKind.GATE, "human.review", config);
        var instance = new NodeInstance(
                "node-instance-1",
                "node-definition-1",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.COMPLETED,
                "stage-1",
                "iteration-1",
                List.of(),
                List.of(),
                instanceResult,
                "run-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        );
        var result = NodeResult.completed("completed", List.of(), resultPayload);

        assertTrue(node.config().containsKey("optional"));
        assertEquals(null, node.config().get("optional"));
        assertTrue(instance.result().containsKey("artifact"));
        assertEquals(null, instance.result().get("artifact"));
        assertTrue(result.result().containsKey("decision"));
        assertEquals(null, result.result().get("decision"));

        var configWithNullKey = new HashMap<String, Object>();
        configWithNullKey.put(null, "value");
        var instanceResultWithNullKey = new HashMap<String, Object>();
        instanceResultWithNullKey.put(null, "value");
        var resultWithNullKey = new HashMap<String, Object>();
        resultWithNullKey.put(null, "value");

        var configException = assertThrows(
                IllegalArgumentException.class,
                () -> new NodeDefinition("review", NodeKind.GATE, "human.review", configWithNullKey)
        );
        var instanceException = assertThrows(
                IllegalArgumentException.class,
                () -> new NodeInstance(
                        "node-instance-1",
                        "node-definition-1",
                        NodeKind.TASK,
                        "noop.task",
                        NodeStatus.COMPLETED,
                        "stage-1",
                        "iteration-1",
                        List.of(),
                        List.of(),
                        instanceResultWithNullKey,
                        "run-1",
                        Instant.parse("2026-05-23T00:00:00Z"),
                        Instant.parse("2026-05-23T00:00:01Z")
                )
        );
        var resultException = assertThrows(
                IllegalArgumentException.class,
                () -> NodeResult.completed("completed", List.of(), resultWithNullKey)
        );

        assertTrue(configException.getMessage().contains("config"));
        assertTrue(instanceException.getMessage().contains("result"));
        assertTrue(resultException.getMessage().contains("result"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mapsRejectNonStringKeysWithFieldName() {
        Map configWithIntegerKey = new HashMap();
        configWithIntegerKey.put(1, "value");
        Map instanceResultWithIntegerKey = new HashMap();
        instanceResultWithIntegerKey.put(2, "value");
        Map resultWithIntegerKey = new HashMap();
        resultWithIntegerKey.put(3, "value");

        var configException = assertThrows(
                IllegalArgumentException.class,
                () -> new NodeDefinition("review", NodeKind.GATE, "human.review", configWithIntegerKey)
        );
        var instanceException = assertThrows(
                IllegalArgumentException.class,
                () -> new NodeInstance(
                        "node-instance-1",
                        "node-definition-1",
                        NodeKind.TASK,
                        "noop.task",
                        NodeStatus.COMPLETED,
                        "stage-1",
                        "iteration-1",
                        List.of(),
                        List.of(),
                        instanceResultWithIntegerKey,
                        "run-1",
                        Instant.parse("2026-05-23T00:00:00Z"),
                        Instant.parse("2026-05-23T00:00:01Z")
                )
        );
        var resultException = assertThrows(
                IllegalArgumentException.class,
                () -> NodeResult.completed("completed", List.of(), resultWithIntegerKey)
        );

        assertTrue(configException.getMessage().contains("config"));
        assertTrue(instanceException.getMessage().contains("result"));
        assertTrue(resultException.getMessage().contains("result"));
    }

    @Test
    void edgeDefinitionValidatesRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeDefinition("", "completed", "to"));
        assertThrows(IllegalArgumentException.class, () -> new EdgeDefinition("from", "", "to"));
        assertThrows(IllegalArgumentException.class, () -> new EdgeDefinition("from", "completed", ""));
    }

    @Test
    void workflowInstanceCopiesActiveNodeInstanceIds() {
        var activeIds = new ArrayList<>(List.of("node-1"));

        var instance = new WorkflowInstance(
                "wf-1",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                activeIds,
                "event-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        );
        activeIds.add("node-2");

        assertEquals(List.of("node-1"), instance.activeNodeInstanceIds());
        assertThrows(UnsupportedOperationException.class, () -> instance.activeNodeInstanceIds().add("node-3"));
    }

    @Test
    void workflowInstanceRejectsRequiredRuntimeFields() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstance(
                "",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                List.of(),
                "event-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstance(
                "wf-1",
                "",
                1,
                WorkflowStatus.RUNNING,
                List.of(),
                "event-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstance(
                "wf-1",
                "agent-lifecycle",
                1,
                null,
                List.of(),
                "event-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstance(
                "wf-1",
                "agent-lifecycle",
                0,
                WorkflowStatus.RUNNING,
                List.of(),
                "event-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstance(
                "wf-1",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                List.of(),
                "",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstance(
                "wf-1",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                List.of(),
                "event-1",
                null,
                Instant.parse("2026-05-23T00:00:01Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstance(
                "wf-1",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                List.of(),
                "event-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                null
        ));
    }

    @Test
    void nodeInstanceCopiesRefsAndResult() {
        var inputRefs = new ArrayList<>(List.of("input-1"));
        var outputRefs = new ArrayList<>(List.of("output-1"));
        var result = new HashMap<String, Object>();
        result.put("answer", "ok");

        var instance = new NodeInstance(
                "node-instance-1",
                "node-definition-1",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.COMPLETED,
                "stage-1",
                "iteration-1",
                inputRefs,
                outputRefs,
                result,
                "run-1",
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:01Z")
        );
        inputRefs.add("input-2");
        outputRefs.add("output-2");
        result.put("answer", "changed");

        assertEquals(List.of("input-1"), instance.inputRefs());
        assertEquals(List.of("output-1"), instance.outputRefs());
        assertEquals("ok", instance.result().get("answer"));
        assertThrows(UnsupportedOperationException.class, () -> instance.inputRefs().add("input-3"));
        assertThrows(UnsupportedOperationException.class, () -> instance.outputRefs().add("output-3"));
        assertThrows(UnsupportedOperationException.class, () -> instance.result().put("answer", "again"));
    }

    @Test
    void nodeInstanceRejectsRequiredRuntimeFields() {
        assertThrows(IllegalArgumentException.class, () -> new NodeInstance(
                "",
                "node-definition-1",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.CREATED,
                "stage-1",
                "iteration-1",
                List.of(),
                List.of(),
                Map.of(),
                "run-1",
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstance(
                "node-instance-1",
                "",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.CREATED,
                "stage-1",
                "iteration-1",
                List.of(),
                List.of(),
                Map.of(),
                "run-1",
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstance(
                "node-instance-1",
                "node-definition-1",
                null,
                "noop.task",
                NodeStatus.CREATED,
                "stage-1",
                "iteration-1",
                List.of(),
                List.of(),
                Map.of(),
                "run-1",
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstance(
                "node-instance-1",
                "node-definition-1",
                NodeKind.TASK,
                "",
                NodeStatus.CREATED,
                "stage-1",
                "iteration-1",
                List.of(),
                List.of(),
                Map.of(),
                "run-1",
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstance(
                "node-instance-1",
                "node-definition-1",
                NodeKind.TASK,
                "noop.task",
                null,
                "stage-1",
                "iteration-1",
                List.of(),
                List.of(),
                Map.of(),
                "run-1",
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstance(
                "node-instance-1",
                "node-definition-1",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.CREATED,
                "",
                "iteration-1",
                List.of(),
                List.of(),
                Map.of(),
                "run-1",
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstance(
                "node-instance-1",
                "node-definition-1",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.CREATED,
                "stage-1",
                "",
                List.of(),
                List.of(),
                Map.of(),
                "run-1",
                null,
                null
        ));
    }

    @Test
    void nodeResultHelpersCreateCompletedAndWaitingResults() {
        var outputs = new ArrayList<>(List.of("artifact-1"));
        var result = new HashMap<String, Object>();
        result.put("summary", "done");

        var completed = NodeResult.completed("completed", outputs, result);
        var waiting = NodeResult.waiting("needs_review", result);
        outputs.add("artifact-2");
        result.put("summary", "changed");

        assertEquals(NodeStatus.COMPLETED, completed.status());
        assertEquals(List.of("artifact-1"), completed.outputRefs());
        assertEquals("done", completed.result().get("summary"));
        assertEquals(NodeStatus.WAITING, waiting.status());
        assertEquals(List.of(), waiting.outputRefs());
        assertEquals("done", waiting.result().get("summary"));
        assertThrows(UnsupportedOperationException.class, () -> completed.result().put("summary", "again"));
    }

    @Test
    void nodeResultRejectsRequiredFields() {
        var nullOutcome = assertThrows(
                IllegalArgumentException.class,
                () -> new NodeResult(null, NodeStatus.COMPLETED, List.of(), Map.of(), null)
        );
        var blankOutcome = assertThrows(
                IllegalArgumentException.class,
                () -> new NodeResult(" ", NodeStatus.COMPLETED, List.of(), Map.of(), null)
        );
        var nullStatus = assertThrows(
                IllegalArgumentException.class,
                () -> new NodeResult("completed", null, List.of(), Map.of(), null)
        );

        assertTrue(nullOutcome.getMessage().contains("outcome"));
        assertTrue(blankOutcome.getMessage().contains("outcome"));
        assertTrue(nullStatus.getMessage().contains("status"));
    }
}
