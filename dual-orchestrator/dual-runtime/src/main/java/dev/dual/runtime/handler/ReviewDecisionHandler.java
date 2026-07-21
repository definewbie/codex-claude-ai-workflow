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
        var read = readLatestDecisionContract(context);
        if (read.missing()) {
            return failed("MISSING_CONTRACT_SIDECAR",
                    "No agent output contract sidecar found in decision input refs.", context, Set.of(), read);
        }
        if (read.document() == null) {
            return failed("INVALID_CONTRACT_SIDECAR", read.message(), context,
                    allowedDecisions(context.definition().id()), read);
        }
        var document = read.document();
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
        if ("BLOCKED".equals(decision)) {
            return new NodeResult(decision, NodeStatus.BLOCKED, context.instance().inputRefs(), facts,
                    "contract sidecar decision is BLOCKED");
        }
        return NodeResult.completed(decision, context.instance().inputRefs(), facts);
    }

    private DecisionContractRead readLatestDecisionContract(DecisionContext context) {
        var root = context.workspaceRoot().toAbsolutePath().normalize();
        var refs = context.instance().inputRefs();
        for (int i = refs.size() - 1; i >= 0; i--) {
            var ref = refs.get(i);
            if (ref.startsWith("artifact://")) {
                continue;
            }
            var path = root.resolve(ref).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)
                    || !path.getFileName().toString().endsWith(".contract.json")) {
                continue;
            }
            var document = AgentOutputContractDocument.read(path, OBJECT_MAPPER);
            if (document.isPresent()) {
                if (AgentOutputLineageVerifier.violation(
                        root, context.workstreamId(), path, document.get()).isPresent()) {
                    return DecisionContractRead.invalid(
                            "CONTRACT_SIDECAR_LINEAGE_INVALID",
                            "Contract sidecar lineage does not match the current workstream: " + path.getFileName(),
                            path.toString());
                }
                if (!document.get().hasDecision()) {
                    return DecisionContractRead.invalid(
                            "CONTRACT_SIDECAR_MISSING_DECISION",
                            "Contract sidecar is missing required decision: " + path.getFileName(),
                            path.toString());
                }
                return DecisionContractRead.valid(document.get(), path.toString());
            }
            return DecisionContractRead.invalid(
                    "CONTRACT_SIDECAR_PARSE_FAILED",
                    "Could not read agent output contract sidecar: " + path.getFileName(),
                    path.toString());
        }
        return DecisionContractRead.notFound();
    }

    private NodeResult failed(String reason, String message, DecisionContext context,
                              Set<String> allowed, DecisionContractRead read) {
        return new NodeResult(reason, NodeStatus.FAILED, List.of(), Map.of(
                "reason", reason,
                "message", message,
                "input_refs", context.instance().inputRefs(),
                "allowed_decisions", List.copyOf(allowed),
                "contract_values", read.document() == null ? Map.of() : read.document().control(),
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

    private record DecisionContractRead(
            AgentOutputContractDocument document,
            String errorCode,
            String message,
            String path,
            boolean missing
    ) {
        static DecisionContractRead valid(AgentOutputContractDocument document, String path) {
            return new DecisionContractRead(document, "", "", path, false);
        }

        static DecisionContractRead invalid(String code, String message, String path) {
            return new DecisionContractRead(null, code, message, path, false);
        }

        static DecisionContractRead notFound() {
            return new DecisionContractRead(null, "", "", "", true);
        }
    }
}
