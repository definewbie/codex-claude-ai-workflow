package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;

import java.nio.file.Path;

public interface GateHandler {
    String type();

    NodeResult openOrInspect(GateContext context) throws Exception;

    record GateContext(
            Path workspaceRoot,
            String workstreamId,
            NodeDefinition definition,
            NodeInstance instance,
            String sourceOutcome,
            dev.dual.ledger.file.WorkflowFileLedger ledger
    ) {
        public GateContext {
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
            if (sourceOutcome == null || sourceOutcome.isBlank()) {
                throw new IllegalArgumentException("sourceOutcome is required");
            }
        }
    }
}
