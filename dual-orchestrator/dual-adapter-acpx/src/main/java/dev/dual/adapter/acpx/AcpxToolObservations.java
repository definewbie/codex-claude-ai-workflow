package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.dual.core.execution.AgentExecutionEvidence.ToolObservation;
import java.util.*;

/** Correlates only typed updates inside the current prompt. No prose or transcript heuristics. */
final class AcpxToolObservations {
    private String session = "";
    private boolean active;
    private String prompt = "";
    private final Map<String, Call> calls = new LinkedHashMap<>();

    void start(String session) { start(session, ""); }
    void start(String session, String prompt) { this.prompt = prompt == null ? "" : prompt; this.session = session == null ? "" : session; active = !this.session.isBlank(); calls.clear(); }
    void finish() { active = false; }
    List<ToolObservation> values() { return calls.values().stream().map(Call::freeze).toList(); }

    void accept(AcpxStreamWireLine.Notification notification) {
        if (!active || !session.equals(notification.sessionId()) || notification.toolUpdate() == null
                || notification.toolCallId().isBlank()) return;
        var update = notification.toolUpdate();
        var id = notification.toolCallId();
        var call = calls.get(id);
        if ("tool_call".equals(notification.updateKind())) {
            if (call != null) { call.invalid = true; return; }
            call = new Call(session, id, prompt); calls.put(id, call);
        } else if (call == null) {
            // A result without the call's creation/input is not evidence of this prompt's execution.
            call = new Call(session, id, prompt); call.invalid = true; calls.put(id, call);
        }
        if (call.terminal && !call.raw.contains(update)) call.invalid = true;
        if (call.raw.contains(update)) return;
        call.raw.add(update.deepCopy());
        if (!notification.toolName().isBlank()) call.name = notification.toolName();
        if (update.hasNonNull("kind")) call.kind = update.path("kind").asText();
        if (update.hasNonNull("rawInput")) {
            if (call.input != null && call.terminal && !call.input.equals(update.get("rawInput"))) call.invalid = true;
            call.input = update.get("rawInput").deepCopy();
        }
        if (update.hasNonNull("rawOutput")) { call.output = update.get("rawOutput").deepCopy(); call.hasRawOutput = true; }
        else if (update.has("content") || update.path("_meta").has("terminal_output")) {
            var result = JsonNodeFactory.instance.objectNode();
            if (update.has("content")) result.set("content", update.get("content").deepCopy());
            if (update.path("_meta").has("terminal_output"))
                result.set("terminal_output", update.path("_meta").get("terminal_output").deepCopy());
            call.outputChunks.add(result);
            if (!call.hasRawOutput) call.output = call.outputChunks.deepCopy();
        }
        if (update.hasNonNull("status")) call.status = update.path("status").asText();
        var reported = update.at("/_meta/terminal_exit/exit_code");
        if (reported.isIntegralNumber() && reported.canConvertToInt()) call.reported = reported.intValue();
        // Pinned Claude ACP forwards this exact result branch in rawOutput. Its _meta exit alone is synthetic/ambiguous.
        if ("Bash".equals(call.name) && call.output != null
                && "bash_code_execution_result".equals(call.output.path("type").asText())) {
            var actual = call.output.path("return_code");
            if (actual.isIntegralNumber() && actual.canConvertToInt()) {
                call.actual = actual.intValue(); call.actualSource = "claude.rawOutput.bash_code_execution_result.return_code";
            }
        }
        call.terminal = "completed".equals(call.status) || "failed".equals(call.status);
    }

    private static final class Call {
        final String session, id, prompt;
        String name = "", kind = "", status = "", actualSource = "";
        JsonNode input, output;
        Integer reported, actual;
        boolean invalid, terminal, hasRawOutput;
        final com.fasterxml.jackson.databind.node.ArrayNode outputChunks = JsonNodeFactory.instance.arrayNode();
        final List<JsonNode> raw = new ArrayList<>();
        Call(String session, String id, String prompt) { this.session = session; this.id = id; this.prompt = prompt; }
        ToolObservation freeze() { return new ToolObservation(session, id, name, kind, input, output,
                status, reported, actual, actualSource, invalid, raw, prompt); }
    }
}
