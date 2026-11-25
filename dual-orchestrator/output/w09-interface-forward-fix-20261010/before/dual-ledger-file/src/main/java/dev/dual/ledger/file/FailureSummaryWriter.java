package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

final class FailureSummaryWriter {
    private final ObjectMapper objectMapper;

    FailureSummaryWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        var node = objectMapper.readValue(paths.nodeJson(nodeInstanceId).toFile(), NodeInstanceDocument.class);
        var current = objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
        Files.createDirectories(paths.recoveryDir());
        Files.writeString(paths.currentFailureMarkdown(), render(paths, current, node), StandardCharsets.UTF_8);
    }

    String render(WorkstreamPaths paths, WorkflowCurrentDocument current, NodeInstanceDocument node) {
        var result = node.result() == null ? Map.<String, Object>of() : node.result();
        var outputRefs = node.outputRefs() == null ? List.<String>of() : node.outputRefs();
        var reason = text(firstNonBlank(
                result.get("acpx_failure.reason"),
                result.get("reason"),
                result.get("error"),
                result.get("message"),
                ""
        ));
        var recoverable = text(firstNonBlank(result.get("acpx_failure.recoverable"), result.get("recoverable"), ""));
        var exitCode = text(firstNonBlank(result.get("exit_code"), ""));
        var runId = text(firstNonBlank(result.get("run_id"), ""));
        var agent = text(firstNonBlank(result.get("agent"), ""));
        var session = text(firstNonBlank(result.get("session"), ""));
        var attention = WorkstreamAttentionEvaluator.evaluate(paths);

        var content = new StringBuilder();
        content.append("# Current Failure\n\n");
        content.append("## Summary\n\n");
        line(content, "Workflow Status", current.status().name());
        line(content, "Node", node.nodeDefinitionId());
        line(content, "Node Instance", node.nodeInstanceId());
        line(content, "Node Status", node.status().name());
        line(content, "Handler", node.handlerType());
        line(content, "Reason", reason);
        line(content, "Recoverable", recoverable);
        line(content, "Exit Code", exitCode);
        line(content, "Run ID", runId);
        line(content, "Agent", agent);
        line(content, "Session", session);
        line(content, "Attention Category", attention.category().name());
        line(content, "Retry Safe", String.valueOf(attention.retrySafe()));
        appendContractFailureDiagnostic(content, result);
        content.append("\n## Evidence\n\n");
        if (outputRefs.isEmpty()) {
            content.append("- Output refs: N/A\n");
        } else {
            for (var ref : outputRefs) {
                content.append("- ").append(ref).append('\n');
            }
        }
        content.append("\n## Suggested Action\n\n");
        content.append("- ").append(blankDefault(attention.detail(), "Inspect the evidence files listed above.")).append('\n');
        content.append("- ").append(blankDefault(attention.nextAction(), "Inspect the workstream ledger before choosing the next action.")).append('\n');
        if (!attention.command().isBlank()) {
            content.append("- Command: `").append(attention.command()).append("`.\n");
        }
        return content.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendContractFailureDiagnostic(StringBuilder content, Map<String, Object> result) {
        var diagnosticJson = text(result.get("contract_failure_diagnostic_ref"));
        var diagnosticMarkdown = text(result.get("contract_failure_diagnostic_markdown_ref"));
        if (diagnosticJson.isBlank() && diagnosticMarkdown.isBlank() && !result.containsKey("parse_error")) {
            return;
        }
        content.append("\n## Contract Failure Diagnostic\n\n");
        line(content, "Diagnostic JSON", diagnosticJson);
        line(content, "Diagnostic Markdown", diagnosticMarkdown);
        if (result.get("parse_error") instanceof Map<?, ?> parseError) {
            line(content, "Error Code", text(parseError.get("code")));
            line(content, "Error Message", text(parseError.get("message")));
            var details = parseError.get("details");
            if (details instanceof Map<?, ?> detailsMap && !detailsMap.isEmpty()) {
                content.append("- Details:\n");
                for (var entry : detailsMap.entrySet()) {
                    content.append("  - ").append(text(entry.getKey())).append(": ")
                            .append(text(entry.getValue())).append('\n');
                }
            }
        }
    }

    private static void line(StringBuilder content, String label, String value) {
        content.append("- ").append(label).append(": ");
        content.append(value == null || value.isBlank() ? "N/A" : value);
        content.append('\n');
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
        return value == null ? "" : value.toString();
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
