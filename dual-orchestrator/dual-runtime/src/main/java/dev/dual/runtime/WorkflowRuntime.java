package dev.dual.runtime;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
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

        // WAITING_FOR_RECOVERY: check auto-retry before rejecting
        if (current.status() == WorkflowStatus.WAITING_FOR_RECOVERY) {
            var nodeDoc = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
            if (nodeDoc.status() == NodeStatus.FAILED && attemptAutoRetry(paths, nodeDoc)) {
                // Node was reset to READY; fall through to execution below.
                // Reload current since autoRetryNode updates it.
                current = ledger.loadCurrent(paths);
            } else {
                return new WorkflowRuntimeResult(command.workstreamId(), current.status(), nodeDoc.nodeDefinitionId(), statusRejectionMessage(nodeDoc));
            }
        }

        var nodeDoc = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        var nodeDefinition = definition.node(nodeDoc.nodeDefinitionId());

        if (nodeDefinition.kind() == NodeKind.GATE) {
            var node = ledger.loadCoreNode(paths, current.activeNodeInstanceIds().get(0));
            handlers.gate(nodeDefinition.handlerType())
                    .openOrInspect(new GateHandler.GateContext(command.workspaceRoot(), command.workstreamId(), nodeDefinition, node));
            return new WorkflowRuntimeResult(command.workstreamId(), WorkflowStatus.WAITING_FOR_HUMAN, nodeDefinition.id(), "waiting for human review");
        }

        // Status guard: resolve node into an executable state
        var guardResult = resolveNodeForExecution(paths, nodeDoc);
        if (guardResult == null) {
            return new WorkflowRuntimeResult(command.workstreamId(), current.status(), nodeDoc.nodeDefinitionId(), statusRejectionMessage(nodeDoc));
        }

        // Mark RUNNING with current PID
        long pid = ProcessHandle.current().pid();
        ledger.markRunning(paths, nodeDoc.nodeInstanceId(), pid);

        // Reload as core NodeInstance for handler context
        var node = ledger.loadCoreNode(paths, nodeDoc.nodeInstanceId());

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

    private boolean attemptAutoRetry(WorkstreamPaths paths, NodeInstanceDocument node) throws Exception {
        var recoverable = node.result().get("acpx_failure.recoverable");
        if (!Boolean.TRUE.equals(recoverable)) {
            return false;
        }
        var reasonValue = node.result().getOrDefault("acpx_failure.reason", "");
        var reason = reasonValue instanceof String s ? s : String.valueOf(reasonValue);
        var policy = RetryStrategies.forReason(reason);

        var countValue = node.result().getOrDefault("auto_retry_count", 0);
        var count = (countValue instanceof Number n) ? n.intValue() : 0;
        if (count >= policy.maxRetries()) {
            return false;
        }

        ledger.autoRetryNode(paths, node.nodeInstanceId());
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
        var pidValue = node.result().get("running_pid");
        if (pidValue instanceof Number number) {
            var alive = ProcessHandle.of(number.longValue()).map(ProcessHandle::isAlive).orElse(false);
            if (alive) {
                return null;
            }
        }
        // PID is dead — just allow execution. markRunning() will overwrite the stale RUNNING state.
        return true;
    }

    private String statusRejectionMessage(NodeInstanceDocument node) {
        return switch (node.status()) {
            case RUNNING -> {
                var pid = node.result().get("running_pid");
                yield "node " + node.nodeDefinitionId() + " is RUNNING (PID=" + pid + "), agent process still alive";
            }
            case FAILED -> {
                var recoverable = node.result().get("acpx_failure.recoverable");
                if (Boolean.TRUE.equals(recoverable)) {
                    var reasonValue = node.result().getOrDefault("acpx_failure.reason", "");
                    var reason = reasonValue instanceof String s ? s : String.valueOf(reasonValue);
                    var policy = RetryStrategies.forReason(reason);
                    var count = node.result().getOrDefault("auto_retry_count", 0);
                    var countInt = (count instanceof Number n) ? n.intValue() : 0;
                    yield "node " + node.nodeDefinitionId() + " FAILED, auto-retry limit reached (" + countInt + "/" + policy.maxRetries() + " for " + reason + "), use dualctl retry";
                }
                yield "node " + node.nodeDefinitionId() + " FAILED (not recoverable), use dualctl retry";
            }
            case BLOCKED -> "node " + node.nodeDefinitionId() + " BLOCKED (revision limit), use dualctl retry or recovery abort";
            case WAITING -> "node " + node.nodeDefinitionId() + " is WAITING, not executable via resume";
            case COMPLETED -> "node " + node.nodeDefinitionId() + " already COMPLETED, should not be active";
            case CREATED, SKIPPED -> "node " + node.nodeDefinitionId() + " has illegal status: " + node.status();
            default -> "unexpected node status: " + node.status();
        };
    }
}
