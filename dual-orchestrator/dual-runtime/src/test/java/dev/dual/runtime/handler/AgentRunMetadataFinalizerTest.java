package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.AgentResultContractResult;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.RecoveryAssessment;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamAttentionCategory;
import dev.dual.ledger.file.WorkstreamAttentionEvaluator;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRunMetadataFinalizerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void invalidTypedProcessResultCannotFallBackToLegacyNodeFacts() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-invalid/runs/run-invalid/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-invalid",
                  "workstreamId": "ws-invalid",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "status": "process_exited",
                  "processResult": {
                    "exitCode": 0,
                    "usability": "NOT_A_PROCESS_USABILITY"
                  },
                  "adapterSignals": []
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", ".agent/workstreams/ws-invalid/runs/run-invalid/run.json");
        facts.put("exit_code", 0);
        facts.put("process_usability", "EXITED_ZERO");
        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.COMPLETED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.valid("IMPLEMENTED"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        assertThrows(IllegalStateException.class,
                () -> new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult));
    }

    @Test
    void typedProcessResultWithWrongShapeCannotFallBackToLegacyNodeFacts() throws Exception {
        assertInvalidTypedMetadataRejected("wrong-process-shape", """
                "processResult": null,
                "adapterSignals": []
                """);
    }

    @Test
    void unknownTypedAdapterSignalCannotBeSilentlyDropped() throws Exception {
        assertInvalidTypedMetadataRejected("unknown-signal", """
                "processResult": {
                  "exitCode": 0,
                  "usability": "EXITED_ZERO",
                  "durationMs": 1
                },
                "adapterSignals": [ { "type": "NOT_AN_ADAPTER_SIGNAL" } ]
                """);
    }

    @Test
    void malformedNodeResultAdapterSignalsCannotBecomeNoSignal() {
        assertMalformedNodeResultSignalsRejected(null);
        assertMalformedNodeResultSignalsRejected(List.of(Map.of("type", "NOT_AN_ADAPTER_SIGNAL")));
    }

    @Test
    void mapsModelMismatchFailureReasonToTypedAdapterSignal() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-model/runs/run-1/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-1",
                  "workstreamId": "ws-model",
                  "nodeId": "intake.claude_brainstorm",
                  "agent": "claude",
                  "status": "failed"
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", ".agent/workstreams/ws-model/runs/run-1/run.json");
        facts.put("exit_code", 4);
        facts.put("process_usability", "KILLED_AFTER_RECOVERABLE_SIGNAL");
        facts.put("acpx_failure.reason", "RECOVERABLE_AGENT_MODEL_MISMATCH");
        facts.put("acpx_failure.recoverable", true);

        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "RECOVERABLE_AGENT_MODEL_MISMATCH",
                NodeStatus.FAILED,
                List.of(),
                facts,
                "model mismatch"
        ), AgentResultContractResult.missing(), List.of(ArtifactContractResult.requiredMissing("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        var attempt = map(json.get("executionAttempt"));
        assertEquals("MODEL_MISMATCH", attempt.get("failureSignal"));
        var outcome = map(json.get("executionOutcome"));
        var signal = map(list(outcome.get("secondarySignals")).get(0));
        assertEquals("MODEL_MISMATCH", signal.get("type"));
    }

    @Test
    void persistsRetrySafeConsistentlyForManualRecoverableAdapterFailure() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-reconnect/runs/run-reconnect/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-reconnect",
                  "workstreamId": "ws-reconnect",
                  "nodeId": "intake.codex_product_review",
                  "agent": "codex",
                  "status": "failed"
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", ".agent/workstreams/ws-reconnect/runs/run-reconnect/run.json");
        facts.put("exit_code", 0);
        facts.put("process_usability", "EXITED_ZERO");
        facts.put("acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT");
        facts.put("acpx_failure.recoverable", true);

        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "RECOVERABLE_AGENT_RECONNECT",
                NodeStatus.FAILED,
                List.of(),
                facts,
                "agent needs reconnect"
        ), AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        var attempt = map(json.get("executionAttempt"));
        assertEquals("RECOVERABLE_FAILED", attempt.get("status"));
        assertEquals("AGENT_RECONNECT_REQUIRED", attempt.get("failureSignal"));
        assertEquals(true, attempt.get("retrySafe"));
        var outcome = map(json.get("executionOutcome"));
        assertEquals("RECOVERABLE_ADAPTER_FAILURE", outcome.get("status"));
        assertEquals("MANUAL_ONLY", outcome.get("retryPolicy"));
        assertFalse(outcome.containsKey("retrySafe"),
                "outcome must not project a derived retrySafe; consume retryPolicy / RecoveryAssessment.retrySafe instead");
    }

    @Test
    void interruptedPermissionFailureFinalizesWithoutContradictoryAttemptState() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-permission/runs/run-permission/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-permission",
                  "workstreamId": "ws-permission",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "status": "process_exited",
                  "startedAt": "2026-08-14T01:33:27Z",
                  "finishedAt": "2026-08-14T01:43:32Z",
                  "executionAttempt": {
                    "attemptId": "att-run-permission",
                    "status": "INTERRUPTED",
                    "processStatus": "INTERRUPTED",
                    "failureSignal": "PERMISSION_BLOCKED",
                    "recoverable": true,
                    "startedAt": "2026-08-14T01:33:27Z",
                    "finishedAt": "2026-08-14T01:43:32Z"
                  },
                  "processResult": {
                    "exitCode": 5,
                    "usability": "INTERRUPTED",
                    "durationMs": 605000,
                    "interrupted": true,
                    "timedOut": false
                  },
                  "adapterSignals": [ {
                    "type": "PERMISSION_BLOCKED",
                    "message": "acpx exited with PERMISSION_DENIED (5)",
                    "observedSequence": 44256,
                    "source": "acpx_exit",
                    "sessionId": "claude-main-ws-permission",
                    "superseded": false
                  } ]
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", ".agent/workstreams/ws-permission/runs/run-permission/run.json");
        facts.put("exit_code", 5);
        facts.put("process_usability", "INTERRUPTED");
        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.COMPLETED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.missing(), List.of(), null);

        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        assertEquals(NodeStatus.FAILED, finalized.status());
        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        var attempt = map(json.get("executionAttempt"));
        assertEquals("INTERRUPTED", attempt.get("status"));
        assertEquals("INTERRUPTED", attempt.get("processStatus"));
        assertEquals("PERMISSION_BLOCKED", attempt.get("failureSignal"));
        var outcome = map(json.get("executionOutcome"));
        assertEquals("RECOVERABLE_ADAPTER_FAILURE", outcome.get("status"));
        assertEquals("permission_blocked", outcome.get("primaryReason"));
    }

    @Test
    void persistsSuccessfulExecutionOutcomeToRunMetadata() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-ok/runs/run-2/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-2",
                  "workstreamId": "ws-ok",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "status": "completed",
                  "startedAt": "2026-05-24T00:00:00Z",
                  "finishedAt": "2026-05-24T00:00:03Z",
                  "executionAttempt": {
                    "attemptId": "att-run-2",
                    "status": "COMPLETED",
                    "processStatus": "EXITED",
                    "message": "completed"
                  }
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_id", "run-2");
        facts.put("run_metadata", ".agent/workstreams/ws-ok/runs/run-2/run.json");
        facts.put("agent", "claude");
        facts.put("exit_code", 0);
        facts.put("process_usability", "EXITED_ZERO");

        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.COMPLETED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.valid("IMPLEMENTED"), List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        assertEquals(3, json.get("schemaVersion"));
        assertEquals("completed", json.get("status"));
        var agentResult = map(json.get("agentResultContract"));
        assertEquals(true, agentResult.get("present"));
        var contracts = list(json.get("artifactContractResults"));
        assertEquals(1, contracts.size());
        var outcome = map(json.get("executionOutcome"));
        assertEquals("SUCCESS", outcome.get("status"));
        assertEquals(true, outcome.get("nodeMaySucceed"));
    }

    @Test
    void usesAdapterProcessResultAsOutcomeEvidenceInsteadOfLegacyNodeStatus() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-process/runs/run-process/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-process",
                  "workstreamId": "ws-process",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "status": "failed",
                  "processResult": {
                    "command": "acpx claude",
                    "pid": 123,
                    "exitCode": 1,
                    "usability": "EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT",
                    "durationMs": 1200,
                    "stdoutPath": ".agent/workstreams/ws-process/runs/run-process/stdout.md",
                    "stderrPath": ".agent/workstreams/ws-process/runs/run-process/stderr.log",
                    "interrupted": false,
                    "timedOut": false
                  }
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_id", "run-process");
        facts.put("run_metadata", ".agent/workstreams/ws-process/runs/run-process/run.json");
        facts.put("agent", "claude");
        facts.put("exit_code", 1);

        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.FAILED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.valid("IMPLEMENTED"), List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        var outcome = map(json.get("executionOutcome"));
        assertEquals("SUCCESS_WITH_WARNINGS", outcome.get("status"));
        assertEquals(true, outcome.get("nodeMaySucceed"));
        var attempt = map(json.get("executionAttempt"));
        assertEquals("COMPLETED", attempt.get("status"));
        assertEquals("EXITED", attempt.get("processStatus"));
    }

    @Test
    void usesAdapterSignalsFromRunMetadataAsOutcomeEvidence() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-signal/runs/run-signal/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-signal",
                  "workstreamId": "ws-signal",
                  "nodeId": "intake.codex_product_review",
                  "agent": "codex",
                  "status": "failed",
                  "processResult": {
                    "command": "acpx codex",
                    "pid": 123,
                    "exitCode": 0,
                    "usability": "EXITED_ZERO",
                    "durationMs": 1200,
                    "stdoutPath": ".agent/workstreams/ws-signal/runs/run-signal/stdout.md",
                    "stderrPath": ".agent/workstreams/ws-signal/runs/run-signal/stderr.log",
                    "interrupted": false,
                    "timedOut": false
                  },
                  "adapterSignals": [ {
                    "type": "RATE_LIMITED",
                    "severity": "RECOVERABLE",
                    "source": "acpx_stream",
                    "message": "provider rate limit",
                    "sessionId": "sess-1",
                    "observedSequence": 7,
                    "superseded": false
                  } ]
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_id", "run-signal");
        facts.put("run_metadata", ".agent/workstreams/ws-signal/runs/run-signal/run.json");
        facts.put("agent", "codex");
        facts.put("exit_code", 0);

        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.COMPLETED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        var outcome = map(json.get("executionOutcome"));
        assertEquals("RECOVERABLE_ADAPTER_FAILURE", outcome.get("status"));
        assertEquals(false, outcome.get("nodeMaySucceed"));
        var attempt = map(json.get("executionAttempt"));
        assertEquals("RECOVERABLE_FAILED", attempt.get("status"));
        assertEquals("RATE_LIMITED", attempt.get("failureSignal"));
        var signal = map(list(json.get("adapterSignals")).get(0));
        assertEquals("RATE_LIMITED", signal.get("type"));
        assertEquals("acpx_stream", signal.get("source"));
        assertEquals("sess-1", signal.get("sessionId"));
    }

    @Test
    void persistedAdapterSignalTakesPrecedenceOverLegacyNodeResultFallback() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-signal-priority/runs/run-signal-priority/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-signal-priority",
                  "workstreamId": "ws-signal-priority",
                  "nodeId": "intake.claude_brainstorm",
                  "agent": "claude",
                  "status": "failed",
                  "processResult": {
                    "command": "acpx claude",
                    "pid": 123,
                    "exitCode": 0,
                    "usability": "EXITED_ZERO",
                    "durationMs": 1200,
                    "stdoutPath": ".agent/workstreams/ws-signal-priority/runs/run-signal-priority/stdout.md",
                    "stderrPath": ".agent/workstreams/ws-signal-priority/runs/run-signal-priority/stderr.log",
                    "interrupted": false,
                    "timedOut": false
                  },
                  "adapterSignals": [ {
                    "type": "PROVIDER_CONNECTION_FAILED",
                    "severity": "RECOVERABLE",
                    "source": "native_result",
                    "message": "provider returned 529",
                    "sessionId": "provider-session",
                    "observedSequence": 3,
                    "superseded": false
                  } ]
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_id", "run-signal-priority");
        facts.put("run_metadata", ".agent/workstreams/ws-signal-priority/runs/run-signal-priority/run.json");
        facts.put("agent", "claude");
        facts.put("exit_code", 0);
        facts.put("acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT");
        facts.put("acpx_failure.recoverable", true);

        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "RECOVERABLE_AGENT_RECONNECT",
                NodeStatus.FAILED,
                List.of(),
                facts,
                "legacy reconnect fallback"
        ), AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        var outcome = map(json.get("executionOutcome"));
        assertEquals("RECOVERABLE_ADAPTER_FAILURE", outcome.get("status"));
        assertEquals("provider_connection_failed", outcome.get("primaryReason"));
        var attempt = map(json.get("executionAttempt"));
        assertEquals("PROVIDER_CONNECTION_FAILED", attempt.get("failureSignal"));
        var firstSignal = map(list(json.get("adapterSignals")).get(0));
        assertEquals("PROVIDER_CONNECTION_FAILED", firstSignal.get("type"));
        assertEquals("native_result", firstSignal.get("source"));
        assertEquals("provider-session", firstSignal.get("sessionId"));
    }

    @Test
    void persistedEmptyAdapterSignalsPreventLegacyReasonFallback() throws Exception {
        var runJson = tempDir.resolve(".agent/workstreams/ws-empty-signals/runs/run-empty-signals/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-empty-signals",
                  "workstreamId": "ws-empty-signals",
                  "nodeId": "intake.claude_brainstorm",
                  "agent": "claude",
                  "status": "process_exited",
                  "processResult": {
                    "command": "acpx claude",
                    "exitCode": 0,
                    "usability": "EXITED_ZERO",
                    "durationMs": 12,
                    "interrupted": false,
                    "timedOut": false
                  },
                  "adapterSignals": []
                }
                """);

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", ".agent/workstreams/ws-empty-signals/runs/run-empty-signals/run.json");
        facts.put("exit_code", 0);
        facts.put("process_usability", "EXITED_ZERO");
        facts.put("acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT");
        facts.put("adapter_signals", null);
        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.COMPLETED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.valid("READY_FOR_PRODUCT_REVIEW"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        assertEquals(NodeStatus.COMPLETED, finalized.status());
        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        assertEquals("SUCCESS", map(json.get("executionOutcome")).get("status"));
        assertEquals(0, list(json.get("adapterSignals")).size());
    }

    private void assertInvalidTypedMetadataRejected(String suffix, String typedFields) throws Exception {
        var relative = ".agent/workstreams/ws-" + suffix + "/runs/run-" + suffix + "/run.json";
        var runJson = tempDir.resolve(relative);
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-%s",
                  "workstreamId": "ws-%s",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "status": "process_exited",
                  %s
                }
                """.formatted(suffix, suffix, typedFields));
        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", relative);
        facts.put("exit_code", 0);
        facts.put("process_usability", "EXITED_ZERO");
        facts.put("acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT");
        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.COMPLETED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.valid("IMPLEMENTED"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        assertThrows(IllegalStateException.class,
                () -> new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult));
    }

    private void assertMalformedNodeResultSignalsRejected(Object signals) {
        var facts = new LinkedHashMap<String, Object>();
        facts.put("exit_code", 0);
        facts.put("process_usability", "EXITED_ZERO");
        facts.put("adapter_signals", signals);
        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "completed",
                NodeStatus.COMPLETED,
                List.of(),
                facts,
                ""
        ), AgentResultContractResult.valid("IMPLEMENTED"),
                List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        assertThrows(IllegalArgumentException.class,
                () -> new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult));
    }

    @Test
    void sessionBootstrapFailureProjectsFailClosedThroughFinalizerRunJsonAndAttention() throws Exception {
        var ws = "ws-bootstrap-chain";
        var runJson = tempDir.resolve(".agent/workstreams/" + ws + "/runs/run-bootstrap/run.json");
        Files.createDirectories(runJson.getParent());
        // run.json carries the transport's typed facts (processResult + adapterSignals). The
        // executionOutcome below is NOT hand-copied — it is produced by AgentRunMetadataFinalizer.
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-bootstrap",
                  "workstreamId": "%s",
                  "nodeId": "intake.codex_review",
                  "agent": "codex",
                  "session": "codex-reviewer",
                  "status": "process_exited",
                  "processResult": {
                    "command": "acpx codex sessions ensure",
                    "exitCode": 4,
                    "usability": "EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT",
                    "durationMs": 117,
                    "stdoutPath": "stdout.log",
                    "stderrPath": "stderr.log",
                    "interrupted": false,
                    "timedOut": false
                  },
                  "adapterSignals": [
                    {
                      "type": "SESSION_BOOTSTRAP_FAILED",
                      "message": "session ensure exited non-zero",
                      "observedSequence": 0,
                      "source": "session_command",
                      "sessionId": "",
                      "superseded": false
                    }
                  ]
                }
                """.formatted(ws));

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", ".agent/workstreams/" + ws + "/runs/run-bootstrap/run.json");
        facts.put("exit_code", 4);
        facts.put("process_usability", "EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT");
        facts.put("agent", "codex");
        facts.put("session", "codex-reviewer");
        // Contradictory legacy reason: the typed outcome must take priority over raw/legacy text.
        facts.put("acpx_failure.reason", "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED");
        facts.put("acpx_failure.recoverable", true);
        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "SESSION_BOOTSTRAP_FAILED",
                NodeStatus.FAILED,
                List.of(),
                facts,
                "session bootstrap failed"
        ), AgentResultContractResult.missing(),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")), null);

        // Production path: AgentRunMetadataFinalizer reads run.json, resolves the typed
        // outcome via the embedded core resolver, and writes executionOutcome to run.json.
        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var runMetadata = map(OBJECT_MAPPER.readValue(runJson.toFile(), Map.class).get("executionOutcome"));
        assertEquals("RECOVERABLE_ADAPTER_FAILURE", runMetadata.get("status"));
        assertEquals("session_bootstrap_failed", runMetadata.get("primaryReason"));
        assertFalse(runMetadata.containsKey("retrySafe"),
                "outcome projection must not emit a derived retrySafe");

        // Production path: ledger stores the finalized node result; RecoveryAssessment consumes
        // the typed executionOutcome; WorkstreamAttention renders it.
        var paths = new WorkstreamPaths(tempDir, new DualId(ws));
        var ledger = new WorkflowFileLedger();
        var definition = new WorkflowDefinition("wf-bootstrap-chain", 1, "intake.codex_review",
                List.of(new NodeDefinition("intake.codex_review", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of());
        ledger.initialize(paths, "wf-bootstrap-chain", definition);
        var initial = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, initial.nodeInstanceId(), NodeStatus.FAILED,
                "session_bootstrap_failed", List.of(), finalized.result());
        var failed = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        var assessment = RecoveryAssessment.from(failed, paths.workspaceRoot(), paths.workstreamId().value());
        assertEquals("agent_session_recovery", assessment.classification());
        assertEquals("INSPECT_BEFORE_RETRY", assessment.retryReadiness());
        assertFalse(assessment.retrySafe(), "generic bootstrap failure must not be retry-safe");
        assertFalse(assessment.suggestedCommand().contains("sessions new"),
                "must not assume sessions new recovers an unknown bootstrap cause");

        var attention = WorkstreamAttentionEvaluator.evaluate(paths);
        assertEquals(assessment.retrySafe(), attention.retrySafe(),
                "WorkstreamAttention must copy RecoveryAssessment.retrySafe");
        assertFalse(attention.retrySafe());
        assertEquals(WorkstreamAttentionCategory.RUNTIME_RECOVERY, attention.category());
        assertFalse(attention.nextAction().contains("Rebuild"), "bootstrap must not suggest rebuild");
        assertTrue(attention.nextAction().contains("Inspect"), "bootstrap must direct inspection");
        assertFalse(attention.command().contains("dualctl resume"), "no auto-resume suggestion");
    }

    @Test
    void reconnectFailureKeepsRebuildSemanticsThroughChain() throws Exception {
        var ws = "ws-reconnect-chain";
        var runJson = tempDir.resolve(".agent/workstreams/" + ws + "/runs/run-reconnect/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 2,
                  "runId": "run-reconnect",
                  "workstreamId": "%s",
                  "nodeId": "intake.codex_review",
                  "agent": "codex",
                  "session": "codex-reviewer",
                  "status": "process_exited",
                  "processResult": {
                    "command": "acpx codex -s codex-reviewer",
                    "exitCode": 4,
                    "usability": "KILLED_AFTER_RECOVERABLE_SIGNAL",
                    "durationMs": 2000,
                    "stdoutPath": "stdout.log",
                    "stderrPath": "stderr.log",
                    "interrupted": false,
                    "timedOut": false
                  },
                  "adapterSignals": [
                    {
                      "type": "AGENT_RECONNECT_REQUIRED",
                      "message": "agent needs reconnect",
                      "observedSequence": 0,
                      "source": "acpx",
                      "sessionId": "",
                      "superseded": false
                    }
                  ]
                }
                """.formatted(ws));

        var facts = new LinkedHashMap<String, Object>();
        facts.put("run_metadata", ".agent/workstreams/" + ws + "/runs/run-reconnect/run.json");
        facts.put("exit_code", 4);
        var nodeResult = AgentExecutionResultFacts.attach(new NodeResult(
                "RECOVERABLE_AGENT_RECONNECT", NodeStatus.FAILED, List.of(), facts, "reconnect"
        ), AgentResultContractResult.missing(),
                List.of(ArtifactContractResult.requiredMissing("agent_output_contract")), null);

        // Same production path as the bootstrap test: finalizer -> run.json -> ledger.
        var finalized = new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);
        var paths = new WorkstreamPaths(tempDir, new DualId(ws));
        var ledger = new WorkflowFileLedger();
        var definition = new WorkflowDefinition("wf-reconnect-chain", 1, "intake.codex_review",
                List.of(new NodeDefinition("intake.codex_review", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of());
        ledger.initialize(paths, "wf-reconnect-chain", definition);
        var initial = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, initial.nodeInstanceId(), NodeStatus.FAILED,
                "RECOVERABLE_AGENT_RECONNECT", List.of(), finalized.result());
        var failed = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        // Reconnect must keep its REBUILD readiness + retrySafe=true (no regression from the
        // bootstrap fail-closed change, which only touched session_bootstrap_failed).
        var assessment = RecoveryAssessment.from(failed, paths.workspaceRoot(), paths.workstreamId().value());
        assertEquals("agent_session_recovery", assessment.classification());
        assertEquals("REBUILD_ACPX_SESSION_THEN_RETRY", assessment.retryReadiness());
        assertTrue(assessment.retrySafe(), "reconnect must remain retry-safe");

        var attention = WorkstreamAttentionEvaluator.evaluate(paths);
        assertTrue(attention.retrySafe());
        assertTrue(attention.nextAction().contains("Rebuild"),
                "reconnect must keep the rebuild next action");
        assertTrue(assessment.suggestedCommand().contains("sessions new --name"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
