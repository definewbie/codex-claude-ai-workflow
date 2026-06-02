package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReviewDecisionHandler implements DecisionHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

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
            var allowed = allowedDecisions(context.definition().id());
            return failed("INVALID_DUAL_CONTROL", parseResult.message(), context, allowed, null, parseResult);
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
            return new NodeResult(decision, NodeStatus.BLOCKED, context.instance().inputRefs(), Map.of(
                    "decision", decision,
                    "source", "dual-control",
                    "summary", control.values().getOrDefault("summary", "")
            ), "dual-control decision is BLOCKED");
        }
        return NodeResult.completed(decision, context.instance().inputRefs(), Map.of(
                "decision", decision,
                "source", "dual-control",
                "summary", control.values().getOrDefault("summary", "")
        ));
    }

    private DualControlParseResult readLatestControlBlock(DecisionContext context) throws Exception {
        var refs = context.instance().inputRefs();
        var nodeId = context.definition().id();
        var hasContract = ContractRegistry.hasContract(nodeId);
        for (int i = refs.size() - 1; i >= 0; i--) {
            var ref = refs.get(i);
            if (ref.startsWith("artifact://")) {
                continue;
            }
            var path = context.workspaceRoot().resolve(ref).normalize();
            if (!path.startsWith(context.workspaceRoot()) || !Files.exists(path)) {
                continue;
            }
            if (path.getFileName().toString().endsWith(".contract.json")) {
                var parsed = parseContractSidecar(path);
                if (parsed.valid()) {
                    return parsed;
                }
                continue;
            }
            var content = Files.readString(path, StandardCharsets.UTF_8);
            DualControlParseResult parsed;
            if (hasContract) {
                parsed = parser.parse(content, ContractRegistry.forNode(nodeId));
            } else {
                parsed = parser.parse(content);
            }
            return parsed;
        }
        return DualControlParseResult.missing();
    }

    private DualControlParseResult parseContractSidecar(java.nio.file.Path path) {
        try {
            var document = OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
            var values = new java.util.LinkedHashMap<String, String>();
            var control = document.get("control");
            if (control instanceof Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                    }
                }
            }
            if (!values.containsKey("decision") && document.get("decision") != null) {
                values.put("decision", String.valueOf(document.get("decision")));
            }
            if (!values.containsKey("summary") && document.get("summary") != null) {
                values.put("summary", String.valueOf(document.get("summary")));
            }
            if (values.containsKey("decision")) {
                return DualControlParseResult.valid(new DualControlBlock(values));
            }
        } catch (Exception ignored) {
            // Invalid sidecars are ignored so older Markdown refs can still be parsed.
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
        if (ContractRegistry.hasContract(nodeId)) {
            return ContractRegistry.forNode(nodeId).allowedDecisions();
        }
        return new LinkedHashSet<>(List.of("PASS", "NEEDS_REVISION", "BLOCKED"));
    }
}
