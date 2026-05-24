package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.runtime.handler.TaskHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AcpxAgentTaskHandler implements TaskHandler {
    private final AcpxAgentTransport transport;
    private final boolean prepareOnly;
    private final AgentPromptRenderer promptRenderer;
    private final AcpxFailureClassifier failureClassifier;

    public AcpxAgentTaskHandler(AcpxAgentTransport transport, boolean prepareOnly) {
        if (transport == null) {
            throw new IllegalArgumentException("transport is required");
        }
        this.transport = transport;
        this.prepareOnly = prepareOnly;
        this.promptRenderer = new AgentPromptRenderer();
        this.failureClassifier = new AcpxFailureClassifier();
    }

    @Override
    public String type() {
        return "acpx.agent_run";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var agent = configText(context, "agent");
        if (!"claude".equals(agent) && !"codex".equals(agent)) {
            throw new IllegalArgumentException("unsupported agent: " + agent);
        }
        var session = configText(context, "session");
        var promptTemplate = configText(context, "prompt_template");
        var runId = "run-" + Instant.now().toEpochMilli();
        var runDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("runs")
                .resolve(runId);
        var promptPath = runDir.resolve("prompts").resolve(promptTemplate + ".md");
        var stdoutPath = runDir.resolve("outputs").resolve(promptTemplate + ".md");
        var stderrPath = runDir.resolve("outputs").resolve(promptTemplate + ".stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(stdoutPath.getParent());
        Files.writeString(
                promptPath,
                promptRenderer.render(context.workspaceRoot(), context.workstreamId(), context.definition().id(), promptTemplate),
                StandardCharsets.UTF_8
        );

        if (prepareOnly) {
            return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(promptPath).toString()), Map.of(
                    "run_id", runId,
                    "agent", agent,
                    "session", session,
                    "prepared", true
            ));
        }

        var request = new AcpxRunRequest(context.workspaceRoot(), session, promptPath, stdoutPath, stderrPath);
        var result = "claude".equals(agent)
                ? transport.runClaudeDesign(request)
                : transport.runCodexDesignReview(request);
        if (result.exitCode() != 0) {
            var failure = failureClassifier.classify(stderrPath, result.exitCode());
            return new NodeResult(
                    failure.reason(),
                    NodeStatus.FAILED,
                    List.of(context.workspaceRoot().relativize(stderrPath).toString()),
                    Map.of(
                            "run_id", runId,
                            "exit_code", result.exitCode(),
                            "agent", agent,
                            "session", session,
                            "failure_reason", failure.reason(),
                            "recoverable", failure.recoverable()
                    ),
                    failure.message()
            );
        }
        return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(stdoutPath).toString()), Map.of(
                "run_id", runId,
                "agent", agent,
                "session", session,
                "exit_code", result.exitCode()
        ));
    }

    private static String configText(TaskContext context, String key) {
        var value = context.definition().config().get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("config " + key + " is required for " + context.definition().id());
        }
        return text;
    }

}
