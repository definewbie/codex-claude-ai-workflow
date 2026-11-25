package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.ArtifactValidateHandler;
import dev.dual.runtime.handler.ContextBuildPackageHandler;
import dev.dual.runtime.handler.ContractCompileHandler;
import dev.dual.runtime.handler.DeliveryPrepareHandler;
import dev.dual.runtime.handler.FailureClassifyHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.HumanReviewGateHandler;
import dev.dual.runtime.handler.PromptGenerateNextStageHandler;
import dev.dual.runtime.handler.ReportGenerateFinalHandler;
import dev.dual.runtime.handler.ReviewDecisionHandler;

import java.util.List;

/**
 * 内置 acpx 生命周期流程的 handler 能力目录。
 *
 * Workflow JSON 是流程定义的唯一来源；这个类只声明运行这些节点所需的
 * handler 能力，避免 CLI 和 Server 各自维护一份容易漂移的列表。
 */
public final class AcpxWorkflowHandlerRegistryFactory {
    private AcpxWorkflowHandlerRegistryFactory() {
    }

    public static HandlerRegistry create() {
        return new HandlerRegistry(
                List.of(
                        new AcpxAgentTaskHandler(new AcpxAgentTransport(), false),
                        new ArtifactValidateHandler(),
                        new ContextBuildPackageHandler(),
                        new ContractCompileHandler(),
                        new FailureClassifyHandler(),
                        new DeliveryPrepareHandler(),
                        new PromptGenerateNextStageHandler(),
                        new ReportGenerateFinalHandler()
                ),
                List.of(new ReviewDecisionHandler()),
                List.of(new HumanReviewGateHandler())
        );
    }
}
