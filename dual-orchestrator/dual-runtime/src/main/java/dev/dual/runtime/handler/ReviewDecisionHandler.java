package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReviewDecisionHandler implements DecisionHandler {
    private final DualControlBlockParser parser = new DualControlBlockParser();

    @Override
    public String type() {
        return "review.decision";
    }

    @Override
    public NodeResult evaluate(DecisionContext context) throws Exception {
        var parseResult = readLatestControlBlock(context);
        if (parseResult.isMissing()) {
            return failed("MISSING_DUAL_CONTROL", "No dual-control block found in decision input refs.", context);
        }
        if (!parseResult.valid()) {
            return failed("INVALID_DUAL_CONTROL", parseResult.message(), context, Set.of(), null, parseResult);
        }
        var control = parseResult.block();
        var allowed = allowedDecisions(context.definition().id());
        var decision = control.values().getOrDefault("decision", "").trim();
        if (decision.isBlank()) {
            return failed("INVALID_DUAL_CONTROL", "dual-control missing required key: decision", context, allowed, control);
        }
        if (!allowed.contains(decision)) {
            return failed("INVALID_DECISION", "Unsupported decision for " + context.definition().id() + ": " + decision, context, allowed, control);
        }
        if ("BLOCKED".equals(decision)) {
            return new NodeResult(decision, NodeStatus.BLOCKED, List.of(), Map.of(
                    "decision", decision,
                    "source", "dual-control",
                    "summary", control.values().getOrDefault("summary", "")
            ), "dual-control decision is BLOCKED");
        }
        return NodeResult.completed(decision, List.of(), Map.of(
                "decision", decision,
                "source", "dual-control",
                "summary", control.values().getOrDefault("summary", "")
        ));
    }

    private DualControlParseResult readLatestControlBlock(DecisionContext context) throws Exception {
        var refs = context.instance().inputRefs();
        for (int i = refs.size() - 1; i >= 0; i--) {
            var ref = refs.get(i);
            if (ref.startsWith("artifact://")) {
                continue;
            }
            var path = context.workspaceRoot().resolve(ref).normalize();
            if (!path.startsWith(context.workspaceRoot()) || !Files.exists(path)) {
                continue;
            }
            var content = Files.readString(path, StandardCharsets.UTF_8);
            var parsed = parser.parse(content);
            // Return whatever we found in this file — valid, invalid, or missing.
            // Do NOT fall back to older files: the latest output is the authoritative
            // source. Falling back could accept stale or injected blocks from earlier stages.
            return parsed;
        }
        return DualControlParseResult.missing();
    }

    private NodeResult failed(String reason, String message, DecisionContext context) {
        return failed(reason, message, context, Set.of(), null);
    }

    private NodeResult failed(String reason, String message, DecisionContext context, Set<String> allowed, DualControlBlock control) {
        return failed(reason, message, context, allowed, control, null);
    }

    private NodeResult failed(String reason, String message, DecisionContext context, Set<String> allowed, DualControlBlock control, DualControlParseResult parseResult) {
        return new NodeResult(reason, NodeStatus.FAILED, List.of(), Map.of(
                "reason", reason,
                "message", message,
                "input_refs", context.instance().inputRefs(),
                "allowed_decisions", List.copyOf(allowed),
                "control_values", control == null ? Map.of() : control.values(),
                "parse_error", parseResult == null ? Map.of() : Map.of(
                        "code", parseResult.errorCode(),
                        "message", parseResult.message(),
                        "details", parseResult.details()
                )
        ), message);
    }

    private Set<String> allowedDecisions(String nodeId) {
        var decisions = switch (nodeId) {
            case "plan.decision" -> List.of("READY_TO_IMPLEMENT", "NEEDS_REVISION", "BLOCKED");
            case "acceptance.decision" -> List.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT", "BLOCKED");
            default -> List.of("PASS", "NEEDS_REVISION", "BLOCKED");
        };
        return new LinkedHashSet<>(decisions);
    }
}
