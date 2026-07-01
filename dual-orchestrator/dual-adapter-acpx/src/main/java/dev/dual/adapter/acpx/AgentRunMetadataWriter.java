package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionAttempt;
import dev.dual.core.execution.AgentExecutionAttemptStatus;
import dev.dual.core.execution.AgentExecutionProcessStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
                null,
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
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                null,
                null,
                List.of()
        ));
    }

    CompletionWriteResult writeCompleted(
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
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure
    ) throws IOException {
        return writeCompleted(
                workspaceRoot,
                workstreamId,
                nodeId,
                agent,
                session,
                promptTemplate,
                runId,
                runDir,
                promptPath,
                stdoutPath,
                stderrPath,
                null,
                result,
                failure,
                null,
                null,
                null
        );
    }

    CompletionWriteResult writeCompleted(
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
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure,
            AcpxUsage usage
    ) throws IOException {
        return writeCompleted(
                workspaceRoot,
                workstreamId,
                nodeId,
                agent,
                session,
                promptTemplate,
                runId,
                runDir,
                promptPath,
                stdoutPath,
                stderrPath,
                null,
                result,
                failure,
                usage,
                null,
                null
        );
    }

    CompletionWriteResult writeCompleted(
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
            AcpxFailureClassifier.AcpxFailure failure,
            AcpxUsage usage,
            SessionMeta sessionMeta,
            SessionCompactionMeta compactionMeta
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
                failure
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
        var explorationPolicyResult = explorationPolicyResult(governanceReportPath);
        var explorationPolicyResultPath = explorationPolicyResultPath(workspaceRoot, governanceReportPath);
        var agentExplorationPlanPath = agentExplorationPlanPath(workspaceRoot, governanceReportPath);
        var policyFailure = explorationPolicyFailure(explorationPolicyResult);
        var topLevelFailure = failure == null ? policyFailure : failure;
        var attempt = executionAttempt(
                runId,
                workstreamId,
                nodeId,
                agent,
                session,
                sessionMeta,
                result,
                failure,
                relative(workspaceRoot, stderrPath),
                relativeOrNull(workspaceRoot, transcriptPath)
        );
        var effectiveUsage = usage;
        if (failure != null) {
            effectiveUsage = usage == null
                    ? AcpxUsage.unavailable(unavailableReason(attempt))
                    : usage.withUnavailableReason(unavailableReason(attempt));
        }
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
                        "text/markdown",
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
                result.startedAt(),
                result.finishedAt(),
                Duration.between(result.startedAt(), result.finishedAt()).toMillis(),
                result.exitCode(),
                topLevelFailure == null ? "completed" : "failed",
                topLevelFailure == null ? null : topLevelFailure.reason(),
                topLevelFailure == null ? null : topLevelFailure.recoverable(),
                effectiveUsage,
                providerModelIdentity(effectiveUsage),
                toolUsage,
                relativeOrNull(workspaceRoot, agentExplorationPlanPath),
                relativeOrNull(workspaceRoot, governanceReportPath),
                relativeOrNull(workspaceRoot, explorationPolicyResultPath),
                explorationPolicyResult,
                processResult(workspaceRoot, result, stdoutPath, stderrPath, failure),
                adapterSignals(failure),
                null,
                List.of(),
                executionAttemptToMap(attempt),
                null,
                executionTimeline(result)
        ));
        return new CompletionWriteResult(
                explorationPolicyResult,
                relativeOrNull(workspaceRoot, explorationPolicyResultPath),
                policyFailure != null
        );
    }

    record CompletionWriteResult(
            Map<String, Object> explorationPolicyResult,
            String explorationPolicyResultPath,
            boolean explorationPolicyFailed
    ) {
    }

    void appendExecutionEvent(Path runJsonPath, AcpxExecutionEvent event) {
        if (runJsonPath == null || event == null || !Files.isRegularFile(runJsonPath)) {
            return;
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
            objectMapper.writeValue(runJsonPath.toFile(), root);
        } catch (IOException | RuntimeException ignored) {
            // In-flight timeline persistence is best-effort. The terminal writeCompleted
            // path still writes the authoritative final event list from AcpxRunResult.
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

    private List<Map<String, Object>> executionTimeline(AcpxRunResult result) {
        if (result == null || result.executionTimeline().isEmpty()) {
            return List.of();
        }
        return result.executionTimeline().stream()
                .map(AcpxExecutionEvent::toMap)
                .toList();
    }

    private Map<String, Object> processResult(
            Path workspaceRoot,
            AcpxRunResult result,
            Path stdoutPath,
            Path stderrPath,
            AcpxFailureClassifier.AcpxFailure failure
    ) {
        return Map.of(
                "command", String.join(" ", result.command()),
                "exitCode", result.exitCode(),
                "usability", usability(result, failure).name(),
                "durationMs", result.durationMs(),
                "stdoutPath", relative(workspaceRoot, stdoutPath),
                "stderrPath", relative(workspaceRoot, stderrPath),
                "interrupted", false,
                "timedOut", false
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> explorationPolicyResult(Path governanceReportPath) {
        if (governanceReportPath == null || !Files.isRegularFile(governanceReportPath)) {
            return null;
        }
        try {
            var report = objectMapper.readValue(governanceReportPath.toFile(), Map.class);
            var result = report.get("explorationPolicyResult");
            return result instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private AcpxFailureClassifier.AcpxFailure explorationPolicyFailure(Map<String, Object> explorationPolicyResult) {
        if (explorationPolicyResult == null || !"failed".equals(text(explorationPolicyResult.get("status")))) {
            return null;
        }
        return new AcpxFailureClassifier.AcpxFailure(
                "EXPLORATION_POLICY_VIOLATION",
                "agent exploration policy failed; inspect explorationPolicyResult for violations",
                false,
                false
        );
    }

    private Path explorationPolicyResultPath(Path workspaceRoot, Path governanceReportPath) {
        if (governanceReportPath == null || !Files.isRegularFile(governanceReportPath)) {
            return null;
        }
        try {
            var report = objectMapper.readValue(governanceReportPath.toFile(), Map.class);
            var ref = report.get("explorationPolicyResultPath");
            if (ref == null || String.valueOf(ref).isBlank()) {
                return null;
            }
            return workspaceRoot.resolve(String.valueOf(ref)).normalize();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private Path agentExplorationPlanPath(Path workspaceRoot, Path governanceReportPath) {
        if (governanceReportPath == null || !Files.isRegularFile(governanceReportPath)) {
            return null;
        }
        try {
            var report = objectMapper.readValue(governanceReportPath.toFile(), Map.class);
            var ref = report.get("agentExplorationPlanPath");
            if (ref == null || String.valueOf(ref).isBlank()) {
                return null;
            }
            return workspaceRoot.resolve(String.valueOf(ref)).normalize();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private dev.dual.core.execution.ProcessUsability usability(
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure
    ) {
        if (failure != null && AcpxFailureReason.AGENT_OUTPUT_INTERRUPTED.code().equals(failure.reason())) {
            return dev.dual.core.execution.ProcessUsability.INTERRUPTED;
        }
        if (result.exitCode() == 0) {
            return dev.dual.core.execution.ProcessUsability.EXITED_ZERO;
        }
        if (failure != null && failure.recoverable()) {
            return dev.dual.core.execution.ProcessUsability.KILLED_AFTER_RECOVERABLE_SIGNAL;
        }
        return dev.dual.core.execution.ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT;
    }

    private AgentExecutionAttempt executionAttempt(
            String runId,
            String workstreamId,
            String nodeId,
            String agent,
            String session,
            SessionMeta sessionMeta,
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure,
            String stderrPath,
            String transcriptPath
    ) {
        var processStatus = processStatus(result, failure);
        var failureSignal = failureSignal(failure);
        var status = attemptStatus(processStatus, failure);
        var recoverable = failure != null && failure.recoverable();
        var message = failure == null ? "completed" : attemptMessage(failure);
        return new AgentExecutionAttempt(
                "att-" + runId,
                runId,
                workstreamId,
                nodeId,
                agent,
                status,
                processStatus,
                failureSignal,
                recoverable,
                false,
                message,
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

    private AgentExecutionAttemptStatus attemptStatus(
            AgentExecutionProcessStatus processStatus,
            AcpxFailureClassifier.AcpxFailure failure
    ) {
        if (failure == null) {
            return AgentExecutionAttemptStatus.COMPLETED;
        }
        if (processStatus == AgentExecutionProcessStatus.TIMED_OUT) {
            return AgentExecutionAttemptStatus.TIMED_OUT;
        }
        if (processStatus == AgentExecutionProcessStatus.STALLED) {
            return AgentExecutionAttemptStatus.STALLED;
        }
        if (processStatus == AgentExecutionProcessStatus.INTERRUPTED) {
            return AgentExecutionAttemptStatus.INTERRUPTED;
        }
        return failure.recoverable()
                ? AgentExecutionAttemptStatus.RECOVERABLE_FAILED
                : AgentExecutionAttemptStatus.RUNTIME_FAILED;
    }

    private AgentExecutionProcessStatus processStatus(
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure
    ) {
        if (failure == null) {
            return AgentExecutionProcessStatus.EXITED;
        }
        if (AcpxFailureReason.AGENT_RECONNECT.code().equals(failure.reason())) {
            return AgentExecutionProcessStatus.RECONNECT_REQUIRED;
        }
        if (AcpxFailureReason.AGENT_OUTPUT_INTERRUPTED.code().equals(failure.reason())) {
            return AgentExecutionProcessStatus.INTERRUPTED;
        }
        if (result.reachedDeclaredTimeout(java.time.Duration.ZERO)) {
            return AgentExecutionProcessStatus.TIMED_OUT;
        }
        return AgentExecutionProcessStatus.FAILED;
    }

    private AdapterSignalType failureSignal(AcpxFailureClassifier.AcpxFailure failure) {
        if (failure == null) {
            return null;
        }
        if (AcpxFailureReason.AGENT_RECONNECT.code().equals(failure.reason())) {
            return AdapterSignalType.AGENT_RECONNECT_REQUIRED;
        }
        if (AcpxFailureReason.AGENT_MODEL_MISMATCH.code().equals(failure.reason())) {
            return AdapterSignalType.MODEL_MISMATCH;
        }
        if (AcpxFailureReason.SESSION_BOOTSTRAP_FAILED.code().equals(failure.reason())
                || AcpxFailureReason.SESSION_MISSING.code().equals(failure.reason())) {
            return AdapterSignalType.SESSION_BOOTSTRAP_FAILED;
        }
        if (AcpxFailureReason.RATE_LIMIT.code().equals(failure.reason())) {
            return AdapterSignalType.RATE_LIMITED;
        }
        if (AcpxFailureReason.CONTEXT_WINDOW.code().equals(failure.reason())) {
            return AdapterSignalType.CONTEXT_WINDOW_EXCEEDED;
        }
        if (AcpxFailureReason.AGENT_PERMISSION_BLOCKED.code().equals(failure.reason())) {
            return AdapterSignalType.PERMISSION_BLOCKED;
        }
        if (AcpxFailureReason.AGENT_AUTH_FAILED.code().equals(failure.reason())) {
            return AdapterSignalType.AUTH_FAILED;
        }
        if (AcpxFailureReason.PROVIDER_CONNECTION_FAILED.code().equals(failure.reason())) {
            return AdapterSignalType.PROVIDER_CONNECTION_FAILED;
        }
        if (AcpxFailureReason.TRANSPORT_FAILED.code().equals(failure.reason())) {
            return AdapterSignalType.PROCESS_STALLED;
        }
        return AdapterSignalType.PROCESS_STALLED;
    }

    private String attemptMessage(AcpxFailureClassifier.AcpxFailure failure) {
        if (AcpxFailureReason.AGENT_RECONNECT.code().equals(failure.reason())) {
            return "agent needs reconnect: " + failure.message();
        }
        return failure.message();
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

    private List<Map<String, Object>> adapterSignals(AcpxFailureClassifier.AcpxFailure failure) {
        if (failure == null || failure.reason() == null || failure.reason().isBlank()) {
            return List.of();
        }
        var type = failureSignal(failure);
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("type", type == null ? AdapterSignalType.PROCESS_STALLED.name() : type.name());
        map.put("reason", failure.reason());
        map.put("severity", failure.recoverable() ? "RECOVERABLE" : "FATAL");
        map.put("source", "acpx");
        map.put("message", failure.message());
        map.put("superseded", false);
        return List.of(map);
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
