package dev.dual.flow.liteflow;

import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.flow.FlowOrchestrationKernel;

/**
 * 统一选择工作流编排内核。
 *
 * <p>默认使用 LiteFlow 作为 routing kernel；保留 built-in 作为诊断和回退入口。
 * 这里返回的是抽象接口，调用方不应该越过 {@link FlowOrchestrationKernel}
 * 直接依赖 LiteFlow 的实现细节。</p>
 */
public final class FlowKernelProvider {
    public static final String LITEFLOW = "liteflow";
    public static final String BUILTIN = "builtin";

    private FlowKernelProvider() {
    }

    public static FlowOrchestrationKernel create(String configuredKernel) {
        var kernel = normalize(configuredKernel);
        return switch (kernel) {
            case LITEFLOW -> new LiteFlowPrototypeKernel();
            case BUILTIN -> new BuiltInFlowOrchestrationKernel();
            default -> throw new IllegalArgumentException(
                    "unsupported flow kernel: " + configuredKernel + ". Supported values: liteflow, builtin"
            );
        };
    }

    public static String normalize(String configuredKernel) {
        if (configuredKernel == null || configuredKernel.isBlank()) {
            return LITEFLOW;
        }
        return configuredKernel.trim().toLowerCase();
    }
}
