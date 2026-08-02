package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.runtime.handler.AgentRunGovernanceContext;
import dev.dual.runtime.handler.AgentResultPromptContract;
import dev.dual.runtime.handler.ContractRegistry;
import dev.dual.runtime.handler.TaskHandler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcpxAgentTaskHandler implements TaskHandler {
    private final AcpxAgentResultAcquirer resultAcquirer;
    private final boolean prepareOnly;
    private final AgentPromptRenderer promptRenderer;
    private final AgentRunMetadataWriter metadataWriter;
    private final AcpxUsageReader usageReader;
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
        this.resultAcquirer = new AcpxAgentResultAcquirer(transport);
        this.prepareOnly = prepareOnly;
        this.promptRenderer = new AgentPromptRenderer();
        this.metadataWriter = new AgentRunMetadataWriter();
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
        var outputPath = runDir.resolve("outputs").resolve(promptTemplate + ".json");
        var transcriptPath = runDir.resolve("outputs").resolve(promptTemplate + ".transcript.log");
        var stderrPath = runDir.resolve("outputs").resolve(promptTemplate + ".stderr.log");
        var initialOutputPath = runDir.resolve("outputs").resolve(promptTemplate + ".attempt-1.json");
        var correctionPromptPath = runDir.resolve("prompts").resolve(promptTemplate + ".contract-correction.md");
        var correctionOutputPath = runDir.resolve("outputs").resolve(promptTemplate + ".contract-correction.json");
        var correctionTranscriptPath = runDir.resolve("outputs").resolve(promptTemplate + ".contract-correction.transcript.log");
        var correctionStderrPath = runDir.resolve("outputs").resolve(promptTemplate + ".contract-correction.stderr.log");
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
                runId,
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
        var correctionRequest = new AcpxRunRequest(
                context.workspaceRoot(),
                session,
                correctionPromptPath,
                correctionTranscriptPath,
                correctionStderrPath,
                AcpxPermissionProfile.contractCorrection(),
                event -> metadataWriter.appendExecutionEvent(runDir.resolve("run.json"), event)
        );
        var authoritySnapshot = ChangeBoundaryAuthoritySnapshot.capture(
                context.workspaceRoot(),
                context.workstreamId(),
                request.permissionProfile().approveAll()
        );
        try {
            var acquisition = resultAcquirer.acquire(
                    agent,
                    request,
                    correctionRequest,
                    correctionPrompt(context, runId),
                    context.workstreamId(),
                    runId,
                    ContractRegistry.hasContract(context.definition().id())
                            ? ContractRegistry.forNode(context.definition().id())
                            : null
            );
            var finalResult = acquisition.finalResult();
            var runTimeline = new ArrayList<>(acquisition.executionTimeline());
            var initialUsage = usageFor(agent, acquisition.initialResult(), promptPath, stderrPath);
            var correctionUsage = acquisition.correctionAttempted()
                    ? usageFor(agent, acquisition.correctionResult(), correctionPromptPath, correctionStderrPath)
                    : null;
            var usage = acquisition.correctionAttempted()
                    ? AcpxUsage.aggregatePromptOccurrences(initialUsage, correctionUsage)
                    : initialUsage;
            var finalArtifact = finalResult.promptCompletion().finalAssistantMessage();
            if (acquisition.correctionAttempted()) {
                Files.writeString(
                        initialOutputPath,
                        acquisition.initialResult().promptCompletion().finalAssistantMessage(),
                        StandardCharsets.UTF_8
                );
                Files.writeString(correctionOutputPath, finalArtifact, StandardCharsets.UTF_8);
            }
            if (finalResult.promptCompletion().completed()) {
                Files.writeString(outputPath, finalArtifact, StandardCharsets.UTF_8);
                appendTimelineEvent(
                        runTimeline,
                        finalResult.startedAt(),
                        AgentExecutionTimelineEventKind.ARTIFACT_EXTRACTED,
                        "adapter"
                );
            }
            var effectiveTranscriptPath = acquisition.correctionAttempted() ? correctionTranscriptPath : transcriptPath;
            var effectiveStderrPath = acquisition.correctionAttempted() ? correctionStderrPath : stderrPath;
            var outputComplete = finalResult.promptCompletion().completed();
            var acquisitionDocument = acquisition.toDocument(
                    context.workspaceRoot(),
                    acquisition.correctionAttempted() ? initialOutputPath : outputPath,
                    correctionOutputPath,
                    initialUsage,
                    correctionUsage
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
                    effectiveStderrPath,
                    transcriptPath,
                    finalResult,
                    runTimeline,
                    usage,
                    sessionMeta,
                    compactionMeta,
                    acquisitionDocument
            );
            return authoritySnapshot.enforce(NodeResult.completed(
                    "completed",
                    evidenceOutputRefs(context, outputPath, transcriptPath, stderrPath),
                    resultFacts(
                            context,
                            runDir,
                            transcriptPath,
                            finalResult,
                            agent,
                            session,
                            runId,
                            outputComplete
                    )
            ));
        } catch (Exception failure) {
            var guarded = authoritySnapshot.enforce(new NodeResult(
                    "AGENT_HANDLER_FAILED",
                    NodeStatus.FAILED,
                    List.of(),
                    Map.of(),
                    failure.getMessage()
            ));
            if (Boolean.TRUE.equals(guarded.result().get("change_boundary_authority_tampered"))) {
                return guarded;
            }
            throw failure;
        }
    }

    private record ChangeBoundaryAuthoritySnapshot(
            Path workspaceRoot,
            Map<String, String> fingerprints
    ) {
        static ChangeBoundaryAuthoritySnapshot capture(
                Path workspaceRoot,
                String workstreamId,
                boolean protect
        ) throws Exception {
            var root = workspaceRoot.toAbsolutePath().normalize();
            if (!protect) {
                return new ChangeBoundaryAuthoritySnapshot(root, Map.of());
            }
            var workstreamDir = root.resolve(".agent/workstreams").resolve(workstreamId).normalize();
            var protectedPaths = new ArrayList<Path>();
            protectedPaths.add(workstreamDir.resolve("artifacts/task-contract.json"));
            protectedPaths.add(workstreamDir.resolve("workstream.json"));
            var workspacesDir = workstreamDir.resolve("workspaces");
            protectedPaths.add(workspacesDir);
            if (Files.isDirectory(workspacesDir)) {
                try (var files = Files.list(workspacesDir)) {
                    files.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .forEach(protectedPaths::add);
                }
            }
            var fingerprints = new LinkedHashMap<String, String>();
            for (var path : protectedPaths) {
                fingerprints.put(root.relativize(path).toString(), fingerprint(path));
            }
            return new ChangeBoundaryAuthoritySnapshot(root, Map.copyOf(fingerprints));
        }

        NodeResult enforce(NodeResult result) {
            if (fingerprints.isEmpty()) {
                return result;
            }
            var changed = fingerprints.entrySet().stream()
                    .filter(entry -> !entry.getValue().equals(currentFingerprint(workspaceRoot.resolve(entry.getKey()))))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            if (changed.isEmpty()) {
                return result;
            }
            var facts = new LinkedHashMap<String, Object>(result.result());
            facts.put("change_boundary_authority_tampered", true);
            facts.put("changed_control_facts", changed);
            return new NodeResult(
                    "CHANGE_BOUNDARY_AUTHORITY_TAMPERED",
                    NodeStatus.FAILED,
                    result.outputRefs(),
                    facts,
                    "implementation agent modified protected change-boundary facts: " + String.join(", ", changed)
            );
        }

        private static String fingerprint(Path path) throws Exception {
            if (Files.isDirectory(path)) {
                var digest = MessageDigest.getInstance("SHA-256");
                try (var files = Files.walk(path)) {
                    for (var file : files.filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString))
                            .toList()) {
                        digest.update(path.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(Files.readAllBytes(file));
                    }
                }
                return HexFormat.of().formatHex(digest.digest());
            }
            if (!Files.isRegularFile(path)) {
                return "<missing>";
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        }

        private static String currentFingerprint(Path path) {
            try {
                return fingerprint(path);
            } catch (Exception ignored) {
                return "<unreadable>";
            }
        }
    }

    private AcpxUsage usageFor(String agent, AcpxRunResult result, java.nio.file.Path promptPath, java.nio.file.Path stderrPath) {
        return result.usage() != null
                ? result.usage()
                : usageReader.read(agent, promptPath, stderrPath).orElse(null);
    }

    private static String correctionPrompt(TaskContext context, String runId) {
        if (!ContractRegistry.hasContract(context.definition().id())) {
            return "";
        }
        var contract = ContractRegistry.forNode(context.definition().id());
        var decisionExample = contract.allowedDecisions().stream().sorted().findFirst().orElse("");
        return """
                Your preceding final assistant message failed strict AgentResultEnvelope validation.
                Re-serialize that same result without changing its decision, evidence, or conclusion.
                Do not re-run the task, use tools, explain, or use Markdown.
                This is the only contract-correction attempt. Return exactly the JSON object required below.

                %s
                """.formatted(AgentResultPromptContract.render(
                context.workstreamId(),
                runId,
                context.definition().id(),
                decisionExample,
                contract.allowedDecisions()
        )).stripTrailing();
    }

    private static void appendTimelineEvent(
            List<AcpxExecutionEvent> timeline,
            Instant startedAt,
            AgentExecutionTimelineEventKind kind,
            String source
    ) {
        timeline.add(AcpxExecutionEvent.now(kind, source, timeline.size() + 1L, startedAt));
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
        facts.put("process_usability", result.processResult().usability().name());
        facts.put("adapter_signals", result.adapterSignals().stream()
                .map(AcpxAgentTaskHandler::adapterSignalFact)
                .toList());
        return facts;
    }

    private static Map<String, Object> adapterSignalFact(AdapterSignal signal) {
        var fact = new LinkedHashMap<String, Object>();
        fact.put("type", signal.type().name());
        fact.put("message", signal.message());
        fact.put("observedSequence", signal.observedSequence());
        fact.put("source", signal.source());
        fact.put("sessionId", signal.sessionId());
        fact.put("superseded", signal.superseded());
        return Map.copyOf(fact);
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }
}
