package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.runtime.handler.TaskHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcpxAgentTaskHandler implements TaskHandler {
    private final AcpxAgentTransport transport;
    private final boolean prepareOnly;
    private final AgentPromptRenderer promptRenderer;
    private final AcpxFailureClassifier failureClassifier;
    private final AgentRunMetadataWriter metadataWriter;
    private final AgentOutputCompletionDetector completionDetector;
    private final AcpxUsageReader usageReader;

    public AcpxAgentTaskHandler(AcpxAgentTransport transport, boolean prepareOnly) {
        this(transport, prepareOnly, new AcpxUsageReader());
    }

    AcpxAgentTaskHandler(AcpxAgentTransport transport, boolean prepareOnly, AcpxUsageReader usageReader) {
        if (transport == null) {
            throw new IllegalArgumentException("transport is required");
        }
        if (usageReader == null) {
            throw new IllegalArgumentException("usageReader is required");
        }
        this.transport = transport;
        this.prepareOnly = prepareOnly;
        this.promptRenderer = new AgentPromptRenderer();
        this.failureClassifier = new AcpxFailureClassifier();
        this.metadataWriter = new AgentRunMetadataWriter();
        this.completionDetector = new AgentOutputCompletionDetector();
        this.usageReader = usageReader;
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
                promptRenderer.render(
                        context.workspaceRoot(),
                        context.workstreamId(),
                        context.definition().id(),
                        promptTemplate,
                        context.instance().inputRefs()
                ),
                StandardCharsets.UTF_8
        );

        if (prepareOnly) {
            metadataWriter.writePrepared(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    stdoutPath,
                    stderrPath
            );
            return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(promptPath).toString()), Map.of(
                    "run_id", runId,
                    "run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString(),
                    "agent", agent,
                    "session", session,
                    "prepared", true
            ));
        }

        var request = new AcpxRunRequest(
                context.workspaceRoot(),
                session,
                promptPath,
                stdoutPath,
                stderrPath,
                permissionProfile(context)
        );
        var result = "claude".equals(agent)
                ? transport.runClaudeDesign(request)
                : transport.runCodexDesignReview(request);
        var usage = usageReader.read(promptPath, stderrPath).orElse(null);
        var failure = failureClassifier.classify(stdoutPath, stderrPath, result.exitCode());
        var outputComplete = completionDetector.isComplete(stdoutPath);
        var interrupted = interruptedOutputFailure(result, outputComplete);
        if (interrupted != null && !failure.recoverable()) {
            metadataWriter.writeCompleted(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    stdoutPath,
                    stderrPath,
                    result,
                    interrupted,
                    usage
            );
            return failedResult(context, runDir, stdoutPath, stderrPath, result, agent, session, runId, interrupted);
        }
        if (result.exitCode() == 0 && outputComplete) {
            metadataWriter.writeCompleted(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    stdoutPath,
                    stderrPath,
                    result,
                    null,
                    usage
            );
            var completedResult = new LinkedHashMap<String, Object>();
            completedResult.put("run_id", runId);
            completedResult.put("run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString());
            completedResult.put("agent", agent);
            completedResult.put("session", session);
            completedResult.put("exit_code", result.exitCode());
            if (failure.recoverable()) {
                completedResult.put("acpx_recoverable_stderr_ignored", true);
                completedResult.put("acpx_failure.reason", failure.reason());
            }
            return NodeResult.completed(
                    "completed",
                    List.of(context.workspaceRoot().relativize(stdoutPath).toString()),
                    completedResult
            );
        }
        if (failure.recoverable() && outputComplete) {
            metadataWriter.writeCompleted(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    stdoutPath,
                    stderrPath,
                    result,
                    null,
                    usage
            );
            var completedResult = new LinkedHashMap<String, Object>();
            completedResult.put("run_id", runId);
            completedResult.put("run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString());
            completedResult.put("agent", agent);
            completedResult.put("session", session);
            completedResult.put("exit_code", result.exitCode());
            completedResult.put("acpx_recoverable_stderr_ignored", true);
            completedResult.put("acpx_failure.reason", failure.reason());
            return NodeResult.completed(
                    "completed",
                    List.of(context.workspaceRoot().relativize(stdoutPath).toString()),
                    completedResult
            );
        }
        if (failure.recoverable()) {
            metadataWriter.writeCompleted(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    stdoutPath,
                    stderrPath,
                    result,
                    failure,
                    usage
            );
            return failedResult(context, runDir, stdoutPath, stderrPath, result, agent, session, runId, failure);
        }
        if (result.exitCode() != 0) {
            if (outputComplete) {
                metadataWriter.writeCompleted(
                        context.workspaceRoot(),
                        context.workstreamId(),
                        context.definition().id(),
                        agent,
                        session,
                        promptTemplate,
                        runId,
                        runDir,
                        promptPath,
                        stdoutPath,
                        stderrPath,
                        result,
                        null,
                        usage
                );
                return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(stdoutPath).toString()), Map.of(
                        "run_id", runId,
                        "run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString(),
                        "agent", agent,
                        "session", session,
                        "exit_code", result.exitCode(),
                        "acpx_exit_tolerated", true
                ));
            }
            metadataWriter.writeCompleted(
                    context.workspaceRoot(),
                    context.workstreamId(),
                    context.definition().id(),
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    runDir,
                    promptPath,
                    stdoutPath,
                    stderrPath,
                    result,
                    failure,
                    usage
            );
            return failedResult(context, runDir, stdoutPath, stderrPath, result, agent, session, runId, failure);
        }
        metadataWriter.writeCompleted(
                context.workspaceRoot(),
                context.workstreamId(),
                context.definition().id(),
                agent,
                session,
                promptTemplate,
                runId,
                runDir,
                promptPath,
                stdoutPath,
                stderrPath,
                result,
                null,
                usage
        );
        return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(stdoutPath).toString()), Map.of(
                "run_id", runId,
                "run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString(),
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

    private static boolean approveAll(TaskContext context) {
        var value = context.definition().config().get("approve_all");
        return Boolean.TRUE.equals(value);
    }

    private static AcpxPermissionProfile permissionProfile(TaskContext context) {
        var value = context.definition().config().get("permission_profile");
        var profile = value instanceof String text ? text : "";
        return AcpxPermissionProfile.fromName(profile, approveAll(context));
    }

    private static NodeResult failedResult(
            TaskContext context,
            java.nio.file.Path runDir,
            java.nio.file.Path stdoutPath,
            java.nio.file.Path stderrPath,
            AcpxRunResult result,
            String agent,
            String session,
            String runId,
            AcpxFailureClassifier.AcpxFailure failure
    ) {
        return new NodeResult(
                failure.reason(),
                NodeStatus.FAILED,
                List.of(
                        context.workspaceRoot().relativize(stdoutPath).toString(),
                        context.workspaceRoot().relativize(stderrPath).toString()
                ),
                Map.of(
                        "run_id", runId,
                        "run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString(),
                        "exit_code", result.exitCode(),
                        "agent", agent,
                        "session", session,
                        "acpx_failure.reason", failure.reason(),
                        "acpx_failure.recoverable", failure.recoverable()
                ),
                failure.message()
        );
    }

    private static AcpxFailureClassifier.AcpxFailure interruptedOutputFailure(
            AcpxRunResult result,
            boolean outputComplete
    ) {
        if (outputComplete || !result.reachedDeclaredTimeout(Duration.ofSeconds(5))) {
            return null;
        }
        return new AcpxFailureClassifier.AcpxFailure(
                AcpxFailureReason.AGENT_OUTPUT_INTERRUPTED.code(),
                "agent output ended before the terminal contract after reaching the acpx timeout",
                true
        );
    }
}
