package dev.dual.runtime;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;

import java.util.Map;

/**
 * Explains why an active node is not executable by resume.
 *
 * This is a runtime guard/projection boundary: it reads typed node state and
 * retry policy facts, then produces a stable operator message. It does not
 * mutate ledger state and does not decide workflow routing.
 */
final class NodeExecutionGuard {
    private NodeExecutionGuard() {
    }

    static NodeExecutionRejection reject(NodeInstanceDocument node, NodeDefinition definition) {
        return new NodeExecutionRejection(switch (node.status()) {
            case RUNNING -> runningMessage(node);
            case FAILED -> failedMessage(node, definition.config());
            case BLOCKED -> "node " + node.nodeDefinitionId() + " BLOCKED (revision limit), use dualctl retry or recovery abort";
            case WAITING -> "node " + node.nodeDefinitionId() + " is WAITING, not executable via resume";
            case COMPLETED -> "node " + node.nodeDefinitionId() + " already COMPLETED, should not be active";
            case CREATED, SKIPPED -> "node " + node.nodeDefinitionId() + " has illegal status: " + node.status();
            default -> "unexpected node status: " + node.status();
        });
    }

    private static String runningMessage(NodeInstanceDocument node) {
        var pid = node.result().get("running_pid");
        return "node " + node.nodeDefinitionId() + " is RUNNING (PID=" + pid + "), agent process still alive";
    }

    private static String failedMessage(NodeInstanceDocument node, Map<String, Object> config) {
        var recoverable = firstNonNull(node.result().get("acpx_failure.recoverable"), node.result().get("recoverable"));
        if (!Boolean.TRUE.equals(recoverable)) {
            return "node " + node.nodeDefinitionId() + " FAILED (not recoverable), use dualctl retry";
        }
        var reason = failureReason(node);
        var policy = RetryPolicyResolver.resolve(reason, config);
        var count = node.result().getOrDefault("auto_retry_count", 0);
        var countInt = count instanceof Number n ? n.intValue() : 0;
        return "node " + node.nodeDefinitionId() + " FAILED, auto-retry limit reached ("
                + countInt + "/" + policy.maxRetries() + " for " + reason + "), use dualctl retry";
    }

    private static String failureReason(NodeInstanceDocument node) {
        var reasonValue = node.result().getOrDefault("acpx_failure.reason", "");
        if (String.valueOf(reasonValue).isBlank()) {
            reasonValue = node.result().getOrDefault("reason", "");
        }
        return reasonValue instanceof String s ? s : String.valueOf(reasonValue);
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    record NodeExecutionRejection(String message) {
    }
}
