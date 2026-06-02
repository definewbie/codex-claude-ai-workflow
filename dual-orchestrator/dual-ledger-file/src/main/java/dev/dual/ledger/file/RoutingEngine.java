package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RoutingEngine {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final LedgerEventLog eventLog;
    private final RevisionTracker revisionTracker;

    RoutingEngine(AtomicJsonWriter jsonWriter, ObjectMapper objectMapper, LedgerEventLog eventLog, RevisionTracker revisionTracker) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = objectMapper;
        this.eventLog = eventLog;
        this.revisionTracker = revisionTracker;
    }

    WorkflowCurrentDocument loadCurrent(WorkstreamPaths paths) throws IOException {
        return objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
    }

    NodeInstanceDocument loadNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        return objectMapper.readValue(paths.nodeJson(nodeInstanceId).toFile(), NodeInstanceDocument.class);
    }

    void completeAndRoute(
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            String nodeInstanceId,
            NodeStatus completedStatus,
            String outcome,
            List<String> outputRefs,
            Map<String, Object> result
    ) throws IOException {
        var current = loadCurrent(paths);
        if (!current.activeNodeInstanceIds().contains(nodeInstanceId)) {
            throw new IOException("node is not active: " + nodeInstanceId);
        }

        var node = loadNode(paths, nodeInstanceId);
        var finishedAt = Instant.now();
        var finished = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                completedStatus,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                outputRefs,
                result,
                node.runRef(),
                node.startedAt(),
                finishedAt
        );
        jsonWriter.write(paths.nodeJson(nodeInstanceId), finished);

        if (completedStatus != NodeStatus.COMPLETED) {
            stopOnNonCompletedNode(paths, current, node, completedStatus, outcome, result);
            return;
        }

        // Build routing context for conditional edge evaluation
        var routingContext = new LinkedHashMap<String, Object>();
        routingContext.put("outcome", outcome);
        if (result != null) routingContext.putAll(result);

        var matchingEdges = definition.edgesFrom(node.nodeDefinitionId(), outcome, routingContext);

        // Fan-out: create successor nodes for all matching edges
        var successorNodeIds = new java.util.ArrayList<String>();
        var allOpenGateIds = new java.util.ArrayList<String>();
        var lastEventId = current.lastEventId();
        var workflowStatus = WorkflowStatus.RUNNING;

        for (var edge : matchingEdges) {
            var nextDefinition = definition.node(edge.to());
            var eventNum = LedgerUtils.currentEventNumber(lastEventId) + 1;
            var nextNodeInstanceId = LedgerUtils.nodeInstanceId(nextDefinition.id(), eventNum);
            var nextStatus = nextDefinition.kind() == NodeKind.GATE ? NodeStatus.WAITING : NodeStatus.READY;
            var nextIterationId = node.iterationId();
            var iterationStartedPayload = Map.<String, Object>of();
            if (RevisionTracker.startsNewIteration(node.nodeDefinitionId(), outcome, nextDefinition.id())) {
                if (RevisionTracker.isRevisionAction(outcome)) {
                    var maxRevisions = RevisionTracker.maxRevisions(definition.node(node.nodeDefinitionId()));
                    var currentRevisionCount = revisionTracker.revisionCount(paths, nextDefinition.id());
                    if (maxRevisions >= 0 && currentRevisionCount >= maxRevisions) {
                        revisionTracker.blockOnRevisionLimit(paths, current, node, outputRefs, result, outcome, maxRevisions, currentRevisionCount);
                        return;
                    }
                }
                var nextIteration = revisionTracker.nextRevisionIteration(paths, node, outcome, nextDefinition.id(), result);
                Files.createDirectories(paths.iterationsDir());
                jsonWriter.write(paths.iterationJson(nextIteration.iterationId()), nextIteration);
                nextIterationId = nextIteration.iterationId();
                iterationStartedPayload = Map.of(
                        "iteration_id", nextIteration.iterationId(),
                        "parent_iteration_id", nextIteration.parentIterationId(),
                        "trigger_node_instance_id", nextIteration.triggerNodeInstanceId(),
                        "trigger_action", nextIteration.triggerAction(),
                        "target_node_definition_id", nextIteration.targetNodeDefinitionId(),
                        "reason", nextIteration.reason()
                );
            }
            var nextNode = new NodeInstanceDocument(
                    1,
                    nextNodeInstanceId,
                    nextDefinition.id(),
                    nextDefinition.kind(),
                    nextDefinition.handlerType(),
                    nextStatus,
                    LedgerUtils.stageId(nextDefinition.id()),
                    nextIterationId,
                    outputRefs,
                    List.of(),
                    Map.of(),
                    null,
                    null,
                    null
            );
            jsonWriter.write(paths.nodeJson(nextNodeInstanceId), nextNode);

            var gateId = "";
            if (nextDefinition.kind() == NodeKind.GATE) {
                gateId = "gate-" + nextNodeInstanceId;
                allOpenGateIds.add(gateId);
                workflowStatus = WorkflowStatus.WAITING_FOR_HUMAN;
                jsonWriter.write(paths.gateJson(gateId), new GateDocument(
                        1,
                        gateId,
                        nextNodeInstanceId,
                        nextDefinition.handlerType(),
                        "open",
                        LedgerUtils.allowedActions(nextDefinition),
                        Instant.now(),
                        null,
                        Map.of()
                    ));
            }

            lastEventId = LedgerUtils.nextEventId(lastEventId);
            eventLog.appendEvent(paths, lastEventId, "node_routed", Map.of(
                    "from", node.nodeDefinitionId(),
                    "outcome", outcome,
                    "to", nextDefinition.id(),
                    "node_instance_id", nextNodeInstanceId,
                    "gate_id", gateId
            ));
            if (!iterationStartedPayload.isEmpty()) {
                lastEventId = LedgerUtils.nextEventId(lastEventId);
                eventLog.appendEvent(paths, lastEventId, "iteration_started", iterationStartedPayload);
            }
            successorNodeIds.add(nextNodeInstanceId);
        }

        // Compute remaining active: remove completed node, add successors
        var nextActiveIds = new java.util.ArrayList<>(current.activeNodeInstanceIds());
        nextActiveIds.remove(nodeInstanceId);
        nextActiveIds.addAll(successorNodeIds);

        if (nextActiveIds.isEmpty()) {
            completeWorkflow(paths, current, node, outcome);
            return;
        }

        var now = Instant.now();
        var next = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                workflowStatus,
                List.copyOf(nextActiveIds),
                List.copyOf(allOpenGateIds),
                lastEventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), next);
        eventLog.writeInstanceFromCurrent(paths, next, now);
    }

    void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note)
            throws IOException {
        if (action == null || action.isBlank()) {
            throw new IOException("human gate action is required");
        }
        var current = loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active gate node");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.kind() != NodeKind.GATE) {
            throw new IOException("active node is not a gate: " + node.nodeDefinitionId());
        }
        var hasEdge = definition.edgesFrom(node.nodeDefinitionId()).stream().anyMatch(edge -> edge.on().equals(action));
        var actionAllowed = LedgerUtils.allowedActions(definition.node(node.nodeDefinitionId())).contains(action);
        if (!hasEdge && !actionAllowed) {
            throw new IOException("unsupported gate action for " + node.nodeDefinitionId() + ": " + action);
        }
        completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, action, node.inputRefs(), Map.of(
                "human_action", action,
                "note", note == null ? "" : note
        ));
    }

    private void completeWorkflow(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            String outcome
    ) throws IOException {
        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        eventLog.appendEvent(paths, eventId, "workflow_completed", Map.of(
                "last_node", node.nodeDefinitionId(),
                "outcome", outcome
        ));
        var now = Instant.now();
        var completed = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.COMPLETED,
                List.of(),
                List.of(),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), completed);
        eventLog.writeInstanceFromCurrent(paths, completed, now);
    }

    private void stopOnNonCompletedNode(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            NodeStatus nodeStatus,
            String outcome,
            Map<String, Object> result
    ) throws IOException {
        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        var workflowStatus = WorkflowStatus.WAITING_FOR_RECOVERY;
        var recoveryGateId = "recovery-" + node.nodeInstanceId();
        var controlRepair = LedgerUtils.isControlRepairFailure(outcome, result);
        var gateResult = recoveryGateResult(node, nodeStatus, outcome, result, controlRepair);
        jsonWriter.write(paths.gateJson(recoveryGateId), new GateDocument(
                1,
                recoveryGateId,
                node.nodeInstanceId(),
                controlRepair ? "control.repair" : "recovery.review",
                "open",
                List.of("retry", "abort"),
                Instant.now(),
                null,
                gateResult
        ));
        var eventPayload = new LinkedHashMap<>(gateResult);
        eventPayload.put("recovery_gate_id", recoveryGateId);
        eventLog.appendEvent(paths, eventId, controlRepair ? "control_repair_required" : "node_" + nodeStatus.name().toLowerCase(), eventPayload);
        var now = Instant.now();
        var stopped = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                workflowStatus,
                List.of(node.nodeInstanceId()),
                List.of(recoveryGateId),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), stopped);
        eventLog.writeInstanceFromCurrent(paths, stopped, now);
    }

    private static Map<String, Object> recoveryGateResult(
            NodeInstanceDocument node,
            NodeStatus nodeStatus,
            String outcome,
            Map<String, Object> result,
            boolean controlRepair
    ) {
        var gateResult = new LinkedHashMap<String, Object>();
        gateResult.put("node", node.nodeDefinitionId());
        gateResult.put("node_status", nodeStatus.name());
        gateResult.put("outcome", outcome);
        if (controlRepair) {
            gateResult.put("reason", LedgerUtils.text(LedgerUtils.firstNonBlank(LedgerUtils.resultValue(result, "reason"), outcome)));
            gateResult.put("message", LedgerUtils.text(LedgerUtils.resultValue(result, "message")));
            gateResult.put("input_refs", LedgerUtils.resultValueOrDefault(result, "input_refs", List.of()));
            gateResult.put("allowed_decisions", LedgerUtils.resultValueOrDefault(result, "allowed_decisions", List.of()));
            gateResult.put("parse_error", LedgerUtils.resultValueOrDefault(result, "parse_error", Map.of()));
        }
        return gateResult;
    }
}
