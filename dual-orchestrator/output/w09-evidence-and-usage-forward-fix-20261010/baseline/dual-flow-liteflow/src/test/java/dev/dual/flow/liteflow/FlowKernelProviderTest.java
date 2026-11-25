package dev.dual.flow.liteflow;

import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowKernelProviderTest {
    @Test
    void defaultsToLiteFlowKernel() {
        assertEquals(FlowKernelProvider.LITEFLOW, FlowKernelProvider.normalize(null));
        assertEquals(FlowKernelProvider.LITEFLOW, FlowKernelProvider.normalize(""));
        assertInstanceOf(LiteFlowPrototypeKernel.class, FlowKernelProvider.create(null));
    }

    @Test
    void supportsBuiltInFallbackKernel() {
        assertEquals(FlowKernelProvider.BUILTIN, FlowKernelProvider.normalize(" BUILTIN "));
        assertInstanceOf(BuiltInFlowOrchestrationKernel.class, FlowKernelProvider.create("builtin"));
    }

    @Test
    void rejectsUnknownKernelName() {
        var error = assertThrows(IllegalArgumentException.class, () -> FlowKernelProvider.create("unknown"));

        assertEquals(
                "unsupported flow kernel: unknown. Supported values: liteflow, builtin",
                error.getMessage()
        );
    }
}
