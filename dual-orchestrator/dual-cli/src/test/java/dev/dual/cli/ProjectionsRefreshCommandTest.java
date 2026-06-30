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
        assertEquals("post_process", topRuns.get(0).get("dominantPhase"));
    }

    @Test
    void missingLedgerReturnsNonZeroWithoutCreatingProjection() throws Exception {
        var command = new ProjectionsRefreshCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-missing";

        assertEquals(1, command.call());
        assertTrue(!Files.exists(tempDir.resolve(".agent/workstreams/ws-missing/timeline.json")));
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

    @FunctionalInterface
    private interface ThrowingCallable {
        int call() throws Exception;
    }
}
