package dev.dual.cli;

import dev.dual.adapter.acpx.AcpxWorkflowHandlerRegistryFactory;
import dev.dual.runtime.WorkflowRuntime;

final class CliWorkflowRuntimeFactory {
    WorkflowRuntime create() {
        return new WorkflowRuntime(CliLedgerFactory.create(), AcpxWorkflowHandlerRegistryFactory.create());
    }
}
