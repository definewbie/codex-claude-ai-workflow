package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
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
            SessionMeta sessionMeta
    ) throws IOException {
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
                promptTemplate,
                workspaceRoot.toString(),
                relative(workspaceRoot, promptPath),
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
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                null
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
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure
    ) throws IOException {
        writeCompleted(
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
                null
        );
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
            AcpxRunResult result,
            AcpxFailureClassifier.AcpxFailure failure,
            AcpxUsage usage
    ) throws IOException {
        writeCompleted(
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
                null
        );
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
            AcpxFailureClassifier.AcpxFailure failure,
            AcpxUsage usage,
            SessionMeta sessionMeta
    ) throws IOException {
        var promptMetadataPath = metadataPath(promptPath);
        var outputMetadataPath = metadataPath(stdoutPath);
        var stderrMetadataPath = metadataPath(stderrPath);
        var transcriptMetadataPath = transcriptPath == null ? null : metadataPath(transcriptPath);
        var toolUsage = transcriptPath == null ? AcpxToolUsage.empty() : toolUsageReader.read(transcriptPath, workspaceRoot);
        writePromptDebugIfEnabled(promptPath);
        var governanceReportPath = governanceActualReportWriter.write(
                workspaceRoot,
                workstreamId,
                nodeId,
                runId,
                transcriptPath,
                toolUsage
        ).orElse(null);
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
                promptTemplate,
                workspaceRoot.toString(),
                relative(workspaceRoot, promptPath),
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
                failure == null ? "completed" : "failed",
                failure == null ? null : failure.reason(),
                failure == null ? null : failure.recoverable(),
                usage,
                toolUsage,
                relativeOrNull(workspaceRoot, governanceReportPath),
                processResult(workspaceRoot, result, stdoutPath, stderrPath),
                adapterSignals(failure),
                null,
                List.of(),
                null
        ));
    }

    private Map<String, Object> processResult(
            Path workspaceRoot,
            AcpxRunResult result,
            Path stdoutPath,
            Path stderrPath
    ) {
        return Map.of(
                "command", String.join(" ", result.command()),
                "exitCode", result.exitCode(),
                "usability", usability(result).name(),
                "durationMs", result.durationMs(),
                "stdoutPath", relative(workspaceRoot, stdoutPath),
                "stderrPath", relative(workspaceRoot, stderrPath),
                "interrupted", false,
                "timedOut", false
        );
    }

    private dev.dual.core.execution.ProcessUsability usability(AcpxRunResult result) {
        if (result.exitCode() == 0) {
            return dev.dual.core.execution.ProcessUsability.EXITED_ZERO;
        }
        return dev.dual.core.execution.ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT;
    }

    private List<Map<String, Object>> adapterSignals(AcpxFailureClassifier.AcpxFailure failure) {
        if (failure == null || failure.reason() == null || failure.reason().isBlank()) {
            return List.of();
        }
        return List.of(Map.of(
                "type", failure.reason(),
                "severity", failure.recoverable() ? "RECOVERABLE" : "FATAL",
                "source", "acpx",
                "message", failure.message(),
                "superseded", false
        ));
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
}
