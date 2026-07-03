package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.runtime.handler.AgentRunGovernanceContext;
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
    private final PromptManifestWriter promptManifestWriter = new PromptManifestWriter();
    private final SessionCompactionPolicy sessionCompactionPolicy = new SessionCompactionPolicy();
    private final SessionCompactionSeedWriter sessionCompactionSeedWriter = new SessionCompactionSeedWriter();

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
        var sessionScopeConfigValue = context.definition().config().get("session_scope");
        var nodeConfigScope = sessionScopeConfigValue instanceof String text ? text.trim() : "";
        var sessionDecision = SessionScopePolicy.resolve(
                agent,
                configuredSession,
                context.workstreamId(),
                context.instance().stageId(),
                context.instance().iterationId(),
                runId,
                nodeConfigScope.isBlank() ? null : nodeConfigScope
        );
        var compactionDecision = sessionCompactionPolicy.decide(
                agent,
                context.definition().id(),
                context.workstreamId(),
                runId,
                configuredSession,
                sessionDecision,
                context.workspaceRoot()
        );
        var session = compactionDecision.enabled() ? compactionDecision.sessionName() : sessionDecision.sessionName();
        var sessionMeta = SessionMeta.from(
                sessionDecision,
                context.instance().stageId(),
                context.instance().iterationId()
        );
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
        var seedPath = sessionCompactionSeedWriter.write(
                context.workspaceRoot(),
                context.workstreamId(),
                runDir,
                compactionDecision
        ).orElse(null);
        var inputRefs = new java.util.ArrayList<>(context.instance().inputRefs());
        inputRefs.add(AgentRunGovernanceContext.ensure(context, runId));
        if (seedPath != null) {
            inputRefs.add(context.workspaceRoot().relativize(seedPath).toString());
        }
        var compactionMeta = SessionCompactionMeta.from(compactionDecision, context.workspaceRoot(), seedPath);
        var rendered = promptRenderer.renderWithSections(
                context.workspaceRoot(),
                context.workstreamId(),
                context.definition().id(),
                promptTemplate,
                inputRefs
        );
        Files.writeString(promptPath, rendered.content(), StandardCharsets.UTF_8);
        promptManifestWriter.write(
                PromptManifestWriter.manifestPathFor(promptPath),
                context.workstreamId(),
                runId,
                context.definition().id(),
                agent,
                promptTemplate,
                promptPath,
                rendered.sections()
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
                    transcriptPath,
                    stderrPath,
                    sessionMeta,
                    compactionMeta
            );
            return NodeResult.completed("completed", List.of(context.workspaceRoot().relativize(promptPath).toString()), Map.of(
                    "run_id", runId,
                    "run_metadata", context.workspaceRoot().relativize(runDir.resolve("run.json")).toString(),
                    "agent", agent,
                    "session", session,
                    "prepared", true
            ));
        }

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
                transcriptPath,
                stderrPath,
                sessionMeta,
                compactionMeta
        );
        var request = new AcpxRunRequest(
                context.workspaceRoot(),
                session,
                promptPath,
                transcriptPath,
                stderrPath,
                permissionProfile(context),
                event -> metadataWriter.appendExecutionEvent(runDir.resolve("run.json"), event)
        );
        try {
            var result = "claude".equals(agent)
                    ? transport.runClaudeDesign(request)
                    : transport.runCodexDesignReview(request);
            var usage = result.usage() != null
                    ? result.usage()
                    : usageReader.read(promptPath, stderrPath).orElse(null);
            var transcriptText = readStringIfExists(transcriptPath);
            var finalArtifact = finalArtifactExtractor.extract(transcriptText);
            var resultWithArtifactTimeline = result;
            if (finalArtifact.isPresent()) {
                Files.writeString(outputPath, finalArtifact.get(), StandardCharsets.UTF_8);
                resultWithArtifactTimeline = appendTimelineEvent(
                        result,
                        AgentExecutionTimelineEventKind.ARTIFACT_EXTRACTED,
                        "adapter"
                );
            }
                var failure = failureClassifier.classify(transcriptPath, stderrPath, result.exitCode());
            var outputComplete = completionDetector.isComplete(transcriptPath);
            var interrupted = interruptedOutputFailure(resultWithArtifactTimeline, outputComplete);
            var completion = metadataWriter.writeCompleted(
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
                    resultWithArtifactTimeline,
                    metadataFailure(failure, interrupted, outputComplete),
                    usage,
                    sessionMeta,
                    compactionMeta
            );
            if (completion.explorationPolicyFailed()) {
                return new NodeResult(
                        "EXPLORATION_POLICY_VIOLATION",
                        dev.dual.core.workflow.NodeStatus.FAILED,
                        evidenceOutputRefs(context, outputPath, transcriptPath, stderrPath),
                        explorationPolicyFailureFacts(
                                context,
                                runDir,
                                transcriptPath,
                                resultWithArtifactTimeline,
                                agent,
                                session,
                                runId,
                                completion
                        ),
                        "agent exploration policy failed"
                );
            }
            return NodeResult.completed(
                    "completed",
                    evidenceOutputRefs(context, outputPath, transcriptPath, stderrPath),
                    resultFacts(
                            context,
                            runDir,
                            transcriptPath,
                            resultWithArtifactTimeline,
                            agent,
                            session,
                            runId,
                            metadataFailure(failure, interrupted, outputComplete),
                            interrupted,
                            outputComplete
                    )
            );
        } catch (Exception failure) {
            return transportFailureResult(
                    context,
                    runDir,
                    promptPath,
                    outputPath,
                    stderrPath,
                    transcriptPath,
                    agent,
                    session,
                    promptTemplate,
                    runId,
                    sessionMeta,
                    compactionMeta,
                    failure
            );
        }
    }

    private NodeResult transportFailureResult(
            TaskContext context,
            java.nio.file.Path runDir,
            java.nio.file.Path promptPath,
            java.nio.file.Path outputPath,
            java.nio.file.Path stderrPath,
            java.nio.file.Path transcriptPath,
            String agent,
            String session,
            String promptTemplate,
            String runId,
            SessionMeta sessionMeta,
            SessionCompactionMeta compactionMeta,
            Exception cause
    ) throws java.io.IOException {
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        Files.createDirectories(stderrPath.getParent());
        Files.writeString(stderrPath, "acpx transport failed: " + cause.getMessage() + "\n", StandardCharsets.UTF_8);
        if (!Files.exists(transcriptPath)) {
            Files.writeString(transcriptPath, "", StandardCharsets.UTF_8);
        }
        var now = Instant.now();
        var result = new AcpxRunResult(1, now, now, List.of("acpx", agent, "transport"));
        var failure = new AcpxFailureClassifier.AcpxFailure(
                AcpxFailureReason.TRANSPORT_FAILED.code(),
                "acpx transport failed: " + cause.getMessage(),
                true,
                false
        );
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
                failure,
                null,
                sessionMeta,
                compactionMeta
        );
        return NodeResult.completed(
                "completed",
                evidenceOutputRefs(context, outputPath, transcriptPath, stderrPath),
                resultFacts(
                        context,
                        runDir,
                        transcriptPath,
                        result,
                        agent,
                        session,
                        runId,
                        failure,
                        null,
                        false
                )
        );
    }

    private static Map<String, Object> explorationPolicyFailureFacts(
            TaskContext context,
            java.nio.file.Path runDir,
            java.nio.file.Path transcriptPath,
            AcpxRunResult result,
            String agent,
            String session,
            String runId,
            AgentRunMetadataWriter.CompletionWriteResult completion
    ) {
        var facts = resultFacts(
                context,
                runDir,
                transcriptPath,
                result,
                agent,
                session,
                runId,
                null,
                null,
                true
        );
        facts.put("harness_failure.reason", "EXPLORATION_POLICY_VIOLATION");
        facts.put("harness_failure.recoverable", false);
        facts.put("exploration_policy_result", completion.explorationPolicyResult());
        facts.put("exploration_policy_result_path", completion.explorationPolicyResultPath());
        return facts;
    }

    private static AcpxRunResult appendTimelineEvent(
            AcpxRunResult result,
            AgentExecutionTimelineEventKind kind,
            String source
    ) {
        var timeline = new java.util.ArrayList<>(result.executionTimeline());
        timeline.add(AcpxExecutionEvent.now(kind, source, timeline.size() + 1L, result.startedAt()));
        return new AcpxRunResult(
                result.exitCode(),
                result.startedAt(),
                result.finishedAt(),
                result.command(),
                timeline,
                result.usage()
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
        } else if (failure != null && failure.recoverable()) {
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
        if (failure != null && failure.recoverable()) {
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
