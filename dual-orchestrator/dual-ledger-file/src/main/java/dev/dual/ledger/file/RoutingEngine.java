package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.flow.FlowExecutionSnapshot;
import dev.dual.core.flow.FlowOrchestrationKernel;
import dev.dual.core.flow.FlowSignal;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.HumanGateActionSemantics;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RoutingEngine {
    private static final String FINAL_NEXT_STAGE_GATE = "gate.human_next_stage_approval";

    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final LedgerEventLog eventLog;
    private final RevisionTracker revisionTracker;
    private final FlowOrchestrationKernel flowKernel;
    private final GoalSatisfactionWriter goalSatisfactionWriter;
    private final CompletionAuditWriter completionAuditWriter;

    RoutingEngine(
            AtomicJsonWriter jsonWriter,
            ObjectMapper objectMapper,
            LedgerEventLog eventLog,
            RevisionTracker revisionTracker,
            FlowOrchestrationKernel flowKernel
    ) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = objectMapper;
        this.eventLog = eventLog;
        this.revisionTracker = revisionTracker;
        this.flowKernel = flowKernel;
        this.goalSatisfactionWriter = new GoalSatisfactionWriter(jsonWriter);
        this.completionAuditWriter = new CompletionAuditWriter(jsonWriter);
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
                node.inputTransition(),
                outputRefs,
                result,
                node.runRef(),
                node.latestExecutionId(),
                node.startedAt(),
                finishedAt
        );
        jsonWriter.write(paths.nodeJson(nodeInstanceId), finished);
        goalSatisfactionWriter.evaluateAndWrite(paths, current, finished, completedStatus, outcome, outputRefs, result);

        if (completedStatus != NodeStatus.COMPLETED) {
            stopOnNonCompletedNode(paths, current, node, completedStatus, outcome, result);
            return;
        }

        // Build routing context for conditional edge evaluation
        var routingContext = new LinkedHashMap<String, Object>();
        routingContext.put("outcome", outcome);
        if (result != null) routingContext.putAll(result);

        var routeResult = flowKernel.advance(
                definition,
                new FlowExecutionSnapshot(current.workflowInstanceId(), activeNodeDefinitionIds(paths, current.activeNodeInstanceIds())),
                FlowSignal.completed(node.nodeDefinitionId(), outcome, routingContext)
        );
        var matchingTransitions = routeResult.transitions();

        var effectiveOutcome = outcome;
        var effectiveResult = result;
        Map<String, Object> gateSourceResult = Map.of();
        var lastEventId = current.lastEventId();
        for (var transition : matchingTransitions) {
            var nextDefinition = definition.node(transition.toNodeDefinitionId());
            var transitionAttributes = effectiveTransitionAttributes(node, transition.attributes());
            if (nextDefinition.kind() == NodeKind.GATE
                    || !startsNewIteration(transitionAttributes, node.nodeDefinitionId(), outcome, nextDefinition.id())) {
                continue;
            }
            var maxRevisions = RevisionTracker.maxRevisions(definition.node(node.nodeDefinitionId()));
            var currentRevisionCount = revisionTracker.revisionCount(
                    paths,
                    node.nodeDefinitionId(),
                    nextDefinition.id()
            );
            if (maxRevisions < 0 || currentRevisionCount < maxRevisions) {
                continue;
            }

            var blockedResult = new LinkedHashMap<String, Object>();
            if (result != null) {
                blockedResult.putAll(result);
            }
            blockedResult.put("reason", "revision_limit_reached");
            blockedResult.put("revision_limit_original_outcome", outcome);
            blockedResult.put("max_revisions", maxRevisions);
            blockedResult.put("current_revision_count", currentRevisionCount);
            var blockedRoutingContext = new LinkedHashMap<String, Object>(blockedResult);
            blockedRoutingContext.put("outcome", "BLOCKED");
            var blockedRoute = flowKernel.advance(
                    definition,
                    new FlowExecutionSnapshot(current.workflowInstanceId(),
                            activeNodeDefinitionIds(paths, current.activeNodeInstanceIds())),
                    FlowSignal.completed(node.nodeDefinitionId(), "BLOCKED", blockedRoutingContext)
            ).transitions();
            if (blockedRoute.isEmpty()) {
                revisionTracker.blockOnRevisionLimit(paths, current, node, outputRefs, result, outcome,
                        maxRevisions, currentRevisionCount);
                return;
            }
            if (blockedRoute.size() != 1
                    || definition.node(blockedRoute.getFirst().toNodeDefinitionId()).kind() != NodeKind.GATE) {
                throw new IOException("revision limit BLOCKED route must resolve to exactly one human gate: "
                        + node.nodeDefinitionId());
            }
            var blockedTransition = blockedRoute.getFirst();
            var blockedTarget = definition.node(blockedTransition.toNodeDefinitionId());
            var blockedAttributes = effectiveTransitionAttributes(node, blockedTransition.attributes());
            if (startsNewIteration(blockedAttributes, node.nodeDefinitionId(), "BLOCKED", blockedTarget.id())) {
                throw new IOException("revision limit BLOCKED route to a human gate must not start an iteration: "
                        + node.nodeDefinitionId());
            }

            effectiveOutcome = "BLOCKED";
            effectiveResult = blockedResult;
            gateSourceResult = Map.of(
                    "reason", "revision_limit_reached",
                    "revision_limit_original_outcome", outcome,
                    "max_revisions", maxRevisions,
                    "current_revision_count", currentRevisionCount
            );
            matchingTransitions = blockedRoute;
            jsonWriter.write(paths.nodeJson(nodeInstanceId), new NodeInstanceDocument(
                    finished.schemaVersion(),
                    finished.nodeInstanceId(),
                    finished.nodeDefinitionId(),
                    finished.kind(),
                    finished.handlerType(),
                    finished.status(),
                    finished.stageId(),
                    finished.iterationId(),
                    finished.inputRefs(),
                    finished.inputTransition(),
                    finished.outputRefs(),
                    effectiveResult,
                    finished.runRef(),
                    finished.latestExecutionId(),
                    finished.startedAt(),
                    finished.finishedAt()
            ));
            lastEventId = LedgerUtils.nextEventId(lastEventId);
            eventLog.appendEvent(paths, lastEventId, "revision_limit_reached", Map.of(
                    "node", node.nodeDefinitionId(),
                    "node_instance_id", node.nodeInstanceId(),
                    "outcome", outcome,
                    "max_revisions", maxRevisions,
                    "current_revision_count", currentRevisionCount,
                    "declared_blocked_gate", blockedTarget.id()
            ));
            break;
        }

        // Fan-out: create successor nodes for all matching edges
        var successorNodeIds = new java.util.ArrayList<String>();
        var allOpenGateIds = new java.util.ArrayList<String>();
        var workflowStatus = WorkflowStatus.RUNNING;

        for (var transition : matchingTransitions) {
            var nextDefinition = definition.node(transition.toNodeDefinitionId());
            var transitionAttributes = effectiveTransitionAttributes(node, transition.attributes());
            if (!joinReady(paths, current, nodeInstanceId, transitionAttributes)) {
                lastEventId = LedgerUtils.nextEventId(lastEventId);
                eventLog.appendEvent(paths, lastEventId, "join_deferred", Map.of(
                        "from", node.nodeDefinitionId(),
                        "outcome", outcome,
                        "to", nextDefinition.id(),
                        "transition", transitionAttributes,
                        "reason", "waiting_for_branch_group"
                ));
                continue;
            }
            var eventNum = LedgerUtils.currentEventNumber(lastEventId) + 1;
            var nextNodeInstanceId = LedgerUtils.nodeInstanceId(nextDefinition.id(), eventNum);
            var nextStatus = nextDefinition.kind() == NodeKind.GATE ? NodeStatus.WAITING : NodeStatus.READY;
            var nextIterationId = node.iterationId();
            var iterationStartedPayload = Map.<String, Object>of();
            if (nextDefinition.kind() != NodeKind.GATE
                    && startsNewIteration(transitionAttributes, node.nodeDefinitionId(), effectiveOutcome, nextDefinition.id())) {
                var maxRevisions = RevisionTracker.maxRevisions(definition.node(node.nodeDefinitionId()));
                var currentRevisionCount = revisionTracker.revisionCount(
                        paths,
                        node.nodeDefinitionId(),
                        nextDefinition.id()
                );
                if (maxRevisions >= 0 && currentRevisionCount >= maxRevisions) {
                    revisionTracker.blockOnRevisionLimit(paths, current, node, outputRefs, effectiveResult,
                            effectiveOutcome, maxRevisions, currentRevisionCount);
                    return;
                }
                var nextIteration = revisionTracker.nextRevisionIteration(
                        paths, node, effectiveOutcome, nextDefinition.id(), effectiveResult);
                Files.createDirectories(paths.iterationsDir());
                jsonWriter.write(paths.iterationJson(nextIteration.iterationId()), nextIteration);
                nextIterationId = nextIteration.iterationId();
                iterationStartedPayload = Map.of(
                        "iteration_id", nextIteration.iterationId(),
                        "parent_iteration_id", nextIteration.parentIterationId(),
                        "trigger_node_instance_id", nextIteration.triggerNodeInstanceId(),
                        "trigger_action", nextIteration.triggerAction(),
                        "target_node_definition_id", nextIteration.targetNodeDefinitionId(),
                        "reason", nextIteration.reason(),
                        "transition_type", text(transitionAttributes.get("transition_type")),
                        "loop_reason", text(transitionAttributes.get("loop_reason")),
                        "branch_group_id", text(transitionAttributes.get("branch_group_id"))
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
                    transitionAttributes,
                    List.of(),
                    nextDefinition.kind() == NodeKind.GATE ? gateSourceResult : Map.of(),
                    null,
                    "",
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
                        HumanGateActionSemantics.allowedActions(nextDefinition),
                        Instant.now(),
                        null,
                        Map.of(
                                "subject_input_refs", List.copyOf(outputRefs),
                                "source_outcome", effectiveOutcome
                        )
                    ));
            }

            lastEventId = LedgerUtils.nextEventId(lastEventId);
            eventLog.appendEvent(paths, lastEventId, "node_routed", Map.of(
                    "from", node.nodeDefinitionId(),
                    "outcome", effectiveOutcome,
                    "to", nextDefinition.id(),
                    "node_instance_id", nextNodeInstanceId,
                    "gate_id", gateId,
                    "transition", transitionAttributes
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
            completeWorkflow(paths, definition, current, node, outcome);
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

    private boolean joinReady(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            String completedNodeInstanceId,
            Map<String, Object> transitionAttributes
    ) throws IOException {
        if (!"join".equals(text(transitionAttributes.get("transition_type")))) {
            return true;
        }
        var joinStrategy = text(transitionAttributes.get("join_strategy"));
        if (joinStrategy.isBlank()) {
            joinStrategy = "all_terminal";
        }
        if (!"all_terminal".equals(joinStrategy)) {
            throw new IOException("unsupported join strategy: " + joinStrategy);
        }
        var branchGroupId = text(transitionAttributes.get("branch_group_id"));
        if (branchGroupId.isBlank()) {
            throw new IOException("join transition requires branch_group_id");
        }
        for (var activeNodeId : current.activeNodeInstanceIds()) {
            if (activeNodeId.equals(completedNodeInstanceId)) {
                continue;
            }
            var activeNode = loadNode(paths, activeNodeId);
            var activeTransition = activeNode.inputTransition();
            if (branchGroupId.equals(text(activeTransition.get("branch_group_id")))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> effectiveTransitionAttributes(
            NodeInstanceDocument sourceNode,
            Map<String, Object> transitionAttributes
    ) {
        var effective = new LinkedHashMap<String, Object>();
        if (transitionAttributes != null) {
            effective.putAll(transitionAttributes);
        }
        var inherited = sourceNode.inputTransition();
        inheritIfMissing(effective, inherited, "branch_group_id");
        inheritIfMissing(effective, inherited, "required");
        inheritIfMissing(effective, inherited, "purpose");
        return Map.copyOf(effective);
    }

    private static void inheritIfMissing(Map<String, Object> target, Map<String, Object> source, String key) {
        if (target.containsKey(key) || !source.containsKey(key)) {
            return;
        }
        target.put(key, source.get(key));
    }

    private static boolean startsNewIteration(
            Map<String, Object> transitionAttributes,
            String sourceNodeDefinitionId,
            String outcome,
            String targetNodeDefinitionId
    ) {
        if ("loop".equals(text(transitionAttributes.get("transition_type")))) {
            return true;
        }
        return RevisionTracker.startsNewIteration(sourceNodeDefinitionId, outcome, targetNodeDefinitionId);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<String> activeNodeDefinitionIds(WorkstreamPaths paths, List<String> activeNodeInstanceIds) throws IOException {
        var nodeDefinitionIds = new java.util.ArrayList<String>();
        for (var activeNodeInstanceId : activeNodeInstanceIds) {
            nodeDefinitionIds.add(loadNode(paths, activeNodeInstanceId).nodeDefinitionId());
        }
        return List.copyOf(nodeDefinitionIds);
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
        var outgoingEdges = definition.edgesFrom(node.nodeDefinitionId());
        var canonicalAction = HumanGateActionSemantics.canonicalAction(outgoingEdges, action.trim());
        if (FINAL_NEXT_STAGE_GATE.equals(node.nodeDefinitionId())
                && ("approve".equals(canonicalAction) || "approved".equals(canonicalAction))) {
            throw new IOException("final next-stage gate cannot be approved; use complete to finish the workstream");
        }
        var hasEdge = outgoingEdges.stream().anyMatch(edge -> edge.on().equals(canonicalAction));
        var actionAllowed = HumanGateActionSemantics.allowedActions(definition.node(node.nodeDefinitionId()))
                .contains(canonicalAction);
        if (!hasEdge && !actionAllowed) {
            throw new IOException("unsupported gate action for " + node.nodeDefinitionId() + ": " + action);
        }
        var gate = requireOpenGate(paths, current, node);
        var sourceOutcome = text(gate.result().get("source_outcome"));
        if ("BLOCKED".equals(sourceOutcome) && (note == null || note.isBlank())) {
            throw new IOException("a decision note is required for a BLOCKED human gate");
        }
        var gatePurpose = text(definition.node(node.nodeDefinitionId()).config().get("gate_purpose"));
        if ("design".equals(gatePurpose)
                && "revise".equals(canonicalAction)
                && (note == null || note.isBlank())) {
            throw new IOException("a decision note is required to revise a design");
        }
        resolveGateDocument(paths, gate, canonicalAction, note);
        completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, canonicalAction, node.inputRefs(), Map.of(
                "human_action", canonicalAction,
                "requested_action", action,
                "note", note == null ? "" : note
        ));
    }

    private GateDocument requireOpenGate(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node
    ) throws IOException {
        for (var gateId : current.openGateIds()) {
            var gatePath = paths.gateJson(gateId);
            if (!Files.isRegularFile(gatePath)) {
                continue;
            }
            var gate = objectMapper.readValue(gatePath.toFile(), GateDocument.class);
            if (gate.nodeInstanceId().equals(node.nodeInstanceId()) && "open".equals(gate.status())) {
                return gate;
            }
        }
        throw new IOException("no matching open gate document for node: " + node.nodeInstanceId());
    }

    private void resolveGateDocument(
            WorkstreamPaths paths,
            GateDocument gate,
            String action,
            String note
    ) throws IOException {
        jsonWriter.write(paths.gateJson(gate.gateId()), gate.resolved(action, note));
    }

    private void completeWorkflow(
            WorkstreamPaths paths,
            WorkflowDefinition definition,
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
        completionAuditWriter.write(paths, definition, node, outcome, now);
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
