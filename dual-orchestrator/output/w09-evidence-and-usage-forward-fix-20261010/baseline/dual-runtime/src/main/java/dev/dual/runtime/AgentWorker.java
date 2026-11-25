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

    public AgentWorker(WorkflowFileLedger ledger, HandlerRegistry handlers) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger is required");
        }
        if (handlers == null) {
            throw new IllegalArgumentException("handlers is required");
        }
        this.ledger = ledger;
        this.handlers = handlers;
        this.artifactContractValidator = new ArtifactContractValidator(ledger);
    }

    public AgentWorkerResult runOne(WorkflowCommand command) throws Exception {
        var paths = new WorkstreamPaths(command.workspaceRoot(), new DualId(command.workstreamId()));
        try (var local = ledger.writeSession(paths, "worker local recovery", java.util.UUID.randomUUID().toString())) { local.commit(); }
        var recovering = RecordedExecutionRecovery.active(ledger, paths);
        if (recovering.isPresent() && recovering.get().status() == dev.dual.ledger.file.NodeExecutionStatus.RUNNING) {
            var recovered = RecordedExecutionRecovery.recover(ledger, paths, recovering.get());
            return new AgentWorkerResult(command.workstreamId(), recovering.get().agentTaskId(), recovered.activeNode(),
                    recovered.status(), false, recovered.message());
        }
        AgentTaskDocument task;
        NodeInstanceDocument nodeDoc;
        NodeDefinition nodeDefinition;
        dev.dual.core.workflow.WorkflowDefinition definition;
        dev.dual.ledger.file.NodeExecutionDocument nodeExecution;
        WorkflowExecutionDocument workflowExecution;
        try (var mutation = ledger.writeSession(paths, "claim queued task", java.util.UUID.randomUUID().toString())) {
            var scoped = mutation.ledger();
            var current = scoped.loadCurrent(paths);
            var queued = scoped.firstQueuedAgentTask(paths);
            if (queued.isEmpty() || current.status().isTerminal())
                return new AgentWorkerResult(command.workstreamId(), "", "", current.status(), false, "no eligible queued task");
            task = queued.orElseThrow();
            nodeDoc = scoped.loadNode(paths, task.nodeInstanceId());
            definition = scoped.loadDefinition(paths);
            nodeDefinition = definition.node(nodeDoc.nodeDefinitionId());
            workflowExecution = scoped.startWorkflowExecution(paths, current, "worker");
            nodeExecution = scoped.startNodeExecution(paths, workflowExecution.workflowExecutionId(), nodeDoc,
                    task, ProcessHandle.current().pid());
            task = scoped.markAgentTaskRunning(paths, task, nodeExecution.nodeExecutionId());
            scoped.markRunning(paths, nodeDoc.nodeInstanceId(), ProcessHandle.current().pid());
            mutation.commit();
        }
        var nodeExecutionIds = new ArrayList<String>();
        var finished = false;
        try {
            var result = executeAndRecord(command, paths, workflowExecution, nodeExecutionIds, task, nodeDoc, nodeDefinition, nodeExecution);
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
            NodeDefinition nodeDefinition,
            dev.dual.ledger.file.NodeExecutionDocument nodeExecution
    ) throws Exception {
        nodeExecutionIds.add(nodeExecution.nodeExecutionId());
        try {
        var node = ledger.loadCoreNode(paths, nodeDoc.nodeInstanceId());
        if (nodeDefinition.kind() == NodeKind.DECISION
                || handlers.task(nodeDefinition.handlerType()).localLedgerMutation()) {
            try (var mutation = ledger.writeSession(paths, "local node result and routing", nodeExecution.nodeExecutionId())) {
                var scoped = mutation.ledger();
                var result = executeNode(command, nodeDefinition, scoped.loadCoreNode(paths, node.nodeInstanceId()), scoped);
                result = new ArtifactContractValidator(scoped).validateAndFinalize(command.workspaceRoot(), command.workstreamId(),
                        node.nodeInstanceId(), nodeDefinition, node.inputRefs(), result);
                scoped.markAgentTaskFinished(paths, task, result);
                scoped.finishNodeExecution(paths, nodeExecution, result);
                WorkflowRuntime.emitDomainEvents(scoped, paths, nodeDefinition.id(), nodeDefinition.handlerType(),
                        node.nodeInstanceId(), result);
                scoped.completeAndRoute(paths, scoped.loadDefinition(paths), node.nodeInstanceId(), result.status(),
                        result.outcome(), result.outputRefs(), result.result());
                mutation.commit();
                return result;
            }
        }
            var result = dev.dual.runtime.handler.EvidenceCollection.execute(ledger, command.workspaceRoot(),
                    command.workstreamId(), nodeDoc, () -> executeNode(command, nodeDefinition, node, ledger));
            result = artifactContractValidator.validateAndFinalize(
                    command.workspaceRoot(),
                    command.workstreamId(),
                    node.nodeInstanceId(),
                    nodeDefinition,
                    node.inputRefs(),
                    result
            );
            result = dev.dual.runtime.handler.AgentRunCommitProtocol.complete(command.workspaceRoot(), ledger, paths,
                    nodeExecution, result);
            if (result.result().containsKey("completion_ref"))
                return AgentExecutionCompletion.commit(ledger, paths, nodeExecution, result);
            try (var mutation = ledger.writeSession(paths, "complete queued task", nodeExecution.nodeExecutionId())) {
                var scoped = mutation.ledger();
                scoped.markAgentTaskFinished(paths, task, result);
                scoped.finishNodeExecution(paths, nodeExecution, result);
                WorkflowRuntime.emitDomainEvents(scoped, paths, nodeDefinition.id(), nodeDefinition.handlerType(),
                        nodeDoc.nodeInstanceId(), result);
                scoped.completeAndRoute(paths, scoped.loadDefinition(paths), nodeDoc.nodeInstanceId(), result.status(),
                        result.outcome(), result.outputRefs(), result.result());
                mutation.commit();
            }
            return result;
        } catch (Exception nodeFailure) {
            // Preserve any prepared L03; recovery must not replace it with a new exception transition.
            try {
                if ("acpx.agent_run".equals(nodeDefinition.handlerType()))
                    dev.dual.runtime.handler.AgentRunCommitProtocol.releaseExecutor(ledger, paths, nodeExecution);
                else AgentExecutionCompletion.recordHandlerFailure(ledger, paths, nodeExecution, nodeFailure);
            }
            catch (Exception releaseFailure) { nodeFailure.addSuppressed(releaseFailure); }
            throw nodeFailure;
        }
    }

    private NodeResult executeNode(WorkflowCommand command, NodeDefinition nodeDefinition, dev.dual.core.workflow.NodeInstance node, WorkflowFileLedger executionLedger)
            throws Exception {
        return switch (nodeDefinition.kind()) {
            case TASK -> handlers.task(nodeDefinition.handlerType())
                    .execute(new TaskHandler.TaskContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node, executionLedger));
            case DECISION -> handlers.decision(nodeDefinition.handlerType())
                    .evaluate(new DecisionHandler.DecisionContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node, executionLedger));
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
                    .openOrInspect(new GateHandler.GateContext(
                            command.workspaceRoot(), command.workstreamId(), nodeDefinition, node,
                            sourceOutcome(paths, activeNodeId), ledger));
        }
    }

    private String sourceOutcome(WorkstreamPaths paths, String nodeInstanceId) throws Exception {
        var value = ledger.loadOpenGateForNode(paths, nodeInstanceId).result().get("source_outcome");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalStateException("open gate is missing source_outcome: " + nodeInstanceId);
        }
        return String.valueOf(value);
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
