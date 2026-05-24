package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.DecisionHandler;
import dev.dual.runtime.handler.GateHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.TaskHandler;

public final class WorkflowRuntime {
    private final WorkflowFileLedger ledger;
    private final HandlerRegistry handlers;

    public WorkflowRuntime(WorkflowFileLedger ledger, HandlerRegistry handlers) {
        if (ledger == null) {
            throw new IllegalArgumentException("ledger is required");
        }
        if (handlers == null) {
            throw new IllegalArgumentException("handlers is required");
        }
        this.ledger = ledger;
        this.handlers = handlers;
    }

    public WorkflowRuntimeResult resume(WorkflowCommand command) throws Exception {
        var paths = new WorkstreamPaths(command.workspaceRoot(), new DualId(command.workstreamId()));
        var definition = ledger.loadDefinition(paths);
        var current = ledger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            return new WorkflowRuntimeResult(command.workstreamId(), current.status(), "", "workflow has no active node");
        }
        if (current.status() == WorkflowStatus.WAITING_FOR_RECOVERY) {
            var active = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId();
            return new WorkflowRuntimeResult(command.workstreamId(), current.status(), active, "waiting for recovery; run dualctl retry after fixing the cause");
        }

        var node = ledger.loadCoreNode(paths, current.activeNodeInstanceIds().get(0));
        var nodeDefinition = definition.node(node.nodeDefinitionId());
        if (nodeDefinition.kind() == NodeKind.GATE) {
            handlers.gate(nodeDefinition.handlerType())
                    .openOrInspect(new GateHandler.GateContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, nodeDefinition.id(), "waiting for human review");
        }

        var result = switch (nodeDefinition.kind()) {
            case TASK -> handlers.task(nodeDefinition.handlerType())
                    .execute(new TaskHandler.TaskContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case DECISION -> handlers.decision(nodeDefinition.handlerType())
                    .evaluate(new DecisionHandler.DecisionContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            case GATE -> throw new IllegalStateException("gate nodes are handled before routing");
        };

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), result.status(), result.outcome(), result.outputRefs(), result.result());
        var next = ledger.loadCurrent(paths);
        var active = "";
        if (!next.activeNodeInstanceIds().isEmpty()) {
            active = ledger.loadNode(paths, next.activeNodeInstanceIds().get(0)).nodeDefinitionId();
        }
        var message = result.status() == NodeStatus.COMPLETED ? "resumed workflow" : "workflow stopped at " + result.status();
        return new WorkflowRuntimeResult(command.workstreamId(), next.status(), active, message);
    }
}
