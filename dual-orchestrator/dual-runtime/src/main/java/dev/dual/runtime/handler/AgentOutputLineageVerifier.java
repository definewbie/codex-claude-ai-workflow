package dev.dual.runtime.handler;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeExecutionStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;

import java.nio.file.Path;
import java.util.Optional;

/** Binds a projected agent-result sidecar to its authoritative ledger occurrence. */
final class AgentOutputLineageVerifier {
    private AgentOutputLineageVerifier() {
    }

    static Optional<String> violation(
            Path workspaceRoot,
            String workstreamId,
            Path sidecarPath,
            AgentOutputContractDocument document
    ) {
        if (!document.hasValidatedLineage(workstreamId, workspaceRoot, sidecarPath)) {
            return Optional.of("sidecar fields or source path are not internally consistent");
        }
        try {
            var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
            var ledger = new WorkflowFileLedger();
            var node = ledger.loadNode(paths, document.nodeInstanceId());
            if (!document.nodeId().equals(node.nodeDefinitionId())) {
                return Optional.of("sidecar node_id does not match the ledger node occurrence");
            }
            if (node.status() != NodeStatus.COMPLETED) {
                return Optional.of("sidecar node occurrence is not completed");
            }
            var sidecarRef = relativeRef(workspaceRoot, sidecarPath);
            if (!containsRef(node.outputRefs(), document.sourceOutputRef())
                    || !containsRef(node.outputRefs(), sidecarRef)) {
                return Optional.of("sidecar source is not published by the ledger node occurrence");
            }
            if (!document.runId().equals(text(node.result().get("run_id")))) {
                return Optional.of("sidecar run_id does not match the ledger node result");
            }
            var execution = ledger.latestNodeExecution(paths, node);
            if (execution.isEmpty()) {
                return Optional.of("sidecar node occurrence has no execution fact");
            }
            var actual = execution.get();
            if (actual.status() != NodeExecutionStatus.SUCCEEDED
                    || !document.nodeInstanceId().equals(actual.nodeInstanceId())
                    || !document.nodeId().equals(actual.nodeDefinitionId())
                    || !document.runId().equals(actual.runRef())) {
                return Optional.of("sidecar lineage does not match the successful node execution");
            }
            if (!containsRef(actual.outputRefs(), document.sourceOutputRef())
                    || !containsRef(actual.outputRefs(), sidecarRef)) {
                return Optional.of("sidecar source is not published by the node execution");
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of("sidecar ledger occurrence cannot be resolved: " + e.getMessage());
        }
    }

    private static String relativeRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString();
    }

    private static boolean containsRef(java.util.List<String> refs, String expected) {
        var normalized = normalize(expected);
        return refs.stream().map(AgentOutputLineageVerifier::normalize).anyMatch(normalized::equals);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
