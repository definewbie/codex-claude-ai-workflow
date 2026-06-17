package dev.dual.server;

import dev.dual.adapter.acpx.AcpxWorkflowHandlerRegistryFactory;
import dev.dual.runtime.WorkflowRuntime;
import org.springframework.stereotype.Component;

@Component
class ServerWorkflowRuntimeFactory {
    WorkflowResumer create() {
        var runtime = new WorkflowRuntime(ServerLedgerFactory.create(), AcpxWorkflowHandlerRegistryFactory.create());
        return runtime::resume;
    }
}
