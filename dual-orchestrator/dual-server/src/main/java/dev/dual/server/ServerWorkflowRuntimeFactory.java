package dev.dual.server;

import dev.dual.adapter.acpx.AcpxAgentTaskHandler;
import dev.dual.adapter.acpx.AcpxAgentTransport;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.runtime.WorkflowRuntime;
import dev.dual.runtime.handler.ArtifactValidateHandler;
import dev.dual.runtime.handler.ContextBuildPackageHandler;
import dev.dual.runtime.handler.ContractCompileHandler;
import dev.dual.runtime.handler.DeliveryCommitHandler;
import dev.dual.runtime.handler.DeliveryPrepareHandler;
import dev.dual.runtime.handler.DeliveryPrCreateHandler;
import dev.dual.runtime.handler.FailureClassifyHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.HumanReviewGateHandler;
import dev.dual.runtime.handler.PromptGenerateNextStageHandler;
import dev.dual.runtime.handler.ReportGenerateFinalHandler;
import dev.dual.runtime.handler.ReviewDecisionHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class ServerWorkflowRuntimeFactory {
    WorkflowResumer create() {
        var registry = new HandlerRegistry(
                List.of(
                        new AcpxAgentTaskHandler(new AcpxAgentTransport(), false),
                        new ArtifactValidateHandler(),
                        new ContextBuildPackageHandler(),
                        new ContractCompileHandler(),
                        new DeliveryCommitHandler(),
                        new FailureClassifyHandler(),
                        new DeliveryPrepareHandler(),
                        new DeliveryPrCreateHandler(),
                        new PromptGenerateNextStageHandler(),
                        new ReportGenerateFinalHandler()
                ),
                List.of(new ReviewDecisionHandler()),
                List.of(new HumanReviewGateHandler())
        );
        var runtime = new WorkflowRuntime(new WorkflowFileLedger(), registry);
        return runtime::resume;
    }
}
