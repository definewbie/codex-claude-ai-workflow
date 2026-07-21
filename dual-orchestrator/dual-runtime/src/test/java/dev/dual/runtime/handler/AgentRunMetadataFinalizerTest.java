package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.AgentResultContractResult;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRunMetadataFinalizerTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

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
        assertEquals(true, outcome.get("retrySafe"));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
