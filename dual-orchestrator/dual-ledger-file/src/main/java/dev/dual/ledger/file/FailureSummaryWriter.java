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

    private String render(WorkstreamPaths paths, WorkflowCurrentDocument current, NodeInstanceDocument node) {
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
        content.append("\n## Evidence\n\n");
        if (outputRefs.isEmpty()) {
            content.append("- Output refs: N/A\n");
        } else {
            for (var ref : outputRefs) {
                content.append("- ").append(ref).append('\n');
            }
        }
        content.append("\n## Suggested Action\n\n");
        content.append("- Inspect the evidence files listed above.\n");
        content.append("- Fix the external issue, agent session, prompt contract, or workspace state.\n");
        content.append("- Resume with `dualctl retry --workdir ")
                .append(paths.workspaceRoot().toAbsolutePath().normalize())
                .append(' ')
                .append(paths.workstreamId().value())
                .append("`.\n");
        return content.toString();
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
}
