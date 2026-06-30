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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                Map.of(),
                List.of(),
                Map.of(
                        "reason", "AGENT_OUTPUT_CONTRACT_VIOLATION",
                        "contract_failure_diagnostic_ref", diagnosticRef,
                        "contract_failure_diagnostic_markdown_ref",
                        ".agent/workstreams/ws-recovery/artifacts/contract-failures/node-001.md"
                ),
                "",
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

    @Test
    void agentExecutionOutcomeTakesPriorityOverLegacyAcpxReason() {
        var node = failedAcpxNode(Map.of(
                "acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT",
                "acpx_failure.recoverable", true,
                "agent_execution_outcome", Map.of(
                        "status", "CONTRACT_FAILED",
                        "primaryReason", "required_artifact_contract_failed",
                        "recoveryCategory", "ARTIFACT_CONTRACT_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "Structured artifact is missing."
                )
        ));

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("artifact_contract_failure", assessment.classification());
        assertEquals("RETRY_AFTER_OUTPUT_CONTRACT_FIX", assessment.retryReadiness());
        assertEquals("Structured artifact is missing.", assessment.reason());
        assertFalse(assessment.suggestedCommand().contains("sessions close"));
    }

    @Test
    void revisionLimitIsSemanticRecoveryNotRuntimeRetry() {
        var node = new NodeInstanceDocument(
                1,
                "node-002",
                "design.decision",
                NodeKind.DECISION,
                "review.decision",
                NodeStatus.BLOCKED,
                "design",
                "iter-002",
                List.of(),
                Map.of(),
                List.of("runs/design-review.md"),
                Map.of(
                        "reason", "revision_limit_reached",
                        "outcome", "NEEDS_REVISION"
                ),
                "",
                "",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:01:00Z")
        );

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("semantic_revision_limit", assessment.classification());
        assertEquals("NEEDS_HUMAN_DECISION", assessment.retryReadiness());
        assertTrue(assessment.reason().contains("revision budget"));
        assertFalse(assessment.reason().contains("quota"));
        assertTrue(assessment.suggestedCommand().contains("recovery abort"));
    }

    @Test
    void interruptedAgentOutputHasTypedRecoveryAction() {
        var node = failedAcpxNode(Map.of(
                "agent", "claude",
                "session", "claude-main",
                "acpx_failure.reason", "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED",
                "acpx_failure.recoverable", true
        ));

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("agent_output_interrupted", assessment.classification());
        assertEquals("REBUILD_ACPX_SESSION_THEN_RETRY", assessment.retryReadiness());
        assertTrue(assessment.reason().contains("terminal contract"));
        assertTrue(assessment.suggestedCommand().contains("acpx claude sessions close claude-main"));
        assertFalse(assessment.reason().contains("quota, reconnect, or session state"));
    }

    @Test
    void authFailureAsksForCredentialsInsteadOfSessionRebuild() {
        var node = failedAcpxNode(Map.of(
                "agent", "claude",
                "session", "claude-main",
                "acpx_failure.reason", "RECOVERABLE_AGENT_AUTH_FAILED",
                "acpx_failure.recoverable", true
        ));

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("agent_auth_failed", assessment.classification());
        assertEquals("FIX_AGENT_AUTH_THEN_RETRY", assessment.retryReadiness());
        assertTrue(assessment.reason().contains("credentials"));
        assertFalse(assessment.suggestedCommand().contains("sessions close"));
        assertTrue(assessment.suggestedCommand().contains("dualctl retry"));
    }

    @Test
    void providerConnectionFailureAsksForProviderAvailabilityBeforeRetry() {
        var node = failedAcpxNode(Map.of(
                "agent", "claude",
                "session", "claude-main",
                "acpx_failure.reason", "RECOVERABLE_PROVIDER_CONNECTION_FAILED",
                "acpx_failure.recoverable", true
        ));

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("provider_connection_failed", assessment.classification());
        assertEquals("WAIT_OR_FIX_PROVIDER_CONNECTION_THEN_RETRY", assessment.retryReadiness());
        assertTrue(assessment.reason().contains("provider connection"));
        assertFalse(assessment.suggestedCommand().contains("sessions close"));
        assertTrue(assessment.suggestedCommand().contains("dualctl retry"));
    }

    @Test
    void providerConnectionFailureFromExecutionOutcomeUsesSpecificReadiness() {
        var node = failedAcpxNode(Map.of(
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "provider_connection_failed",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "Provider socket was unavailable."
                )
        ));

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("provider_connection_failed", assessment.classification());
        assertEquals("WAIT_OR_FIX_PROVIDER_CONNECTION_THEN_RETRY", assessment.retryReadiness());
        assertEquals("Provider socket was unavailable.", assessment.reason());
    }

    private static NodeInstanceDocument failedAcpxNode(Map<String, Object> result) {
        return new NodeInstanceDocument(
                1,
                "node-acpx-failed",
                "intake.claude_brainstorm",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.FAILED,
                "intake",
                "iter-001",
                List.of(),
                Map.of(),
                List.of(),
                result,
                "",
                "",
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:01:00Z")
        );
    }
}
