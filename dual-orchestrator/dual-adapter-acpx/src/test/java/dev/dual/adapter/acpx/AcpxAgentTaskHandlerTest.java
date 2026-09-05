package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.runtime.handler.AgentRunMetadataFinalizer;
import dev.dual.runtime.handler.ArtifactContractValidator;
import dev.dual.runtime.handler.RepositoryFactBinding;
import dev.dual.runtime.handler.TaskHandler;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowIterationDocument;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxAgentTaskHandlerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void invalidRevisionContextStopsBeforeProviderDispatch() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-invalid-revision");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Revise the task contract.\n");
        var definition = new NodeDefinition(
                "intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude", "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"));
        var instance = new NodeInstance(
                "node-intake-revision", definition.id(), NodeKind.TASK, "acpx.agent_run",
                NodeStatus.READY, "stage-intake", "iter-002", List.of(), List.of(), Map.of(),
                null, null, null);

        var result = new AcpxAgentTaskHandler(new ThrowingTransport(), false).execute(
                new TaskHandler.TaskContext(tempDir, "ws-invalid-revision", definition, instance));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_REVISION_CONTEXT_INVALID", result.outcome());
        assertTrue(result.outputRefs().isEmpty(), "provider outputs must not exist before context validation");
        assertFalse(Files.exists(workstreamDir.resolve("runs")),
                "no provider run may be prepared when revision lineage is unresolved");
    }
    private static final ObjectMapper LEDGER_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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
                "prompt_template", "design-claude",
                "permission_profile", "review"
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

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));

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
        assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-test-run-"));
        assertTrue(runMetadata.contains("\"sessionScope\" : \"run\""));
        assertTrue(runMetadata.contains("\"sessionScopeReason\" : \"execution-policy:occurrence-bound\""));
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
    void normalRunWritesPreparedMetadataBeforeLaunchingAgentProcess() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var transport = new PreparedMetadataObservedTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "permission_profile", "implement",
                "prompt_template", "implement-claude"
        ));

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("implement.claude_implement")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertTrue(transport.sawPreparedRunJson);
        assertTrue(transport.localBindingAllowed);
    }

    @Test
    void adjacentE1ExecutionsDoNotReuseAConflictingPolicySession() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-adjacent-e1");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement the accepted change.\n");

        var transport = new PolicyConflictGuardTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "session_scope", "workstream",
                "permission_profile", "implement",
                "prompt_template", "implement-claude"
        ));
        var instance = readyInstance("implement.claude_implement");

        var first = handler.execute(taskContext(tempDir, "ws-adjacent-e1", definition, instance));
        Thread.sleep(5L);
        var second = handler.execute(taskContext(tempDir, "ws-adjacent-e1", definition, instance));

        assertEquals(NodeStatus.COMPLETED, first.status());
        assertEquals(NodeStatus.COMPLETED, second.status());
        assertEquals(2, transport.sessions.size());
        assertEquals(2, transport.sessions.stream().distinct().count());
        assertEquals(2, transport.policyShas.stream().distinct().count());
        assertTrue(transport.sessions.stream().allMatch(
                session -> session.startsWith("claude-main-ws-adjacent-e1-run-")));
    }

    @Test
    void acceptanceBindingMismatchFailsBeforeTransportInvocation() throws Exception {
        var fixture = AcceptanceContractProjectionFixture.create(
                tempDir, "ws-test", "acceptance.codex_verifier_review");
        var metadata = new ObjectMapper().readTree(fixture.metadataPath().toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) metadata).put("taskContractSha256", "0".repeat(64));
        new ObjectMapper().writeValue(fixture.metadataPath().toFile(), metadata);
        var transport = new CountingAcceptanceTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition(
                "acceptance.codex_verifier_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "codex",
                "session", "codex-reviewer",
                "permission_profile", "review",
                "prompt_template", "acceptance-codex-review",
                "expected_artifact", "acceptance_review"));
        var instance = new NodeInstance(
                "node-acceptance", definition.id(), NodeKind.TASK, definition.handlerType(),
                NodeStatus.READY, "acceptance", "iter-001", fixture.inputRefs(), List.of(), Map.of(),
                null, null, null);

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED", result.outcome());
        assertEquals(0, transport.calls);
    }

    @Test
    void configuredPostContractNodeWithoutQualificationFailsBeforeTransportInvocation() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-unqualified");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement the approved change.\n");
        var transport = new CountingAcceptanceTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition(
                "design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "permission_profile", "observe",
                "prompt_template", "design-claude",
                "requires_approved_task_contract", true));
        var instance = readyInstance(definition.id());

        var result = handler.execute(taskContext(tempDir, "ws-unqualified", definition, instance));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED", result.outcome());
        assertEquals(0, transport.calls);
    }

    @Test
    void prosePrefixedFinalMessageUsesOneCorrectionAndOnlyCorrectedEnvelopeRoutes() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-correction");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Document the existing transition validation.\n");

        var transport = new ContractCorrectionTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "design-claude"
        ));
        var instance = readyInstance("design.claude_design");

        var candidate = handler.execute(taskContext(
                tempDir, "ws-correction", definition, instance));
        var validated = new ArtifactContractValidator().validate(
                tempDir,
                "ws-correction",
                instance.nodeInstanceId(),
                definition,
                instance.inputRefs(),
                candidate
        );
        validated = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, validated);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertEquals("READY_FOR_ARCH_REVIEW", validated.result().get("decision"));
        assertEquals(2, transport.calls);
        assertEquals(List.of(
                "claude-main-ws-correction",
                "claude-main-ws-correction"
        ), transport.sessions);
        assertTrue(transport.correctionProfile.denyAll());
        assertFalse(transport.correctionProfile.terminalEnabled());
        assertFalse(transport.correctionProfile.approveAll());

        var runMetadataPath = tempDir.resolve((String) validated.result().get("run_metadata"));
        var runDir = runMetadataPath.getParent();
        var authoritativeOutput = Files.readString(runDir.resolve("outputs/design-claude.json"));
        assertTrue(authoritativeOutput.startsWith("{"));
        assertFalse(authoritativeOutput.contains("I have completed the design"));
        assertTrue(Files.readString(runDir.resolve("outputs/design-claude.attempt-1.json"))
                .startsWith("I have completed the design"));
        assertTrue(Files.exists(runDir.resolve("outputs/design-claude.contract-correction.json")));

        var metadata = OBJECT_MAPPER.readTree(runMetadataPath.toFile());
        var acquisition = metadata.path("agentResultAcquisition");
        assertTrue(acquisition.path("correctionAttempted").asBoolean());
        assertEquals(1, acquisition.path("maxCorrectionAttempts").asInt());
        assertEquals(2, acquisition.path("selectedCandidateOccurrence").asInt());
        assertEquals("PARSE_FAILURE", acquisition.path("correctionReason").asText());
        assertEquals("ATTEMPTED", acquisition.path("correctionDisposition").asText());
        assertEquals(2, acquisition.path("occurrences").size());
        assertEquals("fixture-initial", acquisition.path("occurrences").get(0).path("requestId").asText());
        assertFalse(acquisition.path("occurrences").get(0).path("contractDecodable").asBoolean());
        assertEquals("AGENT_RESULT_PARSE_FAILED",
                acquisition.path("occurrences").get(0).path("parseErrorCode").asText());
        assertEquals("NOT_EVALUATED",
                acquisition.path("occurrences").get(0).path("contractCorrectionDisposition").asText());
        assertEquals(101, acquisition.path("occurrences").get(0).path("usage").path("totalTokens").asInt());
        assertEquals("fixture-correction", acquisition.path("occurrences").get(1).path("requestId").asText());
        assertTrue(acquisition.path("occurrences").get(1).path("contractDecodable").asBoolean());
        assertTrue(acquisition.path("occurrences").get(1).path("contractSchemaValid").asBoolean());
        assertEquals("NONE",
                acquisition.path("occurrences").get(1).path("contractCorrectionDisposition").asText());
        assertEquals(23, acquisition.path("occurrences").get(1).path("usage").path("totalTokens").asInt());
        assertEquals(124, metadata.path("usage").path("totalTokens").asInt());
        assertEquals(AcpxUsage.ATTR_MULTI_PROMPT_SUM, metadata.path("usage").path("attribution").asText());
        assertEquals(3000, metadata.path("durationMs").asLong());
    }

    @Test
    void schemaMissingFieldUsesOneCorrectionAndOnlyCorrectedEnvelopeRoutes() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-schema-correction");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Verify the completed README change.\n");

        var transport = new SchemaContractCorrectionTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition(
                "implement.claude_self_test_report",
                NodeKind.TASK,
                "acpx.agent_run",
                Map.of(
                        "agent", "claude",
                        "session", "claude-main",
                        "permission_profile", "implement",
                        "prompt_template", "implement-claude-self-test"
                )
        );
        var instance = readyInstance("implement.claude_self_test_report");

        var candidate = handler.execute(taskContext(
                tempDir,
                "ws-schema-correction",
                definition,
                instance
        ));
        var validated = new ArtifactContractValidator().validate(
                tempDir,
                "ws-schema-correction",
                instance.nodeInstanceId(),
                definition,
                instance.inputRefs(),
                candidate
        );
        validated = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, validated);

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertEquals("SELF_TEST_COMPLETE", validated.result().get("decision"));
        assertEquals(2, transport.calls);
        assertEquals(1, transport.sessions.stream().distinct().count());
        assertEquals(2, transport.sessions.size());
        assertTrue(transport.sessions.getFirst().startsWith(
                "claude-main-ws-schema-correction-run-"));
        assertTrue(transport.correctionProfile.denyAll());
        assertFalse(transport.correctionProfile.terminalEnabled());
        assertFalse(transport.correctionProfile.approveAll());

        var runMetadataPath = tempDir.resolve((String) validated.result().get("run_metadata"));
        var metadata = OBJECT_MAPPER.readTree(runMetadataPath.toFile());
        var acquisition = metadata.path("agentResultAcquisition");
        assertTrue(acquisition.path("correctionAttempted").asBoolean());
        assertEquals("SCHEMA_FAILURE", acquisition.path("correctionReason").asText());
        assertEquals("ATTEMPTED", acquisition.path("correctionDisposition").asText());
        assertEquals(2, acquisition.path("occurrences").size());
        assertTrue(acquisition.path("occurrences").get(0).path("contractDecodable").asBoolean());
        assertFalse(acquisition.path("occurrences").get(0).path("contractSchemaValid").asBoolean());
        assertEquals("SCHEMA_REPAIRABLE",
                acquisition.path("occurrences").get(0).path("contractCorrectionDisposition").asText());
        assertEquals("result.missing_evidence is required",
                acquisition.path("occurrences").get(0).path("contractValidationErrors").get(0).asText());
        assertTrue(acquisition.path("occurrences").get(1).path("contractSchemaValid").asBoolean());
        assertEquals("NONE",
                acquisition.path("occurrences").get(1).path("contractCorrectionDisposition").asText());
    }

    @Test
    void schemaCorrectionStillInvalidCannotRouteAndDoesNotStartThirdPrompt() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-schema-correction-fails");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Verify the completed README change.\n");

        var transport = new SchemaContractCorrectionTransport(false);
        var definition = new NodeDefinition(
                "implement.claude_self_test_report",
                NodeKind.TASK,
                "acpx.agent_run",
                Map.of(
                        "agent", "claude",
                        "session", "claude-main",
                        "prompt_template", "implement-claude-self-test"
                )
        );
        var instance = readyInstance("implement.claude_self_test_report");

        var candidate = new AcpxAgentTaskHandler(transport, false).execute(taskContext(
                tempDir,
                "ws-schema-correction-fails",
                definition,
                instance
        ));
        var validated = new ArtifactContractValidator().validate(
                tempDir,
                "ws-schema-correction-fails",
                instance.nodeInstanceId(),
                definition,
                instance.inputRefs(),
                candidate
        );

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals(2, transport.calls);
        var runMetadataPath = tempDir.resolve((String) candidate.result().get("run_metadata"));
        var acquisition = OBJECT_MAPPER.readTree(runMetadataPath.toFile()).path("agentResultAcquisition");
        assertTrue(acquisition.path("correctionAttempted").asBoolean());
        assertFalse(acquisition.path("occurrences").get(1).path("contractSchemaValid").asBoolean());
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
                    "permission_profile", "implement",
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

            var result = handler.execute(taskContext(tempDir, "ws-compact", definition, instance));
            var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
            var runMetadata = Files.readString(runMetadataPath);
            assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-compact-run-"));
            assertTrue(runMetadata.contains("\"sessionScope\" : \"run\""));
            assertTrue(runMetadata.contains("\"sessionCompactionMode\" : \"implement_boundary\""));
            assertTrue(runMetadata.contains("\"sessionCompactionEnabled\" : true"));
            assertTrue(runMetadata.contains("occurrence-bound session retained"));
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
    void prepareOnlyProviderCacheAwareWritesCompactionSeedWhenStabilitySignalTriggers() throws Exception {
        System.setProperty(SessionCompactionPolicy.ENV, "provider_cache_aware");
        try {
            var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-cache-aware");
            Files.createDirectories(workstreamDir.resolve("memory"));
            Files.writeString(workstreamDir.resolve("requirement.md"), "Implement from typed memory.\n");
            Files.writeString(workstreamDir.resolve("memory/workstream-memory.json"), """
                    {
                      "decidedScope": ["Keep the implementation boundary narrow"],
                      "readFiles": [
                        {"path":"src/cache.js","sha":"123456abcdef","summary":"exports cache policy","trusted":true,"readRunId":"run-old"}
                      ]
                    }
                    """);
            writeCompactionMetrics("ws-cache-aware", 1_500_000, 0);

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

            var result = handler.execute(taskContext(tempDir, "ws-cache-aware", definition, instance));
            var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
            var runMetadata = Files.readString(runMetadataPath);
            assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-cache-aware-compact-implement.claude_implement\""));
            assertTrue(runMetadata.contains("\"sessionCompactionMode\" : \"provider_cache_aware\""));
            assertTrue(runMetadata.contains("\"sessionCompactionEnabled\" : true"));
            assertTrue(runMetadata.contains("\"sessionCompactionReason\" : \"env:DUAL_SESSION_COMPACTION_EXPERIMENT=provider_cache_aware; stability_compaction; cachedReadTokens=1500000 >= 1000000\""));
            assertTrue(runMetadata.contains("\"sessionCompactionSeedKind\" : \"agent_handoff\""));
            assertTrue(runMetadata.contains("\"sessionCompactionSeedPath\" :"));

            var seedPath = runMetadataPath.getParent().resolve("prompts/session-compaction-seed.md");
            assertTrue(Files.exists(seedPath));
            var seed = Files.readString(seedPath);
            assertTrue(seed.contains("Session Compaction Seed"));
            assertTrue(seed.contains("Keep the implementation boundary narrow"));
            assertTrue(seed.contains("src/cache.js"));
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

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));

        var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
        var runMetadata = Files.readString(runMetadataPath);
        assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-test-run-"));
    }

    @Test
    void completedRunWritesArtifactMetadataSidecars() throws Exception {
        var runDir = tempDir.resolve(".agent/workstreams/ws-test/runs/run-001");
        var promptPath = runDir.resolve("prompts/design-claude.md");
        var outputPath = runDir.resolve("outputs/design-claude.json");
        var stderrPath = runDir.resolve("outputs/design-claude.stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(outputPath.getParent());
        Files.writeString(promptPath, "# Prompt\n");
        Files.writeString(outputPath, "{\"schema_version\":1}\n");
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
                null,
                fixtureResult(
                        0,
                        startedAt,
                        finishedAt,
                        List.of("acpx", "claude"),
                        List.of(),
                        null,
                        AcpxPromptCompletion.EMPTY
                ),
                List.of(),
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
                ),
                null,
                null,
                null,
                repositoryFactBindingFixture("ws-test", "node-design", "run-001")
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
        var executionAttempt = OBJECT_MAPPER.readTree(runMetadata).path("executionAttempt");
        assertEquals("run-001", executionAttempt.path("runId").asText());
        assertEquals("ws-test", executionAttempt.path("workstreamId").asText());
        assertEquals("design.claude_design", executionAttempt.path("nodeId").asText());
        assertEquals("claude", executionAttempt.path("agent").asText());
        assertEquals("2026-05-24T00:00:00Z", executionAttempt.path("startedAt").asText());
        assertEquals("2026-05-24T00:00:03Z", executionAttempt.path("finishedAt").asText());
        var outputMetadata = Files.readString(runDir.resolve("outputs/design-claude.json.meta.json"));
        assertTrue(outputMetadata.contains("\"artifactType\" : \"output\""));
        assertTrue(outputMetadata.contains("\"contentType\" : \"application/json\""));
        assertTrue(outputMetadata.contains("\"artifactPath\" : \".agent/workstreams/ws-test/runs/run-001/outputs/design-claude.json\""));
        assertTrue(outputMetadata.contains("\"estimatedTokens\" :"));
        assertTrue(outputMetadata.contains("\"lineCount\" : 1"));
        assertTrue(outputMetadata.contains("\"sha256\" :"));
    }

    @Test
    void preparedRunHasNonTerminalExecutionAttemptAndTimelineAppendDoesNotFinalizeIt() throws Exception {
        var runDir = tempDir.resolve(".agent/workstreams/ws-test/runs/run-prepared");
        var promptPath = runDir.resolve("prompts/intake-claude.md");
        var outputPath = runDir.resolve("outputs/intake-claude.md");
        var transcriptPath = runDir.resolve("outputs/intake-claude.transcript.log");
        var stderrPath = runDir.resolve("outputs/intake-claude.stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(outputPath.getParent());
        Files.writeString(promptPath, "# Prompt\n");
        Files.writeString(outputPath, "");
        Files.writeString(transcriptPath, "");
        Files.writeString(stderrPath, "");

        var writer = new AgentRunMetadataWriter();
        writer.writePrepared(
                tempDir,
                "ws-test",
                "intake.claude_brainstorm",
                "claude",
                "claude-main",
                "intake-claude-brainstorm",
                "run-prepared",
                runDir,
                promptPath,
                outputPath,
                transcriptPath,
                stderrPath,
                null,
                null
        );

        var prepared = OBJECT_MAPPER.readTree(runDir.resolve("run.json").toFile());
        assertEquals("prepared", prepared.path("status").asText());
        var preparedAttempt = prepared.path("executionAttempt");
        assertFalse(preparedAttempt.isMissingNode() || preparedAttempt.isNull(),
                "writePrepared must record a non-terminal executionAttempt");
        assertEquals("RUNNING", preparedAttempt.path("status").asText());
        assertEquals("RUNNING", preparedAttempt.path("processStatus").asText());
        assertTrue(preparedAttempt.path("transcriptPath").asText().endsWith("outputs/intake-claude.transcript.log"),
                "prepared attempt must point to the execution transcript, not the final artifact output");
        assertTrue(prepared.path("executionOutcome").isMissingNode() || prepared.path("executionOutcome").isNull(),
                "writePrepared must not write a terminal executionOutcome");
        assertTrue(prepared.path("failureReason").isMissingNode() || prepared.path("failureReason").isNull(),
                "writePrepared must not write a terminal failureReason");
        assertEquals("NETWORK_AUTHORITY_NOT_CONFIGURED",
                prepared.path("executionAuthority").path("transportDisposition").asText());

        writer.appendExecutionEvent(
                runDir.resolve("run.json"),
                new AcpxExecutionEvent(
                        AgentExecutionTimelineEventKind.PROCESS_STARTED,
                        "adapter",
                        1,
                        Instant.parse("2026-07-01T00:00:00Z"),
                        0
                )
        );

        var finalized = OBJECT_MAPPER.readTree(runDir.resolve("run.json").toFile());
        assertEquals("running", finalized.path("status").asText(),
                "a durable process-start event must move the run out of prepared without finalizing it");
        var timeline = finalized.path("executionTimeline");
        var kinds = new java.util.ArrayList<String>();
        timeline.forEach(node -> kinds.add(node.path("kind").asText()));
        assertTrue(kinds.contains("process_started"), "timeline must contain process_started: " + kinds);
        var attemptAfterAppend = finalized.path("executionAttempt");
        assertFalse(attemptAfterAppend.isMissingNode() || attemptAfterAppend.isNull(),
                "in-flight timeline append must not drop the executionAttempt");
        assertEquals("PROCESS_STARTED", attemptAfterAppend.path("status").asText(),
                "the same writer must project the durable process-start fact onto the attempt");
        assertEquals("RUNNING", attemptAfterAppend.path("processStatus").asText());
        assertTrue(finalized.path("executionOutcome").isMissingNode() || finalized.path("executionOutcome").isNull(),
                "in-flight timeline append must not synthesize an executionOutcome");
        assertTrue(finalized.path("failureReason").isMissingNode() || finalized.path("failureReason").isNull(),
                "in-flight timeline append must not write a terminal failureReason");

        writer.appendExecutionEvent(
                runDir.resolve("run.json"),
                new AcpxExecutionEvent(
                        AgentExecutionTimelineEventKind.PROMPT_ACCEPTED,
                        "acpx_stream",
                        2,
                        Instant.parse("2026-07-01T00:00:01Z"),
                        1_000
                )
        );
        var streaming = OBJECT_MAPPER.readTree(runDir.resolve("run.json").toFile());
        assertEquals("STREAMING", streaming.path("executionAttempt").path("status").asText(),
                "a durable prompt occurrence must be visible without waiting for finalization");
        assertTrue(streaming.path("agentResultAcquisition").isNull()
                        || streaming.path("agentResultAcquisition").isMissingNode(),
                "prompt acceptance is not a parsed AgentResult candidate");
    }

    @Test
    void preparedRunWritesNonTerminalExecutionAttemptWithoutOutcome() throws Exception {
        var runDir = tempDir.resolve(".agent/workstreams/ws-test/runs/run-prepared-attempt");
        var promptPath = runDir.resolve("prompts/intake-claude.md");
        var outputPath = runDir.resolve("outputs/intake-claude.md");
        var transcriptPath = runDir.resolve("outputs/intake-claude.transcript.log");
        var stderrPath = runDir.resolve("outputs/intake-claude.stderr.log");
        Files.createDirectories(promptPath.getParent());
        Files.createDirectories(outputPath.getParent());
        Files.writeString(promptPath, "# Prompt\n");
        Files.writeString(outputPath, "");
        Files.writeString(transcriptPath, "");
        Files.writeString(stderrPath, "");

        new AgentRunMetadataWriter().writePrepared(
                tempDir,
                "ws-test",
                "intake.claude_brainstorm",
                "claude",
                "claude-main",
                "intake-claude-brainstorm",
                "run-prepared-attempt",
                runDir,
                promptPath,
                outputPath,
                transcriptPath,
                stderrPath,
                null,
                null
        );

        var root = OBJECT_MAPPER.readTree(runDir.resolve("run.json").toFile());
        assertEquals("prepared", root.path("status").asText());
        var attempt = root.path("executionAttempt");
        assertFalse(attempt.isMissingNode() || attempt.isNull(),
                "prepared run must carry a non-terminal executionAttempt so the boundary can be reconciled");
        assertEquals("RUNNING", attempt.path("status").asText());
        assertEquals("RUNNING", attempt.path("processStatus").asText());
        assertEquals("att-run-prepared-attempt", attempt.path("attemptId").asText());
        assertEquals("run-prepared-attempt", attempt.path("runId").asText());
        assertEquals("ws-test", attempt.path("workstreamId").asText());
        assertEquals("intake.claude_brainstorm", attempt.path("nodeId").asText());
        assertEquals("claude", attempt.path("agent").asText());
        assertTrue(attempt.path("transcriptPath").asText().endsWith("outputs/intake-claude.transcript.log"),
                "prepared attempt must preserve the transcript path for stale reconciliation");
        assertTrue(attempt.path("startedAt").asText().startsWith("2"),
                "prepared attempt must record startedAt");
        var finishedAt = attempt.path("finishedAt").asText("");
        assertTrue(finishedAt.isEmpty() || "null".equals(finishedAt),
                "non-terminal attempt must not carry finishedAt: " + finishedAt);
        var outcome = root.path("executionOutcome");
        assertTrue(outcome.isMissingNode() || outcome.isNull(),
                "prepared run must not write a terminal executionOutcome");
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

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(4, result.outputRefs().size());
        var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
        var finalArtifactRef = OBJECT_MAPPER.readTree(runMetadataPath.toFile()).path("outputPath").asText();
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith(".transcript.log")));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith(".stderr.log")));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("/binding.json")));

        var outputPath = tempDir.resolve(finalArtifactRef);
        var finalArtifact = Files.readString(outputPath);
        assertTrue(finalArtifact.contains("\"artifact_type\" : \"task_contract_proposal\""));
        assertTrue(finalArtifact.contains("\"decision\" : \"READY_FOR_PRODUCT_REVIEW\""));
        assertFalse(finalArtifact.contains("dual-control"));
        assertFalse(finalArtifact.contains("<dual-artifact"));
        assertFalse(finalArtifact.contains("[tool]"));
        assertFalse(finalArtifact.contains("[client]"));
        assertFalse(finalArtifact.contains("\n  input:"));
        assertFalse(finalArtifact.contains("\n  output:"));

        var transcriptRef = (String) result.result().get("transcript_ref");
        assertTrue(transcriptRef.endsWith(".transcript.log"));
        var transcript = Files.readString(tempDir.resolve(transcriptRef));
        assertTrue(transcript.contains("[tool] Bash echo ok"));
        assertTrue(transcript.contains("input:"));
        assertTrue(transcript.contains("output:"));

        var runMetadata = Files.readString(runMetadataPath);
        assertTrue(runMetadata.contains("\"outputPath\" :"));
        assertTrue(runMetadata.contains("\"transcriptPath\" :"));
        assertTrue(runMetadata.contains("\"transcriptMetadataPath\" :"));
        var transcriptMetadata = Files.readString(tempDir.resolve(transcriptRef + ".meta.json"));
        assertTrue(transcriptMetadata.contains("\"artifactType\" : \"transcript\""));
        assertTrue(transcriptMetadata.contains("\"estimatedTokens\" :"));
    }

    @Test
    void completedRunPreservesFencedRawTranscriptButPersistsStrictCandidate() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-fenced");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new FencedCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        var result = handler.execute(taskContext(
                tempDir,
                "ws-fenced",
                definition,
                readyInstance("intake.claude_brainstorm")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var runMetadataPath = tempDir.resolve((String) result.result().get("run_metadata"));
        var metadata = OBJECT_MAPPER.readTree(runMetadataPath.toFile());
        var outputPath = tempDir.resolve(metadata.path("outputPath").asText());
        var candidate = Files.readString(outputPath);
        assertTrue(candidate.startsWith("{"));
        assertFalse(candidate.contains("```"));
        assertTrue(new dev.dual.runtime.handler.AgentResultEnvelopeParser().parse(candidate).valid());

        var transcript = Files.readString(tempDir.resolve((String) result.result().get("transcript_ref")));
        assertTrue(transcript.contains("```json"), "raw ACP presentation must remain in the transcript");
        var acquisition = metadata.path("agentResultAcquisition");
        assertEquals(2, acquisition.path("schemaVersion").asInt());
        assertFalse(acquisition.path("correctionAttempted").asBoolean());
        var occurrence = acquisition.path("occurrences").get(0);
        assertEquals("SINGLE_JSON_FENCE_REMOVED", occurrence.path("presentationDisposition").asText());
        assertEquals(64, occurrence.path("rawAssistantSha256").asText().length());
        assertEquals(64, occurrence.path("candidateSha256").asText().length());
        assertFalse(occurrence.path("rawAssistantSha256").asText()
                .equals(occurrence.path("candidateSha256").asText()));
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

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));

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
    void completedAgentRunPrefersTransportUsageWhenNoAcpxSessionStreamExists() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new CompletingTransportUsageTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"source\" : \"acpx_stream\""), runMetadata);
        assertTrue(runMetadata.contains("\"attribution\" : \"EXACT_PROMPT_MATCH\""), runMetadata);
        assertTrue(runMetadata.contains("\"inputTokens\" : 12"), runMetadata);
        assertTrue(runMetadata.contains("\"cachedReadTokens\" : 34"), runMetadata);
        assertTrue(runMetadata.contains("\"cachedWriteTokens\" : 5"), runMetadata);
        assertTrue(runMetadata.contains("\"outputTokens\" : 6"), runMetadata);
        assertTrue(runMetadata.contains("\"totalTokens\" : 57"), runMetadata);
        assertTrue(runMetadata.contains("\"provider\" : \"zhipu\""), runMetadata);
        assertTrue(runMetadata.contains("\"model\" : \"glm-5.2\""), runMetadata);
        assertTrue(runMetadata.contains("\"billingMode\" : \"subscription_token_only\""), runMetadata);
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

        handler.execute(taskContext(tempDir, "ws-test", definition, readyInstance("intake.claude_brainstorm")));

        assertEquals(false, transport.lastApproveAll);
        assertEquals("observe", transport.lastPermissionProfile.name());
        assertTrue(transport.lastPermissionProfile.autoApprovedTools().contains("Read"));
        assertFalse(transport.lastPermissionProfile.autoApprovedTools().contains("Bash(git *)"));
        assertFalse(transport.lastPermissionProfile.autoApproveKinds().contains("execute"));
    }

    @Test
    void contractDiscoveryProfileAllowsReadAndSearchWithoutExecute() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");
        initializeGitRepository();

        var transport = new CapturingApprovalTransport();
        var handler = new AcpxAgentTaskHandler(transport, false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm",
                "permission_profile", "contract_discovery"
        ));

        handler.execute(taskContext(tempDir, "ws-test", definition, readyInstance("intake.claude_brainstorm")));

        assertEquals(false, transport.lastApproveAll);
        assertEquals("contract_discovery", transport.lastPermissionProfile.name());
        assertTrue(transport.lastPermissionProfile.autoApprovedTools().contains("Read"));
        assertTrue(transport.lastPermissionProfile.autoApprovedTools().contains("Grep"));
        assertTrue(transport.lastPermissionProfile.autoApprovedTools().contains("Glob"));
        assertEquals(List.of("read", "search"), transport.lastPermissionProfile.autoApproveKinds());
        assertFalse(transport.lastPermissionProfile.terminalEnabled());
        assertFalse(transport.lastPermissionProfile.autoApproveKinds().contains("execute"));
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

        handler.execute(taskContext(tempDir, "ws-test", definition, readyInstance("design.codex_arch_review")));

        assertEquals(false, transport.lastApproveAll);
        assertEquals("review", transport.lastPermissionProfile.name());
        assertTrue(transport.lastPermissionProfile.autoApprovedTools().contains("Grep"));
        assertFalse(transport.lastPermissionProfile.autoApprovedTools().contains("WebSearch"));
        assertTrue(transport.lastPermissionProfile.escalateKinds().contains("fetch"));
        assertFalse(transport.lastPermissionProfile.autoApprovedTools().contains("Bash(rg *)"));
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

        handler.execute(taskContext(tempDir, "ws-test", definition, readyInstance("implement.claude_implement")));

        assertEquals(true, transport.lastApproveAll);
        assertEquals("implement", transport.lastPermissionProfile.name());
    }

    @Test
    void namedImplementProfileOverridesLegacyApproveAllFlag() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement the approved plan.\n");

        var transport = new CapturingApprovalTransport();
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "implement-claude",
                "permission_profile", "implement",
                "approve_all", true
        ));

        new AcpxAgentTaskHandler(transport, false).execute(
                taskContext(tempDir, "ws-test", definition, readyInstance("implement.claude_implement")));

        assertEquals(false, transport.lastApproveAll);
        assertEquals("implement", transport.lastPermissionProfile.name());
        assertTrue(transport.lastPermissionProfile.autoApprovedTools().contains("Bash"));
        assertTrue(transport.lastPermissionProfile.autoApproveKinds().contains("edit"));
        assertFalse(transport.lastPermissionProfile.autoApprovedTools().contains("WebSearch"));
        assertFalse(transport.lastPermissionProfile.autoApprovedTools().contains("WebFetch"));
        assertTrue(transport.lastPermissionProfile.escalateKinds().contains("fetch"));
    }

    @Test
    void typedPublicWebSearchApprovalAddsOnlyExactToolForOneRetry() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Review public dependency metadata.\n");
        var transport = new CapturingApprovalTransport();
        var definition = new NodeDefinition("design.codex_arch_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "codex",
                "session", "codex-reviewer",
                "prompt_template", "design-codex-review",
                "permission_profile", "review"
        ));
        var base = readyInstance("design.codex_arch_review");
        var approval = Map.<String, Object>ofEntries(
                Map.entry("schema_version", 1),
                Map.entry("type", "PUBLIC_WEBSEARCH"),
                Map.entry("workstream_id", "ws-test"),
                Map.entry("node_instance_id", base.nodeInstanceId()),
                Map.entry("tool_name", "WebSearch"),
                Map.entry("tool_kind", "fetch"),
                Map.entry("source_run_ref", ".agent/workstreams/ws-test/runs/run-1/run.json"),
                Map.entry("source_tool_call_id", "call-websearch-1"),
                Map.entry("source_query_sha256", "a".repeat(64)),
                Map.entry("public_repository_attested", true),
                Map.entry("note", "public repository dependency lookup"),
                Map.entry("approved_at", "2026-08-28T00:00:00Z")
        );
        var approved = new NodeInstance(
                base.nodeInstanceId(), base.nodeDefinitionId(), base.kind(), base.handlerType(), base.status(),
                base.stageId(), base.iterationId(), base.inputRefs(), base.outputRefs(),
                Map.of("permission_approval", approval), base.runRef(), base.startedAt(), base.finishedAt());

        new AcpxAgentTaskHandler(transport, false).execute(taskContext(tempDir, "ws-test", definition, approved));

        assertTrue(transport.lastPermissionProfile.autoApprovedTools().contains("WebSearch"));
        assertFalse(transport.lastPermissionProfile.autoApprovedTools().contains("WebFetch"));
        assertTrue(transport.lastPermissionProfile.escalateKinds().contains("fetch"));
        var prompt = Files.readString(Files.list(workstreamDir.resolve("runs"))
                .findFirst().orElseThrow().resolve("prompts/design-codex-review.md"));
        assertTrue(prompt.contains("WebSearch is operator-approved"));
        assertTrue(prompt.contains("Never use WebFetch"));
    }

    @Test
    void implementationAgentCannotRewriteTaskContractAuthority() throws Exception {
        writeChangeBoundaryControlFacts("ws-test");
        var handler = new AcpxAgentTaskHandler(
                new ControlFactTamperingTransport(ControlFactTamperingTransport.Target.TASK_CONTRACT),
                false
        );
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "implement-claude",
                "approve_all", true
        ));

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("implement.claude_implement")
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("CHANGE_BOUNDARY_AUTHORITY_TAMPERED", result.outcome());
        assertEquals(true, result.result().get("change_boundary_authority_tampered"));
        assertTrue(((List<?>) result.result().get("changed_control_facts")).stream()
                .anyMatch(path -> path.toString().endsWith("artifacts/task-contract.json")));
    }

    @Test
    void implementationAgentCannotRewriteWorkspaceBaseAuthority() throws Exception {
        writeChangeBoundaryControlFacts("ws-test");
        var handler = new AcpxAgentTaskHandler(
                new ControlFactTamperingTransport(ControlFactTamperingTransport.Target.WORKSPACE),
                false
        );
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "implement-claude",
                "approve_all", true
        ));

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("implement.claude_implement")
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("CHANGE_BOUNDARY_AUTHORITY_TAMPERED", result.outcome());
        assertTrue(((List<?>) result.result().get("changed_control_facts")).stream()
                .anyMatch(path -> path.toString().endsWith("workspaces/wsp-test.json")));
    }

    @Test
    void implementationAgentCannotCreateWorkspaceAuthority() throws Exception {
        writeChangeBoundaryControlFacts("ws-test");
        Files.delete(tempDir.resolve(".agent/workstreams/ws-test/workspaces/wsp-test.json"));
        var handler = new AcpxAgentTaskHandler(
                new ControlFactTamperingTransport(ControlFactTamperingTransport.Target.WORKSPACE),
                false
        );
        var definition = new NodeDefinition("implement.claude_implement", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "implement-claude",
                "approve_all", true
        ));

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("implement.claude_implement")
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("CHANGE_BOUNDARY_AUTHORITY_TAMPERED", result.outcome());
        assertTrue(((List<?>) result.result().get("changed_control_facts")).stream()
                .anyMatch(path -> path.toString().endsWith("workspaces")));
    }

    @Test
    void treatsNonZeroExitAsCompletedWhenStdoutHasCompleteTerminalArtifact() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new CompletingExitFiveTransport(), false);
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "design-claude",
                "permission_profile", "review"
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

        var candidate = handler.execute(taskContext(tempDir, "ws-test", definition, instance));
        var validated = new ArtifactContractValidator().validate(
                tempDir,
                "ws-test",
                instance.nodeInstanceId(),
                definition,
                instance.inputRefs(),
                candidate
        );
        var result = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, validated);

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result() + " / " + result.errorMessage());
        try (var paths = Files.walk(workstreamDir.resolve("runs"))) {
            var runJson = paths.filter(path -> path.getFileName().toString().equals("run.json"))
                    .findFirst().orElseThrow();
            var run = OBJECT_MAPPER.readTree(runJson.toFile());
            assertEquals("POLICY_BOUND",
                    run.path("executionAuthority").path("transportDisposition").asText());
            assertEquals(ClaudeExecutionPolicy.SCHEMA,
                    run.path("executionAuthority").path("schema").asText());
        }
        assertEquals("completed", result.outcome());
        assertEquals(5, result.result().get("exit_code"));
        assertEquals("EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT", result.result().get("process_usability"));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("design-claude.json")));
        var runMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) result.result().get("run_metadata")).toFile()
        );
        assertEquals(5, runMetadata.path("processResult").path("exitCode").asInt());
        assertEquals(
                "EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT",
                runMetadata.path("processResult").path("usability").asText()
        );
        assertEquals("SUCCESS_WITH_WARNINGS", runMetadata.path("executionOutcome").path("status").asText());
        assertEquals("completed", runMetadata.path("status").asText());
        assertTrue(runMetadata.path("failureReason").asText("").isBlank());
    }

    @Test
    void deniedOptionalToolWithProseWrappedFencedArtifactCompletesWithWarnings() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-non-zero-correction");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var transport = new NonZeroProsePrefixedTransport();
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "design-claude"
        ));
        var instance = readyInstance("design.claude_design");

        var candidate = new AcpxAgentTaskHandler(transport, false).execute(
                taskContext(tempDir, "ws-non-zero-correction", definition, instance)
        );
        var result = new ArtifactContractValidator().validate(
                tempDir,
                "ws-non-zero-correction",
                instance.nodeInstanceId(),
                definition,
                instance.inputRefs(),
                candidate
        );

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals(1, transport.initialCalls);
        assertEquals(0, transport.correctionCalls);
        var runMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) candidate.result().get("run_metadata")).toFile()
        );
        var acquisition = runMetadata.path("agentResultAcquisition");
        assertFalse(acquisition.path("correctionAttempted").asBoolean());
        assertEquals("NONE", acquisition.path("correctionReason").asText());
        assertEquals("SKIPPED_PROCESS_NON_ZERO", acquisition.path("correctionDisposition").asText());
        assertEquals(1, acquisition.path("occurrences").size());
        assertEquals(5, acquisition.path("occurrences").get(0).path("exitCode").asInt());
        assertEquals("SINGLE_FENCED_JSON_EXTRACTED",
                acquisition.path("occurrences").get(0).path("presentationDisposition").asText());

        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, result);
        assertEquals(NodeStatus.COMPLETED, finalized.status());
        var finalizedMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) finalized.result().get("run_metadata")).toFile()
        );
        assertEquals("SUCCESS_WITH_WARNINGS",
                finalizedMetadata.path("executionOutcome").path("status").asText());
        assertEquals("completed_with_adapter_or_process_warnings",
                finalizedMetadata.path("executionOutcome").path("primaryReason").asText());
        assertEquals("PERMISSION_BLOCKED",
                finalizedMetadata.path("executionOutcome").path("secondarySignals").get(0).path("type").asText());
    }

    @Test
    void deniedOptionalToolWithOnlyMissingRootClosureCompletesWithWarnings() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-root-closure-warning");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var transport = new NonZeroProsePrefixedTransport(true);
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "design-claude"
        ));
        var instance = readyInstance("design.claude_design");

        var candidate = new AcpxAgentTaskHandler(transport, false).execute(
                taskContext(tempDir, "ws-root-closure-warning", definition, instance)
        );
        var validated = new ArtifactContractValidator().validate(
                tempDir,
                "ws-root-closure-warning",
                instance.nodeInstanceId(),
                definition,
                instance.inputRefs(),
                candidate
        );

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertEquals(1, transport.initialCalls);
        assertEquals(0, transport.correctionCalls);
        var runMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) candidate.result().get("run_metadata")).toFile()
        );
        var acquisition = runMetadata.path("agentResultAcquisition");
        assertFalse(acquisition.path("correctionAttempted").asBoolean());
        assertEquals("ROOT_OBJECT_CLOSURE_RECOVERED",
                acquisition.path("occurrences").get(0).path("presentationDisposition").asText());
        assertTrue(acquisition.path("occurrences").get(0).path("contractDecodable").asBoolean());

        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, validated);
        assertEquals(NodeStatus.COMPLETED, finalized.status());
        var finalizedMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) finalized.result().get("run_metadata")).toFile()
        );
        assertEquals("SUCCESS_WITH_WARNINGS",
                finalizedMetadata.path("executionOutcome").path("status").asText());
        assertEquals("PERMISSION_BLOCKED",
                finalizedMetadata.path("executionOutcome").path("secondarySignals").get(0).path("type").asText());
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

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(5, result.result().get("exit_code"));
        assertEquals("EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT", result.result().get("process_usability"));
        assertFalse(result.result().containsKey("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 5"));
        assertTrue(runMetadata.contains("\"status\" : \"process_exited\""));
        assertFalse(runMetadata.contains("\"failureSignal\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
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

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(0, result.result().get("exit_code"));
        assertEquals("EXITED_ZERO", result.result().get("process_usability"));
        assertEquals(3, result.outputRefs().size());
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("intake-claude-brainstorm.transcript.log")));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("intake-claude-brainstorm.stderr.log")));
        assertTrue(result.outputRefs().stream().anyMatch(ref -> ref.endsWith("/binding.json")));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 0"));
        assertFalse(runMetadata.contains("RECOVERABLE_AGENT_RECONNECT"), runMetadata);
        assertTrue(runMetadata.contains("\"executionAttempt\" :"), runMetadata);
        assertTrue(runMetadata.contains("\"status\" : \"PROCESS_EXITED\""), runMetadata);
        assertTrue(runMetadata.contains("\"processStatus\" : \"EXITED\""), runMetadata);
        assertTrue(runMetadata.contains("\"failureSignal\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
        assertTrue(runMetadata.contains("\"message\" : \"process exited\""), runMetadata);
        assertTrue(runMetadata.contains("\"measurementType\" : \"UNAVAILABLE\""), runMetadata);
        assertTrue(runMetadata.contains("\"unavailableReason\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
        assertFalse(runMetadata.contains("\"explorationPolicyResult\""), runMetadata);
    }

    @Test
    void unresolvedSessionRequestCannotUseEarlierCompletionToSuppressReconnect() throws Exception {
        var originalUserHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("home");
        var sessionsDir = fakeHome.resolve(".acpx/sessions");
        var sessionId = "7db7b7ad-a6f5-418f-b8e0-0465b0a5ea6b";
        Files.createDirectories(sessionsDir);
        System.setProperty("user.home", fakeHome.toString());
        try {
            var workstreamDir = tempDir.resolve(".agent/workstreams/ws-pending-session");
            Files.createDirectories(workstreamDir);
            Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

            var handler = new AcpxAgentTaskHandler(
                    new UnresolvedSessionReconnectTransport(sessionsDir, sessionId), false);
            var definition = new NodeDefinition(
                    "intake.claude_brainstorm",
                    NodeKind.TASK,
                    "acpx.agent_run",
                    Map.of(
                            "agent", "claude",
                            "session", "claude-main",
                            "prompt_template", "intake-claude-brainstorm"
                    )
            );

            var result = handler.execute(taskContext(
                    tempDir,
                    "ws-pending-session",
                    definition,
                    readyInstance("intake.claude_brainstorm")
            ));

            var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
            assertFalse(runMetadata.contains("RECOVERABLE_AGENT_RECONNECT"), runMetadata);
            assertTrue(runMetadata.contains("\"failureSignal\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
            assertTrue(runMetadata.contains("\"type\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
        } finally {
            if (originalUserHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalUserHome);
            }
        }
    }

    @Test
    void recordsTypedTimeoutForZeroExitWithoutPromptCompletionAtDeadline() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement the approved plan.\n");

        var handler = new AcpxAgentTaskHandler(new DeadlineExitZeroTransport(), false);
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

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));
        var diagnosticMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) result.result().get("run_metadata")).toFile());
        var diagnostic = Files.readString(tempDir.resolve(diagnosticMetadata.path("stderrPath").asText()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(0, result.result().get("exit_code"), diagnostic);
        assertEquals("TIMED_OUT", result.result().get("process_usability"));
        assertFalse(result.result().containsKey("acpx_failure.reason"));
        var runMetadata = OBJECT_MAPPER.readTree(tempDir.resolve((String) result.result().get("run_metadata")).toFile());
        assertEquals(0, runMetadata.path("exitCode").asInt());
        assertEquals("process_exited", runMetadata.path("status").asText());
        assertEquals("TIMED_OUT", runMetadata.path("executionAttempt").path("status").asText());
        assertEquals("TIMED_OUT", runMetadata.path("executionAttempt").path("processStatus").asText());
        assertTrue(runMetadata.path("executionOutcome").isMissingNode() || runMetadata.path("executionOutcome").isNull());
        assertEquals(0, runMetadata.path("adapterSignals").size());
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

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        ));
        var diagnosticMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) result.result().get("run_metadata")).toFile());
        var diagnostic = Files.readString(tempDir.resolve(diagnosticMetadata.path("stderrPath").asText()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals(4, result.result().get("exit_code"), diagnostic);
        assertEquals("TIMED_OUT", result.result().get("process_usability"));
        assertFalse(result.result().containsKey("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"type\" : \"SESSION_BOOTSTRAP_FAILED\""));
        assertTrue(runMetadata.contains("\"status\" : \"TIMED_OUT\""));
        assertTrue(runMetadata.contains("\"session\" : \"claude-main-ws-test\""));
    }

    @Test
    void recordsProviderConnectionFailureAsAdapterBoundaryFailure() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new ProviderConnectionFailedTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        var result = handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals(1, result.result().get("exit_code"));
        assertEquals("EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT", result.result().get("process_usability"));
        assertFalse(result.result().containsKey("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"type\" : \"PROVIDER_CONNECTION_FAILED\""), runMetadata);
        assertTrue(runMetadata.contains("\"failureSignal\" : \"PROVIDER_CONNECTION_FAILED\""), runMetadata);
        assertTrue(runMetadata.contains("\"unavailableReason\" : \"PROVIDER_CONNECTION_FAILED\""), runMetadata);
        assertFalse(runMetadata.contains("\"explorationPolicyResult\""), runMetadata);
    }

    @Test
    void handlerCannotFabricateExecutionFactsWhenTransportContractThrows() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new ThrowingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        assertThrows(java.io.IOException.class, () -> handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        )));

        var runMetadataPath = Files.walk(workstreamDir.resolve("runs"))
                .filter(path -> path.getFileName().toString().equals("run.json"))
                .findFirst()
                .orElseThrow();
        var runMetadata = OBJECT_MAPPER.readTree(runMetadataPath.toFile());
        assertEquals("prepared", runMetadata.path("status").asText());
        assertTrue(runMetadata.path("processResult").isMissingNode()
                || runMetadata.path("processResult").isNull());
        assertTrue(runMetadata.path("adapterSignals").isMissingNode()
                || runMetadata.path("adapterSignals").isNull()
                || runMetadata.path("adapterSignals").isEmpty());
        assertTrue(runMetadata.path("executionOutcome").isMissingNode()
                || runMetadata.path("executionOutcome").isNull());
    }

    @Test
    void startedRunIsClosedToTerminalWhenHandlerFailsBeforeFinalizer() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        // The transport launches the process (emits the transport-owned process_started
        // fact with a now-dead pid) and then fails before the completion finalizer runs.
        var handler = new AcpxAgentTaskHandler(new StartedThenFailingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        assertThrows(java.io.IOException.class, () -> handler.execute(taskContext(
                tempDir,
                "ws-test",
                definition,
                readyInstance("intake.claude_brainstorm")
        )));

        var runMetadataPath = Files.walk(workstreamDir.resolve("runs"))
                .filter(path -> path.getFileName().toString().equals("run.json"))
                .findFirst()
                .orElseThrow();
        var runMetadata = OBJECT_MAPPER.readTree(runMetadataPath.toFile());
        // The started execution must NOT be left as a bare "prepared" illusion.
        assertNotEquals("prepared", runMetadata.path("status").asText(),
                "a started execution whose handler failed before the finalizer must be closed, not left prepared");
        assertEquals("INTERRUPTED", runMetadata.path("executionOutcome").path("status").asText(),
                "the closer must converge a dead-pid started run to INTERRUPTED");
        assertEquals(99_999_989L, runMetadata.path("processResult").path("pid").asLong(),
                "the closer must persist the transport-owned process pid of the started execution");
        assertTrue(runMetadata.path("usage").isMissingNode() || runMetadata.path("usage").isNull(),
                "the closer must not fabricate provider usage");
        assertTrue(runMetadata.path("agentResultAcquisition").isMissingNode()
                        || runMetadata.path("agentResultAcquisition").isNull(),
                "process_started alone must not fabricate a provider result occurrence");
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

        var result = handler.execute(taskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals("EXITED_ZERO", result.result().get("process_usability"));
        assertFalse(result.result().containsKey("acpx_failure.reason"));
        var runMetadata = Files.readString(tempDir.resolve((String) result.result().get("run_metadata")));
        assertTrue(runMetadata.contains("\"exitCode\" : 0"));
        assertTrue(runMetadata.contains("\"status\" : \"process_exited\""));
        assertFalse(runMetadata.contains("\"failureSignal\" : \"AGENT_RECONNECT_REQUIRED\""), runMetadata);
    }

    @Test
    void capturedLargeStreamKeepsOccurrenceFactsConsistentThroughFinalization() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-large-stream");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");
        var existingFile = tempDir.resolve("repo/relative/existing.ext");
        Files.createDirectories(existingFile.getParent());
        Files.writeString(existingFile, "existing\n");
        Files.writeString(tempDir.resolve("main.py"), "def hello(): return 'hello'\n");
        Files.writeString(tempDir.resolve("README.md"), "# Fixture\n");
        initializeGitRepository("main.py", "README.md", "repo/relative/existing.ext");
        var sessionsDir = tempDir.resolve(".acpx/sessions");
        Files.createDirectories(sessionsDir);
        var fakeAcpx = tempDir.resolve("captured-large-acpx.sh");
        var strictResultFixture = Path.of("..", "scripts/fixtures/emit-strict-agent-result.py")
                .toAbsolutePath()
                .normalize();
        assertTrue(Files.isRegularFile(strictResultFixture));
        writeCapturedLargeStreamAcpx(fakeAcpx, sessionsDir, strictResultFixture);

        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm",
                "permission_profile", "contract_discovery"
        ));
        var instance = readyInstance("intake.claude_brainstorm");
        writeInitialIntakeLineage(workstreamDir, instance);
        var originalUserHome = System.getProperty("user.home");
        NodeResult candidate;
        try {
            System.setProperty("user.home", tempDir.toString());
            candidate = new AcpxAgentTaskHandler(
                    new AcpxAgentTransport(fakeAcpx.toString(), java.time.Duration.ofSeconds(30)),
                    false
            ).execute(taskContext(tempDir, "ws-large-stream", definition, instance));
        } finally {
            System.setProperty("user.home", originalUserHome);
        }
        var validated = new ArtifactContractValidator().validate(
                tempDir,
                "ws-large-stream",
                instance.nodeInstanceId(),
                definition,
                instance.inputRefs(),
                candidate
        );
        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, validated);

        assertEquals(NodeStatus.COMPLETED, finalized.status(), finalized.toString());
        var runMetadata = OBJECT_MAPPER.readTree(
                tempDir.resolve((String) finalized.result().get("run_metadata")).toFile());
        assertEquals("completed", runMetadata.path("status").asText());
        assertEquals("EXITED_ZERO", runMetadata.path("processResult").path("usability").asText());
        assertEquals("SUCCESS", runMetadata.path("executionOutcome").path("status").asText());
        assertTrue(runMetadata.path("executionAttempt").path("failureSignal").isNull());
        assertEquals("AGENT_RECONNECT_REQUIRED", runMetadata.path("adapterSignals").get(0).path("type").asText());
        assertTrue(runMetadata.path("adapterSignals").get(0).path("superseded").asBoolean());
        assertEquals(1, runMetadata.path("agentResultAcquisition").path("occurrences").size());
        assertEquals("EXITED_ZERO", runMetadata.path("agentResultAcquisition").path("occurrences")
                .get(0).path("processResult").path("usability").asText());
    }

    private static void writeInitialIntakeLineage(Path workstreamDir, NodeInstance instance) throws Exception {
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var iterations = workstreamDir.resolve("iterations");
        var nodes = workstreamDir.resolve("nodes");
        Files.createDirectories(iterations);
        Files.createDirectories(nodes);
        LEDGER_MAPPER.writeValue(iterations.resolve("iter-001.json").toFile(),
                WorkflowIterationDocument.initial("intake.claude_brainstorm", now));
        LEDGER_MAPPER.writeValue(nodes.resolve(instance.nodeInstanceId() + ".json").toFile(),
                new NodeInstanceDocument(
                        1,
                        instance.nodeInstanceId(),
                        instance.nodeDefinitionId(),
                        instance.kind(),
                        instance.handlerType(),
                        instance.status(),
                        instance.stageId(),
                        instance.iterationId(),
                        instance.inputRefs(),
                        Map.of(),
                        instance.outputRefs(),
                        instance.result(),
                        "",
                        "",
                        now,
                        null));
    }

    private void initializeGitRepository(String... trackedPaths) throws Exception {
        runGit("init");
        runGit("config", "user.email", "dual-test@example.invalid");
        runGit("config", "user.name", "Dual Test");
        if (trackedPaths.length > 0) {
            var add = new java.util.ArrayList<String>();
            add.add("add");
            add.add("--");
            add.addAll(List.of(trackedPaths));
            runGit(add.toArray(String[]::new));
        }
        runGit("commit", "--allow-empty", "-m", "fixture baseline");
    }

    private void runGit(String... arguments) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    private static void writeCapturedLargeStreamAcpx(
            Path fakeAcpx,
            Path sessionsDir,
            Path strictResultFixture
    ) throws Exception {
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "build-info" ]; then
                  cat <<'EOF'
                {"upstream_commit":"fd173f04aa1b56f9e3f5ca5190c034ddcae28792","patch_sha256":"41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c","policy_schema":"claude_execution_policy.v2"}
                EOF
                  exit 0
                fi
                case " $* " in
                  *" sessions "*)
                    name=""
                    while [ "$#" -gt 0 ]; do
                      if [ "$1" = "--name" ]; then
                        shift
                        name="$1"
                      fi
                      shift
                    done
                    printf '{"entries":[{"name":"%s","cwd":"__WORKDIR__","acpxRecordId":"123e4567-e89b-12d3-a456-426614174106","closed":false,"lastUsedAt":"2026-08-14T00:00:00Z"}]}\n' "$name" > "__SESSIONS__/index.json"
                    exit 0
                    ;;
                esac
                prompt=""
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "-f" ]; then
                    shift
                    prompt="$1"
                  fi
                  shift
                done
                echo "[acpx] session claude-main (123e4567-e89b-12d3-a456-426614174106) · agent needs reconnect" >&2
                mkdir -p "__SESSIONS__"
                HOME="__TEST_HOME__" python3 "__STRICT_FIXTURE__" \
                  --prompt "$prompt" \
                  --template intake-claude-brainstorm.md \
                  --profile controlled \
                  --session-id 123e4567-e89b-12d3-a456-426614174106
                padding=$(awk 'BEGIN{for(i=0;i<140000;i++) printf "x"}')
                stream="__SESSIONS__/123e4567-e89b-12d3-a456-426614174106.stream.ndjson"
                captured="$stream.captured"
                head -n 1 "$stream" > "$captured"
                printf '{"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"fresh","update":{"sessionUpdate":"tool_call_update","rawOutput":{"formatted_output":"%s"}}}}\\n' "$padding" >> "$captured"
                tail -n +2 "$stream" >> "$captured"
                mv "$captured" "$stream"
                exit 0
                """
                .replace("__SESSIONS__", sessionsDir.toString())
                .replace("__TEST_HOME__", sessionsDir.getParent().getParent().toString())
                .replace("__WORKDIR__", sessionsDir.getParent().getParent().toString())
                .replace("__STRICT_FIXTURE__", strictResultFixture.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));
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
                handler.execute(taskContext(tempDir, "ws-test", definition, instance)));
        assertTrue(error.getMessage().contains("unsupported agent"));
    }

    private static final class CompletingExitFiveTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            assertTrue(request.claudeExecutionPolicy() != null,
                    "every Claude execution occurrence must carry an E1 policy binding");
            request.claudeExecutionPolicy().verify(request.workdir());
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
            return completedResult(request, 5, now, List.of("acpx", "claude"), "READY_FOR_ARCH_REVIEW");
        }
    }

    private static final class NonZeroProsePrefixedTransport extends AcpxAgentTransport {
        private int initialCalls;
        private int correctionCalls;
        private final boolean missingRootClosure;

        private NonZeroProsePrefixedTransport() {
            this(false);
        }

        private NonZeroProsePrefixedTransport(boolean missingRootClosure) {
            this.missingRootClosure = missingRootClosure;
        }

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            initialCalls++;
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "");
            Files.writeString(request.stderrPath(), "[acpx] session claude-main · agent connected\n");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            var resultJson = completionJson(request, "READY_FOR_ARCH_REVIEW");
            var finalMessage = missingRootClosure
                    ? resultJson.substring(0, resultJson.length() - 1)
                    : "I completed the design.\n\n```json\n" + resultJson + "\n```";
            return fixtureResult(
                    5,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude"),
                    List.of(),
                    null,
                    new AcpxPromptCompletion(
                            "fixture-non-zero",
                            "end_turn",
                            finalMessage
                    ),
                    List.of(new AdapterSignal(
                            AdapterSignalType.PERMISSION_BLOCKED,
                            "acpx exited with PERMISSION_DENIED (5)",
                            1,
                            "acpx_exit",
                            request.sessionName(),
                            false
                    ))
            );
        }

        @Override
        AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request) {
            correctionCalls++;
            throw new AssertionError("non-zero process must not start correction");
        }
    }

    // Mirrors the live cancer-clinic native `claude --print` path: the provider fully
    // succeeds (exit 0, is_error=false, stop_reason=end_turn) but the result is plain
    // markdown with no dual-control fence and no typed artifact. This must be adapter/
    private static final class PreparedMetadataObservedTransport extends AcpxAgentTransport {
        private boolean sawPreparedRunJson;
        private boolean localBindingAllowed;

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            var runJson = request.stdoutPath().getParent().getParent().resolve("run.json");
            sawPreparedRunJson = Files.exists(runJson)
                    && Files.readString(runJson).contains("\"status\" : \"prepared\"");
            var policy = new ObjectMapper().readTree(
                    request.workdir().resolve(request.claudeExecutionPolicy().ref()).toFile());
            localBindingAllowed = policy.path("sandbox").path("network")
                    .path("allow_local_binding").asBoolean(false);
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
            return completedResult(request, 0, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
        }
    }

    private static final class PolicyConflictGuardTransport extends AcpxAgentTransport {
        private final java.util.ArrayList<String> sessions = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> policyShas = new java.util.ArrayList<>();
        private final java.util.Map<String, String> policyBySession = new java.util.LinkedHashMap<>();

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            var binding = request.claudeExecutionPolicy();
            if (binding == null) {
                throw new java.io.IOException("E1 execution policy is required");
            }
            var previous = policyBySession.putIfAbsent(request.sessionName(), binding.sha256());
            if (previous != null && !previous.equals(binding.sha256())) {
                throw new java.io.IOException("CLAUDE_EXECUTION_POLICY_CONFLICT");
            }
            sessions.add(request.sessionName());
            policyShas.add(binding.sha256());
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "");
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-09-02T00:00:00Z");
            return completedResult(
                    request, 0, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
        }
    }

    private static final class ContractCorrectionTransport extends AcpxAgentTransport {
        private final java.util.ArrayList<String> sessions = new java.util.ArrayList<>();
        private int calls;
        private AcpxPermissionProfile correctionProfile;

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            calls++;
            sessions.add(request.sessionName());
            prepareLogs(request);
            var now = Instant.parse("2026-07-22T00:00:00Z");
            return fixtureResult(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude", "initial"),
                    List.of(),
                    usage(101),
                    new AcpxPromptCompletion(
                            "fixture-initial",
                            "end_turn",
                            "I have completed the design.\n" + completionJson(request, "READY_FOR_ARCH_REVIEW")
                    )
            );
        }

        @Override
        AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request) throws java.io.IOException {
            calls++;
            sessions.add(request.sessionName());
            correctionProfile = request.permissionProfile();
            prepareLogs(request);
            var now = Instant.parse("2026-07-22T00:00:02Z");
            return fixtureResult(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude", "correction"),
                    List.of(),
                    usage(23),
                    new AcpxPromptCompletion(
                            "fixture-correction",
                            "end_turn",
                            completionJson(request, "READY_FOR_ARCH_REVIEW")
                    )
            );
        }

        private static void prepareLogs(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "");
            Files.writeString(request.stderrPath(), "");
        }

        private static AcpxUsage usage(long totalTokens) {
            return new AcpxUsage(
                    totalTokens - 1,
                    1,
                    0,
                    0,
                    totalTokens,
                    null,
                    "",
                    "fixture",
                    AcpxUsage.PROVIDER_RUN_USAGE,
                    AcpxUsage.ATTR_EXACT_PROMPT_MATCH,
                    false,
                    0,
                    null,
                    "",
                    "fixture-provider",
                    "fixture-model",
                    "token_only",
                    "fixture",
                    ""
            );
        }
    }

    private static final class SchemaContractCorrectionTransport extends AcpxAgentTransport {
        private final java.util.ArrayList<String> sessions = new java.util.ArrayList<>();
        private final boolean repairSecondOccurrence;
        private int calls;
        private AcpxPermissionProfile correctionProfile;
        private String initialArtifact;

        private SchemaContractCorrectionTransport() {
            this(true);
        }

        private SchemaContractCorrectionTransport(boolean repairSecondOccurrence) {
            this.repairSecondOccurrence = repairSecondOccurrence;
        }

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            calls++;
            sessions.add(request.sessionName());
            prepareLogs(request);
            initialArtifact = completionJson(request, "SELF_TEST_COMPLETE")
                    .replace("    \"missing_evidence\" : [ ],\n", "");
            var now = Instant.parse("2026-07-26T00:00:00Z");
            return fixtureResult(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude", "initial"),
                    List.of(),
                    null,
                    new AcpxPromptCompletion("fixture-schema-initial", "end_turn", initialArtifact)
            );
        }

        @Override
        AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request) throws java.io.IOException {
            calls++;
            sessions.add(request.sessionName());
            correctionProfile = request.permissionProfile();
            prepareLogs(request);
            var corrected = (com.fasterxml.jackson.databind.node.ObjectNode) OBJECT_MAPPER.readTree(initialArtifact);
            if (repairSecondOccurrence) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) corrected.path("result"))
                        .putArray("missing_evidence");
            }
            var now = Instant.parse("2026-07-26T00:00:02Z");
            return fixtureResult(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude", "correction"),
                    List.of(),
                    null,
                    new AcpxPromptCompletion(
                            "fixture-schema-correction",
                            "end_turn",
                            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(corrected)
                    )
            );
        }

        private static void prepareLogs(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "");
            Files.writeString(request.stderrPath(), "");
        }
    }

    private static final class ThrowingTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            throw new java.io.IOException("simulated transport failure");
        }
    }

    private static final class CountingAcceptanceTransport extends AcpxAgentTransport {
        private int calls;

        @Override
        public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) {
            calls++;
            throw new AssertionError("transport must not run for an invalid task contract binding");
        }
    }

    private static final class StartedThenFailingTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            // The process launched: persist the transport-owned process_started fact (with a
            // now-dead pid) exactly as the real transport does, then fail before the finalizer.
            request.executionEventSink().accept(new AcpxExecutionEvent(
                    AgentExecutionTimelineEventKind.PROCESS_STARTED,
                    "adapter",
                    1,
                    Instant.now(),
                    0,
                    java.util.Map.of("pid", 99_999_989L)
            ));
            throw new java.io.IOException("simulated failure after process started");
        }
    }

    private static final class TranscriptCompletingTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    [client] initialize (running)
                    [tool] Bash echo ok (completed)
                      input: {"command":"echo ok"}
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
            return completedResult(request, 0, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
        }
    }

    private static final class FencedCompletingTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            var now = Instant.parse("2026-06-01T00:00:00Z");
            var bare = completedResult(
                    request,
                    0,
                    now,
                    List.of("acpx", "claude"),
                    "READY_FOR_PRODUCT_REVIEW"
            );
            var raw = "```json\n" + bare.promptCompletion().finalAssistantMessage() + "\n```";
            // Mirror the real transport invariant: the persisted raw stream and projected
            // final assistant message carry the same fenced presentation.
            Files.writeString(request.stdoutPath(), raw);
            Files.writeString(request.stderrPath(), "");
            return new AcpxRunResult(
                    bare.startedAt(),
                    bare.finishedAt(),
                    bare.command(),
                    bare.executionTimeline(),
                    bare.usage(),
                    new AcpxPromptCompletion(
                            bare.promptCompletion().requestId(),
                            bare.promptCompletion().stopReason(),
                            raw
                    ),
                    bare.processResult(),
                    bare.adapterSignals()
            );
        }
    }

    private static final class DirectReadCompletingTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
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
            return completedResult(request, 0, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
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
            return completedResult(
                    request,
                    0,
                    now,
                    List.of("acpx", "claude"),
                    List.of(new AcpxExecutionEvent(
                            AgentExecutionTimelineEventKind.PROCESS_WAIT_COMPLETED,
                            "acp",
                            7,
                            now.plusMillis(750),
                            750
                    )),
                    null,
                    "READY_FOR_PRODUCT_REVIEW"
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
            var resultJson = completionJson(request, "NEEDS_REVISION");
            var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) OBJECT_MAPPER.readTree(resultJson);
            var result = (com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("result");
            result.put("summary", "token refresh missing");
            result.withArray("must_fix").add("refresh token on expiry");
            result.withArray("open_risks").add("race on logout");
            return fixtureResult(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "codex"),
                    List.of(),
                    null,
                    new AcpxPromptCompletion(
                            "fixture-prompt",
                            "end_turn",
                            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope))
            );
        }
    }

    @Test
    void runMetadataRecordsSessionScopeStageAndIteration() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-sess");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new DirectReadCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        handler.execute(taskContext(tempDir, "ws-sess", definition, readyInstance("intake.claude_brainstorm")));

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
        assertTrue(runMetadata.contains("\"contextGovernanceActualReportPath\" :"), runMetadata);
        assertFalse(runMetadata.contains("\"explorationPolicyResult\""), runMetadata);
    }

    @Test
    void completedRunRecordsActualReadTelemetryWithoutReadPolicyFields() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-explore");
        Files.createDirectories(workstreamDir.resolve("memory"));
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");

        var handler = new AcpxAgentTaskHandler(new DirectReadCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "intake-claude-brainstorm"
        ));

        var result = handler.execute(taskContext(tempDir, "ws-explore", definition,
                readyInstance("intake.claude_brainstorm")));

        assertEquals(NodeStatus.COMPLETED, result.status(),
                "read telemetry must not replace the change-boundary decision");

        var runJson = Files.walk(workstreamDir.resolve("runs"))
                .filter(path -> path.getFileName().toString().equals("run.json"))
                .findFirst()
                .orElseThrow();
        var runMetadata = Files.readString(runJson);
        assertTrue(runMetadata.contains("\"contextGovernanceActualReportPath\" :"), runMetadata);
        assertFalse(runMetadata.contains("\"explorationPolicyResult\""), runMetadata);
        assertFalse(runMetadata.contains("\"agentExplorationPlanPath\""), runMetadata);

        var root = OBJECT_MAPPER.readTree(runJson.toFile());
        assertEquals("process_exited", root.path("status").asText(),
                "handler persistence stops at process facts; runtime finalizer owns terminal status");
        assertEquals("PROCESS_EXITED", root.path("executionAttempt").path("status").asText(),
                "handler persistence must not invent a terminal execution outcome");
        assertTrue(root.path("failureReason").isMissingNode()
                        || root.path("failureReason").isNull()
                        || root.path("failureReason").asText().isBlank(),
                "read telemetry must not set failureReason");
        assertTrue(root.path("recoverable").isMissingNode()
                        || root.path("recoverable").isNull()
                        || !root.path("recoverable").asBoolean(),
                "read telemetry must not trigger recovery");
        assertEquals(0, root.path("adapterSignals").size(),
                "read telemetry must not become an adapter failure");

        var reportPath = tempDir.resolve(root.path("contextGovernanceActualReportPath").asText());
        var report = OBJECT_MAPPER.readTree(reportPath.toFile());
        assertTrue(report.path("actualReadFiles").isArray());
        assertTrue(report.path("actualDirectReadFiles").isArray());
        assertFalse(report.has("explorationPolicyResult"));
        assertFalse(report.has("outsideScopeReads"));
    }

    @Test
    void runWritesPromptManifestByDefault() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-manifest");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Clarify the request first.\n");
        var handler = new AcpxAgentTaskHandler(new TranscriptCompletingTransport(), false);
        var definition = new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude", "session", "claude-main", "prompt_template", "intake-claude-brainstorm"));
        handler.execute(taskContext(tempDir, "ws-manifest", definition,
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
        var manifestJson = OBJECT_MAPPER.readTree(manifest.toFile());
        var contractSection = java.util.stream.StreamSupport.stream(manifestJson.path("sections").spliterator(), false)
                .filter(section -> "prompt_contract".equals(section.path("name").asText()))
                .findFirst()
                .orElseThrow();
        assertTrue(contractSection.path("bytes").asInt() > 0, contractSection.toPrettyString());
        assertTrue(contractSection.path("estimatedTokens").asInt() > 0, contractSection.toPrettyString());

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

        handler.execute(taskContext(tempDir, "ws-timeline", definition,
                readyInstance("intake.claude_brainstorm")));

        var runJson = Files.walk(workstreamDir.resolve("runs"))
                .filter(p -> p.getFileName().toString().equals("run.json")).findFirst().orElseThrow();
        var runMetadata = Files.readString(runJson);
        assertTrue(runMetadata.contains("\"executionTimeline\" :"), runMetadata);
        assertTrue(runMetadata.contains("\"kind\" : \"process_wait_completed\""), runMetadata);
        assertTrue(runMetadata.contains("\"kind\" : \"artifact_extracted\""), runMetadata);
        assertTrue(runMetadata.contains("\"elapsedMs\" : 750"), runMetadata);
    }

    @Test
    void unvalidatedCompletionDoesNotEnterSemanticMemory() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-sem-run");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("requirement.md"), "Ship token refresh.\n");
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {
                  "id":"task-ws-sem-run","version":1,"created_at":"2026-07-23T00:00:00Z",
                  "objective":"o","status":"READY_FOR_REVIEW",
                  "scope":{"included":[{"path":"src/tokenRefresh.js","operation":"CREATE"}],"excluded":[]},
                  "non_goals":["SSO"],"acceptance_criteria":["tokens refresh"],
                  "test_requirements":[],"constraints":[],"risk_areas":[],
                  "ambiguity_notes":[],"human_questions":[],"compilation_pass":1
                }
                """);

        var handler = new AcpxAgentTaskHandler(new RejectionEnvelopeTransport(), false);
        var definition = new NodeDefinition("design.codex_arch_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "codex", "session", "codex-main", "prompt_template", "design-codex-review"
        ));
        handler.execute(taskContext(tempDir, "ws-sem-run", definition,
                readyInstance("design.codex_arch_review")));

        var memory = Files.readString(workstreamDir.resolve("memory/workstream-memory.json"));
        assertTrue(memory.contains("\"status\" : \"unavailable\""),
                "unqualified stable contract must not enter semantic memory: " + memory);
        assertFalse(memory.contains("src/tokenRefresh.js"),
                "unqualified stable contract must not define decidedScope: " + memory);
        assertFalse(memory.contains("NEEDS_REVISION"), "unvalidated decision must not enter memory: " + memory);
        assertFalse(memory.contains("refresh token on expiry"), "unvalidated issue must not enter memory: " + memory);
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

    private TaskHandler.TaskContext taskContext(
            Path workspaceRoot,
            String workstreamId,
            NodeDefinition definition,
            NodeInstance instance
    ) throws IOException {
        if ("intake.claude_brainstorm".equals(definition.id())) {
            var iterationsDir = workspaceRoot.resolve(".agent/workstreams")
                    .resolve(workstreamId)
                    .resolve("iterations");
            Files.createDirectories(iterationsDir);
            var iteration = WorkflowIterationDocument.initial(
                    definition.id(), Instant.parse("2026-08-10T00:00:00Z"));
            new ObjectMapper().registerModule(new JavaTimeModule()).writerWithDefaultPrettyPrinter()
                    .writeValue(iterationsDir.resolve(instance.iterationId() + ".json").toFile(), iteration);
        }
        return new TaskHandler.TaskContext(workspaceRoot, workstreamId, definition, instance);
    }

    private void writeCompactionMetrics(String workstreamId, long claudeCachedReadTokens, long codexCachedReadTokens)
            throws Exception {
        var metricsDir = tempDir.resolve(".agent")
                .resolve("workstreams")
                .resolve(workstreamId)
                .resolve("metrics");
        Files.createDirectories(metricsDir);
        Files.writeString(metricsDir.resolve("workstream-metrics.json"), """
                {
                  "schemaVersion": 1,
                  "tokenMetrics": {
                    "total": {
                      "cachedReadTokens": %d
                    },
                    "byAgent": {
                      "claude": {
                        "cachedReadTokens": %d
                      },
                      "codex": {
                        "cachedReadTokens": %d
                      }
                    }
                  }
                }
                """.formatted(claudeCachedReadTokens + codexCachedReadTokens, claudeCachedReadTokens, codexCachedReadTokens));
    }

    private void writeChangeBoundaryControlFacts(String workstreamId) throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams").resolve(workstreamId);
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.createDirectories(workstreamDir.resolve("workspaces"));
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {
                  "id":"task-test","version":1,"created_at":"2026-07-26T00:00:00Z",
                  "objective":"Implement the approved change.","status":"READY_FOR_REVIEW",
                  "scope":{"included":[{"path":"README.md","operation":"MODIFY"}],"excluded":[]},
                  "acceptance_criteria":[],"test_requirements":[],"constraints":[],
                  "non_goals":[],"risk_areas":[],"ambiguity_notes":[],"human_questions":[],
                  "compilation_pass":1
                }
                """);
        Files.writeString(workstreamDir.resolve("workstream.json"), """
                {"primaryWorkspaceId":"wsp-test"}
                """);
        Files.writeString(workstreamDir.resolve("workspaces/wsp-test.json"), """
                {"baseCommit":"1111111111111111111111111111111111111111"}
                """);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement the approved change.\n");
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
            return completedResult(request, 0, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
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
            return completedResult(request, 0, now, List.of("acpx", "codex"), "READY_FOR_PRODUCT_REVIEW");
        }
    }

    private static final class ControlFactTamperingTransport extends AcpxAgentTransport {
        enum Target {
            TASK_CONTRACT,
            WORKSPACE
        }

        private final Target target;

        private ControlFactTamperingTransport(Target target) {
            this.target = target;
        }

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            var workstreamDir = request.workdir().resolve(".agent/workstreams/ws-test");
            var targetPath = target == Target.TASK_CONTRACT
                    ? workstreamDir.resolve("artifacts/task-contract.json")
                    : workstreamDir.resolve("workspaces/wsp-test.json");
            Files.writeString(targetPath, "{\"tampered\":true}\n");
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "");
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-07-26T00:00:00Z");
            return completedResult(
                    request,
                    0,
                    now,
                    List.of("acpx", "claude"),
                    "READY_FOR_SELF_TEST"
            );
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
            return completedResult(request, 5, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
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
            return resultWithFacts(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude"),
                    ProcessUsability.EXITED_ZERO,
                    AdapterSignalType.AGENT_RECONNECT_REQUIRED
            );
        }
    }

    private static final class UnresolvedSessionReconnectTransport extends AcpxAgentTransport {
        private final Path sessionsDir;
        private final String sessionId;

        private UnresolvedSessionReconnectTransport(Path sessionsDir, String sessionId) {
            this.sessionsDir = sessionsDir;
            this.sessionId = sessionId;
        }

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.createDirectories(sessionsDir);
            Files.writeString(request.stdoutPath(), "Agent output stopped before terminal control.\n");
            Files.writeString(
                    request.stderrPath(),
                    "[acpx] session claude-main (" + sessionId + ") · agent needs reconnect\n"
            );
            Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                    {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"fresh","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"done"}}}}
                    {"jsonrpc":"2.0","id":3,"result":{"stopReason":"end_turn"}}
                    {"jsonrpc":"2.0","method":"session/new","id":4,"params":{}}
                    """);
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return resultWithFacts(
                    0,
                    now,
                    now.plusSeconds(1),
                    List.of("acpx", "claude"),
                    ProcessUsability.EXITED_ZERO,
                    AdapterSignalType.AGENT_RECONNECT_REQUIRED
            );
        }
    }

    private static final class DeadlineExitZeroTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), """
                    # Implementation Report

                    The agent produced a long report but stopped before writing the terminal
                    """);
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return resultWithFacts(
                    0,
                    now,
                    now.plusSeconds(605),
                    List.of("acpx", "--timeout", "600", "claude"),
                    ProcessUsability.TIMED_OUT,
                    null
            );
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
            return resultWithFacts(
                    4,
                    now,
                    now.plusSeconds(45),
                    List.of("acpx", "claude", "sessions", "ensure", "--name", request.sessionName()),
                    ProcessUsability.TIMED_OUT,
                    AdapterSignalType.SESSION_BOOTSTRAP_FAILED
            );
        }
    }

    private static final class ProviderConnectionFailedTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "[error] RUNTIME: API Error: Unable to connect to API (FailedToOpenSocket)\n");
            Files.writeString(request.stderrPath(), "");
            var now = Instant.parse("2026-06-01T00:00:00Z");
            return resultWithFacts(
                    1,
                    now,
                    now.plusSeconds(188),
                    List.of("acpx", "claude"),
                    ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT,
                    AdapterSignalType.PROVIDER_CONNECTION_FAILED
            );
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
            return completedResult(request, 0, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
        }
    }

    private static final class CompletingTransportUsageTransport extends AcpxAgentTransport {
        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
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
            var usage = new AcpxUsage(
                    12,
                    6,
                    34,
                    5,
                    57,
                    null,
                    "",
                    "acpx_stream",
                    AcpxUsage.PROVIDER_RUN_USAGE,
                    AcpxUsage.ATTR_EXACT_PROMPT_MATCH,
                    false,
                    0,
                    null,
                    "",
                    "zhipu",
                    "glm-5.2",
                    AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS,
                    AcpxProviderMetadata.SOURCE_CLAUDE_SETTINGS_ENV,
                    ""
            );
            return completedResult(
                    request,
                    0,
                    now,
                    List.of("claude", "--print"),
                    List.of(),
                    usage,
                    "READY_FOR_PRODUCT_REVIEW"
            );
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
            return completedResult(request, 0, now, List.of("acpx", "claude"), "READY_FOR_PRODUCT_REVIEW");
        }
    }

    private static AcpxRunResult completedResult(
            AcpxRunRequest request,
            int exitCode,
            Instant startedAt,
            List<String> command,
            String decision
    ) throws java.io.IOException {
        return completedResult(request, exitCode, startedAt, command, List.of(), null, decision);
    }

    private static AcpxRunResult completedResult(
            AcpxRunRequest request,
            int exitCode,
            Instant startedAt,
            List<String> command,
            List<AcpxExecutionEvent> timeline,
            AcpxUsage usage,
            String decision
    ) throws java.io.IOException {
        var resultJson = completionJson(request, decision);
        return fixtureResult(
                exitCode,
                startedAt,
                startedAt.plusSeconds(1),
                command,
                timeline,
                usage,
                new AcpxPromptCompletion("fixture-prompt", "end_turn", resultJson)
        );
    }

    private static AcpxRunResult resultWithFacts(
            int exitCode,
            Instant startedAt,
            Instant finishedAt,
            List<String> command,
            ProcessUsability usability,
            AdapterSignalType signalType
    ) {
        var process = new ProcessResult(
                String.join(" ", command),
                null,
                exitCode,
                usability,
                Math.max(0, java.time.Duration.between(startedAt, finishedAt).toMillis()),
                "",
                "",
                usability == ProcessUsability.INTERRUPTED,
                usability == ProcessUsability.TIMED_OUT
        );
        var signals = signalType == null
                ? List.<AdapterSignal>of()
                : List.of(new AdapterSignal(signalType, signalType.name(), 0, "fixture", "", false));
        return new AcpxRunResult(
                startedAt,
                finishedAt,
                command,
                List.of(),
                null,
                AcpxPromptCompletion.EMPTY,
                process,
                signals
        );
    }

    private static AcpxRunResult fixtureResult(
            int exitCode,
            Instant startedAt,
            Instant finishedAt,
            List<String> command,
            List<AcpxExecutionEvent> timeline,
            AcpxUsage usage,
            AcpxPromptCompletion completion
    ) {
        return fixtureResult(exitCode, startedAt, finishedAt, command, timeline, usage, completion, List.of());
    }

    private static AcpxRunResult fixtureResult(
            int exitCode,
            Instant startedAt,
            Instant finishedAt,
            List<String> command,
            List<AcpxExecutionEvent> timeline,
            AcpxUsage usage,
            AcpxPromptCompletion completion,
            List<AdapterSignal> signals
    ) {
        var usability = exitCode == 0
                ? ProcessUsability.EXITED_ZERO
                : completion.completed()
                        ? ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT
                        : ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT;
        return new AcpxRunResult(
                startedAt,
                finishedAt,
                command,
                timeline,
                usage,
                completion,
                new ProcessResult(
                        String.join(" ", command),
                        null,
                        exitCode,
                        usability,
                        Math.max(0, java.time.Duration.between(startedAt, finishedAt).toMillis()),
                        "",
                        "",
                        false,
                        false
                ),
                signals
        );
    }

    private RepositoryFactBinding.Verified repositoryFactBindingFixture(
            String workstreamId,
            String nodeInstanceId,
            String runId
    ) throws Exception {
        var occurrenceDir = tempDir.resolve(".agent/workstreams").resolve(workstreamId)
                .resolve("artifacts/context-governance/occurrences")
                .resolve(nodeInstanceId + "-" + runId);
        Files.createDirectories(occurrenceDir);
        var repoMap = occurrenceDir.resolve("repo-map.json");
        OBJECT_MAPPER.writeValue(repoMap.toFile(), Map.of(
                "artifactType", "repo_map",
                "inventoryHead", "",
                "inventorySha256", "",
                "inventoryPathCount", 0,
                "files", List.of()));
        var binding = occurrenceDir.resolve("binding.json");
        OBJECT_MAPPER.writeValue(binding.toFile(), new RepositoryFactBinding.Document(
                RepositoryFactBinding.SCHEMA_VERSION,
                workstreamId,
                nodeInstanceId,
                runId,
                "",
                "",
                0,
                Map.of("repoMap", new RepositoryFactBinding.ArtifactRef(
                        tempDir.relativize(repoMap).toString(),
                        RepositoryFactBinding.sha256(repoMap)))));
        var bindingRef = tempDir.relativize(binding).toString();
        var verification = RepositoryFactBinding.verify(
                tempDir,
                workstreamId,
                bindingRef,
                RepositoryFactBinding.sha256(binding),
                nodeInstanceId,
                runId,
                OBJECT_MAPPER);
        assertTrue(verification.valid(), verification.errors().toString());
        return verification.verified();
    }

    private static String completionJson(AcpxRunRequest request, String decision) throws java.io.IOException {
        var prompt = Files.readString(request.promptPath());
        var start = prompt.lastIndexOf("{\n  \"schema_version\"");
        if (start < 0) {
            throw new AssertionError("strict AgentResultEnvelope example missing from prompt");
        }
        return prompt.substring(start).strip()
                .replace("<one allowed decision>", decision);
    }
}
