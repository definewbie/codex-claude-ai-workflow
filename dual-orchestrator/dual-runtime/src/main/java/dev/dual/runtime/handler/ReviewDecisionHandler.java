package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReviewDecisionHandler implements DecisionHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return "review.decision";
    }

    @Override
    public NodeResult evaluate(DecisionContext context) throws Exception {
        var parseResult = readLatestDecisionContract(context);
        if (parseResult.isMissing()) {
            return failed("MISSING_CONTRACT_SIDECAR", "No agent output contract sidecar found in decision input refs.", context);
        }
        if (!parseResult.valid()) {
            var allowed = allowedDecisions(context.definition().id());
            return failed("INVALID_CONTRACT_SIDECAR", parseResult.message(), context, allowed, null, parseResult);
        }
        var control = parseResult.block();
        var allowed = allowedDecisions(context.definition().id());
        var decision = control.values().getOrDefault("decision", "").trim();
        if (decision.isBlank()) {
            return failed("INVALID_CONTRACT_SIDECAR", "contract sidecar missing required decision", context, allowed, control);
        }
        if (!allowed.contains(decision)) {
            return failed("INVALID_DECISION", "Unsupported decision for " + context.definition().id() + ": " + decision, context, allowed, control);
        }
        if ("BLOCKED".equals(decision)) {
            return new NodeResult(decision, NodeStatus.BLOCKED, context.instance().inputRefs(), Map.of(
                    "decision", decision,
                    "source", "contract-sidecar",
                    "summary", control.values().getOrDefault("summary", "")
            ), "contract sidecar decision is BLOCKED");
        }
        return NodeResult.completed(decision, context.instance().inputRefs(), Map.of(
                "decision", decision,
                "source", "contract-sidecar",
                "summary", control.values().getOrDefault("summary", "")
        ));
    }

    private DualControlParseResult readLatestDecisionContract(DecisionContext context) throws Exception {
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
            if (path.getFileName().toString().endsWith(".contract.json")) {
                return parseContractSidecar(path);
            }
        }
        return DualControlParseResult.missing();
    }

    private DualControlParseResult parseContractSidecar(java.nio.file.Path path) {
        var document = AgentOutputContractDocument.read(path, OBJECT_MAPPER);
        if (document.isEmpty()) {
            return DualControlParseResult.invalid(
                    "CONTRACT_SIDECAR_PARSE_FAILED",
                    "Could not read agent output contract sidecar: " + path.getFileName(),
                    Map.of("path", path.toString())
            );
        }
        var values = new java.util.LinkedHashMap<>(document.get().control());
        if (!values.containsKey("decision") && document.get().hasDecision()) {
            values.put("decision", document.get().decision());
        }
        if (!values.containsKey("summary") && !document.get().summary().isBlank()) {
            values.put("summary", document.get().summary());
        }
        if (values.containsKey("decision")) {
            return DualControlParseResult.valid(new DualControlBlock(values));
        }
        return DualControlParseResult.invalid(
                "CONTRACT_SIDECAR_MISSING_DECISION",
                "Agent output contract sidecar is missing decision.",
                Map.of("path", path.toString())
        );
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
