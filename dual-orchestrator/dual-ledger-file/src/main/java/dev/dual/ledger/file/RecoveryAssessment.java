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
 */
public record RecoveryAssessment(
        String classification,
        String retryReadiness,
        String reason,
        String suggestedCommand,
        String diagnosticJsonRef,
        String diagnosticMarkdownRef,
        Map<String, Object> contractDiagnostic
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public static RecoveryAssessment from(
            NodeInstanceDocument node,
            Path workspaceRoot,
            String workstreamId
    ) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var result = node.result() == null ? Map.<String, Object>of() : node.result();
        var retry = "dualctl retry --workdir " + root + " " + workstreamId;
        var abort = "dualctl recovery abort --workdir " + root + " " + workstreamId;
        var diagnosticJsonRef = text(result.get("contract_failure_diagnostic_ref"));
        var diagnosticMarkdownRef = text(result.get("contract_failure_diagnostic_markdown_ref"));
        var diagnostic = readDiagnostic(root, diagnosticJsonRef);
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
                    diagnostic
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
                    diagnostic
            );
        }
        if ("RECOVERABLE_AGENT_MODEL_MISMATCH".equals(failureReason)) {
            var agent = blankDefault(text(result.get("agent")), "claude");
            var session = blankDefault(text(result.get("session")), agent + "-main");
            return new RecoveryAssessment(
                    "agent_model_mismatch",
                    "REBUILD_ACPX_SESSION_THEN_RETRY",
                    "The acpx session model state is stale or incompatible with the current Claude Code/provider configuration. Rebuild the acpx session before retrying.",
                    "acpx " + agent + " sessions close " + session
                            + " && acpx " + agent + " sessions new --name " + session
                            + " && " + retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic
            );
        }
        if ("RECOVERABLE_AGENT_RECONNECT".equals(failureReason)
                || "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED".equals(failureReason)
                || "RECOVERABLE_AGENT_PERMISSION_BLOCKED".equals(failureReason)
                || "RECOVERABLE_SESSION_MISSING".equals(failureReason)
                || "RECOVERABLE_CONTEXT_WINDOW".equals(failureReason)) {
            var agent = blankDefault(text(result.get("agent")), "claude");
            var session = blankDefault(text(result.get("session")), agent + "-main");
            var classification = "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED".equals(failureReason)
                    ? "agent_output_interrupted"
                    : "RECOVERABLE_AGENT_PERMISSION_BLOCKED".equals(failureReason)
                    ? "agent_permission_blocked"
                    : "agent_session_recovery";
            return new RecoveryAssessment(
                    classification,
                    "REBUILD_ACPX_SESSION_THEN_RETRY",
                    sessionRecoveryReason(failureReason),
                    "acpx " + agent + " sessions close " + session
                            + " && acpx " + agent + " sessions new --name " + session
                            + " && " + retry,
                    diagnosticJsonRef,
                    diagnosticMarkdownRef,
                    diagnostic
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
                    diagnostic
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
                    diagnostic
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
                    diagnostic
            );
        }
        return new RecoveryAssessment(
                "manual_recovery_required",
                "INSPECT_BEFORE_RETRY",
                "The failure is not marked recoverable. Read the evidence before deciding whether retry is safe.",
                retry + "  # only after fixing the cause",
                diagnosticJsonRef,
                diagnosticMarkdownRef,
                diagnostic
        );
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("classification", classification);
        map.put("retryReadiness", retryReadiness);
        map.put("reason", reason);
        map.put("suggestedCommand", suggestedCommand);
        map.put("diagnosticJsonRef", diagnosticJsonRef);
        map.put("diagnosticMarkdownRef", diagnosticMarkdownRef);
        map.put("contractDiagnostic", contractDiagnostic);
        return map;
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
