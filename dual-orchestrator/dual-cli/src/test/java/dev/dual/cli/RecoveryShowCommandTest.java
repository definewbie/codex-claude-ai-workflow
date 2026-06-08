package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryShowCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void showPrintsRecoverableRuntimeAssessmentAndCurrentFailureSummary() throws Exception {
        var workstreamId = "ws-recovery-show";
        writeFailure(workstreamId, NodeStatus.FAILED, "RECOVERABLE_RATE_LIMIT", Map.of(
                "exit_code", 4,
                "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT",
                "acpx_failure.recoverable", true
        ));

        var printed = runShow(workstreamId);

        assertTrue(printed.contains("Recovery Assessment"));
        assertTrue(printed.contains("- Classification: recoverable_agent_runtime"));
        assertTrue(printed.contains("- Retry Readiness: RETRY_AFTER_EXTERNAL_FIX"));
        assertTrue(printed.contains("dualctl retry --workdir " + tempDir.toAbsolutePath().normalize() + " " + workstreamId));
        assertTrue(printed.contains("Current Failure"));
        assertTrue(printed.contains("RECOVERABLE_RATE_LIMIT"));
        assertTrue(printed.contains("design.stderr.log"));
        assertTrue(printed.contains(".agent/workstreams/" + workstreamId + "/recovery/current-failure.md"));
    }

    @Test
    void showWarnsRevisionLimitNeedsHumanDecision() throws Exception {
        var workstreamId = "ws-revision-limit";
        writeFailure(workstreamId, NodeStatus.BLOCKED, "revision_limit_reached", Map.of(
                "reason", "revision_limit_reached",
                "decision", "NEEDS_REVISION"
        ));

        var printed = runShow(workstreamId);

        assertTrue(printed.contains("- Classification: semantic_revision_limit"));
        assertTrue(printed.contains("- Retry Readiness: NEEDS_HUMAN_DECISION"));
        assertTrue(printed.contains("Blind retry will likely repeat the same loop."));
        assertTrue(printed.contains("dualctl recovery abort --workdir " + tempDir.toAbsolutePath().normalize() + " " + workstreamId));
    }

    @Test
    void showPrintsContractDiagnosticRefsWhenAvailable() throws Exception {
        var workstreamId = "ws-contract-diagnostic";
        var diagnosticJson = ".agent/workstreams/" + workstreamId + "/artifacts/contract-failures/node-design.json";
        writeDiagnostic(diagnosticJson);
        writeFailure(workstreamId, NodeStatus.FAILED, "AGENT_OUTPUT_CONTRACT_VIOLATION", Map.of(
                "reason", "AGENT_OUTPUT_CONTRACT_VIOLATION",
                "contract_failure_diagnostic_ref", diagnosticJson,
                "contract_failure_diagnostic_markdown_ref",
                ".agent/workstreams/" + workstreamId + "/artifacts/contract-failures/node-design.md"
        ));

        var printed = runShow(workstreamId);

        assertTrue(printed.contains("- Classification: artifact_contract_failure"));
        assertTrue(printed.contains("- Retry Readiness: RETRY_AFTER_OUTPUT_CONTRACT_FIX"));
        assertTrue(printed.contains("- Reason: Ask the agent to emit the typed artifact."));
        assertTrue(printed.contains("- Diagnostic JSON: " + diagnosticJson));
        assertTrue(printed.contains("- Diagnostic Markdown: .agent/workstreams/" + workstreamId
                + "/artifacts/contract-failures/node-design.md"));
    }

    private void writeDiagnostic(String diagnosticRef) throws Exception {
        var path = tempDir.resolve(diagnosticRef);
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                {
                  "artifact_type": "contract_failure_diagnostic",
                  "category": "missing_structured_artifact",
                  "recovery_hint": "Ask the agent to emit the typed artifact."
                }
                """, StandardCharsets.UTF_8);
    }

    private void writeFailure(String workstreamId, NodeStatus nodeStatus, String outcome, Map<String, Object> result) throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "test-recovery-show",
                1,
                "design.claude",
                List.of(new NodeDefinition("design.claude", NodeKind.TASK, "agent.acpx", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-recovery-show", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), nodeStatus,
                outcome,
                List.of(".agent/workstreams/" + workstreamId + "/runs/run-001/outputs/design.stderr.log"),
                result);
    }

    private String runShow(String workstreamId) throws Exception {
        var command = new RecoveryShowCommand();
        command.workdir = tempDir;
        command.workstreamId = workstreamId;

        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        return out.toString(StandardCharsets.UTF_8);
    }
}
