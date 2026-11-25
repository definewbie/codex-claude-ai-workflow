package dev.dual.ledger.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a deterministic recovery read model for CLI and API callers.
 *
 * Runtime handlers write the raw node result and optional contract diagnostic.
 * This class does not decide workflow transitions; it only explains why the
 * current node stopped and what an operator should inspect next.
 *
 * <p>{@code retrySafe} is the authoritative operator recovery fact: whether the
 * operator may continue recovery by running {@code suggestedCommand}. It is
 * declared at construction for every recovery result; downstream renderers
 * (WorkstreamAttention, CLI, API, reports) must copy it and never re-derive it
 * from {@code classification} or {@code retryReadiness}.
 */
public record RecoveryAssessment(
        String classification,
        String retryReadiness,
        String reason,
        String suggestedCommand,
        String diagnosticJsonRef,
        String diagnosticMarkdownRef,
        Map<String, Object> contractDiagnostic,
        boolean retrySafe,
        Map<String, Object> permissionRequest,
        java.util.List<String> allowedActions
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public RecoveryAssessment(
            String classification,
            String retryReadiness,
            String reason,
            String suggestedCommand,
            String diagnosticJsonRef,
            String diagnosticMarkdownRef,
            Map<String, Object> contractDiagnostic,
            boolean retrySafe
    ) {
        this(classification, retryReadiness, reason, suggestedCommand, diagnosticJsonRef,
                diagnosticMarkdownRef, contractDiagnostic, retrySafe, Map.of(), java.util.List.of());
    }

    public RecoveryAssessment {
        permissionRequest = permissionRequest == null ? Map.of() : Map.copyOf(permissionRequest);
        allowedActions = allowedActions == null ? java.util.List.of() : java.util.List.copyOf(allowedActions);
    }

    public static RecoveryAssessment from(
            NodeInstanceDocument node,
            Path workspaceRoot,
            String workstreamId
    ) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var result = node.result() == null ? Map.<String, Object>of() : node.result();
        var retry = "dualctl retry --workdir " + root + " " + workstreamId;
        var abort = "dualctl recovery abort --workdir " + root + " " + workstreamId;
        if ("UNKNOWN".equals(result.get("external_effect"))) {
            return new RecoveryAssessment("external_result_unknown", "STOP_AND_ADJUDICATE",
                    "An external intent exists without a verified completion. Stopping does not prove the operation had no effect.",
                    "Inspect dualctl recovery show --workdir " + root + " " + workstreamId
                            + "; obtain a recorded native stop, then submit an explicit retry/abort --decision. Ordinary retry is refused.",
                    "", "", Map.of(), false, Map.of(), java.util.List.of("inspect", "stop_recorded_session", "adjudicate"));
        }
        var diagnosticJsonRef = text(result.get("contract_failure_diagnostic_ref"));
        var diagnosticMarkdownRef = text(result.get("contract_failure_diagnostic_markdown_ref"));
        var diagnostic = readDiagnostic(root, diagnosticJsonRef);
        var agent = blankDefault(text(result.get("agent")), "claude");
        var session = blankDefault(text(result.get("session")), agent + "-main");
        if (node.status() == dev.dual.core.workflow.NodeStatus.FAILED
                && ("VERIFICATION_READINESS_REQUIRED".equals(result.get("readiness_reason"))
                || result.get("input_preparation_failure") instanceof Map<?, ?> preparation
                && "VERIFICATION_READINESS_REQUIRED".equals(preparation.get("code"))
                && Boolean.FALSE.equals(preparation.get("providerInvoked")))) {
            return new RecoveryAssessment("verification_readiness_required", "RECORD_READINESS_BEFORE_MANUAL_RETRY",
                    "Verification prerequisites are missing or no longer applicable; this does not report a model output failure.",
                    "Use dualctl review record-readiness --workdir " + root + " --subject " + node.nodeInstanceId()
                            + " --request-id <new-id> --file <declaration.json> " + workstreamId
                            + ". Recording does not approve or execute anything. Retry only with the same reviewed inputs; "
                            + "to revise use dualctl review revise --workdir " + root + " --subject " + node.nodeInstanceId()
                            + " --note <repair-requirement> " + workstreamId + "; the author and independent reviewer must run again.",
                    "", "", Map.of(), false);
        }
        if (result.get("input_preparation_failure") instanceof Map<?, ?> failure
                && node.status() == dev.dual.core.workflow.NodeStatus.FAILED
                && Boolean.FALSE.equals(failure.get("providerInvoked"))) {
            return new RecoveryAssessment("input_preparation_failure", "FIX_INPUTS_BEFORE_MANUAL_RETRY",
                    "Input preparation rejected before provider dispatch: " + text(failure.get("reason"))
                            + " / " + text(failure.get("code")) + ": " + text(failure.get("details")),
                    "Inspect the current input identity and approval evidence before `" + retry
                            + "`; or stop with `" + abort + "`.",
                    "", "", Map.of(), false);
        }
        var outcomeAssessment = fromAgentExecutionOutcome(
                result.get("agent_execution_outcome"),
                retry,
                agent,
                session,
                diagnosticJsonRef,
                diagnosticMarkdownRef,
                diagnostic
        );
        if (outcomeAssessment != null) {
            var permissionRequest = readPermissionEscalation(root, workstreamId, result);
            if ("agent_permission_blocked".equals(outcomeAssessment.classification())
                    && !permissionRequest.isEmpty()) {
                return new RecoveryAssessment(
                        "agent_permission_approval_required",
                        "HUMAN_APPROVE_PUBLIC_WEBSEARCH_OR_ABORT",
                        "The agent requested exact WebSearch for a public fact. The current turn was denied; a human may approve WebSearch for one retry only after confirming this repository is public.",
                        "dualctl recovery approve-websearch --public-repository --note \"<reason>\" --workdir "
                                + root + " " + workstreamId,
                        diagnosticJsonRef,
                        diagnosticMarkdownRef,
                        diagnostic,
                        false,
                        permissionRequest,
                        java.util.List.of("approve_public_websearch", "abort")
                );
            }
            return outcomeAssessment;
        }
        var diagnosticCategory = text(diagnostic.get("category"));
        var diagnosticHint = text(diagnostic.get("recovery_hint"));
        var failureReason = text(firstNonBlank(
                result.get("acpx_failure.reason"),
                result.get("reason"),
                result.get("error"),
                result.get("message")
        ));
        var recoverable = Boolean.TRUE.equals(result.get("acpx_failure.recoverable"))
                || Boolean.TRUE.equals(result.get("recoverable"));

        if ("revision_limit_reached".equals(failureReason) || "BLOCKED".equals(node.status().name())) {
            return new RecoveryAssessment(
                    "semantic_revision_limit",
                    "NEEDS_HUMAN_DECISION",
                    "The workflow exhausted its revision budget. Blind retry will likely repeat the same loop.",
                    "Inspect evidence, adjust the task/contract, then run `" + retry + "`; or stop with `" + abort + "`.",
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if (!diagnosticCategory.isBlank() || FailureReasonSemantics.isArtifactContractFailure(failureReason)) {
            return new RecoveryAssessment(
                    "artifact_contract_failure",
                    "RETRY_AFTER_OUTPUT_CONTRACT_FIX",
                    diagnosticHint.isBlank()
                            ? "The agent output contract was invalid or missing. Inspect the diagnostic before retrying."
                            : diagnosticHint,
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if ("RECOVERABLE_AGENT_MODEL_MISMATCH".equals(failureReason)) {
            return new RecoveryAssessment(
                    "agent_model_mismatch",
                    "REBUILD_ACPX_SESSION_THEN_RETRY",
                    "The acpx session model state is stale or incompatible with the current Claude Code/provider configuration. Rebuild the acpx session before retrying.",
                    "acpx " + agent + " sessions close " + session
                            + " && acpx " + agent + " sessions new --name " + session
                            + " && " + retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    true
            );
        }
        if ("RECOVERABLE_AGENT_AUTH_FAILED".equals(failureReason)) {
            return new RecoveryAssessment(
                    "agent_auth_failed",
                    "FIX_AGENT_AUTH_THEN_RETRY",
                    "The agent adapter failed authentication. Fix the underlying Claude/Codex runtime credentials or adapter configuration, then retry the same active node.",
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if ("RECOVERABLE_PROVIDER_CONNECTION_FAILED".equals(failureReason)) {
            return new RecoveryAssessment(
                    "provider_connection_failed",
                    "WAIT_OR_FIX_PROVIDER_CONNECTION_THEN_RETRY",
                    "The agent provider connection failed before a trustworthy agent output boundary. Verify provider/network/socket availability, then retry the same active node.",
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if ("RECOVERABLE_AGENT_RECONNECT".equals(failureReason)
                || "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED".equals(failureReason)
                || "RECOVERABLE_AGENT_PERMISSION_BLOCKED".equals(failureReason)
                || "RECOVERABLE_SESSION_MISSING".equals(failureReason)
                || "RECOVERABLE_CONTEXT_WINDOW".equals(failureReason)) {
            var classification = "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED".equals(failureReason)
                    ? "agent_output_interrupted"
                    : "RECOVERABLE_AGENT_PERMISSION_BLOCKED".equals(failureReason)
                    ? "agent_permission_blocked"
                    : "agent_session_recovery";
            // A blocked permission is not safe to retry blindly; session/output
            // recovery rebuilds the session first, so the suggested command is safe.
            var branchRetrySafe = !"agent_permission_blocked".equals(classification);
            return new RecoveryAssessment(
                    classification,
                    "REBUILD_ACPX_SESSION_THEN_RETRY",
                    sessionRecoveryReason(failureReason),
                    "acpx " + agent + " sessions close " + session
                            + " && acpx " + agent + " sessions new --name " + session
                            + " && " + retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    branchRetrySafe
            );
        }
        if ("RECOVERABLE_RATE_LIMIT".equals(failureReason)
                || "RECOVERABLE_AGENT_OVERLOADED".equals(failureReason)) {
            return new RecoveryAssessment(
                    "agent_capacity_recovery",
                    "WAIT_THEN_RETRY",
                    capacityRecoveryReason(failureReason),
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    true
            );
        }
        if (recoverable) {
            return new RecoveryAssessment(
                    "recoverable_agent_runtime",
                    "RETRY_AFTER_EXTERNAL_FIX",
                    "The failed node is marked recoverable, but its typed reason is not mapped to a specific operator action yet. Inspect adapter evidence before retrying.",
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    true
            );
        }
        if (FailureReasonSemantics.isArtifactContractFailure(failureReason)) {
            return new RecoveryAssessment(
                    "artifact_contract_failure",
                    "RETRY_AFTER_OUTPUT_CONTRACT_FIX",
                    "The agent output contract was invalid or missing. Inspect the output before retrying.",
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        return new RecoveryAssessment(
                "manual_recovery_required",
                "INSPECT_BEFORE_RETRY",
                "The failure is not marked recoverable. Read the evidence before deciding whether retry is safe.",
                retry + "  # only after fixing the cause",
                diagnosticJsonRef,
                diagnosticMarkdownRef,
                diagnostic,
                false
        );
    }

    private static RecoveryAssessment fromAgentExecutionOutcome(
            Object value,
            String retry,
            String agent,
            String session,
            String diagnosticJsonRef,
            String diagnosticMarkdownRef,
            Map<String, Object> diagnostic
    ) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        var outcome = asStringMap(raw);
        var recoveryCategory = text(outcome.get("recoveryCategory"));
        var primaryReason = text(outcome.get("primaryReason"));
        var operatorMessage = text(outcome.get("operatorMessage"));
        if ("ARTIFACT_CONTRACT_FAILURE".equals(recoveryCategory)) {
            var diagnosticHint = text(diagnostic.get("recovery_hint"));
            return new RecoveryAssessment(
                    "artifact_contract_failure",
                    "RETRY_AFTER_OUTPUT_CONTRACT_FIX",
                    diagnosticHint.isBlank()
                            ? blankDefault(operatorMessage, "The agent output contract was invalid or missing. Inspect the diagnostic before retrying.")
                            : diagnosticHint,
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if ("OUTPUT_INCOMPLETE".equals(recoveryCategory)) {
            return new RecoveryAssessment(
                    "agent_output_incomplete",
                    "process_output_incomplete".equals(primaryReason)
                            ? "RETRY_AFTER_EXTERNAL_FIX" : "RETRY_AFTER_OUTPUT_CONTRACT_FIX",
                    blankDefault(operatorMessage, "The agent output stopped before a valid terminal control and artifact boundary."),
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if ("TIMEOUT_OR_STALLED".equals(recoveryCategory)) {
            return new RecoveryAssessment(
                    "agent_timeout_or_stalled",
                    "RETRY_AFTER_EXTERNAL_FIX",
                    blankDefault(operatorMessage, "The agent process timed out or stalled before completing the execution boundary."),
                    retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if ("INTERRUPTED".equals(recoveryCategory)) {
            return new RecoveryAssessment(
                    "agent_interrupted",
                    "INSPECT_BEFORE_RETRY",
                    blankDefault(operatorMessage, "The agent execution was interrupted before completion."),
                    retry + "  # only after confirming the interruption cause",
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        if ("RECOVERABLE_ADAPTER_FAILURE".equals(recoveryCategory)) {
            return new RecoveryAssessment(
                    adapterClassification(primaryReason),
                    adapterRetryReadiness(primaryReason),
                    blankDefault(operatorMessage, "The adapter reported a recoverable execution failure."),
                    adapterSuggestedCommand(primaryReason, agent, session, retry),
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    adapterRetrySafe(primaryReason)
            );
        }
        if ("NON_RECOVERABLE_ADAPTER_FAILURE".equals(recoveryCategory)) {
            return new RecoveryAssessment(
                    "non_recoverable_adapter_failure",
                    "NEEDS_HUMAN_DECISION",
                    blankDefault(operatorMessage, "The adapter reported a non-recoverable execution failure."),
                    retry + "  # only after changing adapter configuration or task boundary",
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic,
                    false
            );
        }
        return null;
    }

    private static Map<String, Object> asStringMap(Map<?, ?> raw) {
        var map = new LinkedHashMap<String, Object>();
        for (var entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                map.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return map;
    }

    private static String adapterClassification(String primaryReason) {
        return switch (primaryReason) {
            case "native_permission_interrupted" -> "native_permission_requires_external_reconciliation";
            case "adapter_incompatible" -> "agent_interface_incompatible";
            case "auth_failed" -> "agent_auth_failed";
            case "permission_blocked" -> "agent_permission_blocked";
            case "provider_connection_failed" -> "provider_connection_failed";
            case "context_window_exceeded" -> "agent_context_window";
            case "rate_limited" -> "agent_capacity_recovery";
            case "agent_reconnect_required", "session_bootstrap_failed" -> "agent_session_recovery";
            default -> "manual_recovery_required";
        };
    }

    private static String adapterRetryReadiness(String primaryReason) {
        return switch (primaryReason) {
            case "native_permission_interrupted" -> "VERIFY_EXTERNAL_STATE_BEFORE_NEW_ATTEMPT";
            case "adapter_incompatible" -> "FIX_AGENT_INTERFACE_THEN_RETRY";
            case "auth_failed" -> "FIX_AGENT_AUTH_THEN_RETRY";
            case "rate_limited" -> "WAIT_THEN_RETRY";
            case "provider_connection_failed" -> "WAIT_OR_FIX_PROVIDER_CONNECTION_THEN_RETRY";
            case "agent_reconnect_required" -> "REBUILD_ACPX_SESSION_THEN_RETRY";
            // session_bootstrap_failed is generic: it may be a command timeout, spawn
            // failure, config error or any non-zero exit, not necessarily a missing
            // session. Do not assume `sessions new` recovers it; inspect before retry.
            case "session_bootstrap_failed" -> "INSPECT_BEFORE_RETRY";
            case "permission_blocked",
                 "context_window_exceeded" -> "RETRY_AFTER_EXTERNAL_FIX";
            default -> "INSPECT_BEFORE_RETRY";
        };
    }

    private static boolean adapterRetrySafe(String primaryReason) {
        // Only adapter failures whose suggested action rebuilds the session or
        // waits for capacity are safe to continue without a human decision.
        return switch (primaryReason) {
            case "rate_limited", "agent_reconnect_required" -> true;
            // A generic bootstrap failure is not blindly retry-safe: its cause is
            // unknown, so retrySafe must match the inspect-then-retry command.
            case "session_bootstrap_failed" -> false;
            default -> false;
        };
    }

    private static String adapterSuggestedCommand(
            String primaryReason,
            String agent,
            String session,
            String retry
    ) {
        return switch (primaryReason) {
            case "native_permission_interrupted" -> "Inspect the original execution and stop evidence; do not resend an approval or automatically retry the prompt.";
            case "adapter_incompatible" -> retry + "  # only after fixing the recorded protocol/session mismatch; never resend an unknown call";
            case "agent_reconnect_required" ->
                    "acpx " + agent + " sessions close " + session
                            + " && acpx " + agent + " sessions new --name " + session
                            + " && " + retry;
            // Bootstrap failure is generic (timeout/spawn/config/nonzero); do not assume
            // `sessions new` recovers it. retrySafe is false and the command asks for
            // inspection before a manual retry.
            case "session_bootstrap_failed" ->
                    retry + "  # only after inspecting the session bootstrap failure";
            case "permission_blocked" ->
                    retry + "  # only after changing the permission policy and rebuilding the acpx session";
            case "auth_failed",
                 "provider_connection_failed",
                 "context_window_exceeded",
                 "rate_limited" -> retry;
            default -> retry + "  # only after inspecting the typed adapter failure";
        };
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("classification", classification);
        map.put("retryReadiness", retryReadiness);
        map.put("retrySafe", retrySafe);
        map.put("reason", reason);
        map.put("suggestedCommand", suggestedCommand);
        map.put("diagnosticJsonRef", diagnosticJsonRef);
        map.put("diagnosticMarkdownRef", diagnosticMarkdownRef);
        map.put("contractDiagnostic", contractDiagnostic);
        map.put("permissionRequest", permissionRequest);
        map.put("allowedActions", allowedActions);
        return map;
    }

    private static Map<String, Object> readPermissionEscalation(
            Path root,
            String workstreamId,
            Map<String, Object> result
    ) {
        var runRef = text(result.get("run_metadata"));
        if (runRef.isBlank()) {
            return Map.of();
        }
        try {
            var path = root.resolve(runRef).normalize();
            var workstreamRoot = root.resolve(".agent/workstreams").resolve(workstreamId).normalize();
            if (!path.startsWith(workstreamRoot) || !Files.isRegularFile(path)) {
                return Map.of();
            }
            var run = OBJECT_MAPPER.readTree(path.toFile());
            var timeline = run.path("executionTimeline");
            if (!timeline.isArray()) {
                return Map.of();
            }
            for (var index = timeline.size() - 1; index >= 0; index--) {
                var event = timeline.get(index);
                var details = event.path("details");
                if ("permission_decision".equals(event.path("kind").asText())
                        && "ESCALATION_REQUIRED".equals(details.path("authorityDisposition").asText())
                        && "fetch".equals(details.path("toolKind").asText())
                        && "WebSearch".equals(details.path("toolName").asText())) {
                    var request = new LinkedHashMap<String, Object>();
                    request.put("toolName", "WebSearch");
                    request.put("toolKind", "fetch");
                    request.put("toolCallId", details.path("toolCallId").asText());
                    request.put("query", details.path("resource").asText());
                    request.put("runRef", runRef);
                    request.put("disposition", details.path("disposition").asText());
                    return Map.copyOf(request);
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
    }

    private static Map<String, Object> readDiagnostic(Path root, String ref) {
        if (ref.isBlank()) {
            return Map.of();
        }
        try {
            var path = root.resolve(ref).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                return Map.of();
            }
            return OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Object firstNonBlank(Object... values) {
        for (var value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String sessionRecoveryReason(String failureReason) {
        return switch (failureReason) {
            case "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED" ->
                    "The agent output stopped before the terminal contract after the acpx timeout. Rebuild the acpx session to avoid continuing a partial context, then retry the same active node.";
            case "RECOVERABLE_AGENT_PERMISSION_BLOCKED" ->
                    "The agent attempted a tool action that was blocked by non-interactive permissions. Inspect the agent output and either adjust the task/session permission policy or retry after confirming the node is allowed to use those tools.";
            case "RECOVERABLE_SESSION_MISSING" ->
                    "The acpx session is missing. Create the session again, then retry the same active node.";
            case "RECOVERABLE_CONTEXT_WINDOW" ->
                    "The agent context window appears exhausted. Rebuild the acpx session to get a fresh context, then retry the same active node.";
            default ->
                    "The acpx agent session needs reconnect. Rebuild the acpx session, then retry the same active node.";
        };
    }

    private static String capacityRecoveryReason(String failureReason) {
        if ("RECOVERABLE_AGENT_OVERLOADED".equals(failureReason)) {
            return "The agent provider is overloaded. Wait for provider capacity to recover, then retry the same active node.";
        }
        return "The agent quota or rate limit was hit. Wait for quota recovery, then retry the same active node.";
    }
}
