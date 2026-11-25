package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.flow.FlowExecutionSnapshot;
import dev.dual.core.flow.FlowOrchestrationKernel;
import dev.dual.core.flow.FlowSignal;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeDefinition;
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
    private final Map<String, String> runtimeIdentity;

    RoutingEngine(
            AtomicJsonWriter jsonWriter,
            ObjectMapper objectMapper,
            LedgerEventLog eventLog,
            RevisionTracker revisionTracker,
            FlowOrchestrationKernel flowKernel,
            Map<String, String> runtimeIdentity
    ) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = objectMapper;
        this.eventLog = eventLog;
        this.revisionTracker = revisionTracker;
        this.flowKernel = flowKernel;
        this.runtimeIdentity = Map.copyOf(runtimeIdentity);
        this.goalSatisfactionWriter = new GoalSatisfactionWriter(jsonWriter);
        this.completionAuditWriter = new CompletionAuditWriter(jsonWriter);
    }

    WorkflowCurrentDocument loadCurrent(WorkstreamPaths paths) throws IOException {
        return jsonWriter.read(paths.workflowCurrentJson(), WorkflowCurrentDocument.class);
    }

    NodeInstanceDocument loadNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        return jsonWriter.read(paths.nodeJson(nodeInstanceId), NodeInstanceDocument.class);
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
        completeAndRoute(paths, definition, nodeInstanceId, completedStatus, outcome, outputRefs, result, false);
    }

    private void completeAndRoute(
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            String nodeInstanceId,
            NodeStatus completedStatus,
            String outcome,
            List<String> outputRefs,
            Map<String, Object> result,
            boolean humanAuthorizedRevision
    ) throws IOException {
        var current = loadCurrent(paths);
        if (!current.activeNodeInstanceIds().contains(nodeInstanceId)) {
            throw new IOException("node is not active: " + nodeInstanceId);
        }

        var node = loadNode(paths, nodeInstanceId);
        if (completedStatus == NodeStatus.COMPLETED) {
            dev.dual.core.workflow.SelfTestContinuationSemantics.validateResult(node.nodeDefinitionId(), outcome, result);
        }
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
            var transitionAttributes = ExecutionNetworkGrantStore.forward(jsonWriter, paths, node, nextDefinition,
                    collectionTransition(paths, finished, nextDefinition, effectiveTransitionAttributes(node, transition.attributes())));
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
            if (humanAuthorizedRevision) {
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
            var transitionAttributes = ExecutionNetworkGrantStore.forward(jsonWriter, paths, node, nextDefinition,
                    collectionTransition(paths, finished, nextDefinition, effectiveTransitionAttributes(node, transition.attributes())));
            var occurrenceTransition = new LinkedHashMap<String, Object>(transitionAttributes);
            occurrenceTransition.put("source_node_instance_id", node.nodeInstanceId());
            occurrenceTransition.put("source_node_sha256", LedgerSession.sha(jsonWriter.readBytes(paths.nodeJson(node.nodeInstanceId()))));
            transitionAttributes = Map.copyOf(occurrenceTransition);
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
            var nextGateResult = new LinkedHashMap<String, Object>();
            if (nextDefinition.kind() == NodeKind.GATE) {
                nextGateResult.putAll(gateSourceResult);
                nextGateResult.put("source_node_instance_id", node.nodeInstanceId());
            }
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
                if (!humanAuthorizedRevision
                        && maxRevisions >= 0
                        && currentRevisionCount >= maxRevisions) {
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
                    nextDefinition.kind() == NodeKind.GATE ? Map.copyOf(nextGateResult) : Map.of(),
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
                        HumanGateActionSemantics.allowedActions(nextDefinition, effectiveOutcome),
                        Instant.now(),
                        null,
                        Map.of(
                                "subject_input_refs", List.copyOf(outputRefs),
                                "source_outcome", effectiveOutcome,
                                "source_node_instance_id", node.nodeInstanceId()
                        )
                    ));
            }

            lastEventId = LedgerUtils.nextEventId(lastEventId);
            var routedPayload = new LinkedHashMap<String, Object>();
            routedPayload.put("from", node.nodeDefinitionId());
            routedPayload.put("outcome", effectiveOutcome);
            routedPayload.put("to", nextDefinition.id());
            routedPayload.put("node_instance_id", nextNodeInstanceId);
            routedPayload.put("gate_id", gateId);
            routedPayload.put("transition", transitionAttributes);
            routedPayload.put("source_node_instance_id", node.nodeInstanceId());
            eventLog.appendEvent(paths, lastEventId, "node_routed", Map.copyOf(routedPayload));
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

    private Map<String, Object> collectionTransition(WorkstreamPaths paths, NodeInstanceDocument source, NodeDefinition target,
                                                       Map<String, Object> attributes) throws IOException {
        var result = new LinkedHashMap<>(attributes);
        if (source.kind() == NodeKind.GATE) {
            if ("collect_evidence".equals(source.result().get("human_action"))
                    || "submit_evidence".equals(source.result().get("human_action"))) {
                if (!source.result().containsKey("evidence_collection_baseline"))
                    throw new IOException("missing evidence collection approval baseline");
                result.put("evidence_collection_gate", source.nodeInstanceId());
                try {
                    result.put("evidence_collection_gate_sha256", java.util.HexFormat.of().formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(jsonWriter.readBytes(paths.nodeJson(source.nodeInstanceId())))));
                } catch (java.security.NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
            }
        } else if (!"loop".equals(attributes.get("transition_type"))
                || "self_test_report".equals(target.config().get("expected_artifact"))) {
            inheritIfMissing(result, source.inputTransition(), "evidence_collection_gate");
            inheritIfMissing(result, source.inputTransition(), "evidence_collection_gate_sha256");
        }
        return Map.copyOf(result);
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
        resolveOpenGate(paths, definition, action, note, null);
    }

    void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note,
                         EvidenceCollectionBaseline collection) throws IOException {
        resolveOpenGate(paths, definition, action, note, collection, null);
    }

    void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note,
                         EvidenceCollectionBaseline collection, EvidenceSubmission submission) throws IOException {
        resolveOpenGate(paths, definition, action, note, collection, submission, null);
    }

    void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note,
                         EvidenceCollectionBaseline collection, EvidenceSubmission submission,
                         ContractQualificationChecks.Decision actualChecks) throws IOException {
        if ("submit_evidence".equals(action) != (submission != null))
            throw new IOException("submit_evidence requires the typed material submission API");
        if ("request_action".equals(action == null ? "" : action.trim())) {
            throw new IOException("request_action does not resolve a gate; use the action request API");
        }
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
        if (HumanGateActionSemantics.COLLECT_EVIDENCE.equals(canonicalAction)
                || HumanGateActionSemantics.SUBMIT_EVIDENCE.equals(canonicalAction)) {
            if (collection == null || !collection.gateNodeInstanceId().equals(node.nodeInstanceId())
                    || current.status() != WorkflowStatus.WAITING_FOR_HUMAN
                    || !current.activeNodeInstanceIds().equals(List.of(node.nodeInstanceId()))
                    || note == null || note.isBlank() || !hasEdge
                    || !HumanGateActionSemantics.allowedActions(definition.node(node.nodeDefinitionId()), sourceOutcome)
                    .contains(canonicalAction)
                    || !node.inputRefs().equals(gate.result().get("subject_input_refs"))) {
                throw new IOException("collect_evidence requires a bound source baseline and explicit human note");
            }
        } else if (collection != null) {
            throw new IOException("source baseline is only valid for collect_evidence");
        }
        if (HumanGateActionSemantics.REQUEST_SCOPE_REVISION.equals(canonicalAction)) {
            var applicable = HumanGateActionSemantics.allowedActions(
                    definition.node(node.nodeDefinitionId()), sourceOutcome).contains(canonicalAction);
            if (!applicable || !hasEdge) {
                throw new IOException("scope revision requires a declared scope-repair occurrence and route");
            }
            if (note == null || note.isBlank()) {
                throw new IOException("a decision note is required for scope revision");
            }
            ScopeRevisionRequestGuard.verify(paths, node, gate, objectMapper);
            var max = RevisionTracker.maxRevisions(definition.node(node.nodeDefinitionId()));
            if (max >= 0 && revisionTracker.revisionCount(paths, node.nodeDefinitionId(),
                    "context.intake_claude_brainstorm") >= max) {
                throw new IOException("scope revision request budget exhausted; gate remains open");
            }
        }
        if (HumanGateActionSemantics.requiresDecisionNote(canonicalAction, sourceOutcome)
                && (note == null || note.isBlank())) {
            throw new IOException("a decision note is required for " + canonicalAction + " at "
                    + node.nodeDefinitionId() + " (" + sourceOutcome + ")");
        }
        var runtimeSha = ContractQualificationChecks.identity(runtimeIdentity);
        var checks = actualChecks == null
                ? new ContractQualificationChecks.Decision(1, ContractQualificationChecks.FAMILY,
                    ContractQualificationChecks.Coverage.NOT_INVOKED, node.nodeInstanceId(),
                    text(gate.result().get("source_node_instance_id")), node.inputRefs(), runtimeSha, List.of())
                : actualChecks;
        if (!checks.gateNodeInstanceId().equals(node.nodeInstanceId())
                || !checks.sourceNodeInstanceId().equals(text(gate.result().get("source_node_instance_id")))
                || !checks.subjectRefs().equals(node.inputRefs()) || !checks.runtimeIdentitySha256().equals(runtimeSha)
                || actualChecks != null && (!"contract".equals(definition.node(node.nodeDefinitionId()).config().get("gate_purpose"))
                    || !"approved".equals(canonicalAction)
                    || checks.familyCoverage() != ContractQualificationChecks.Coverage.COVERED
                    || checks.checks().stream().anyMatch(c -> c.result() == ContractQualificationChecks.Result.FAILED)))
            throw new IOException("qualification observations do not bind the current decision");
        jsonWriter.write(paths.gateJson(gate.gateId()), gate.resolved(canonicalAction, note, checks));
        var humanAuthorizedRevision = "revise".equals(canonicalAction)
                && note != null
                && !note.isBlank();
        var resolvedNodeResult = new LinkedHashMap<String, Object>(node.result());
        resolvedNodeResult.put("human_action", canonicalAction);
        resolvedNodeResult.put("requested_action", action);
        resolvedNodeResult.put("note", note == null ? "" : note);
        resolvedNodeResult.put("decision_checks", checks.toMap());
        if (collection != null) resolvedNodeResult.put("evidence_collection_baseline", collection);
        if (submission != null) resolvedNodeResult.put("external_evidence", submission);
        completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, canonicalAction,
                node.inputRefs(), Map.copyOf(resolvedNodeResult), humanAuthorizedRevision);
    }

    Map<String, Object> requestAction(WorkstreamPaths paths, WorkflowDefinition definition, HumanActionRequest request)
            throws IOException {
        var current = loadCurrent(paths);
        if (current.status() != WorkflowStatus.WAITING_FOR_HUMAN
                || !current.activeNodeInstanceIds().equals(List.of(request.gateNodeInstanceId()))) {
            throw new IOException("action request requires the current waiting gate occurrence");
        }
        var node = loadNode(paths, request.gateNodeInstanceId());
        var gate = requireOpenGate(paths, current, node);
        if (node.kind() != NodeKind.GATE || !HumanGateActionSemantics.allowedActions(
                definition.node(node.nodeDefinitionId()), text(gate.result().get("source_outcome")))
                .contains("request_action")) throw new IOException("action request is not available at this gate");
        if (!node.inputRefs().equals(request.sourceRefs())
                || !node.inputRefs().equals(gate.result().get("subject_input_refs"))) {
            throw new IOException("action request source references do not match the current gate");
        }
        var content = Map.<String, Object>of("request_id", request.requestId(),
                "gate_node_instance_id", request.gateNodeInstanceId(), "source_refs", request.sourceRefs(),
                "expected_result", request.expectedResult(),
                "why_existing_actions_insufficient", request.whyExistingActionsInsufficient());
        var requests = new java.util.ArrayList<Object>();
        var existing = node.result().get("action_requests");
        if (existing != null && !(existing instanceof List<?>)) throw new IOException("invalid action request ledger");
        if (existing instanceof List<?> list) requests.addAll(list);
        for (var entry : requests) {
            if (!(entry instanceof Map<?, ?> item)) throw new IOException("invalid action request ledger");
            if (request.requestId().equals(item.get("request_id"))) {
                if (!content.equals(item.get("content"))) throw new IOException("request id already has different content");
                return objectMapper.convertValue(item, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            }
        }
        var record = Map.<String, Object>of("request_id", request.requestId(), "content", content,
                "recorded_at", Instant.now().toString());
        requests.add(record);
        var result = new LinkedHashMap<>(node.result());
        result.put("action_requests", List.copyOf(requests));
        jsonWriter.write(paths.nodeJson(node.nodeInstanceId()), new NodeInstanceDocument(node.schemaVersion(),
                node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(), node.handlerType(), node.status(),
                node.stageId(), node.iterationId(), node.inputRefs(), node.inputTransition(), node.outputRefs(),
                result, node.runRef(), node.latestExecutionId(), node.startedAt(), node.finishedAt()));
        // The node is authoritative even if appending this derived audit event fails.
        eventLog.appendEvent(paths, "human-action-" + java.util.UUID.randomUUID(), "human_action_requested",
                Map.of("node_instance_id", node.nodeInstanceId(), "request_id", request.requestId()));
        return record;
    }

    private GateDocument requireOpenGate(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node
    ) throws IOException {
        for (var gateId : current.openGateIds()) {
            var gatePath = paths.gateJson(gateId);
            if (!jsonWriter.exists(gatePath)) {
                continue;
            }
            var gate = jsonWriter.read(gatePath, GateDocument.class);
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
