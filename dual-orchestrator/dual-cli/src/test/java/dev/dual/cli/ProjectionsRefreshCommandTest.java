package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionsRefreshCommandTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void refreshesTimelineAndMetricsFromExistingLedger() throws Exception {
        writeLedgerWithoutProjections();
        var workflowCurrent = tempDir.resolve(".agent/workstreams/ws-refresh/workflow/current.json");
        var nodeJson = tempDir.resolve(".agent/workstreams/ws-refresh/nodes/node-1.json");
        var runJson = tempDir.resolve(".agent/workstreams/ws-refresh/runs/run-1/run.json");
        var workflowBefore = Files.readString(workflowCurrent, StandardCharsets.UTF_8);
        var nodeBefore = Files.readString(nodeJson, StandardCharsets.UTF_8);
        var runBefore = Files.readString(runJson, StandardCharsets.UTF_8);

        var command = new ProjectionsRefreshCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-refresh";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Projections refreshed"));
        var timeline = tempDir.resolve(".agent/workstreams/ws-refresh/timeline.json");
        var metrics = tempDir.resolve(".agent/workstreams/ws-refresh/metrics/workstream-metrics.json");
        assertTrue(Files.isRegularFile(timeline));
        assertTrue(Files.isRegularFile(metrics));

        var timelineJson = MAPPER.readValue(timeline.toFile(), Map.class);
        assertEquals("RUNNING", String.valueOf(timelineJson.get("workflowStatus")));

        var metricsJson = MAPPER.readValue(metrics.toFile(), Map.class);
        var attribution = map(metricsJson.get("tokenAttribution"));
        var executionLatency = map(attribution.get("executionLatency"));
        var topRuns = list(executionLatency.get("topRuns"));
        assertEquals("run-1", topRuns.get(0).get("runId"));
        assertEquals("process_wait", topRuns.get(0).get("dominantPhase"));
        assertEquals(workflowBefore, Files.readString(workflowCurrent, StandardCharsets.UTF_8),
                "projection replay must not mutate workflow/current.json source of truth");
        assertEquals(nodeBefore, Files.readString(nodeJson, StandardCharsets.UTF_8),
                "projection replay must not mutate node source of truth");
        assertEquals(runBefore, Files.readString(runJson, StandardCharsets.UTF_8),
                "projection replay must not mutate run.json execution read model");
    }

    @Test
    void missingLedgerReturnsNonZeroWithoutCreatingProjection() throws Exception {
        var command = new ProjectionsRefreshCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-missing";

        assertEquals(1, command.call());
        assertTrue(!Files.exists(tempDir.resolve(".agent/workstreams/ws-missing/timeline.json")));
    }

    @Test
    void refreshSurfacesExecutionConsistencyWarningsWithoutMutatingLedger() throws Exception {
        writeStaleRunningLedgerWithoutProjections();
        var workflowCurrent = tempDir.resolve(".agent/workstreams/ws-stale/workflow/current.json");
        var nodeJson = tempDir.resolve(".agent/workstreams/ws-stale/nodes/node-1.json");
        var runJson = tempDir.resolve(".agent/workstreams/ws-stale/runs/run-stale/run.json");
        var workflowBefore = Files.readString(workflowCurrent, StandardCharsets.UTF_8);
        var nodeBefore = Files.readString(nodeJson, StandardCharsets.UTF_8);
        var runBefore = Files.readString(runJson, StandardCharsets.UTF_8);

        var command = new ProjectionsRefreshCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-stale";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Execution consistency: stale_legacy_running=1"));
        var metrics = tempDir.resolve(".agent/workstreams/ws-stale/metrics/workstream-metrics.json");
        var metricsJson = MAPPER.readValue(metrics.toFile(), Map.class);
        var provider = map(metricsJson.get("providerMetrics"));
        assertEquals(1, number(map(provider.get("executionConsistencyCounts")).get("stale_legacy_running")));
        var tokenRun = map(list(map(metricsJson.get("tokenMetrics")).get("runs")).get(0));
        assertEquals("stale_legacy_running", tokenRun.get("executionConsistency"));
        var attrRun = map(list(map(metricsJson.get("tokenAttribution")).get("perRun")).get(0));
        assertEquals("stale_legacy_running", attrRun.get("executionConsistency"));
        assertEquals(workflowBefore, Files.readString(workflowCurrent, StandardCharsets.UTF_8),
                "projection replay must not mutate workflow/current.json source of truth");
        assertEquals(nodeBefore, Files.readString(nodeJson, StandardCharsets.UTF_8),
                "projection replay must not mutate node source of truth");
        assertEquals(runBefore, Files.readString(runJson, StandardCharsets.UTF_8),
                "projection replay must not mutate run.json execution read model");
    }

    @Test
    void refreshSurfacesHistoricalDeadPidRunningNodeWithoutMutatingLedger() throws Exception {
        writeHistoricalDeadPidRunningLedgerWithoutProjections();
        var workflowCurrent = tempDir.resolve(".agent/workstreams/ws-historical-stale/workflow/current.json");
        var nodeJson = tempDir.resolve(".agent/workstreams/ws-historical-stale/nodes/node-1.json");
        var runJson = tempDir.resolve(".agent/workstreams/ws-historical-stale/runs/run-old/run.json");
        var workflowBefore = Files.readString(workflowCurrent, StandardCharsets.UTF_8);
        var nodeBefore = Files.readString(nodeJson, StandardCharsets.UTF_8);
        var runBefore = Files.readString(runJson, StandardCharsets.UTF_8);

        var command = new ProjectionsRefreshCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-historical-stale";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Execution consistency: "));
        assertTrue(printed.contains("missing_attempt_and_outcome=1"));
        assertTrue(printed.contains("stale_legacy_running=1"));
        var metrics = tempDir.resolve(".agent/workstreams/ws-historical-stale/metrics/workstream-metrics.json");
        var metricsJson = MAPPER.readValue(metrics.toFile(), Map.class);
        var provider = map(metricsJson.get("providerMetrics"));
        var counts = map(provider.get("executionConsistencyCounts"));
        assertEquals(1, number(counts.get("missing_attempt_and_outcome")));
        assertEquals(1, number(counts.get("stale_legacy_running")));
        assertEquals(1, number(metricsJson.get("runningCount")));
        var tokenRun = map(list(map(metricsJson.get("tokenMetrics")).get("runs")).get(0));
        assertEquals("missing_attempt_and_outcome", tokenRun.get("executionConsistency"));
        assertEquals(workflowBefore, Files.readString(workflowCurrent, StandardCharsets.UTF_8),
                "projection replay must not mutate workflow/current.json source of truth");
        assertEquals(nodeBefore, Files.readString(nodeJson, StandardCharsets.UTF_8),
                "projection replay must not mutate node source of truth");
        assertEquals(runBefore, Files.readString(runJson, StandardCharsets.UTF_8),
                "projection replay must not mutate run.json execution read model");
    }

    @Test
    void refreshSurfacesAttemptOutcomeConflictsWithoutMutatingLedger() throws Exception {
        writeAttemptOutcomeConflictLedgerWithoutProjections();
        var workflowCurrent = tempDir.resolve(".agent/workstreams/ws-conflict/workflow/current.json");
        var nodeJson = tempDir.resolve(".agent/workstreams/ws-conflict/nodes/node-1.json");
        var runJson = tempDir.resolve(".agent/workstreams/ws-conflict/runs/run-conflict/run.json");
        var workflowBefore = Files.readString(workflowCurrent, StandardCharsets.UTF_8);
        var nodeBefore = Files.readString(nodeJson, StandardCharsets.UTF_8);
        var runBefore = Files.readString(runJson, StandardCharsets.UTF_8);

        var command = new ProjectionsRefreshCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-conflict";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Execution consistency: attempt_outcome_conflict=1"));
        var metrics = tempDir.resolve(".agent/workstreams/ws-conflict/metrics/workstream-metrics.json");
        var metricsJson = MAPPER.readValue(metrics.toFile(), Map.class);
        var provider = map(metricsJson.get("providerMetrics"));
        assertEquals(1, number(map(provider.get("executionConsistencyCounts")).get("attempt_outcome_conflict")));
        var tokenRun = map(list(map(metricsJson.get("tokenMetrics")).get("runs")).get(0));
        assertEquals("attempt_outcome_conflict", tokenRun.get("executionConsistency"));
        var attrRun = map(list(map(metricsJson.get("tokenAttribution")).get("perRun")).get(0));
        assertEquals("attempt_outcome_conflict", attrRun.get("executionConsistency"));
        assertEquals(workflowBefore, Files.readString(workflowCurrent, StandardCharsets.UTF_8),
                "projection replay must not mutate workflow/current.json source of truth");
        assertEquals(nodeBefore, Files.readString(nodeJson, StandardCharsets.UTF_8),
                "projection replay must not mutate node source of truth");
        assertEquals(runBefore, Files.readString(runJson, StandardCharsets.UTF_8),
                "projection replay must not mutate run.json execution read model");
    }

    private void writeLedgerWithoutProjections() throws Exception {
        var root = tempDir.resolve(".agent/workstreams/ws-refresh");
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("nodes"));
        Files.createDirectories(root.resolve("runs/run-1"));

        Files.writeString(root.resolve("workflow/current.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-refresh",
                  "workflowInstanceId": "wf-refresh",
                  "workflowId": "agent-lifecycle-full",
                  "definitionVersion": 1,
                  "status": "RUNNING",
                  "activeNodeInstanceIds": ["node-1"],
                  "openGateIds": [],
                  "lastEventId": "evt-1",
                  "updatedAt": "2026-06-30T00:00:00Z"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("nodes/node-1.json"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-1",
                  "nodeDefinitionId": "implement.claude_implement",
                  "kind": "TASK",
                  "handlerType": "acpx.agent_run",
                  "status": "RUNNING",
                  "stageId": "stage-implement",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "inputTransition": {},
                  "outputRefs": [],
                  "result": {},
                  "runRef": "run-1",
                  "latestExecutionId": "exec-1",
                  "startedAt": "2026-06-30T00:00:00Z",
                  "finishedAt": null
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("runs/run-1/run.json"), """
                {
                  "schemaVersion": 4,
                  "runId": "run-1",
                  "workstreamId": "ws-refresh",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "status": "completed",
                  "durationMs": 1000,
                  "usage": {
                    "inputTokens": 100,
                    "outputTokens": 20,
                    "cachedReadTokens": 300,
                    "cachedWriteTokens": 0,
                    "totalTokens": 420,
                    "source": "acpx_stream",
                    "measurementType": "PROVIDER_RUN_USAGE"
                  },
                  "executionTimeline": [
                    {"kind": "process_started", "elapsedMs": 10},
                    {"kind": "terminal_control_detected", "elapsedMs": 300},
                    {"kind": "process_wait_completed", "elapsedMs": 500},
                    {"kind": "artifact_extracted", "elapsedMs": 700},
                    {"kind": "contract_validated", "elapsedMs": 900}
                  ]
                }
                """, StandardCharsets.UTF_8);
    }

    private void writeStaleRunningLedgerWithoutProjections() throws Exception {
        var root = tempDir.resolve(".agent/workstreams/ws-stale");
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("nodes"));
        Files.createDirectories(root.resolve("runs/run-stale"));

        Files.writeString(root.resolve("workflow/current.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-stale",
                  "workflowInstanceId": "wf-stale",
                  "workflowId": "agent-lifecycle-full",
                  "definitionVersion": 1,
                  "status": "RUNNING",
                  "activeNodeInstanceIds": ["node-1"],
                  "openGateIds": [],
                  "lastEventId": "evt-1",
                  "updatedAt": "2026-06-30T00:00:00Z"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("nodes/node-1.json"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-1",
                  "nodeDefinitionId": "implement.claude_implement",
                  "kind": "TASK",
                  "handlerType": "acpx.agent_run",
                  "status": "RUNNING",
                  "stageId": "stage-implement",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "inputTransition": {},
                  "outputRefs": [],
                  "result": {},
                  "runRef": "run-stale",
                  "latestExecutionId": "exec-stale",
                  "startedAt": "2026-06-30T00:00:00Z",
                  "finishedAt": null
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("runs/run-stale/run.json"), """
                {
                  "schemaVersion": 4,
                  "runId": "run-stale",
                  "workstreamId": "ws-stale",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "status": "running",
                  "durationMs": 1000,
                  "usage": {
                    "inputTokens": 100,
                    "outputTokens": 20,
                    "cachedReadTokens": 300,
                    "cachedWriteTokens": 0,
                    "totalTokens": 420,
                    "source": "acpx_stream",
                    "measurementType": "PROVIDER_RUN_USAGE"
                  },
                  "executionAttempt": {
                    "status": "STALLED",
                    "processStatus": "PROCESS_STALLED",
                    "failureSignal": "AGENT_PROCESS_STALLED"
                  },
                  "executionOutcome": {
                    "status": "RUNTIME_FAILED",
                    "primaryReason": "AGENT_PROCESS_STALLED",
                    "nodeMaySucceed": false
                  }
                }
                """, StandardCharsets.UTF_8);
    }

    private void writeHistoricalDeadPidRunningLedgerWithoutProjections() throws Exception {
        var root = tempDir.resolve(".agent/workstreams/ws-historical-stale");
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("nodes"));
        Files.createDirectories(root.resolve("runs/run-old"));

        Files.writeString(root.resolve("workflow/current.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-historical-stale",
                  "workflowInstanceId": "wf-historical-stale",
                  "workflowId": "agent-lifecycle-full",
                  "definitionVersion": 1,
                  "status": "RUNNING",
                  "activeNodeInstanceIds": ["node-1"],
                  "openGateIds": [],
                  "lastEventId": "evt-1",
                  "updatedAt": "2026-06-30T00:00:00Z"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("nodes/node-1.json"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-1",
                  "nodeDefinitionId": "implement.claude_self_test_report",
                  "kind": "TASK",
                  "handlerType": "acpx.agent_run",
                  "status": "RUNNING",
                  "stageId": "stage-implement",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "inputTransition": {},
                  "outputRefs": [],
                  "result": {"running_pid": 999999999},
                  "runRef": null,
                  "latestExecutionId": "",
                  "startedAt": "2026-06-30T00:00:00Z",
                  "finishedAt": null
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("runs/run-old/run.json"), """
                {
                  "schemaVersion": 4,
                  "runId": "run-old",
                  "workstreamId": "ws-historical-stale",
                  "nodeId": "implement.claude_self_test_report",
                  "agent": "claude",
                  "status": "failed",
                  "durationMs": 1000,
                  "failureReason": "ACPX_EXIT_143",
                  "usage": {
                    "inputTokens": 100,
                    "outputTokens": 20,
                    "cachedReadTokens": 300,
                    "cachedWriteTokens": 0,
                    "totalTokens": 420,
                    "source": "acpx_stream",
                    "measurementType": "PROVIDER_RUN_USAGE"
                  }
                }
                """, StandardCharsets.UTF_8);
    }

    private void writeAttemptOutcomeConflictLedgerWithoutProjections() throws Exception {
        var root = tempDir.resolve(".agent/workstreams/ws-conflict");
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("nodes"));
        Files.createDirectories(root.resolve("runs/run-conflict"));

        Files.writeString(root.resolve("workflow/current.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-conflict",
                  "workflowInstanceId": "wf-conflict",
                  "workflowId": "agent-lifecycle-full",
                  "definitionVersion": 1,
                  "status": "RUNNING",
                  "activeNodeInstanceIds": ["node-1"],
                  "openGateIds": [],
                  "lastEventId": "evt-1",
                  "updatedAt": "2026-06-30T00:00:00Z"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("nodes/node-1.json"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-1",
                  "nodeDefinitionId": "intake.codex_product_review",
                  "kind": "TASK",
                  "handlerType": "acpx.agent_run",
                  "status": "RUNNING",
                  "stageId": "stage-intake",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "inputTransition": {},
                  "outputRefs": [],
                  "result": {},
                  "runRef": "run-conflict",
                  "latestExecutionId": "exec-conflict",
                  "startedAt": "2026-06-30T00:00:00Z",
                  "finishedAt": null
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("runs/run-conflict/run.json"), """
                {
                  "schemaVersion": 4,
                  "runId": "run-conflict",
                  "workstreamId": "ws-conflict",
                  "nodeId": "intake.codex_product_review",
                  "agent": "codex",
                  "status": "failed",
                  "durationMs": 1000,
                  "usage": {
                    "inputTokens": 100,
                    "outputTokens": 20,
                    "cachedReadTokens": 0,
                    "cachedWriteTokens": 0,
                    "totalTokens": 120,
                    "source": "acpx_stream",
                    "measurementType": "PROVIDER_RUN_USAGE"
                  },
                  "executionAttempt": {
                    "status": "RECOVERABLE_FAILED",
                    "processStatus": "RECONNECT_REQUIRED",
                    "failureSignal": "AGENT_RECONNECT_REQUIRED"
                  },
                  "executionOutcome": {
                    "status": "SUCCESS",
                    "primaryReason": "completed",
                    "nodeMaySucceed": true
                  }
                }
                """, StandardCharsets.UTF_8);
    }

    private String captureStdout(ThrowingCallable callable) throws Exception {
        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, callable.call());
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<Map<String, Object>> list(Object value) {
        return (java.util.List<Map<String, Object>>) value;
    }

    private Number number(Object value) {
        return (Number) value;
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        int call() throws Exception;
    }
}
