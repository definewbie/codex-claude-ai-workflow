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

final class AgentRunMetadataWriter {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

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
            Path stderrPath
    ) throws IOException {
        var promptMetadataPath = metadataPath(promptPath);
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
                1,
                runId,
                workstreamId,
                nodeId,
                agent,
                session,
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
                usage
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
            AcpxUsage usage
    ) throws IOException {
        var promptMetadataPath = metadataPath(promptPath);
        var outputMetadataPath = metadataPath(stdoutPath);
        var stderrMetadataPath = metadataPath(stderrPath);
        var transcriptMetadataPath = transcriptPath == null ? null : metadataPath(transcriptPath);
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
                1,
                runId,
                workstreamId,
                nodeId,
                agent,
                session,
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
                usage
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
