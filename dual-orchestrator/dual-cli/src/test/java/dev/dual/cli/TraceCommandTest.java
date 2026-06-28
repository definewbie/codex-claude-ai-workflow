package dev.dual.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void printsOneScreenTraceForCompletedWorkstream() throws Exception {
        writeCompletedLedger();

        var command = new TraceCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-trace";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Trace"));
        assertTrue(printed.contains("Workstream: ws-trace"));
        assertTrue(printed.contains("Status: COMPLETED"));
        assertTrue(printed.contains("Final Acceptance: ACCEPT_WITH_NOTES"));
        assertTrue(printed.contains("Repair Loops: 1"));
        assertTrue(printed.contains("Verification Confidence: MEDIUM"));
        assertTrue(printed.contains("Why:"));
        assertTrue(printed.contains("Workflow completed"));
        assertTrue(printed.contains("Attention:"));
        assertTrue(printed.contains("[warning] Accepted with notes"));
        assertTrue(printed.contains("[info] Repair loop occurred"));
        assertTrue(printed.contains("[ok] intake.claude_brainstorm"));
        assertTrue(printed.contains("promptTokens="));
        assertTrue(printed.contains("topSections="));
        assertTrue(printed.contains("[loop] repair: REJECT_IMPLEMENTATION_BUG"));
        assertTrue(printed.contains("[ok] delivery.prepare_pr"));
        assertTrue(printed.contains("Tool Governance:"));
        assertTrue(printed.contains("missingRereadReasons=1"));
        assertTrue(printed.contains("README.md x2 evidenceKnown=true rereadReason=missing"));
        assertTrue(printed.contains("Token Attribution:"));
        assertTrue(printed.contains("top node: design.claude_design"));
        assertTrue(printed.contains("heavy=[cachedRead, transcript]"));
        assertTrue(printed.contains("usage visibility: {provider_run_usage=1, session_snapshot_only=1}"));
        assertTrue(printed.contains("usage gap: codex acceptance.codex_verifier_review visibility=session_snapshot_only"));
        assertTrue(printed.contains("transcriptGrowthRun=run-1"));
        assertTrue(printed.contains("top read cost: README.md"));
        assertTrue(printed.contains("Final Report:"));
        assertTrue(printed.contains("Handoff JSON:"));
        assertFalse(printed.contains("Current Failure:"));
        assertTrue(printed.contains("dualctl summary --workdir"));
    }

    @Test
    void printsRecoveryNextActionForFailedWorkstream() throws Exception {
        writeFailedLedger();
        writeReconnectRun();

        var command = new TraceCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-trace";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Status: WAITING_FOR_RECOVERY"));
        assertTrue(printed.contains("Current Node: acceptance.classify_failure"));
        assertTrue(printed.contains("[critical] Agent capacity recovery required"));
        assertTrue(printed.contains("[critical] Final acceptance rejected"));
        assertTrue(printed.contains("[warning] Verification confidence is LOW"));
        assertTrue(printed.contains("dualctl recovery show --workdir"));
        assertTrue(printed.contains("dualctl retry --workdir"));
        assertTrue(printed.contains("Current Failure:"));
        assertTrue(printed.contains("failed/RECOVERABLE_FAILED(AGENT_RECONNECT_REQUIRED)"));
        assertTrue(printed.contains("tokens=unavailable type=UNAVAILABLE src=not_available reason=AGENT_RECONNECT_REQUIRED"));
    }

    @Test
    void doesNotWarnAboutVerificationConfidenceBeforeAcceptance() throws Exception {
        writeHumanGateLedger();

        var command = new TraceCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-trace";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Status: WAITING_FOR_HUMAN"));
        assertTrue(printed.contains("[info] Human gate is open"));
        assertTrue(printed.contains("dualctl review show --workdir"));
        assertFalse(printed.contains("[warning] Verification confidence is UNKNOWN"));
    }

    @Test
    void treatsInvalidRunMetadataAsProjectionWarning() throws Exception {
        writeCompletedLedger();
        var runsDir = tempDir.resolve(".agent/workstreams/ws-trace/runs");
        Files.createDirectories(runsDir.resolve("run-bad"));
        Files.writeString(runsDir.resolve("run-bad/run.json"), "{not-json", StandardCharsets.UTF_8);
        Files.createDirectories(runsDir.resolve("run-missing"));

        var command = new TraceCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-trace";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Status: COMPLETED"));
        assertTrue(printed.contains("Run Metadata Warnings:"));
        assertTrue(printed.contains("run-bad  metadata unavailable:"));
        assertTrue(printed.contains("run-missing  metadata unavailable: missing run.json"));
    }

    private void writeCompletedLedger() throws Exception {
        writeBaseLedger("COMPLETED", """
                "activeNodeInstanceIds": [],
                "openGateIds": [],
                """);
        writeSummary("""
                {
                  "final_status": "COMPLETED",
                  "final_acceptance_decision": "ACCEPT_WITH_NOTES",
                  "repair_loop_count": 1,
                  "verification_confidence": "MEDIUM",
                  "original_repo_clean": true
                }
                """);
    }

    private void writeFailedLedger() throws Exception {
        writeBaseLedger("WAITING_FOR_RECOVERY", """
                "activeNodeInstanceIds": ["node-failed"],
                "openGateIds": [],
                """);
        writeSummary("""
                {
                  "final_status": "WAITING_FOR_RECOVERY",
                  "final_acceptance_decision": "REJECT",
                  "repair_loop_count": 0,
                  "verification_confidence": "LOW",
                  "original_repo_clean": true
                }
                """);
    }

    private void writeHumanGateLedger() throws Exception {
        writeBaseLedger("WAITING_FOR_HUMAN", """
                "activeNodeInstanceIds": ["node-human-gate"],
                "openGateIds": ["gate-node-human-gate"],
                """);
        writeSummary("{}");
    }

    private void writeReconnectRun() throws Exception {
        var run = tempDir.resolve(".agent/workstreams/ws-trace/runs/run-1/run.json");
        Files.writeString(run, """
                {
                  "schemaVersion": 4,
                  "runId": "run-1",
                  "workstreamId": "ws-trace",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "session": "claude-main-ws-trace",
                  "sessionScope": "workstream",
                  "sessionSegmentKey": "ws:ws-trace",
                  "durationMs": 1000,
                  "exitCode": 4,
                  "status": "failed",
                  "failureReason": "RECOVERABLE_AGENT_RECONNECT",
                  "recoverable": true,
                  "executionAttempt": {
                    "attemptId": "att-run-1",
                    "status": "RECOVERABLE_FAILED",
                    "processStatus": "RECONNECT_REQUIRED",
                    "failureSignal": "AGENT_RECONNECT_REQUIRED",
                    "recoverable": true,
                    "retrySafe": false,
                    "message": "agent needs reconnect",
                    "sessionName": "claude-main-ws-trace",
                    "sessionScope": "workstream",
                    "sessionSegmentKey": "ws:ws-trace"
                  },
                  "usage": {
                    "inputTokens": 0,
                    "outputTokens": 0,
                    "cachedReadTokens": 0,
                    "cachedWriteTokens": 0,
                    "totalTokens": 0,
                    "source": "not_available",
                    "measurementType": "UNAVAILABLE",
                    "unavailableReason": "AGENT_RECONNECT_REQUIRED"
                  }
                }
                """, StandardCharsets.UTF_8);
    }

    private void writeBaseLedger(String status, String activeBlock) throws Exception {
        var root = tempDir.resolve(".agent/workstreams/ws-trace");
        Files.createDirectories(root.resolve("reports"));
        Files.createDirectories(root.resolve("artifacts/next-stage"));
        Files.createDirectories(root.resolve("delivery"));
        Files.createDirectories(root.resolve("metrics"));
        Files.createDirectories(root.resolve("recovery"));
        Files.createDirectories(root.resolve("runs/run-1"));
        Files.createDirectories(root.resolve("runs/run-1/prompts"));
        Files.writeString(root.resolve("runs/run-1/prompts/review.md.manifest.json"), """
                {"totalEstimatedTokens": 42, "sections":[{"name":"context_package","estimatedTokens":30},{"name":"requirement","estimatedTokens":12}]}
                """, StandardCharsets.UTF_8);
        Files.createDirectories(root.resolve("workflow"));
        Files.createDirectories(root.resolve("nodes"));
        Files.writeString(root.resolve("reports/latest-final-report.md"), "# Final\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("artifacts/next-stage/handoff.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("artifacts/next-stage/next-stage-prompt.md"), "# Next\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("delivery/pr-body.md"), "# PR\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("recovery/current-failure.md"), "# Current Failure\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("runs/run-1/run.json"), """
                {
                  "schemaVersion": 1,
                  "runId": "run-1",
                  "workstreamId": "ws-trace",
                  "nodeId": "acceptance.codex_verifier_review",
                  "agent": "codex",
                  "session": "codex-reviewer",
                  "promptManifestPath": ".agent/workstreams/ws-trace/runs/run-1/prompts/review.md.manifest.json",
                  "durationMs": 123,
                  "exitCode": 0,
                  "status": "completed",
                  "usage": {
                    "inputTokens": 10,
                    "outputTokens": 2,
                    "cachedReadTokens": 0,
                    "cachedWriteTokens": 0,
                    "totalTokens": 12,
                    "costAmount": 0.01,
                    "costCurrency": "USD"
                  }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("events.jsonl"), """
                {"event_type":"node_routed","payload":{"outcome":"REJECT_IMPLEMENTATION_BUG"}}
                {"event_type":"iteration_started","payload":{"trigger_action":"REJECT_IMPLEMENTATION_BUG"}}
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("metrics/workstream-metrics.json"), """
                {
                  "schemaVersion": 1,
                  "toolMetrics": {
                    "toolCallCount": 5,
                    "readCount": 4,
                    "bashCount": 1,
                    "repeatedReadFiles": [
                      {
                        "path": "README.md",
                        "count": 2,
                        "nodes": ["intake.claude_brainstorm", "design.claude_design"],
                        "agents": ["claude"],
                        "evidenceKnown": true,
                        "rereadReasonRequired": true,
                        "rereadReasonStatus": "missing"
                      }
                    ],
                    "missingRereadReasonCount": 1
                  },
                  "tokenAttribution": {
                    "schemaVersion": 1,
                    "usageVisibility": {
                      "byVisibility": {"provider_run_usage": 1, "session_snapshot_only": 1},
                      "byAgent": {
                        "claude": {"provider_run_usage": 1},
                        "codex": {"session_snapshot_only": 1}
                      },
                      "gaps": [
                        {
                          "runId": "run-1",
                          "nodeId": "acceptance.codex_verifier_review",
                          "agent": "codex",
                          "visibility": "session_snapshot_only",
                          "reason": "acpx exposed only cumulative session usage_update; per-run provider result.usage was not attributable"
                        }
                      ]
                    },
                    "nodeHotspots": [
                      {"nodeId":"design.claude_design","agent":"claude","runCount":1,"totalTokens":5000,"cachedReadTokens":4500,"outputTokens":200,"transcriptBytes":30000,"heavy":["cachedRead","transcript"]}
                    ],
                    "sessionSnowball": [
                      {"sessionSegmentKey":"ws:ws-trace","maxTranscriptGrowthRun":"run-1","runs":[]}
                    ],
                    "fileReadCost": [
                      {"path":"README.md","readCount":2,"fileBytes":800,"estimatedReadTokens":200,"rereadReasonStatus":"missing"}
                    ]
                  }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(root.resolve("workflow/current.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-trace",
                  "workflowInstanceId": "wf-ws-trace",
                  "workflowId": "agent-lifecycle-full",
                  "definitionVersion": 3,
                  "status": "%s",
                  %s
                  "lastEventId": "evt-000001",
                  "updatedAt": "2026-06-04T00:00:00Z"
                }
                """.formatted(status, activeBlock), StandardCharsets.UTF_8);
        writeNode(root, "node-human-gate", "gate.human_requirement_review", "GATE", "human.review", "WAITING", "iter-001", "{}");
        writeNode(root, "node-failed", "acceptance.classify_failure", "TASK", "acceptance.failure_classify", "FAILED", "iter-003", """
                {
                  "acpx_failure.reason": "RECOVERABLE_RATE_LIMIT",
                  "acpx_failure.recoverable": true
                }
                """);
        Files.writeString(root.resolve("timeline.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-trace",
                  "workflowId": "agent-lifecycle-full",
                  "definitionVersion": 3,
                  "workflowStatus": "%s",
                  %s
                  "updatedAt": "2026-06-04T00:00:00Z",
                  "nodes": [
                    {
                      "nodeDefinitionId": "intake.claude_brainstorm",
                      "kind": "TASK",
                      "status": "COMPLETED",
                      "iterationId": "iter-001",
                      "durationMs": 10
                    },
                    {
                      "nodeDefinitionId": "acceptance.classify_failure",
                      "kind": "TASK",
                      "status": "COMPLETED",
                      "iterationId": "iter-002",
                      "durationMs": 20,
                      "outcome": "REJECT_IMPLEMENTATION_BUG"
                    },
                    {
                      "nodeDefinitionId": "delivery.prepare_pr",
                      "kind": "TASK",
                      "status": "COMPLETED",
                      "iterationId": "iter-003",
                      "durationMs": 30
                    },
                    {
                      "nodeInstanceId": "node-human-gate",
                      "nodeDefinitionId": "gate.human_requirement_review",
                      "kind": "GATE",
                      "status": "WAITING",
                      "iterationId": "iter-001"
                    },
                    {
                      "nodeInstanceId": "node-failed",
                      "nodeDefinitionId": "acceptance.classify_failure",
                      "kind": "TASK",
                      "status": "FAILED",
                      "iterationId": "iter-003",
                      "durationMs": 40
                    }
                  ]
                }
                """.formatted(status, activeBlock), StandardCharsets.UTF_8);
    }

    private void writeNode(String root, String nodeInstanceId, String nodeDefinitionId, String kind,
                           String handlerType, String status, String iterationId, String result) throws Exception {
        writeNode(Path.of(root), nodeInstanceId, nodeDefinitionId, kind, handlerType, status, iterationId, result);
    }

    private void writeNode(Path root, String nodeInstanceId, String nodeDefinitionId, String kind,
                           String handlerType, String status, String iterationId, String result) throws Exception {
        Files.writeString(root.resolve("nodes").resolve(nodeInstanceId + ".json"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "%s",
                  "nodeDefinitionId": "%s",
                  "kind": "%s",
                  "handlerType": "%s",
                  "status": "%s",
                  "stageId": "stage-test",
                  "iterationId": "%s",
                  "inputRefs": [],
                  "inputTransition": {},
                  "outputRefs": [],
                  "result": %s,
                  "runRef": null,
                  "latestExecutionId": "",
                  "startedAt": null,
                  "finishedAt": null
                }
                """.formatted(nodeInstanceId, nodeDefinitionId, kind, handlerType, status, iterationId, result), StandardCharsets.UTF_8);
    }

    private void writeSummary(String json) throws Exception {
        var dir = tempDir.resolve(".agent/workstreams/ws-trace/reports");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("real-case-summary.json"), json, StandardCharsets.UTF_8);
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

    @FunctionalInterface
    private interface ThrowingCallable {
        int call() throws Exception;
    }
}
