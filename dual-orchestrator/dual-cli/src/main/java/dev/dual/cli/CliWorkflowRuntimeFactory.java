package dev.dual.cli;

import dev.dual.adapter.acpx.AcpxWorkflowHandlerRegistryFactory;
import dev.dual.runtime.AgentTaskDispatchMode;
import dev.dual.runtime.WorkflowRuntime;

final class CliWorkflowRuntimeFactory {
    WorkflowRuntime create() {
        return create(AgentTaskDispatchMode.INLINE);
    }

    WorkflowRuntime create(AgentTaskDispatchMode dispatchMode) {
        return new WorkflowRuntime(CliLedgerFactory.create(), AcpxWorkflowHandlerRegistryFactory.create(), dispatchMode);
    }
}
