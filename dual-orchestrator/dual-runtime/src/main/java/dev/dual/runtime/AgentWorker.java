package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.AgentTaskDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowExecutionDocument;
import dev.dual.ledger.file.WorkflowExecutionStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.AgentRunMetadataFinalizer;
import dev.dual.runtime.handler.ArtifactContractValidator;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.GateHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.TaskHandler;

import java.util.ArrayList;

/**
 * Executes one queued AgentTask.
 *
 * Worker owns dispatch execution only. It claims the queued task, records a
 * NodeExecution, invokes the handler, validates the artifact contract, and then
 * returns the node result to the ledger router. It does not choose workflow
 * transitions and it does not decide goal satisfaction.
 */
public final class AgentWorker {
    private final WorkflowFileLedger ledger;
    private final HandlerRegistry handlers;
    private final ArtifactContractValidator artifactContractValidator;
    private final AgentRunMetadataFinalizer runMetadataFinalizer;

    public AgentWorker(WorkflowFileLedger ledger, HandlerRegistry handlers) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger is required");
        }
        if (handlers == null) {
            throw new IllegalArgumentException("handlers is required");
        }
        this.ledger = ledger;
        this.handlers = handlers;
        this.artifactContractValidator = new ArtifactContractValidator();
        this.runMetadataFinalizer = new AgentRunMetadataFinalizer();
    }

    public AgentWorkerResult runOne(WorkflowCommand command) throws Exception {
        var paths = new WorkstreamPaths(command.workspaceRoot(), new DualId(command.workstreamId()));
        var task = ledger.firstQueuedAgentTask(paths);
        if (task.isEmpty()) {
            var current = ledger.loadCurrent(paths);
            return new AgentWorkerResult(command.workstreamId(), "", "", current.status(), false, "no queued agent task");
        }
        return executeQueued(command, paths, task.get());
    }

    private AgentWorkerResult executeQueued(
            WorkflowCommand command,
            WorkstreamPaths paths,
            AgentTaskDocument task
    ) throws Exception {
        var definition = ledger.loadDefinition(paths);
        var current = ledger.loadCurrent(paths);
        var nodeDoc = ledger.loadNode(paths, task.nodeInstanceId());
        var nodeDefinition = definition.node(nodeDoc.nodeDefinitionId());
        if (nodeDoc.status() != NodeStatus.RUNNING) {
            return new AgentWorkerResult(command.workstreamId(), task.agentTaskId(), nodeDefinition.id(),
                    current.status(), false, "queued task node is not running: " + nodeDoc.status());
        }

        var workflowExecution = ledger.startWorkflowExecution(paths, current, "worker");
        var nodeExecutionIds = new ArrayList<String>();
        var finished = false;
        try {
            var result = executeAndRecord(command, paths, workflowExecution, nodeExecutionIds, task, nodeDoc, nodeDefinition);
            ledger.completeAndRoute(paths, definition, nodeDoc.nodeInstanceId(), result.status(), result.outcome(),
                    result.outputRefs(), result.result());
            var next = ledger.loadCurrent(paths);
            openWaitingGates(command, paths, definition, next);
            var active = activeNodeDefinitionId(paths, next);
            ledger.finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, workflowExecutionStatus(next.status()),
                    "worker completed " + nodeDefinition.id());
            finished = true;
            return new AgentWorkerResult(command.workstreamId(), task.agentTaskId(), active, next.status(), true,
                    result.status() == NodeStatus.COMPLETED ? "agent task completed" : "workflow stopped at " + result.status());
        } catch (Exception failure) {
            if (!finished) {
                ledger.finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, WorkflowExecutionStatus.FAILED,
                        failure.getMessage());
            }
            throw failure;
        }
    }

    private NodeResult executeAndRecord(
            WorkflowCommand command,
            WorkstreamPaths paths,
            WorkflowExecutionDocument workflowExecution,
            ArrayList<String> nodeExecutionIds,
            AgentTaskDocument task,
            NodeInstanceDocument nodeDoc,
            NodeDefinition nodeDefinition
    ) throws Exception {
        long pid = ProcessHandle.current().pid();
        var nodeExecution = ledger.startNodeExecution(paths, workflowExecution.workflowExecutionId(), nodeDoc, task, pid);
        task = ledger.markAgentTaskRunning(paths, task, nodeExecution.nodeExecutionId());
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
                    node.inputRefs(),
                    result
            );
            result = runMetadataFinalizer.finalizeNodeResult(command.workspaceRoot(), result);
            ledger.markAgentTaskFinished(paths, task, result);
            ledger.finishNodeExecution(paths, nodeExecution, result);
            return result;
        } catch (Exception nodeFailure) {
            ledger.markAgentTaskFailed(paths, task, nodeFailure);
            ledger.failNodeExecution(paths, nodeExecution, nodeFailure);
            throw nodeFailure;
        }
    }

    private NodeResult executeNode(WorkflowCommand command, NodeDefinition nodeDefinition, dev.dual.core.workflow.NodeInstance node)
            throws Exception {
        return switch (nodeDefinition.kind()) {
            case TASK -> handlers.task(nodeDefinition.handlerType())
                    .execute(new TaskHandler.TaskContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case DECISION -> handlers.decision(nodeDefinition.handlerType())
                    .evaluate(new DecisionHandler.DecisionContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case GATE -> throw new IllegalStateException("gate nodes are not agent tasks");
        };
    }

    private void openWaitingGates(
            WorkflowCommand command,
            WorkstreamPaths paths,
            dev.dual.core.workflow.WorkflowDefinition definition,
            dev.dual.ledger.file.WorkflowCurrentDocument current
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

    private String activeNodeDefinitionId(WorkstreamPaths paths, dev.dual.ledger.file.WorkflowCurrentDocument current)
            throws Exception {
        if (current.activeNodeInstanceIds().isEmpty()) {
            return "";
        }
        return ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId();
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
}
