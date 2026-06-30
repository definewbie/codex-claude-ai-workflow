package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.TerminalControlResult;
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
        ), TerminalControlResult.missing(), List.of(ArtifactContractResult.requiredMissing("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        var attempt = map(json.get("executionAttempt"));
        assertEquals("MODEL_MISMATCH", attempt.get("failureSignal"));
        var outcome = map(json.get("executionOutcome"));
        var signal = map(list(outcome.get("secondarySignals")).get(0));
        assertEquals("MODEL_MISMATCH", signal.get("type"));
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
        ), TerminalControlResult.valid("IMPLEMENTED"), List.of(ArtifactContractResult.requiredValid("agent_output_contract")), null);

        new AgentRunMetadataFinalizer().finalizeNodeResult(tempDir, nodeResult);

        var json = OBJECT_MAPPER.readValue(runJson.toFile(), Map.class);
        assertEquals(3, json.get("schemaVersion"));
        assertEquals("completed", json.get("status"));
        var terminal = map(json.get("terminalControlResult"));
        assertEquals(true, terminal.get("present"));
        var contracts = list(json.get("artifactContractResults"));
        assertEquals(1, contracts.size());
        var outcome = map(json.get("executionOutcome"));
        assertEquals("SUCCESS", outcome.get("status"));
        assertEquals(true, outcome.get("nodeMaySucceed"));
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
