package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

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
        var read = AgentEvidenceIndex.latestDecisionFromRefs(
                context.workspaceRoot(),
                context.workstreamId(),
                context.instance().inputRefs(),
                OBJECT_MAPPER
        );
        if (read.missing()) {
            return failed("MISSING_CONTRACT_SIDECAR",
                    "No agent output contract sidecar found in decision input refs.", context, Set.of(), read);
        }
        if (read.entry() == null) {
            return failed("INVALID_CONTRACT_SIDECAR", read.message(), context,
                    allowedDecisions(context.definition().id()), read);
        }
        var document = read.entry().document();
        var allowed = allowedDecisions(context.definition().id());
        var decision = document.decision().trim();
        if (decision.isBlank()) {
            return failed("INVALID_CONTRACT_SIDECAR", "contract sidecar missing required decision",
                    context, allowed, read);
        }
        if (!allowed.contains(decision)) {
            return failed("INVALID_DECISION",
                    "Unsupported decision for " + context.definition().id() + ": " + decision,
                    context, allowed, read);
        }
        var facts = Map.<String, Object>of(
                "decision", decision,
                "source", "contract-sidecar",
                "summary", document.summary());
        return NodeResult.completed(decision, context.instance().inputRefs(), facts);
    }

    private NodeResult failed(String reason, String message, DecisionContext context,
                              Set<String> allowed, AgentEvidenceIndex.DecisionRead read) {
        return new NodeResult(reason, NodeStatus.FAILED, List.of(), Map.of(
                "reason", reason,
                "message", message,
                "input_refs", context.instance().inputRefs(),
                "allowed_decisions", List.copyOf(allowed),
                "contract_values", read.entry() == null ? Map.of() : read.entry().document().control(),
                "parse_error", read.errorCode().isBlank() ? Map.of() : Map.of(
                        "code", read.errorCode(), "message", read.message(), "path", read.path())
        ), message);
    }

    private Set<String> allowedDecisions(String nodeId) {
        if (ContractRegistry.hasContract(nodeId)) {
            return ContractRegistry.forNode(nodeId).allowedDecisions();
        }
        return new LinkedHashSet<>(List.of("PASS", "NEEDS_REVISION", "BLOCKED"));
    }

}
