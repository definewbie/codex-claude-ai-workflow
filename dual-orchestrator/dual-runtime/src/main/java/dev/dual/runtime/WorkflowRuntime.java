package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.NodeExecutionStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowExecutionStatus;
import dev.dual.ledger.file.WorkflowExecutionDocument;
import dev.dual.ledger.file.WorkflowCurrentDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.ArtifactContractValidator;
import dev.dual.runtime.handler.ContractEventTypes;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.GateHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.TaskHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Drives one workflow instance forward from the authoritative ledger state.
 *
 * Runtime owns transition execution and runtime facts only: it creates workflow
 * and node execution records, invokes handlers, validates artifact contracts,
 * and asks the ledger to route completed nodes. It must not infer product goal
 * satisfaction from prose, and it must guard terminal workflows before creating
 * any new execution side effects.
 */
public final class WorkflowRuntime {
    private final WorkflowFileLedger ledger;
    private final HandlerRegistry handlers;
    private final ArtifactContractValidator artifactContractValidator;
    private final AgentTaskDispatchMode dispatchMode;

    public WorkflowRuntime(WorkflowFileLedger ledger, HandlerRegistry handlers) {
        this(ledger, handlers, AgentTaskDispatchMode.INLINE);
    }

    public WorkflowRuntime(WorkflowFileLedger ledger, HandlerRegistry handlers, AgentTaskDispatchMode dispatchMode) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger is required");
        }
        if (handlers == null) {
            throw new IllegalArgumentException("handlers is required");
        }
        this.ledger = ledger;
        this.handlers = handlers;
        this.dispatchMode = dispatchMode == null ? AgentTaskDispatchMode.INLINE : dispatchMode;
        this.artifactContractValidator = new ArtifactContractValidator();
    }

    /**
     * Advances the current workflow until it reaches a gate, recovery boundary,
     * terminal state, or the current active-node snapshot is exhausted.
     *
     * Key steps:
     * 1. Load definition/current from the ledger.
     * 2. Refuse terminal workflows before creating a workflow execution.
     * 3. Create a workflow execution and per-node execution/task records.
     * 4. Execute handlers and validate structured artifacts.
     * 5. Finish the workflow execution with a typed runtime status.
     */
    public WorkflowRuntimeResult resume(WorkflowCommand command) throws Exception {
        var paths = new WorkstreamPaths(command.workspaceRoot(), new DualId(command.workstreamId()));
        var definition = ledger.loadDefinition(paths);
        var current = ledger.loadCurrent(paths);

        // Terminal observation is allowed elsewhere, but resume must be side-effect free.
        if (current.status().isTerminal()) {
            return new WorkflowRuntimeResult(
                    command.workstreamId(),
                    current.status(),
                    "",
                    "workflow is terminal: " + current.status()
            );
        }
        var workflowExecution = ledger.startWorkflowExecution(paths, current, "resume");
        var nodeExecutionIds = new ArrayList<String>();
        var workflowExecutionFinished = false;
        try {
            var outcome = resumeWithinExecution(command, paths, definition, current, workflowExecution, nodeExecutionIds);
            var finalResult = outcome.result();
            finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, outcome.executionStatus(), finalResult.message());
            workflowExecutionFinished = true;
            return finalResult;
        } catch (Exception failure) {
            if (!workflowExecutionFinished) {
                finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, WorkflowExecutionStatus.FAILED, failure.getMessage());
            }
            throw failure;
        }
    }

    private ResumeOutcome resumeWithinExecution(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            WorkflowCurrentDocument current,
            WorkflowExecutionDocument workflowExecution,
            ArrayList<String> nodeExecutionIds
    ) throws Exception {
        if (current.activeNodeInstanceIds().isEmpty()) {
            return stopped(new WorkflowRuntimeResult(command.workstreamId(), current.status(), "", "workflow has no active node"));
        }
        if (current.status() == WorkflowStatus.WAITING_FOR_RECOVERY) {
            var recoveryRejection = rejectRecoveryOrAutoRetry(command, paths, definition, current);
            if (recoveryRejection != null) {
                return stopped(recoveryRejection);
            }
            current = ledger.loadCurrent(paths);
        }
        var result = executeActiveSnapshot(command, paths, definition, current, workflowExecution, nodeExecutionIds);
        return new ResumeOutcome(result, workflowExecutionStatus(result.status()));
    }

    private WorkflowRuntimeResult rejectRecoveryOrAutoRetry(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            WorkflowCurrentDocument current
    ) throws Exception {
        var nodeDoc = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        var nodeDef = definition.node(nodeDoc.nodeDefinitionId());
        if (nodeDoc.status() == NodeStatus.FAILED && attemptAutoRetry(paths, nodeDoc, nodeDef)) {
            return null;
        }
        return rejectionResult(command, current, nodeDoc, nodeDef);
    }

    private WorkflowRuntimeResult executeActiveSnapshot(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            WorkflowCurrentDocument current,
            WorkflowExecutionDocument workflowExecution,
            ArrayList<String> nodeExecutionIds
    ) throws Exception {
        var activeSnapshot = new ArrayList<>(current.activeNodeInstanceIds());
        WorkflowRuntimeResult lastResult = null;

        for (var activeNodeId : activeSnapshot) {
            current = ledger.loadCurrent(paths);
            if (current.activeNodeInstanceIds().isEmpty()) {
                break;
            }
            if (!current.activeNodeInstanceIds().contains(activeNodeId)) {
                continue;
            }
            lastResult = executeActiveNode(command, paths, definition, workflowExecution, nodeExecutionIds, activeNodeId);
        }
        return lastResult != null
                ? lastResult
                : new WorkflowRuntimeResult(command.workstreamId(), current.status(), "", "workflow has no active node");
    }

    private WorkflowRuntimeResult executeActiveNode(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            WorkflowExecutionDocument workflowExecution,
            ArrayList<String> nodeExecutionIds,
            String activeNodeId
    ) throws Exception {
        var nodeDoc = ledger.loadNode(paths, activeNodeId);
        var nodeDefinition = definition.node(nodeDoc.nodeDefinitionId());

        if (nodeDefinition.kind() == NodeKind.GATE) {
            var node = ledger.loadCoreNode(paths, activeNodeId);
            handlers.gate(nodeDefinition.handlerType())
                    .openOrInspect(new GateHandler.GateContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, nodeDefinition.id(), "waiting for human review");
        }

        var guardResult = resolveNodeForExecution(paths, nodeDoc);
        if (guardResult == null) {
            var refreshedCurrent = ledger.loadCurrent(paths);
            var refreshedNode = ledger.loadNode(paths, activeNodeId);
            var refreshedDefinition = definition.node(refreshedNode.nodeDefinitionId());
            return rejectionResult(command, refreshedCurrent, refreshedNode, refreshedDefinition);
        }

        if (dispatchMode == AgentTaskDispatchMode.WORKER) {
            return enqueueAgentTask(command, paths, workflowExecution, nodeDoc, nodeDefinition);
        }

        var result = executeAndRecordNode(command, paths, workflowExecution, nodeExecutionIds, nodeDoc, nodeDefinition);
        emitDomainEvents(paths, nodeDefinition.id(), nodeDefinition.handlerType(), nodeDoc.nodeInstanceId(), result);
        ledger.completeAndRoute(paths, definition, nodeDoc.nodeInstanceId(), result.status(), result.outcome(), result.outputRefs(), result.result());
        var next = ledger.loadCurrent(paths);
        openWaitingGates(command, paths, definition, next);
        var active = activeNodeDefinitionId(paths, next);
        var message = result.status() == NodeStatus.COMPLETED ? "resumed workflow" : "workflow stopped at " + result.status();
        return new WorkflowRuntimeResult(command.workstreamId(), next.status(), active, message);
    }

    private WorkflowRuntimeResult enqueueAgentTask(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowExecutionDocument workflowExecution,
            NodeInstanceDocument nodeDoc,
            NodeDefinition nodeDefinition
    ) throws Exception {
        var agentTask = ledger.createAgentTask(paths, workflowExecution.workflowExecutionId(), nodeDefinition, nodeDoc);
        ledger.markQueuedForAgentTask(paths, nodeDoc.nodeInstanceId(), agentTask.agentTaskId());
        return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.RUNNING, nodeDefinition.id(), "agent task queued");
    }

    private WorkflowRuntimeResult rejectionResult(
            WorkflowCommand command,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            NodeDefinition definition
    ) {
        var rejection = NodeExecutionGuard.reject(node, definition);
        return new WorkflowRuntimeResult(command.workstreamId(), current.status(), node.nodeDefinitionId(), rejection.message());
    }

    private String activeNodeDefinitionId(WorkstreamPaths paths, WorkflowCurrentDocument current) throws Exception {
        if (current.activeNodeInstanceIds().isEmpty()) {
            return "";
        }
        return ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId();
    }

    private static ResumeOutcome stopped(WorkflowRuntimeResult result) {
        return new ResumeOutcome(result, WorkflowExecutionStatus.STOPPED);
    }

    private record ResumeOutcome(WorkflowRuntimeResult result, WorkflowExecutionStatus executionStatus) {
    }

    private NodeResult executeAndRecordNode(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowExecutionDocument workflowExecution,
            ArrayList<String> nodeExecutionIds,
            NodeInstanceDocument nodeDoc,
            NodeDefinition nodeDefinition
    ) throws Exception {
        long pid = ProcessHandle.current().pid();
        var agentTask = ledger.createAgentTask(paths, workflowExecution.workflowExecutionId(), nodeDefinition, nodeDoc);
        var nodeExecution = ledger.startNodeExecution(paths, workflowExecution.workflowExecutionId(), nodeDoc, agentTask, pid);
        agentTask = ledger.markAgentTaskRunning(paths, agentTask, nodeExecution.nodeExecutionId());
        nodeExecutionIds.add(nodeExecution.nodeExecutionId());
        ledger.markRunning(paths, nodeDoc.nodeInstanceId(), pid);

        var node = ledger.loadCoreNode(paths, nodeDoc.nodeInstanceId());
        try {
            var result = executeNode(command, nodeDefinition, node);
            result = artifactContractValidator.validate(
                    command.workspaceRoot(),
                    command.workstreamId(),
                    node.nodeInstanceId(),
                    nodeDefinition,
                    result
            );
            ledger.markAgentTaskSucceeded(paths, agentTask, result);
            ledger.finishNodeExecution(paths, nodeExecution, result);
            return result;
        } catch (Exception nodeFailure) {
            ledger.markAgentTaskFailed(paths, agentTask, nodeFailure);
            ledger.failNodeExecution(paths, nodeExecution, nodeFailure);
            throw nodeFailure;
        }
    }

    private NodeResult executeNode(WorkflowCommand command, NodeDefinition nodeDefinition, dev.dual.core.workflow.NodeInstance node) throws Exception {
        return switch (nodeDefinition.kind()) {
            case TASK -> handlers.task(nodeDefinition.handlerType())
                    .execute(new TaskHandler.TaskContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case DECISION -> handlers.decision(nodeDefinition.handlerType())
                    .evaluate(new DecisionHandler.DecisionContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case GATE -> throw new IllegalStateException("gate nodes are handled before routing");
        };
    }

    private void finishWorkflowExecution(
            WorkstreamPaths paths,
            WorkflowExecutionDocument workflowExecution,
            ArrayList<String> nodeExecutionIds,
            WorkflowExecutionStatus status,
            String stopReason
    ) throws Exception {
        ledger.finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, status, stopReason);
    }

    private static WorkflowExecutionStatus workflowExecutionStatus(WorkflowStatus status) {
        return switch (status) {
            case CREATED -> WorkflowExecutionStatus.CREATED;
            case RUNNING -> WorkflowExecutionStatus.STOPPED;
            case WAITING_FOR_HUMAN -> WorkflowExecutionStatus.WAITING_FOR_HUMAN;
            case WAITING_FOR_RECOVERY -> WorkflowExecutionStatus.WAITING_FOR_RECOVERY;
            case BLOCKED -> WorkflowExecutionStatus.BLOCKED;
            case COMPLETED -> WorkflowExecutionStatus.ENDED;
            case FAILED -> WorkflowExecutionStatus.FAILED;
        };
    }

    private void openWaitingGates(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            WorkflowCurrentDocument current
    ) throws Exception {
        if (current.status() != WorkflowStatus.WAITING_FOR_HUMAN) {
            return;
        }
        for (var activeNodeId : current.activeNodeInstanceIds()) {
            var nodeDoc = ledger.loadNode(paths, activeNodeId);
            if (nodeDoc.kind() != NodeKind.GATE) {
                continue;
            }
            var nodeDefinition = definition.node(nodeDoc.nodeDefinitionId());
            var node = ledger.loadCoreNode(paths, activeNodeId);
            handlers.gate(nodeDefinition.handlerType())
                    .openOrInspect(new GateHandler.GateContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
        }
    }

    private void emitDomainEvents(WorkstreamPaths paths, String nodeDefinitionId,
                                 String handlerType, String nodeInstanceId,
                                 NodeResult result) throws Exception {
        if (result.status() != NodeStatus.COMPLETED) {
            return;
        }
        String eventId = "domain-" + nodeInstanceId;

        var commonPayload = new LinkedHashMap<String, Object>();
        commonPayload.put("handler_type", handlerType);
        commonPayload.put("node_id", nodeDefinitionId);
        commonPayload.put("node_instance_id", nodeInstanceId);

        switch (handlerType) {
            case "contract.compile" -> {
                commonPayload.put("contract_id", result.result().getOrDefault("contract_id", ""));
                commonPayload.put("contract_status", result.result().getOrDefault("contract_status", ""));
                ledger.appendEvent(paths, eventId,
                        ContractEventTypes.COMPILATION_COMPLETED, commonPayload);
            }
            case "acceptance.failure_classify" -> {
                commonPayload.put("category", result.result().getOrDefault("category", ""));
                commonPayload.put("matched_rule", result.result().getOrDefault("matched_rule", ""));
                commonPayload.put("review_decision", result.result().getOrDefault("review_decision", ""));
                ledger.appendEvent(paths, eventId,
                        ContractEventTypes.FAILURE_CLASSIFIED, commonPayload);
            }
            default -> { /* no domain event for this handler type */ }
        }
    }

    private boolean attemptAutoRetry(WorkstreamPaths paths, NodeInstanceDocument node, NodeDefinition nodeDef) throws Exception {
        var recoverable = node.result().get("acpx_failure.recoverable");
        if (!Boolean.TRUE.equals(recoverable)) {
            return false;
        }
        var reasonValue = node.result().getOrDefault("acpx_failure.reason", "");
        var reason = reasonValue instanceof String s ? s : String.valueOf(reasonValue);
        var policy = RetryPolicyResolver.resolve(reason, nodeDef.config());

        var countValue = node.result().getOrDefault("auto_retry_count", 0);
        var count = (countValue instanceof Number n) ? n.intValue() : 0;
        if (count >= policy.maxRetries()) {
            return false;
        }

        var delay = policy.delayForAttempt(count + 1);
        ledger.autoRetryNode(paths, node.nodeInstanceId(), delay);
        if (delay > 0) {
            Thread.sleep(delay);
        }
        return true;
    }

    private Boolean resolveNodeForExecution(WorkstreamPaths paths, NodeInstanceDocument node) throws Exception {
        return switch (node.status()) {
            case READY -> true;
            case RUNNING -> resolveRunningNode(paths, node);
            case BLOCKED, WAITING, COMPLETED, CREATED, SKIPPED, FAILED -> null;
        };
    }

    private Boolean resolveRunningNode(WorkstreamPaths paths, NodeInstanceDocument node) throws Exception {
        if (hasActiveQueuedAgentTask(paths, node)) {
            return null;
        }
        var latestExecution = ledger.latestNodeExecution(paths, node);
        if (latestExecution.isPresent()) {
            var execution = latestExecution.get();
            if (execution.status() == NodeExecutionStatus.RUNNING) {
                var pid = execution.pid() == null ? 0L : execution.pid();
                var alive = pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
                if (alive) {
                    return null;
                }
                ledger.markNodeExecutionStale(paths, execution, "detected by runtime resume");
                recoverStaleRunningNode(paths, node, pid <= 0 ? "" : pid);
                return null;
            }
        }
        var pidValue = node.result().get("running_pid");
        if (pidValue instanceof Number number) {
            var pid = number.longValue();
            var alive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            if (alive) {
                return null;
            }
            recoverStaleRunningNode(paths, node, pid);
            return null;
        }
        recoverStaleRunningNode(paths, node, "");
        return null;
    }

    private boolean hasActiveQueuedAgentTask(WorkstreamPaths paths, NodeInstanceDocument node) throws Exception {
        var taskId = node.result().get("queued_agent_task_id");
        if (taskId == null || String.valueOf(taskId).isBlank()) {
            return false;
        }
        return ledger.readAgentTask(paths, String.valueOf(taskId))
                .map(task -> task.status() == dev.dual.ledger.file.AgentTaskStatus.QUEUED
                        || task.status() == dev.dual.ledger.file.AgentTaskStatus.RUNNING)
                .orElse(false);
    }

    private void recoverStaleRunningNode(WorkstreamPaths paths, NodeInstanceDocument node, Object pid) throws Exception {
        ledger.appendEvent(paths, "stale-running-" + node.nodeInstanceId() + "-" + System.currentTimeMillis(),
                "node_stale_running_detected", Map.of(
                        "node", node.nodeDefinitionId(),
                        "node_instance_id", node.nodeInstanceId(),
                        "previous_running_pid", pid,
                        "action", "move_to_recovery"
                ));
        ledger.recoverStaleRunningNode(paths, "detected by runtime resume");
    }

}
