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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(runMetadata.contains("\"schemaVersion\" : 2"));
        assertTrue(runMetadata.contains("\"status\" : \"prepared\""));
        assertTrue(runMetadata.contains("\"nodeId\" : \"design.claude_design\""));
        assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-test\""));
        assertTrue(runMetadata.contains("\"promptPath\" :"));
        assertTrue(runMetadata.contains("\"promptMetadataPath\" :"));
        assertTrue(runMetadata.contains("\"toolUsage\" : null"));
        var promptMetadataPath = tempDir.resolve(runMetadataPath.getParent().resolve("prompts/design-claude.md.meta.json"));
        assertTrue(Files.exists(promptMetadataPath));
        var promptMetadata = Files.readString(promptMetadataPath);
        assertTrue(promptMetadata.contains("\"artifactType\" : \"prompt\""));
        assertTrue(promptMetadata.contains("\"sha256\" :"));
        assertTrue(promptMetadata.contains("\"byteSize\" :"));
    }

    @Test
    void prepareOnlyRunWritesSessionCompactionSeedWhenExperimentEnabled() throws Exception {
        System.setProperty(SessionCompactionPolicy.ENV, "implement_boundary");
        try {
            var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-compact");
            Files.createDirectories(workstreamDir.resolve("memory"));
            Files.writeString(workstreamDir.resolve("requirement.md"), "Implement from typed memory.\n");
            Files.writeString(workstreamDir.resolve("memory/workstream-memory.json"), """
                    {
                      "decidedScope": ["Touch only implementation files"],
                      "nonGoals": ["Do not redesign workflow"],
                      "readFiles": [
                        {"path":"src/a.js","sha":"abcdef123456","summary":"exports a()","trusted":true,"readRunId":"run-old"}
                      ]
                    }
                    """);

            var handler = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true);
            var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                    "agent", "claude",
                    "session", "claude-main",
                    "prompt_template", "implement-claude"
            ));
            var instance = new NodeInstance(
                    "node-implement",
                    "implement.claude_implement",
                    NodeKind.TASK,
                    "acpx.agent_run",
                    NodeStatus.READY,
                    "stage-implement",
                    "iter-001",
                    List.of(),
                    List.of(),
                    Map.of(),
                    null,
                    null,
                    null
            );

            var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-compact", definition, instance));
            var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
            var runMetadata = Files.readString(runMetadataPath);
            assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-compact-compact-implement.claude_implement\""));
            assertTrue(runMetadata.contains("\"sessionCompactionMode\" : \"implement_boundary\""));
            assertTrue(runMetadata.contains("\"sessionCompactionEnabled\" : true"));
            assertTrue(runMetadata.contains("\"sessionCompactionSeedKind\" : \"agent_handoff\""));
            assertTrue(runMetadata.contains("\"sessionCompactionSeedPath\" :"));

            var seedPath = runMetadataPath.getParent().resolve("prompts/session-compaction-seed.md");
            assertTrue(Files.exists(seedPath));
            var seed = Files.readString(seedPath);
            assertTrue(seed.contains("Session Compaction Seed"));
            assertTrue(seed.contains("Touch only implementation files"));
            assertTrue(seed.contains("src/a.js"));

            var promptPath = runMetadataPath.getParent().resolve("prompts/implement-claude.md");
            var prompt = Files.readString(promptPath);
            assertTrue(prompt.contains("session-compaction-seed.md"));
            assertTrue(prompt.contains("Session Compaction Seed"));
        } finally {
            System.clearProperty(SessionCompactionPolicy.ENV);
        }
    }

    @Test
    void runScopedSessionRemainsOptIn() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true);
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "session_scope", "run",
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

        var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
        var runMetadata = Files.readString(runMetadataPath);
        assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-test-run-"));
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
                null,
                new AcpxUsage(
                        100,
                        5,
                        1000,
                        2,
                        1107,
                        new java.math.BigDecimal("0.25"),
                        "USD",
                        "acpx_stream",
                        AcpxUsage.PROVIDER_RUN_USAGE,
                        AcpxUsage.ATTR_EXACT_PROMPT_MATCH,
                        false,
                        0,
                        new java.math.BigDecimal("1.25"),
                        "USD",
                        "zhipu",
                        "glm-5.2",
                        AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS,
                        AcpxProviderMetadata.SOURCE_ACPX_SESSION_JSON,
                        ""
                )
        );

        var runMetadata = Files.readString(runDir.resolve("run.json"));
        assertTrue(runMetadata.contains("\"outputMetadataPath\" :"));
        assertTrue(runMetadata.contains("\"durationMs\" : 3000"));
        assertTrue(runMetadata.contains("\"usage\" :"));
        assertTrue(runMetadata.contains("\"inputTokens\" : 100"));
        assertTrue(runMetadata.contains("\"cachedReadTokens\" : 1000"));
        assertTrue(runMetadata.contains("\"totalTokens\" : 1107"));
        assertTrue(runMetadata.contains("\"costAmount\" : 0.25"));
        assertTrue(runMetadata.contains("\"costCurrency\" : \"USD\""));
        assertTrue(runMetadata.contains("\"source\" : \"acpx_stream\""));
        assertTrue(runMetadata.contains("\"measurementType\" : \"PROVIDER_RUN_USAGE\""));
        assertTrue(runMetadata.contains("\"sessionCostAmount\" : 1.25"));
        var outputMetadata = Files.readString(runDir.resolve("outputs/design-claude.md.meta.json"));
        assertTrue(outputMetadata.contains("\"artifactType\" : \"output\""));
        assertTrue(outputMetadata.contains("\"artifactPath\" : \".agent/workstreams/ws-test/runs/run-001/outputs/design-claude.md\""));
        assertTrue(outputMetadata.contains("\"estimatedTokens\" :"));
        assertTrue(outputMetadata.contains("\"lineCount\" : 2"));
        assertTrue(outputMetadata.contains("\"sha256\" :"));
    }

    @Test
    void completedRunSplitsRawTranscriptFromFinalArtifactOutput() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new TranscriptCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(3, result.outputRefs().size());
        var finalArtifactRef = result.outputRefs().stream()
                .filter(ref -> ref.endsWith("intake-claude-brainstorm.md"))
                .findFirst()
                .orElseThrow();
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith(".transcript.log")));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith(".stderr.log")));

        var outputPath = tempDir.resolve(finalArtifactRef);
        var finalArtifact = Files.readString(outputPath);
        assertTrue(finalArtifact.contains("<dual-artifact type=\"agent_output_contract\">"));
        assertTrue(finalArtifact.contains("decision: READY_FOR_PRODUCT_REVIEW"));
        assertFalse(finalArtifact.contains("[tool]"));
        assertFalse(finalArtifact.contains("[client]"));
        assertFalse(finalArtifact.contains("\n  input:"));
        assertFalse(finalArtifact.contains("\n  output:"));

        var transcriptRef = (String) result.result().get("transcript_ref");
        assertTrue(transcriptRef.endsWith(".transcript.log"));
        var transcript = Files.readString(tempDir.resolve(transcriptRef));
        assertTrue(transcript.contains("[tool] Read huge-file.md"));
        assertTrue(transcript.contains("input:"));
        assertTrue(transcript.contains("output:"));

        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"outputPath\" :"));
        assertTrue(runMetadata.contains("\"transcriptPath\" :"));
        assertTrue(runMetadata.contains("\"transcriptMetadataPath\" :"));
        var transcriptMetadata = Files.readString(tempDir.resolve(transcriptRef + ".meta.json"));
        assertTrue(transcriptMetadata.contains("\"artifactType\" : \"transcript\""));
        assertTrue(transcriptMetadata.contains("\"estimatedTokens\" :"));
    }

    @Test
    void completedAgentRunRecordsAcpxUsageFromSessionStream() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var sessionsDir = tempDir.resolve("sessions");
        Files.createDirectories(workstreamDir);
        Files.createDirectories(sessionsDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(
                new CompletingUsageStreamTransport(sessionsDir),
                false,
                new AcpxUsageReader(sessionsDir)
        );
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
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"usage\" :"));
        assertTrue(runMetadata.contains("\"inputTokens\" : 100"));
        assertTrue(runMetadata.contains("\"cachedReadTokens\" : 1000"));
        assertTrue(runMetadata.contains("\"costAmount\" : 0.25"));
        assertTrue(runMetadata.contains("\"providerModelIdentity\" :"));
        assertTrue(runMetadata.contains("\"provider\" : \"zhipu\""));
        assertTrue(runMetadata.contains("\"model\" : \"glm-5.2\""));
        assertTrue(runMetadata.contains("\"billingMode\" : \"subscription_token_only\""));
    }

    @Test
    void nonImplementationAgentNodeRunsWithoutApproveAll() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var transport = new CapturingApprovalTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, readyInstance("intake.claude_brainstorm")));

        assertEquals(false, transport.lastApproveAll);
        assertEquals("observe", transport.lastPermissionProfile.name());
        assertTrue(transport.lastPermissionProfile.allowedTools().contains("Read"));
        assertFalse(transport.lastPermissionProfile.allowedTools().contains("Bash(git *)"));
        assertFalse(transport.lastPermissionProfile.autoApproveKinds().contains("execute"));
    }

    @Test
    void contractDiscoveryProfileDoesNotAllowDirectRead() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var transport = new CapturingApprovalTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm",
                "permission_profile", "contract_discovery"
        ));

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, readyInstance("intake.claude_brainstorm")));

        assertEquals(false, transport.lastApproveAll);
        assertEquals("contract_discovery", transport.lastPermissionProfile.name());
        assertFalse(transport.lastPermissionProfile.allowedTools().contains("Read"));
        assertTrue(transport.lastPermissionProfile.allowedTools().contains("Grep"));
        assertTrue(transport.lastPermissionProfile.allowedTools().contains("Glob"));
        assertEquals(List.of("search"), transport.lastPermissionProfile.autoApproveKinds());
    }

    @Test
    void nodePermissionProfileIsPassedToAcpxTransport() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Review the request first.\n");

        var transport = new CapturingApprovalTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("design.codex_arch_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "codex",
                "session", "codex-reviewer",
                "prompt_template", "design-codex-review",
                "permission_profile", "review"
        ));

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, readyInstance("design.codex_arch_review")));

        assertEquals(false, transport.lastApproveAll);
        assertEquals("review", transport.lastPermissionProfile.name());
        assertTrue(transport.lastPermissionProfile.allowedTools().contains("Grep"));
        assertFalse(transport.lastPermissionProfile.allowedTools().contains("Bash(rg *)"));
        assertFalse(transport.lastPermissionProfile.autoApproveKinds().contains("execute"));
    }

    @Test
    void implementationAgentNodeRunsWithApproveAllWhenConfigured() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement the approved plan.\n");

        var transport = new CapturingApprovalTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "implement-claude",
                "approve_all", true
        ));

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, readyInstance("implement.claude_implement")));

        assertEquals(true, transport.lastApproveAll);
        assertEquals("implement", transport.lastPermissionProfile.name());
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
        assertEquals("EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT", result.result().get("process_usability"));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("intake-claude-brainstorm.md")));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 5"));
        assertTrue(runMetadata.contains("\"status\" : \"completed\""));
    }

    @Test
    void completesReconnectExitWhenStdoutHasCompleteTerminalArtifact() throws Exception {
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

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(5, result.result().get("exit_code"));
        assertEquals("EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT", result.result().get("process_usability"));
        assertEquals("RECOVERABLE_AGENT_RECONNECT", result.result().get("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 5"));
        assertTrue(runMetadata.contains("\"status\" : \"completed\""));
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

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(0, result.result().get("exit_code"));
        assertEquals("EXITED_ZERO", result.result().get("process_usability"));
        assertEquals(2, result.outputRefs().size());
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("intake-claude-brainstorm.transcript.log")));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("intake-claude-brainstorm.stderr.log")));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 0"));
        assertTrue(runMetadata.contains("\"failureReason\" : \"RECOVERABLE_AGENT_RECONNECT\""));
        assertTrue(runMetadata.contains("\"executionAttempt\" :"), runMetadata);
        assertTrue(runMetadata.contains("\"status\" : \"RECOVERABLE_FAILED\""), runMetadata);
        assertTrue(runMetadata.contains("\"processStatus\" : \"RECONNECT_REQUIRED\""), runMetadata);
        assertTrue(runMetadata.contains("\"failureSignal\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
        assertTrue(runMetadata.contains("\"message\" : \"agent needs reconnect"), runMetadata);
        assertTrue(runMetadata.contains("\"measurementType\" : \"UNAVAILABLE\""), runMetadata);
        assertTrue(runMetadata.contains("\"unavailableReason\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
    }

    @Test
    void treatsTimeoutShapedIncompleteOutputAsRecoverableFailure() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement the approved plan.\n");

        var handler = new AcpxAgentTaskHandler(new TimeoutInterruptedExitZeroTransport(), false);
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "implement-claude"
        ));
        var instance = new NodeInstance(
                "node-implement",
                "implement.claude_implement",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-implement",
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
        assertEquals(0, result.result().get("exit_code"));
        assertEquals("INTERRUPTED", result.result().get("process_usability"));
        assertEquals(true, result.result().get("acpx_failure.recoverable"));
        assertEquals("RECOVERABLE_AGENT_OUTPUT_INTERRUPTED", result.result().get("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 0"));
        assertTrue(runMetadata.contains("\"failureReason\" : \"RECOVERABLE_AGENT_OUTPUT_INTERRUPTED\""));
    }

    @Test
    void recordsSessionBootstrapFailureAsAdapterBoundaryFailure() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new SessionBootstrapFailedTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals(4, result.result().get("exit_code"));
        assertEquals("KILLED_AFTER_RECOVERABLE_SIGNAL", result.result().get("process_usability"));
        assertEquals(true, result.result().get("acpx_failure.recoverable"));
        assertEquals("RECOVERABLE_SESSION_BOOTSTRAP_FAILED", result.result().get("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"failureReason\" : \"RECOVERABLE_SESSION_BOOTSTRAP_FAILED\""));
        assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-test\""));
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
        assertEquals("completed", result.outcome());
        assertEquals("EXITED_ZERO", result.result().get("process_usability"));
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

    @Test
    void classifiesRateLimitFromStdoutAsRecoverable() throws Exception {
        var stdout = tempDir.resolve("stdout.log");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(stdout, "[error] RUNTIME: API Error: Request rejected (429) 已达到 5 小时的使用上限\n");
        Files.writeString(stderr, "[acpx] session claude-main · agent connected\n");

        var failure = new AcpxFailureClassifier().classify(stdout, stderr, 1);

        assertEquals("RECOVERABLE_RATE_LIMIT", failure.reason());
        assertEquals(true, failure.recoverable());
        assertTrue(failure.message().contains("quota"));
    }

    @Test
    void classifiesAuthFailureFromRuntimeStdoutBeforeReconnectWrapper() throws Exception {
        var stdout = tempDir.resolve("stdout.log");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(stdout, """
                [client] session/new (running)
                Failed to authenticate. API Error: 401 Invalid authentication credentials
                [error] RUNTIME: Internal error: Failed to authenticate. API Error: 401 Invalid authentication credentials
                """);
        Files.writeString(stderr, "[acpx] session claude-main · agent needs reconnect\n");

        var failure = new AcpxFailureClassifier().classify(stdout, stderr, 1);

        assertEquals("RECOVERABLE_AGENT_AUTH_FAILED", failure.reason());
        assertEquals(true, failure.recoverable());
        assertTrue(failure.message().contains("authentication"));
    }

    @Test
    void ignoresBusinessTextRateLimitMentionsWhenReconnectWrapperIsPresent() throws Exception {
        var stdout = tempDir.resolve("stdout.log");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(stdout, "Product risk: bulk validation could hit rate limit for validators.\n");
        Files.writeString(stderr, "[acpx] session codex-reviewer · agent needs reconnect\n");

        var failure = new AcpxFailureClassifier().classify(stdout, stderr, 4);

        assertEquals("RECOVERABLE_AGENT_RECONNECT", failure.reason());
        assertEquals(true, failure.recoverable());
    }

    @Test
    void classifiesProviderOverloadFromAcpxSessionStreamBeforeReconnectWrapper() throws Exception {
        var stdout = tempDir.resolve("stdout.log");
        var stderr = tempDir.resolve("stderr.log");
        var sessionsDir = tempDir.resolve("sessions");
        var sessionId = "ff545b54-96a8-4bf0-84e4-96f8789700c8";
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ") · agent needs reconnect\n");
        Files.writeString(
                sessionsDir.resolve(sessionId + ".stream.ndjson"),
                """
                {"type":"session/prompt/failed","error":"Internal error: API Error: 529 [1305][该模型当前访问量过大，请您稍后再试]"}
                """
        );

        var failure = new AcpxFailureClassifier(
                new AcpxFailureSignalCatalog(),
                new AcpxSessionStreamFailureEvidenceReader(sessionsDir)
        ).classify(stdout, stderr, 1);

        assertEquals("RECOVERABLE_AGENT_OVERLOADED", failure.reason());
        assertEquals(true, failure.recoverable());
        assertTrue(failure.message().contains("529"));
    }

    @Test
    void classifiesModelMismatchFromAcpxSessionStreamBeforeReconnectWrapper() throws Exception {
        var stdout = tempDir.resolve("stdout.log");
        var stderr = tempDir.resolve("stderr.log");
        var sessionsDir = tempDir.resolve("sessions");
        var sessionId = "cd0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ") · agent needs reconnect\n");
        Files.writeString(
                sessionsDir.resolve(sessionId + ".stream.ndjson"),
                """
                {"type":"session/prompt/failed","error":"Internal error: API Error: 400 {\\"error\\":{\\"message\\":\\"The supported API model names are deepseek-v4-pro or deepseek-v4-flash, but you passed glm-5.2.\\",\\"type\\":\\"invalid_request_error\\"}}"}
                """
        );

        var failure = new AcpxFailureClassifier(
                new AcpxFailureSignalCatalog(),
                new AcpxSessionStreamFailureEvidenceReader(sessionsDir)
        ).classify(stdout, stderr, 1);

        assertEquals("RECOVERABLE_AGENT_MODEL_MISMATCH", failure.reason());
        assertEquals(true, failure.recoverable());
        assertTrue(failure.message().contains("rebuild"));
    }

    @Test
    void ignoresPromptPayloadTextFromSessionStreamWhenClassifyingProviderFailure() throws Exception {
        var stdout = tempDir.resolve("stdout.log");
        var stderr = tempDir.resolve("stderr.log");
        var sessionsDir = tempDir.resolve("sessions");
        var sessionId = "edab1e36-b5c5-42c6-a50d-cbb88d74651d";
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "[client] initialize (running)\n[error] RUNTIME: Interrupted\n");
        Files.writeString(stderr, "[acpx] session codex-reviewer (" + sessionId + ") · agent needs reconnect\n");
        Files.writeString(
                sessionsDir.resolve(sessionId + ".stream.ndjson"),
                """
                {"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"Product risk: could hit rate limit for validators."}]}}
                {"jsonrpc":"2.0","result":{"stopReason":"cancelled"},"id":3}
                """
        );

        var failure = new AcpxFailureClassifier(
                new AcpxFailureSignalCatalog(),
                new AcpxSessionStreamFailureEvidenceReader(sessionsDir)
        ).classify(stdout, stderr, 4);

        assertEquals("RECOVERABLE_AGENT_RECONNECT", failure.reason());
        assertEquals(true, failure.recoverable());
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

    private static final class TranscriptCompletingTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    [client] initialize (running)
                    [tool] Read huge-file.md (completed)
                      input: {"file":"huge-file.md"}
                      output:
                        This tool transcript must stay out of workflow outputRefs.

                    <dual-artifact type="agent_output_contract">
                    {"artifact_type":"agent_output_contract","summary":"Ready"}
                    </dual-artifact>

                    ```dual-control
                    decision: READY_FOR_PRODUCT_REVIEW
                    needs_human_input: false
                    summary: ready
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(1), List.of("acpx", "claude"));
        }
    }

    private static final class TimelineCompletingTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    <dual-artifact type="agent_output_contract">
                    {"artifact_type":"agent_output_contract","summary":"Ready"}
                    </dual-artifact>

                    ```dual-control
                    decision: READY_FOR_PRODUCT_REVIEW
                    needs_human_input: false
                    summary: ready
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude"),
                    List.of(new AcpxExecutionEvent(
                            "terminal_control_detected",
                            "stdout",
                            7,
                            now.plusMillis(750),
                            750
                    ))
            );
        }
    }

    private static final class RejectionEnvelopeTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    ## Architecture Review

                    Rejected: token refresh missing.

                    <dual-artifact type="agent_output_contract" version="1">
                    {"node_id":"design.codex_arch_review","artifact_type":"agent_output_contract","decision":"REJECTED","summary":"token refresh missing","must_fix":["refresh token on expiry"],"open_risks":["race on logout"],"changed_files":["src/auth.py"],"stage_outcome":"needs_revision","blocking":true}
                    </dual-artifact>

                    ```dual-control
                    decision: REJECTED
                    needs_human_input: false
                    summary: token refresh missing
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(1), List.of("acpx", "codex"));
        }
    }

    @Test
    void runMetadataRecordsSessionScopeStageAndIteration() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-sess");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new TranscriptCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-sess", definition, readyInstance("intake.claude_brainstorm")));

        var runJson = Files.walk(workstreamDir.resolve("runs"))
                .filter(path -> path.getFileName().toString().equals("run.json"))
                .findFirst()
                .orElseThrow();
        var runMetadata = Files.readString(runJson);

        // P1: run.json 必须记录 session scope 决策，便于 benchmark 解释 token 变化
        assertTrue(runMetadata.contains("\"sessionScope\" : \"workstream\""), runMetadata);
        assertTrue(runMetadata.contains("\"sessionSegmentKey\" :"), runMetadata);
        assertTrue(runMetadata.contains("\"stage\" : \"stage-test\""), runMetadata);
        assertTrue(runMetadata.contains("\"semanticIteration\" : \"iter-001\""), runMetadata);
        // session name 恒含 workstreamId，永不裸名
        assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-sess\""), runMetadata);
    }

    @Test
    void completedRunEmbedsExplorationPolicyResultInRunMetadata() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-explore");
        Files.createDirectories(workstreamDir.resolve("memory"));
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");
        Files.writeString(workstreamDir.resolve("memory/exploration-plan-node-intake.json"), """
                {
                  "schemaVersion": 1,
                  "artifactType": "agent_exploration_plan",
                  "workstreamId": "ws-explore",
                  "stageId": "intake",
                  "nodeDefinitionId": "intake.claude_brainstorm",
                  "targetNodeDefinitionId": "intake.claude_brainstorm",
                  "nodeInstanceId": "node-intake",
                  "iterationId": "iter-001",
                  "explorationIntent": "contract_discovery",
                  "preferredCapabilityKinds": ["symbol_index", "direct_file_read"],
                  "allowedDirectReadFiles": ["README.md"],
                  "maxDirectReadFiles": 1,
                  "failureMode": "warn"
                }
                """);

        var handler = new AcpxAgentTaskHandler(new TranscriptCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-explore", definition,
                readyInstance("intake.claude_brainstorm")));

        var runJson = Files.walk(workstreamDir.resolve("runs"))
                .filter(path -> path.getFileName().toString().equals("run.json"))
                .findFirst()
                .orElseThrow();
        var runMetadata = Files.readString(runJson);
        assertTrue(runMetadata.contains("\"explorationPolicyResult\" :"), runMetadata);
        assertTrue(runMetadata.contains("\"READ_OUTSIDE_EXPLORATION_SCOPE\""), runMetadata);
        assertTrue(runMetadata.contains("\"SCOPE_EXPANSION_REQUEST_MISSING\""), runMetadata);
        assertTrue(runMetadata.contains("\"agentExplorationPlanPath\" :"), runMetadata);
        assertTrue(runMetadata.contains("exploration-plan-node-intake.json"), runMetadata);
        assertTrue(runMetadata.contains("\"contextGovernanceActualReportPath\" :"), runMetadata);
    }

    @Test
    void runWritesPromptManifestByDefault() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-manifest");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");
        var handler = new AcpxAgentTaskHandler(new TranscriptCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude", "session", "claude-main", "prompt_template", "intake-claude-brainstorm"));
        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-manifest", definition,
                readyInstance("intake.claude_brainstorm")));

        // run.json references the manifest
        var runJson = Files.walk(workstreamDir.resolve("runs"))
                .filter(p -> p.getFileName().toString().equals("run.json")).findFirst().orElseThrow();
        var runMetadata = Files.readString(runJson);
        assertTrue(runMetadata.contains("\"promptManifestPath\" :"), runMetadata);

        // manifest exists with sections + totals (default-on, independent of DUAL_AGENT_PROMPT_DEBUG)
        var manifest = Files.walk(workstreamDir.resolve("runs"))
                .filter(p -> p.getFileName().toString().endsWith(".manifest.json")).findFirst().orElseThrow();
        var manifestText = Files.readString(manifest);
        assertTrue(manifestText.contains("\"totalEstimatedTokens\""), manifestText);
        assertTrue(manifestText.contains("\"sections\""), manifestText);
        assertTrue(manifestText.contains("\"sourceType\""), manifestText);
        assertTrue(manifestText.contains("\"requirement\""), manifestText);

        // DUAL_AGENT_PROMPT_DEBUG unset -> NO full-content debug dump (manifest ≠ content dump)
        var debugDump = Files.walk(workstreamDir.resolve("runs"))
                .filter(p -> p.getFileName().toString().endsWith(".debug.json")).findFirst();
        assertTrue(debugDump.isEmpty(), "content debug dump must NOT exist when DUAL_AGENT_PROMPT_DEBUG unset");
    }

    @Test
    void runWritesExecutionTimeline() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-timeline");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");
        var handler = new AcpxAgentTaskHandler(new TimelineCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude", "session", "claude-main", "prompt_template", "intake-claude-brainstorm"));

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-timeline", definition,
                readyInstance("intake.claude_brainstorm")));

        var runJson = Files.walk(workstreamDir.resolve("runs"))
                .filter(p -> p.getFileName().toString().equals("run.json")).findFirst().orElseThrow();
        var runMetadata = Files.readString(runJson);
        assertTrue(runMetadata.contains("\"executionTimeline\" :"), runMetadata);
        assertTrue(runMetadata.contains("\"kind\" : \"terminal_control_detected\""), runMetadata);
        assertTrue(runMetadata.contains("\"elapsedMs\" : 750"), runMetadata);
    }

    @Test
    void completedRunWritesSemanticMemoryFromContractEnvelope() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-sem-run");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("requirement.md"), "Ship token refresh.\n");
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {"objective":"o","status":"active","scope":{"included":["token refresh"],"excluded":[]},
                 "nonGoals":["SSO"],"acceptanceCriteria":["tokens refresh"],"constraints":[],"riskAreas":[]}
                """);

        var handler = new AcpxAgentTaskHandler(new RejectionEnvelopeTransport(), false);
        var definition = new NodeDefinition("design.codex_arch_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "codex", "session", "codex-main", "prompt_template", "design-codex-review"
        ));
        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-sem-run", definition,
                readyInstance("design.codex_arch_review")));

        var memory = Files.readString(workstreamDir.resolve("memory/workstream-memory.json"));
        assertTrue(memory.contains("token refresh"), "decidedScope must come from task-contract: " + memory);
        assertTrue(memory.contains("REJECTED"), "verdict decision must be recorded: " + memory);
        assertTrue(memory.contains("refresh token on expiry"), "open issue must be recorded: " + memory);
    }

    private static NodeInstance readyInstance(String definitionId) {
        return new NodeInstance(
                "node-" + definitionId.replace('.', '-'),
                definitionId,
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-test",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );
    }

    private static final class CapturingApprovalTransport extends AcpxAgentTransport {
        Boolean lastApproveAll;
        AcpxPermissionProfile lastPermissionProfile;

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            lastApproveAll = request.approveAll();
            lastPermissionProfile = request.permissionProfile();
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    ## Requirement Restatement

                    Ready.

                    ```dual-control
                    decision: READY_FOR_PRODUCT_REVIEW
                    needs_human_input: false
                    summary: ready
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(1), List.of("acpx", "claude"));
        }

        @Override
        public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws java.io.IOException {
            lastApproveAll = request.approveAll();
            lastPermissionProfile = request.permissionProfile();
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    ## Product Review

                    Ready.

                    ```dual-control
                    decision: PASS
                    needs_human_input: false
                    summary: pass
                    ```

                    [done] end_turn
                    """);
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(1), List.of("acpx", "codex"));
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

    private static final class TimeoutInterruptedExitZeroTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    # Implementation Report

                    The agent produced a long report but stopped before writing the terminal
                    """);
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(605), List.of("acpx", "--timeout", "600", "claude"));
        }
    }

    private static final class SessionBootstrapFailedTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "");
            Files.writeString(request.stderrPath(), """

                    [dual] acpx session ensure failed agent=claude session=claude-main-ws-test timed_out=true exit_code=-1
                    """);
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(4, now, now.plusSeconds(45), List.of(
                    "acpx", "claude", "sessions", "ensure", "--name", request.sessionName()
            ));
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

    private static final class CompletingUsageStreamTransport extends AcpxAgentTransport {
        private final Path sessionsDir;

        private CompletingUsageStreamTransport(Path sessionsDir) {
            this.sessionsDir = sessionsDir;
        }

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            var sessionId = "cd0caac4-0c68-423a-a448-60a58353ad7c";
            Files.createDirectories(request.stdoutPath().getParent());
            Files.createDirectories(sessionsDir);
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
            Files.writeString(request.stderrPath(), "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
            Files.writeString(sessionsDir.resolve(sessionId + ".json"), """
                    {
                      "current_model_id": "default",
                      "available_models": [{
                        "id": "model",
                        "description": "Use the default model (currently glm-5.2[1m]) · bundled subscription"
                      }]
                    }
                    """);
            var promptText = Files.readString(request.promptPath())
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");
            Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                    {"jsonrpc":"2.0","id":7,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"%s"}]}}
                    {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":110,"size":200000,"cost":{"amount":0.25,"currency":"USD"}}}}
                    {"jsonrpc":"2.0","id":7,"result":{"usage":{"inputTokens":100,"outputTokens":5,"cachedReadTokens":1000,"cachedWriteTokens":2,"totalTokens":1107}}}
                    """.formatted(promptText));
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return new AcpxRunResult(0, now, now.plusSeconds(1), List.of("acpx", "claude"));
        }
    }
}
