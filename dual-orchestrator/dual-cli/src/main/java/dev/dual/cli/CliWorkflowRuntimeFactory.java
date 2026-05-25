package dev.dual.cli;

import dev.dual.adapter.acpx.AcpxAgentTaskHandler;
import dev.dual.adapter.acpx.AcpxAgentTransport;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.runtime.WorkflowRuntime;
import dev.dual.runtime.handler.ArtifactValidateHandler;
import dev.dual.runtime.handler.ContextBuildPackageHandler;
import dev.dual.runtime.handler.HandlerRegistry;
import dev.dual.runtime.handler.HumanReviewGateHandler;
import dev.dual.runtime.handler.PromptGenerateNextStageHandler;
import dev.dual.runtime.handler.ReportGenerateFinalHandler;
import dev.dual.runtime.handler.ReviewDecisionHandler;

import java.util.List;

final class CliWorkflowRuntimeFactory {
    WorkflowRuntime create() {
        var registry = new HandlerRegistry(
                List.of(
                        new AcpxAgentTaskHandler(new AcpxAgentTransport(), false),
                        new ArtifactValidateHandler(),
                        new ContextBuildPackageHandler(),
                        new PromptGenerateNextStageHandler(),
                        new ReportGenerateFinalHandler()
                ),
                List.of(new ReviewDecisionHandler()),
                List.of(new HumanReviewGateHandler())
        );
        return new WorkflowRuntime(new WorkflowFileLedger(), registry);
    }
}
