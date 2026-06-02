package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.runtime.handler.TaskHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxAgentTaskHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesPromptAndReturnsPreparedWhenConfiguredForPrepareOnly() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true);
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "design-claude"
        ));
        var instance = new NodeInstance(
                "node-design",
                "design.claude_design",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-design",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(true, result.result().get("prepared"));
        assertTrue(Files.walk(tempDir.resolve(".agent"))
                .anyMatch(path -> path.getFileName().toString().equals("design-claude.md")));
        var promptPath = Files.walk(tempDir.resolve(".agent"))
                .filter(path -> path.getFileName().toString().equals("design-claude.md"))
                .findFirst()
                .orElseThrow();
        var prompt = Files.readString(promptPath);
        assertTrue(prompt.contains("Claude Design"));
        assertTrue(prompt.contains("Clarify the request first."));
        var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
        assertTrue(Files.exists(runMetadataPath));
        var runMetadata = Files.readString(runMetadataPath);
        assertTrue(runMetadata.contains("\"schemaVersion\" : 1"));
        assertTrue(runMetadata.contains("\"status\" : \"prepared\""));
        assertTrue(runMetadata.contains("\"nodeId\" : \"design.claude_design\""));
        assertTrue(runMetadata.contains("\"promptPath\" :"));
        assertTrue(runMetadata.contains("\"promptMetadataPath\" :"));
        var promptMetadataPath = tempDir.resolve(runMetadataPath.getParent().resolve("prompts/design-claude.md.meta.json"));
        assertTrue(Files.exists(promptMetadataPath));
        var promptMetadata = Files.readString(promptMetadataPath);
        assertTrue(promptMetadata.contains("\"artifactType\" : \"prompt\""));
        assertTrue(promptMetadata.contains("\"sha256\" :"));
        assertTrue(promptMetadata.contains("\"byteSize\" :"));
    }

    @Test
    void completedRunWritesArtifactMetadataSidecars() throws Exception {
        var runDir = tempDir.resolve(".agent/workstreams/ws-test/runs/run-001");
        var promptPath = runDir.resolve("prompts/design-claude.md");
        var outputPath = runDir.resolve("outputs/design-claude.md");
        var stderrPath = runDir.resolve("outputs/design-claude.stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(outputPath.getParent());
        Files.writeString(promptPath, "# Prompt\n");
        Files.writeString(outputPath, "# Output\nDecision text.\n");
        Files.writeString(stderrPath, "");

        var startedAt = Instant.parse("2026-05-24T00:00:00Z");
        var finishedAt = Instant.parse("2026-05-24T00:00:03Z");
        new AgentRunMetadataWriter().writeCompleted(
                tempDir,
                "ws-test",
                "design.claude_design",
                "claude",
                "claude-main",
                "design-claude",
                "run-001",
                runDir,
                promptPath,
                outputPath,
                stderrPath,
                new AcpxRunResult(0, startedAt, finishedAt, List.of("acpx", "claude")),
                null
        );

        var runMetadata = Files.readString(runDir.resolve("run.json"));
        assertTrue(runMetadata.contains("\"outputMetadataPath\" :"));
        assertTrue(runMetadata.contains("\"durationMs\" : 3000"));
        var outputMetadata = Files.readString(runDir.resolve("outputs/design-claude.md.meta.json"));
        assertTrue(outputMetadata.contains("\"artifactType\" : \"output\""));
        assertTrue(outputMetadata.contains("\"artifactPath\" : \".agent/workstreams/ws-test/runs/run-001/outputs/design-claude.md\""));
        assertTrue(outputMetadata.contains("\"lineCount\" : 2"));
        assertTrue(outputMetadata.contains("\"sha256\" :"));
    }

    @Test
    void treatsNonZeroExitAsCompletedWhenStdoutHasCompleteTerminalArtifact() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new CompletingExitFiveTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));
        var instance = new NodeInstance(
                "node-intake",
                "intake.claude_brainstorm",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-intake",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(5, result.result().get("exit_code"));
        assertEquals(true, result.result().get("acpx_exit_tolerated"));
        assertTrue(result.outputRefs().get(0).endsWith("intake-claude-brainstorm.md"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 5"));
        assertTrue(runMetadata.contains("\"status\" : \"completed\""));
    }

    @Test
    void treatsReconnectExitAsRecoverableFailureEvenWhenStdoutLooksComplete() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new CompletingReconnectExitFiveTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));
        var instance = new NodeInstance(
                "node-intake",
                "intake.claude_brainstorm",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-intake",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("RECOVERABLE_AGENT_RECONNECT", result.outcome());
        assertEquals(true, result.result().get("acpx_failure.recoverable"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 5"));
        assertTrue(runMetadata.contains("\"status\" : \"failed\""));
    }

    @Test
    void treatsReconnectStderrAsRecoverableFailureEvenWhenExitCodeIsZero() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new ReconnectExitZeroTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));
        var instance = new NodeInstance(
                "node-intake",
                "intake.claude_brainstorm",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-intake",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("RECOVERABLE_AGENT_RECONNECT", result.outcome());
        assertEquals(0, result.result().get("exit_code"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 0"));
        assertTrue(runMetadata.contains("\"failureReason\" : \"RECOVERABLE_AGENT_RECONNECT\""));
    }

    @Test
    void completesWhenExitCodeIsZeroAndOutputIsCompleteDespiteReconnectStderr() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new CompletingReconnectExitZeroTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));
        var instance = new NodeInstance(
                "node-intake",
                "intake.claude_brainstorm",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-intake",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals(true, result.result().get("acpx_recoverable_stderr_ignored"));
        assertEquals("RECOVERABLE_AGENT_RECONNECT", result.result().get("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 0"));
        assertTrue(runMetadata.contains("\"status\" : \"completed\""));
    }

    @Test
    void rejectsUnsupportedAgent() {
        var handler = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true);
        var definition = new NodeDefinition("design.bad_agent", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "unknown",
                "session", "unknown-main",
                "prompt_template", "bad-agent"
        ));
        var instance = new NodeInstance(
                "node-bad",
                "design.bad_agent",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-design",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var error = assertThrows(IllegalArgumentException.class, () ->
                handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance)));
        assertTrue(error.getMessage().contains("unsupported agent"));
    }

    @Test
    void classifiesReconnectFailureAsRecoverable() throws Exception {
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(stderr, "[acpx] session claude-main · agent needs reconnect\n");

        var failure = new AcpxFailureClassifier().classify(stderr, 5);

        assertEquals("RECOVERABLE_AGENT_RECONNECT", failure.reason());
        assertEquals(true, failure.recoverable());
        assertTrue(failure.message().contains("dualctl retry"));
    }

    private static final class CompletingExitFiveTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    ## Requirement Restatement

                    Add a small function.

                    ```dual-control
                    decision: READY_FOR_PRODUCT_REVIEW
                    needs_human_input: false
                    summary: ready
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "[acpx] session claude-main · agent connected\n");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(5, now, now.plusSeconds(1), List.of("acpx", "claude"));
        }
    }

    private static final class CompletingReconnectExitFiveTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    ## Requirement Restatement

                    Add a small function.

                    ```dual-control
                    decision: READY_FOR_PRODUCT_REVIEW
                    needs_human_input: false
                    summary: ready
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "[acpx] session claude-main · agent needs reconnect\n");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(5, now, now.plusSeconds(1), List.of("acpx", "claude"));
        }
    }

    private static final class ReconnectExitZeroTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    ## Requirement Restatement

                    The agent stopped before writing the terminal control block.

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "[acpx] session claude-main · agent needs reconnect\n");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(1), List.of("acpx", "claude"));
        }
    }

    private static final class CompletingReconnectExitZeroTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    ## Requirement Restatement

                    Add a README.

                    ```dual-control
                    decision: READY_FOR_PRODUCT_REVIEW
                    needs_human_input: true
                    summary: ready
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "[acpx] session claude-main · agent needs reconnect\n");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(1), List.of("acpx", "claude"));
        }
    }
}
