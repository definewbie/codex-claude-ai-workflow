package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryAssessmentTest {
    @TempDir
    Path tempDir;

    @Test
    void readsContractFailureDiagnosticWhenPresent() throws Exception {
        var diagnosticRef = ".agent/workstreams/ws-recovery/artifacts/contract-failures/node-001.json";
        var diagnosticPath = tempDir.resolve(diagnosticRef);
        Files.createDirectories(diagnosticPath.getParent());
        Files.writeString(diagnosticPath, """
                {
                  "artifact_type": "contract_failure_diagnostic",
                  "category": "missing_structured_artifact",
                  "recovery_hint": "Ask the agent to emit the typed artifact.",
                  "recommended_owner": "agent_or_prompt"
                }
                """, StandardCharsets.UTF_8);
        var node = new NodeInstanceDocument(
                1,
                "node-001",
                "implement.claude_self_test_report",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.FAILED,
                "implement",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(
                        "reason", "AGENT_OUTPUT_CONTRACT_VIOLATION",
                        "contract_failure_diagnostic_ref", diagnosticRef,
                        "contract_failure_diagnostic_markdown_ref",
                        ".agent/workstreams/ws-recovery/artifacts/contract-failures/node-001.md"
                ),
                "",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:01:00Z")
        );

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("artifact_contract_failure", assessment.classification());
        assertEquals("RETRY_AFTER_OUTPUT_CONTRACT_FIX", assessment.retryReadiness());
        assertEquals("Ask the agent to emit the typed artifact.", assessment.reason());
        assertEquals(diagnosticRef, assessment.diagnosticJsonRef());
        assertEquals("missing_structured_artifact", assessment.contractDiagnostic().get("category"));
        assertTrue(assessment.suggestedCommand().contains("dualctl retry --workdir " + tempDir.toAbsolutePath().normalize()));
        assertEquals("artifact_contract_failure", assessment.toMap().get("classification"));
    }
}
