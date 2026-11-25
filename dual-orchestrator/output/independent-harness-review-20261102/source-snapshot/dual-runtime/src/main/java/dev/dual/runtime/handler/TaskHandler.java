package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;

import java.nio.file.Path;

public interface TaskHandler {
    String type();

    NodeResult execute(TaskContext context) throws Exception;

    /** Pure local planning only: no provider, process, Git mutation or lock acquisition. */
    default boolean localLedgerMutation() { return false; }

    record TaskContext(
            Path workspaceRoot,
            String workstreamId,
            NodeDefinition definition,
            NodeInstance instance,
            dev.dual.ledger.file.WorkflowFileLedger ledger
    ) {
        public TaskContext {
            if (ledger == null) throw new IllegalArgumentException("assembled ledger is required");
            if (workspaceRoot == null) {
                throw new IllegalArgumentException("workspaceRoot is required");
            }
            if (workstreamId == null || workstreamId.isBlank()) {
                throw new IllegalArgumentException("workstreamId is required");
            }
            if (definition == null) {
                throw new IllegalArgumentException("definition is required");
            }
            if (instance == null) {
                throw new IllegalArgumentException("instance is required");
            }
        }
    }
}
