package dev.dual.server;

import dev.dual.flow.liteflow.FlowKernelProvider;
import dev.dual.ledger.file.WorkflowFileLedger;

final class ServerLedgerFactory {
    private static final String FLOW_KERNEL_PROPERTY = "dual.flow.kernel";
    private static final String FLOW_KERNEL_ENV = "DUAL_FLOW_KERNEL";

    private ServerLedgerFactory() {
    }

    static WorkflowFileLedger create() {
        var configured = configuredKernel();
        var kernel = FlowKernelProvider.create(configured);
        var identity = dev.dual.runtime.FrozenRuntimeIdentity.capture(DualServerApplication.class, configured,
                FlowKernelProvider.normalize(configured),
                dev.dual.adapter.acpx.ReviewedAcpxBuildIdentity.runtimeBehavior());
        return new WorkflowFileLedger(kernel, identity);
    }

    private static String configuredKernel() {
        var propertyValue = System.getProperty(FLOW_KERNEL_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        var envValue = System.getenv(FLOW_KERNEL_ENV);
        return envValue == null ? "" : envValue;
    }
}
