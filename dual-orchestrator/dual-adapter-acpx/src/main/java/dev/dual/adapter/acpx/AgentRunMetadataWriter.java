package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionAttempt;
import dev.dual.core.execution.AgentExecutionAttemptStatus;
import dev.dual.core.execution.AgentExecutionProcessStatus;
import dev.dual.core.execution.ProcessResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class AgentRunMetadataWriter {
    private static final String PROMPT_DEBUG_ENV = "DUAL_AGENT_PROMPT_DEBUG";
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final AcpxTranscriptToolUsageReader toolUsageReader = new AcpxTranscriptToolUsageReader();
    private final ContextGovernanceActualReportWriter governanceActualReportWriter =
            new ContextGovernanceActualReportWriter();
    private final WorkstreamMemoryUpdater workstreamMemoryUpdater = new WorkstreamMemoryUpdater();

    void writePrepared(
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            String agent,
            String session,
            String promptTemplate,
            String runId,
            Path runDir,
            Path promptPath,
            Path stdoutPath,
            Path transcriptPath,
            Path stderrPath,
            SessionMeta sessionMeta,
            SessionCompactionMeta compactionMeta
    ) throws IOException {
        var compaction = compactionMeta == null ? SessionCompactionMeta.off() : compactionMeta;
        var promptMetadataPath = metadataPath(promptPath);
        writePromptDebugIfEnabled(promptPath);
        writeArtifactMetadata(
                workspaceRoot,
                promptMetadataPath,
                artifactDocument(
                        workspaceRoot,
                        workstreamId,
                        nodeId,
                        agent,
                        session,
                        promptTemplate,
                        runId,
                        "prompt",
                        "text/markdown",
                        promptPath
                )
        );
        write(workspaceRoot, runDir, new AgentRunDocument(
                2,
                runId,
                workstreamId,
                nodeId,
                agent,
                session,
                sessionMeta == null ? "" : sessionMeta.scope(),
                sessionMeta == null ? "" : sessionMeta.segmentKey(),
                sessionMeta == null ? "" : sessionMeta.reason(),
                sessionMeta == null ? "" : sessionMeta.stage(),
                sessionMeta == null ? "" : sessionMeta.semanticIteration(),
                compaction.mode(),
                compaction.enabled(),
                compaction.reason(),
                compaction.seedKind(),
                compaction.seedPath(),
                promptTemplate,
                workspaceRoot.toString(),
                relative(workspaceRoot, promptPath),
                relative(workspaceRoot, PromptManifestWriter.manifestPathFor(promptPath)),
                relative(workspaceRoot, stdoutPath),
                relativeOrNull(workspaceRoot, transcriptPath),
                relative(workspaceRoot, stderrPath),
                relative(workspaceRoot, promptMetadataPath),
                null,
                null,
                null,
                List.of(),
                null,
                null,
                0,
                null,
                "prepared",
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                executionAttemptToMap(preparedExecutionAttempt(
                        workspaceRoot, workstreamId, nodeId, agent, session, runId, transcriptPath, stderrPath, sessionMeta)),
                null,
                List.of()
        ));
    }

    void writeCompleted(
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            String agent,
            String session,
            String promptTemplate,
            String runId,
            Path runDir,
            Path promptPath,
            Path stdoutPath,
            Path stderrPath,
            Path transcriptPath,
            AcpxRunResult result,
            List<AcpxExecutionEvent> runTimeline,
            AcpxUsage usage,
            SessionMeta sessionMeta,
            SessionCompactionMeta compactionMeta,
            AgentResultAcquisitionDocument agentResultAcquisition
    ) throws IOException {
        var compaction = compactionMeta == null ? SessionCompactionMeta.off() : compactionMeta;
        var promptMetadataPath = metadataPath(promptPath);
        var outputMetadataPath = metadataPath(stdoutPath);
        var stderrMetadataPath = metadataPath(stderrPath);
        var transcriptMetadataPath = transcriptPath == null ? null : metadataPath(transcriptPath);
        var toolUsage = transcriptPath == null ? AcpxToolUsage.empty() : toolUsageReader.read(transcriptPath, workspaceRoot);
        workstreamMemoryUpdater.updateMemory(
                workspaceRoot,
                workstreamId,
                runId,
                nodeId,
                sessionMeta == null ? "" : sessionMeta.stage(),
                agent,
                stdoutPath,
                toolUsage,
                result.adapterSignals()
        );
        writePromptDebugIfEnabled(promptPath);
        var governanceReportPath = governanceActualReportWriter.write(
                workspaceRoot,
                workstreamId,
                nodeId,
                runId,
                stdoutPath,
                transcriptPath,
                toolUsage
        ).orElse(null);
        var attempt = executionAttempt(
                runId,
                workstreamId,
                nodeId,
                agent,
                session,
                sessionMeta,
                result,
                relative(workspaceRoot, stderrPath),
                relativeOrNull(workspaceRoot, transcriptPath)
        );
        var effectiveUsage = usage;
        if (hasActiveBlockingSignal(result)) {
            effectiveUsage = usage == null
                    ? AcpxUsage.unavailable(unavailableReason(attempt))
                    : usage.withUnavailableReason(unavailableReason(attempt));
        }
        var runStartedAt = runStartedAt(result, agentResultAcquisition);
        var runFinishedAt = runFinishedAt(result, agentResultAcquisition);
        writeArtifactMetadata(
                workspaceRoot,
                promptMetadataPath,
                artifactDocument(
                        workspaceRoot,
                        workstreamId,
                        nodeId,
                        agent,
                        session,
                        promptTemplate,
                        runId,
                        "prompt",
                        "text/markdown",
                        promptPath
                )
        );
        writeArtifactMetadata(
                workspaceRoot,
                outputMetadataPath,
                artifactDocument(
                        workspaceRoot,
                        workstreamId,
                        nodeId,
                        agent,
                        session,
                        promptTemplate,
                        runId,
                        "output",
                        "application/json",
                        stdoutPath
                )
        );
        if (transcriptPath != null) {
            writeArtifactMetadata(
                    workspaceRoot,
                    transcriptMetadataPath,
                    artifactDocument(
                            workspaceRoot,
                            workstreamId,
                            nodeId,
                            agent,
                            session,
                            promptTemplate,
                            runId,
                            "transcript",
                            "text/plain",
                            transcriptPath
                    )
            );
        }
        writeArtifactMetadata(
                workspaceRoot,
                stderrMetadataPath,
                artifactDocument(
                        workspaceRoot,
                        workstreamId,
                        nodeId,
                        agent,
                        session,
                        promptTemplate,
                        runId,
                        "stderr",
                        "text/plain",
                        stderrPath
                )
        );
        write(workspaceRoot, runDir, new AgentRunDocument(
                2,
                runId,
                workstreamId,
                nodeId,
                agent,
                session,
                sessionMeta == null ? "" : sessionMeta.scope(),
                sessionMeta == null ? "" : sessionMeta.segmentKey(),
                sessionMeta == null ? "" : sessionMeta.reason(),
                sessionMeta == null ? "" : sessionMeta.stage(),
                sessionMeta == null ? "" : sessionMeta.semanticIteration(),
                compaction.mode(),
                compaction.enabled(),
                compaction.reason(),
                compaction.seedKind(),
                compaction.seedPath(),
                promptTemplate,
                workspaceRoot.toString(),
                relative(workspaceRoot, promptPath),
                relative(workspaceRoot, PromptManifestWriter.manifestPathFor(promptPath)),
                relative(workspaceRoot, stdoutPath),
                relativeOrNull(workspaceRoot, transcriptPath),
                relative(workspaceRoot, stderrPath),
                relative(workspaceRoot, promptMetadataPath),
                relative(workspaceRoot, outputMetadataPath),
                relativeOrNull(workspaceRoot, transcriptMetadataPath),
                relative(workspaceRoot, stderrMetadataPath),
                result.command(),
                runStartedAt,
                runFinishedAt,
                Math.max(0, Duration.between(runStartedAt, runFinishedAt).toMillis()),
                result.exitCode(),
                "process_exited",
                null,
                null,
                effectiveUsage,
                agentResultAcquisition,
                providerModelIdentity(effectiveUsage),
                toolUsage,
                relativeOrNull(workspaceRoot, governanceReportPath),
                processResult(result.processResult()),
                adapterSignals(result.adapterSignals()),
                null,
                List.of(),
                executionAttemptToMap(attempt),
                null,
                executionTimeline(runTimeline)
        ));
    }

    void appendExecutionEvent(Path runJsonPath, AcpxExecutionEvent event) {
        if (runJsonPath == null || event == null || !Files.isRegularFile(runJsonPath)) {
            throw new IllegalStateException("run metadata must exist before execution events are persisted");
        }
        try {
            var root = objectMapper.readValue(
                    runJsonPath.toFile(),
                    new TypeReference<LinkedHashMap<String, Object>>() {
                    }
            );
            var existing = root.get("executionTimeline");
            var timeline = existing instanceof List<?> list ? new ArrayList<Object>(list) : new ArrayList<>();
            var eventMap = new LinkedHashMap<String, Object>(event.toMap());
            if (!containsExecutionEvent(timeline, eventMap)) {
                timeline.add(eventMap);
            }
            root.put("executionTimeline", timeline);
            projectInFlightAttempt(root, event);
            writeJsonAtomically(runJsonPath, root);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("failed to persist authoritative execution event", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private void projectInFlightAttempt(Map<String, Object> root, AcpxExecutionEvent event) {
        var value = root.get("executionAttempt");
        if (!(value instanceof Map<?, ?> existing)) {
            throw new IllegalStateException("executionAttempt must exist before execution events are persisted");
        }
        var attempt = new LinkedHashMap<String, Object>((Map<String, Object>) existing);
        switch (event.kind()) {
            case PROCESS_STARTED -> {
                root.put("status", "running");
                attempt.put("status", AgentExecutionAttemptStatus.PROCESS_STARTED.name());
                attempt.put("processStatus", AgentExecutionProcessStatus.RUNNING.name());
            }
            case PROMPT_ACCEPTED, PROMPT_COMPLETED -> {
                root.put("status", "running");
                attempt.put("status", AgentExecutionAttemptStatus.STREAMING.name());
                attempt.put("processStatus", AgentExecutionProcessStatus.RUNNING.name());
            }
            case PROCESS_WAIT_COMPLETED, PROCESS_EXITED -> {
                root.put("status", "running");
                attempt.put("status", AgentExecutionAttemptStatus.PROCESS_EXITED.name());
                attempt.put("processStatus", AgentExecutionProcessStatus.EXITED.name());
            }
            default -> {
                return;
            }
        }
        root.put("executionAttempt", attempt);
    }

    private void writeJsonAtomically(Path target, Object value) throws IOException {
        var directory = target.getParent();
        Files.createDirectories(directory);
        var tmp = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            objectMapper.writeValue(tmp.toFile(), value);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private boolean containsExecutionEvent(List<Object> timeline, Map<String, Object> event) {
        return timeline.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(existing -> sameExecutionEvent(existing, event));
    }

    private boolean sameExecutionEvent(Map<?, ?> left, Map<String, Object> right) {
        return String.valueOf(left.get("kind")).equals(String.valueOf(right.get("kind")))
                && String.valueOf(left.get("sequence")).equals(String.valueOf(right.get("sequence")))
                && String.valueOf(left.get("observedAt")).equals(String.valueOf(right.get("observedAt")));
    }

    private Map<String, Object> providerModelIdentity(AcpxUsage usage) {
        if (usage == null || (usage.providerId().isBlank() && usage.model().isBlank() && usage.billingMode().isBlank())) {
            return Map.of(
                    "provider", "unknown",
                    "model", "",
                    "billingMode", "pricing_unknown",
                    "source", "",
                    "confidence", "low"
            );
        }
        var provider = usage.providerId().isBlank() ? "unknown" : usage.providerId();
        var billingMode = normalizeBillingMode(usage.billingMode().isBlank() ? "pricing_unknown" : usage.billingMode());
        return Map.of(
                "provider", provider,
                "model", usage.model(),
                "billingMode", billingMode,
                "source", usage.providerMetadataSource(),
                "confidence", provider.equals("unknown") || usage.model().isBlank() ? "low" : "high"
        );
    }

    private String normalizeBillingMode(String billingMode) {
        if ("subscription_tokens".equalsIgnoreCase(billingMode) || "token_only".equalsIgnoreCase(billingMode)) {
            return "subscription_token_only";
        }
        return billingMode == null ? "" : billingMode;
    }

    private List<Map<String, Object>> executionTimeline(List<AcpxExecutionEvent> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return List.of();
        }
        return timeline.stream()
                .map(AcpxExecutionEvent::toMap)
                .toList();
    }

    private Map<String, Object> processResult(ProcessResult process) {
        var map = new LinkedHashMap<String, Object>();
        map.put("command", process.command());
        map.put("pid", process.pid());
        map.put("exitCode", process.exitCode());
        map.put("usability", process.usability().name());
        map.put("durationMs", process.durationMs());
        map.put("stdoutPath", process.stdoutPath());
        map.put("stderrPath", process.stderrPath());
        map.put("interrupted", process.interrupted());
        map.put("timedOut", process.timedOut());
        return map;
    }

    private AgentExecutionAttempt executionAttempt(
            String runId,
            String workstreamId,
            String nodeId,
            String agent,
            String session,
            SessionMeta sessionMeta,
            AcpxRunResult result,
            String stderrPath,
            String transcriptPath
    ) {
        var processStatus = processStatus(result.processResult(), result.activeAdapterSignals());
        var failureSignal = result.activeAdapterSignals().stream().findFirst().map(AdapterSignal::type).orElse(null);
        var attemptStatus = attemptStatus(result.processResult());
        return new AgentExecutionAttempt(
                "att-" + runId,
                runId,
                workstreamId,
                nodeId,
                agent,
                attemptStatus,
                processStatus,
                failureSignal,
                attemptStatus == AgentExecutionAttemptStatus.TIMED_OUT
                        || attemptStatus == AgentExecutionAttemptStatus.INTERRUPTED
                        || attemptStatus == AgentExecutionAttemptStatus.STALLED
                        || attemptStatus == AgentExecutionAttemptStatus.RECOVERABLE_FAILED
                        || failureSignal != null
                        && failureSignal.severity() != dev.dual.core.execution.AdapterSignalSeverity.BLOCKING,
                false,
                "process exited",
                stderrPath,
                transcriptPath,
                session,
                "",
                sessionMeta == null ? "" : sessionMeta.scope(),
                sessionMeta == null ? "" : sessionMeta.segmentKey(),
                result.startedAt(),
                result.finishedAt()
        );
    }

    private AgentExecutionAttemptStatus attemptStatus(ProcessResult process) {
        return switch (process.usability()) {
            case TIMED_OUT -> AgentExecutionAttemptStatus.TIMED_OUT;
            case INTERRUPTED -> AgentExecutionAttemptStatus.INTERRUPTED;
            case STALLED -> AgentExecutionAttemptStatus.STALLED;
            case KILLED_AFTER_RECOVERABLE_SIGNAL -> AgentExecutionAttemptStatus.RECOVERABLE_FAILED;
            case EXITED_ZERO, EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT, EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT ->
                    AgentExecutionAttemptStatus.PROCESS_EXITED;
        };
    }

    private AgentExecutionProcessStatus processStatus(
            ProcessResult process,
            List<AdapterSignal> activeSignals
    ) {
        return switch (process.usability()) {
            case TIMED_OUT -> AgentExecutionProcessStatus.TIMED_OUT;
            case INTERRUPTED -> AgentExecutionProcessStatus.INTERRUPTED;
            case STALLED -> AgentExecutionProcessStatus.STALLED;
            case KILLED_AFTER_RECOVERABLE_SIGNAL -> activeSignals.stream()
                    .anyMatch(signal -> signal.type() == AdapterSignalType.AGENT_RECONNECT_REQUIRED)
                    ? AgentExecutionProcessStatus.RECONNECT_REQUIRED
                    : AgentExecutionProcessStatus.FAILED;
            case EXITED_ZERO, EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT, EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT ->
                    AgentExecutionProcessStatus.EXITED;
        };
    }

    private boolean hasActiveBlockingSignal(AcpxRunResult result) {
        return result.activeAdapterSignals().stream().anyMatch(signal -> signal.type().blocksSuccess());
    }

    private Instant runStartedAt(AcpxRunResult result, AgentResultAcquisitionDocument acquisition) {
        if (acquisition == null || acquisition.occurrences().isEmpty()) {
            return result.startedAt();
        }
        return acquisition.occurrences().get(0).startedAt();
    }

    private Instant runFinishedAt(AcpxRunResult result, AgentResultAcquisitionDocument acquisition) {
        if (acquisition == null || acquisition.occurrences().isEmpty()) {
            return result.finishedAt();
        }
        return acquisition.occurrences().get(acquisition.occurrences().size() - 1).finishedAt();
    }

    private String unavailableReason(AgentExecutionAttempt attempt) {
        return attempt.failureSignal() == null ? attempt.status().name() : attempt.failureSignal().name();
    }

    private Map<String, Object> executionAttemptToMap(AgentExecutionAttempt attempt) {
        if (attempt == null) {
            return null;
        }
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("attemptId", attempt.attemptId());
        map.put("runId", attempt.runId());
        map.put("workstreamId", attempt.workstreamId());
        map.put("nodeId", attempt.nodeId());
        map.put("agent", attempt.agent());
        map.put("status", attempt.status().name());
        map.put("processStatus", attempt.processStatus().name());
        map.put("failureSignal", attempt.failureSignal() == null ? null : attempt.failureSignal().name());
        map.put("recoverable", attempt.recoverable());
        map.put("retrySafe", attempt.retrySafe());
        map.put("message", attempt.message());
        map.put("stderrPath", attempt.stderrPath());
        map.put("transcriptPath", attempt.transcriptPath());
        map.put("sessionName", attempt.sessionName());
        map.put("sessionId", attempt.sessionId());
        map.put("sessionScope", attempt.sessionScope());
        map.put("sessionSegmentKey", attempt.sessionSegmentKey());
        map.put("startedAt", attempt.startedAt() == null ? null : attempt.startedAt().toString());
        map.put("finishedAt", attempt.finishedAt() == null ? null : attempt.finishedAt().toString());
        return map;
    }

    /**
     * Non-terminal attempt recorded at prepare time so an agent run can never
     * sit at {@code status=prepared} with no execution evidence. This is not a
     * terminal outcome; {@code AgentRunMetadataFinalizer} overwrites it on
     * completion, and {@code AgentExecutionAttemptReconciler} closes it if the
     * transport never returns.
     */
    private AgentExecutionAttempt preparedExecutionAttempt(
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            String agent,
            String session,
            String runId,
            Path transcriptPath,
            Path stderrPath,
            SessionMeta sessionMeta
    ) {
        return new AgentExecutionAttempt(
                "att-" + runId,
                runId,
                workstreamId,
                nodeId,
                agent,
                AgentExecutionAttemptStatus.RUNNING,
                AgentExecutionProcessStatus.RUNNING,
                null,
                false,
                false,
                "agent execution prepared",
                relative(workspaceRoot, stderrPath),
                relative(workspaceRoot, transcriptPath),
                session == null ? "" : session,
                "",
                sessionMeta == null ? "" : sessionMeta.scope(),
                sessionMeta == null ? "" : sessionMeta.segmentKey(),
                java.time.Instant.now(),
                null
        );
    }

    private List<Map<String, Object>> adapterSignals(List<AdapterSignal> signals) {
        if (signals == null || signals.isEmpty()) {
            return List.of();
        }
        return signals.stream().map(signal -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("type", signal.type().name());
            map.put("severity", signal.type().severity().name());
            map.put("source", signal.source());
            map.put("message", signal.message());
            map.put("sessionId", signal.sessionId());
            map.put("observedSequence", signal.observedSequence());
            map.put("superseded", signal.superseded());
            return Map.copyOf(map);
        }).toList();
    }

    private void write(Path workspaceRoot, Path runDir, AgentRunDocument document) throws IOException {
        Files.createDirectories(runDir);
        objectMapper.writeValue(runDir.resolve("run.json").toFile(), document);
    }

    private void writeArtifactMetadata(Path workspaceRoot, Path metadataPath, AgentArtifactDocument document) throws IOException {
        Files.createDirectories(metadataPath.getParent());
        objectMapper.writeValue(metadataPath.toFile(), document);
    }

    private AgentArtifactDocument artifactDocument(
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            String agent,
            String session,
            String promptTemplate,
            String runId,
            String artifactType,
            String contentType,
            Path artifactPath
    ) throws IOException {
        var bytes = Files.exists(artifactPath) ? Files.readAllBytes(artifactPath) : new byte[0];
        return new AgentArtifactDocument(
                1,
                artifactType,
                runId,
                workstreamId,
                nodeId,
                agent,
                session,
                promptTemplate,
                workspaceRoot.toString(),
                relative(workspaceRoot, artifactPath),
                contentType,
                bytes.length,
                estimatedTokens(bytes.length),
                lineCount(bytes),
                sha256(bytes),
                Instant.now()
        );
    }

    private Path metadataPath(Path artifactPath) {
        return artifactPath.resolveSibling(artifactPath.getFileName() + ".meta.json");
    }

    private void writePromptDebugIfEnabled(Path promptPath) throws IOException {
        if (!Boolean.parseBoolean(System.getenv(PROMPT_DEBUG_ENV))) {
            return;
        }
        var bytes = Files.exists(promptPath) ? Files.readAllBytes(promptPath) : new byte[0];
        var debug = new java.util.LinkedHashMap<String, Object>();
        debug.put("schemaVersion", 1);
        debug.put("enabledBy", PROMPT_DEBUG_ENV);
        debug.put("promptPath", promptPath.toString());
        debug.put("byteSize", bytes.length);
        debug.put("estimatedTokens", estimatedTokens(bytes.length));
        debug.put("sha256", sha256(bytes));
        debug.put("content", new String(bytes, StandardCharsets.UTF_8));
        objectMapper.writeValue(promptPath.resolveSibling(promptPath.getFileName() + ".debug.json").toFile(), debug);
    }

    private long lineCount(byte[] bytes) {
        if (bytes.length == 0) {
            return 0;
        }
        var text = new String(bytes, StandardCharsets.UTF_8);
        return text.lines().count();
    }

    private String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 digest is not available", e);
        }
    }

    private long estimatedTokens(long byteSize) {
        return (byteSize + 3) / 4;
    }

    private String relative(Path workspaceRoot, Path path) {
        return workspaceRoot.relativize(path).toString();
    }

    private String relativeOrNull(Path workspaceRoot, Path path) {
        return path == null ? null : relative(workspaceRoot, path);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
