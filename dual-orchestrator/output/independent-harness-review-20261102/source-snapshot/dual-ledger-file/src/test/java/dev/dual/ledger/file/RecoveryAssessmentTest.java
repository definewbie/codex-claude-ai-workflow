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
        assertFalse(assessment.retrySafe());
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

    @Test
    void typedPermissionFailurePreservesCurrentActionForOperatorWithoutBlindRetry() {
        var node = failedAcpxNode(Map.of(
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "permission_blocked",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "Blocking adapter signal: PERMISSION_BLOCKED - current ACP turn permission denied: Run tests: mvn test"
                )
        ));

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("agent_permission_blocked", assessment.classification());
        assertEquals("RETRY_AFTER_EXTERNAL_FIX", assessment.retryReadiness());
        assertFalse(assessment.retrySafe());
        assertTrue(assessment.reason().contains("mvn test"));
        assertTrue(assessment.suggestedCommand().contains("changing the permission policy"));
        assertTrue(assessment.suggestedCommand().contains("rebuilding the acpx session"));
    }

    @Test
    void typedWebSearchEscalationProjectsExactHumanAuthorityAction() throws Exception {
        var runRef = ".agent/workstreams/ws-recovery/runs/run-websearch/run.json";
        var runPath = tempDir.resolve(runRef);
        Files.createDirectories(runPath.getParent());
        Files.writeString(runPath, """
                {
                  "executionTimeline": [{
                    "kind": "permission_decision",
                    "details": {
                      "authorityDisposition": "ESCALATION_REQUIRED",
                      "disposition": "DENIED",
                      "toolCallId": "call-websearch-1",
                      "toolKind": "fetch",
                      "toolName": "WebSearch",
                      "title": "Mailpit latest stable Docker tag",
                      "resource": "Mailpit latest stable Docker tag"
                    }
                  }]
                }
                """, StandardCharsets.UTF_8);
        var node = failedAcpxNode(Map.of(
                "run_metadata", runRef,
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "permission_blocked",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "WebSearch requires approval."
                )
        ));

        var assessment = RecoveryAssessment.from(node, tempDir, "ws-recovery");

        assertEquals("agent_permission_approval_required", assessment.classification());
        assertEquals("HUMAN_APPROVE_PUBLIC_WEBSEARCH_OR_ABORT", assessment.retryReadiness());
        assertFalse(assessment.retrySafe());
        assertEquals("WebSearch", assessment.permissionRequest().get("toolName"));
        assertEquals("Mailpit latest stable Docker tag", assessment.permissionRequest().get("query"));
        assertEquals(List.of("approve_public_websearch", "abort"), assessment.allowedActions());
        assertTrue(assessment.suggestedCommand().contains("recovery approve-websearch"));
        assertTrue(assessment.suggestedCommand().contains("--public-repository"));
    }

    @Test
    void typedReconnectMatchesLegacySessionRecovery() {
        var typed = RecoveryAssessment.from(failedAcpxNode(Map.of(
                "agent", "codex",
                "session", "codex-reviewer",
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "agent_reconnect_required",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "The ACP session needs reconnect."
                )
        )), tempDir, "ws-recovery");
        var legacy = RecoveryAssessment.from(failedAcpxNode(Map.of(
                "agent", "codex",
                "session", "codex-reviewer",
                "acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT",
                "acpx_failure.recoverable", true
        )), tempDir, "ws-recovery");

        assertEquals("agent_session_recovery", typed.classification());
        assertEquals("REBUILD_ACPX_SESSION_THEN_RETRY", typed.retryReadiness());
        assertEquals(legacy.classification(), typed.classification());
        assertEquals(legacy.retryReadiness(), typed.retryReadiness());
        assertTrue(typed.suggestedCommand().contains("acpx codex sessions close codex-reviewer"));
        assertTrue(typed.suggestedCommand().contains("acpx codex sessions new --name codex-reviewer"));
        assertTrue(typed.retrySafe());
        assertTrue(legacy.retrySafe());
    }

    @Test
    void unknownTypedAdapterReasonFailsClosed() {
        var assessment = RecoveryAssessment.from(failedAcpxNode(Map.of(
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "future_provider_signal",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "An unknown typed adapter failure occurred."
                )
        )), tempDir, "ws-recovery");

        assertEquals("manual_recovery_required", assessment.classification());
        assertEquals("INSPECT_BEFORE_RETRY", assessment.retryReadiness());
        assertTrue(assessment.suggestedCommand().contains("only after inspecting"));
        assertFalse(assessment.retrySafe(),
                "unknown typed classification must fail closed without auto-retry readiness");
    }

    @Test
    void interfaceMismatchPreservesItsReasonAndRequiresManualCompatibilityRepair() {
        var assessment = RecoveryAssessment.from(failedAcpxNode(Map.of(
                "agent_execution_outcome", Map.of("status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "adapter_incompatible", "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY", "operatorMessage", "required ACP protocol version 1 is unavailable")
        )), tempDir, "ws-recovery");
        assertEquals("agent_interface_incompatible", assessment.classification());
        assertEquals("FIX_AGENT_INTERFACE_THEN_RETRY", assessment.retryReadiness());
        assertFalse(assessment.retrySafe());
        assertTrue(assessment.suggestedCommand().contains("never resend an unknown call"));
        assertFalse(assessment.suggestedCommand().contains("sessions new"));
    }

    @Test
    void sessionBootstrapFailureIsGenericFailClosedSessionRecovery() {
        var assessment = RecoveryAssessment.from(failedAcpxNode(Map.of(
                "agent", "codex",
                "session", "codex-reviewer",
                "agent_execution_outcome", Map.of(
                        "status", "RECOVERABLE_ADAPTER_FAILURE",
                        "primaryReason", "session_bootstrap_failed",
                        "recoveryCategory", "RECOVERABLE_ADAPTER_FAILURE",
                        "retryPolicy", "MANUAL_ONLY",
                        "operatorMessage", "The acpx session could not be bootstrapped before the prompt."
                )
        )), tempDir, "ws-recovery");

        // SESSION_BOOTSTRAP_FAILED is generic (timeout/spawn/config/nonzero), so it must
        // stay fail-closed: classify as session recovery, but inspect before retry and do
        // not assume `sessions new` recovers an unknown cause.
        assertEquals("agent_session_recovery", assessment.classification());
        assertEquals("INSPECT_BEFORE_RETRY", assessment.retryReadiness());
        assertFalse(assessment.retrySafe(),
                "generic bootstrap failure must not be blindly retry-safe");
        assertFalse(assessment.suggestedCommand().contains("sessions new"),
                "must not assume sessions new recovers an unknown bootstrap cause");
        assertFalse(assessment.suggestedCommand().contains("sessions close"));
        assertEquals(false, assessment.toMap().get("retrySafe"));
    }

    @Test
    void incompleteProcessAndMissingEnvelopeRequireDifferentManualChecks() {
        var cases = Map.of("process_output_incomplete", "RETRY_AFTER_EXTERNAL_FIX",
                "agent_result_missing_or_invalid", "RETRY_AFTER_OUTPUT_CONTRACT_FIX");
        for (var entry : cases.entrySet()) {
            var assessment = RecoveryAssessment.from(failedAcpxNode(Map.of(
                    "agent_execution_outcome", Map.of(
                            "status", "OUTPUT_INCOMPLETE",
                            "primaryReason", entry.getKey(),
                            "recoveryCategory", "OUTPUT_INCOMPLETE",
                            "retryPolicy", "MANUAL_ONLY",
                            "operatorMessage", "The recorded boundary did not complete."
                    )
            )), tempDir, "ws-recovery");
            assertEquals("agent_output_incomplete", assessment.classification());
            assertEquals(entry.getValue(), assessment.retryReadiness());
            assertFalse(assessment.retrySafe(), "neither failure permits blind retry");
        }
    }

    @Test
    void unknownPermissionReceiptRequiresExternalReconciliationInsteadOfJsonRepairOrAutomaticRetry() {
        var outcome=new dev.dual.core.execution.AgentExecutionOutcomeResolver().resolve(new dev.dual.core.execution.AgentExecutionEvidence(
                dev.dual.core.execution.ProcessResult.nonZeroIncomplete(5), dev.dual.core.execution.AgentResultContractResult.missing(), List.of(),
                List.of(dev.dual.core.execution.AdapterSignal.of(dev.dual.core.execution.AdapterSignalType.NATIVE_PERMISSION_INTERRUPTED))));
        var facts=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(outcome,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        var assessment=RecoveryAssessment.from(failedAcpxNode(Map.of("agent","codex","session","original", "agent_execution_outcome",facts)),tempDir,"ws-recovery");
        assertEquals("native_permission_requires_external_reconciliation",assessment.classification());
        assertEquals("VERIFY_EXTERNAL_STATE_BEFORE_NEW_ATTEMPT",assessment.retryReadiness());
        assertFalse(assessment.retrySafe());
        assertFalse(assessment.suggestedCommand().contains("sessions new"));
        assertTrue(assessment.suggestedCommand().contains("do not resend"));
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
