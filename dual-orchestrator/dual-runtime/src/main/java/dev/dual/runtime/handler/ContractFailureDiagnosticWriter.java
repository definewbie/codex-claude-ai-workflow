package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeDefinition;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes harness-owned diagnostics for agent output contract failures.
 *
 * Agent output remains in the run output directories. These diagnostics live under
 * artifacts because they are deterministic harness judgments about the output.
 */
final class ContractFailureDiagnosticWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ContractFailureDiagnosticWriter() {
    }

    static DiagnosticRefs write(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            NodeOutputContract contract,
            String sourceOutputRef,
            String reason,
            String message,
            Map<String, Object> details
    ) throws Exception {
        if (workstreamId == null || workstreamId.isBlank() || nodeInstanceId == null || nodeInstanceId.isBlank()) {
            return DiagnosticRefs.empty();
        }
        var root = workspaceRoot.toAbsolutePath().normalize();
        var dir = root.resolve(".agent")
                .resolve("workstreams")
                .resolve(workstreamId)
                .resolve("artifacts")
                .resolve("contract-failures");
        Files.createDirectories(dir);

        var baseName = safeFileName(nodeInstanceId);
        var jsonPath = dir.resolve(baseName + ".json");
        var markdownPath = dir.resolve(baseName + ".md");
        var category = category(reason);
        var recovery = recoveryHint(category, nodeDefinition.id());

        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", 1);
        document.put("artifact_type", "contract_failure_diagnostic");
        document.put("generated_at", Instant.now().toString());
        document.put("workstream_id", workstreamId);
        document.put("node_instance_id", nodeInstanceId);
        document.put("node_id", nodeDefinition.id());
        document.put("handler_type", nodeDefinition.handlerType());
        document.put("category", category);
        document.put("reason", reason);
        document.put("message", message);
        document.put("source_output_ref", sourceOutputRef == null ? "" : sourceOutputRef);
        document.put("required_keys", List.copyOf(contract.requiredKeys()));
        document.put("allowed_decisions", List.copyOf(contract.allowedDecisions()));
        document.put("details", details == null ? Map.of() : details);
        document.put("recovery_hint", recovery);
        document.put("recommended_owner", recommendedOwner(category));

        Files.writeString(
                jsonPath,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n",
                StandardCharsets.UTF_8
        );
        Files.writeString(markdownPath, markdown(document), StandardCharsets.UTF_8);

        return new DiagnosticRefs(root.relativize(jsonPath).toString(), root.relativize(markdownPath).toString());
    }

    private static String markdown(Map<String, Object> document) {
        var builder = new StringBuilder();
        builder.append("# Contract Failure Diagnostic\n\n");
        appendField(builder, "Category", document.get("category"));
        appendField(builder, "Reason", document.get("reason"));
        appendField(builder, "Node", document.get("node_id"));
        appendField(builder, "Node Instance", document.get("node_instance_id"));
        appendField(builder, "Source Output", document.get("source_output_ref"));
        appendField(builder, "Recommended Owner", document.get("recommended_owner"));
        builder.append("\n## Message\n\n");
        builder.append(text(document.get("message"))).append("\n\n");
        builder.append("## Recovery Hint\n\n");
        builder.append(text(document.get("recovery_hint"))).append("\n\n");
        builder.append("## Contract Shape\n\n");
        builder.append("- Required Keys: ").append(document.get("required_keys")).append("\n");
        builder.append("- Allowed Decisions: ").append(document.get("allowed_decisions")).append("\n");
        return builder.toString();
    }

    private static void appendField(StringBuilder builder, String label, Object value) {
        var text = text(value);
        if (!text.isBlank()) {
            builder.append("- ").append(label).append(": ").append(text).append("\n");
        }
    }

    private static String category(String reason) {
        return switch (reason == null ? "" : reason) {
            case "MISSING_DUAL_CONTROL" -> "missing_control";
            case "NON_TERMINAL_CONTROL_BLOCK", "MALFORMED_DUAL_CONTROL", "CONTRACT_VALIDATION_FAILED" -> "malformed_control";
            case "STRUCTURED_ARTIFACT_MISSING" -> "missing_structured_artifact";
            case "STRUCTURED_ARTIFACT_PARSE_FAILED" -> "structured_artifact_parse_failed";
            case "CONTROL_ARTIFACT_DECISION_MISMATCH" -> "decision_mismatch";
            case "STRUCTURED_ARTIFACT_CONTRACT_VIOLATION" -> "structured_artifact_contract_violation";
            case "STRUCTURED_ARTIFACT_SEMANTIC_VIOLATION" -> "semantic_conflict";
            default -> "contract_violation";
        };
    }

    private static String recommendedOwner(String category) {
        return switch (category) {
            case "missing_control", "malformed_control", "missing_structured_artifact",
                 "structured_artifact_parse_failed", "decision_mismatch", "semantic_conflict" -> "agent_or_prompt";
            default -> "harness_or_prompt";
        };
    }

    private static String recoveryHint(String category, String nodeId) {
        return switch (category) {
            case "missing_control" -> "Ask the agent step to emit the required terminal dual-control block for " + nodeId + ".";
            case "malformed_control" -> "Repair the terminal dual-control block so it is top-level, last, and contains all required keys.";
            case "missing_structured_artifact" -> "Ask the agent to emit the required typed <dual-artifact> block before the terminal dual-control block.";
            case "structured_artifact_parse_failed" -> "Fix the JSON inside the typed <dual-artifact>; do not recover by parsing Markdown sections.";
            case "decision_mismatch" -> "Align the typed artifact decision with the terminal dual-control decision.";
            case "semantic_conflict" -> "Resolve the contradiction between lifecycle status, decision, and evidence fields.";
            default -> "Inspect the diagnostic details, prompt contract, and source output before retrying the node.";
        };
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record DiagnosticRefs(String jsonRef, String markdownRef) {
        static DiagnosticRefs empty() {
            return new DiagnosticRefs("", "");
        }

        boolean present() {
            return !jsonRef.isBlank();
        }
    }
}
