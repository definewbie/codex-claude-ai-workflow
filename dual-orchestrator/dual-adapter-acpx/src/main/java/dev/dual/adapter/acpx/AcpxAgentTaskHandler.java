package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.runtime.handler.TaskHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final AgentFinalArtifactExtractor finalArtifactExtractor;

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
        this.finalArtifactExtractor = new AgentFinalArtifactExtractor();
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
        var configuredSession = configText(context, "session");
        var promptTemplate = configText(context, "prompt_template");
        var runId = "run-" + Instant.now().toEpochMilli();
        var session = configuredSession + "-" + runId;
        var runDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("runs")
                .resolve(runId);
        var promptPath = runDir.resolve("prompts").resolve(promptTemplate + ".md");
        var outputPath = runDir.resolve("outputs").resolve(promptTemplate + ".md");
        var transcriptPath = runDir.resolve("outputs").resolve(promptTemplate + ".transcript.log");
        var stderrPath = runDir.resolve("outputs").resolve(promptTemplate + ".stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(outputPath.getParent());
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
                    outputPath,
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
                transcriptPath,
                stderrPath,
                permissionProfile(context)
        );
        var result = "claude".equals(agent)
                ? transport.runClaudeDesign(request)
                : transport.runCodexDesignReview(request);
        var usage = usageReader.read(promptPath, stderrPath).orElse(null);
        var finalArtifact = finalArtifactExtractor.extract(readStringIfExists(transcriptPath));
        if (finalArtifact.isPresent()) {
            Files.writeString(outputPath, finalArtifact.get(), StandardCharsets.UTF_8);
        }
        var failure = failureClassifier.classify(transcriptPath, stderrPath, result.exitCode());
        var outputComplete = completionDetector.isComplete(transcriptPath);
        var interrupted = interruptedOutputFailure(result, outputComplete);
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
                outputPath,
                stderrPath,
                transcriptPath,
                result,
                metadataFailure(failure, interrupted, outputComplete),
                usage
        );
        return NodeResult.completed(
                "agent_execution_observed",
                evidenceOutputRefs(context, outputPath, transcriptPath, stderrPath),
                resultFacts(context, runDir, transcriptPath, result, agent, session, runId, failure, interrupted, outputComplete)
        );
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

    private static List<String> evidenceOutputRefs(
            TaskContext context,
            java.nio.file.Path outputPath,
            java.nio.file.Path transcriptPath,
            java.nio.file.Path stderrPath
    ) {
        var refs = new java.util.ArrayList<String>();
        refs.add(context.workspaceRoot().relativize(stderrPath).toString());
        refs.add(context.workspaceRoot().relativize(transcriptPath).toString());
        if (Files.exists(outputPath)) {
            refs.add(context.workspaceRoot().relativize(outputPath).toString());
        }
        return List.copyOf(refs);
    }

    private static Map<String, Object> resultFacts(
            TaskContext context,
            java.nio.file.Path runDir,
            java.nio.file.Path transcriptPath,
            AcpxRunResult result,
            String agent,
            String session,
            String runId,
            AcpxFailureClassifier.AcpxFailure failure,
            AcpxFailureClassifier.AcpxFailure interrupted,
            boolean outputComplete
    ) {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_id", runId);
        facts.put("run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString());
        facts.put("agent", agent);
        facts.put("session", session);
        facts.put("exit_code", result.exitCode());
        facts.put("transcript_ref", context.workspaceRoot().relativize(transcriptPath).toString());
        facts.put("output_complete", outputComplete);
        facts.put("process_usability", processUsability(result, failure, interrupted, outputComplete).name());
        if (interrupted != null) {
            facts.put("acpx_failure.reason", interrupted.reason());
            facts.put("acpx_failure.recoverable", interrupted.recoverable());
        } else if (failure.recoverable()) {
            facts.put("acpx_failure.reason", failure.reason());
            facts.put("acpx_failure.recoverable", true);
        }
        return facts;
    }

    private static AcpxFailureClassifier.AcpxFailure metadataFailure(
            AcpxFailureClassifier.AcpxFailure failure,
            AcpxFailureClassifier.AcpxFailure interrupted,
            boolean outputComplete
    ) {
        if (interrupted != null) {
            return interrupted;
        }
        return outputComplete ? null : failure;
    }

    private static ProcessUsability processUsability(
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure,
            AcpxFailureClassifier.AcpxFailure interrupted,
            boolean outputComplete
    ) {
        if (interrupted != null) {
            return ProcessUsability.INTERRUPTED;
        }
        if (result.exitCode() == 0) {
            return ProcessUsability.EXITED_ZERO;
        }
        if (outputComplete) {
            return ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT;
        }
        if (failure.recoverable()) {
            return ProcessUsability.KILLED_AFTER_RECOVERABLE_SIGNAL;
        }
        return ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT;
    }

    private static String readStringIfExists(java.nio.file.Path path) throws java.io.IOException {
        if (!Files.exists(path)) {
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static AcpxFailureClassifier.AcpxFailure interruptedOutputFailure(AcpxRunResult result, boolean outputComplete) {
        if (outputComplete || !result.reachedDeclaredTimeout(java.time.Duration.ofSeconds(5))) {
            return null;
        }
        return new AcpxFailureClassifier.AcpxFailure(
                AcpxFailureReason.AGENT_OUTPUT_INTERRUPTED.code(),
                "agent output ended before the terminal contract after reaching the acpx timeout",
                true
        );
    }
}
