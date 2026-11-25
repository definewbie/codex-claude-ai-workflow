package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
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
                context.ledger(), context.workspaceRoot(),
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
        try { VerificationEvidence.verifyPublished(context.ledger(), context.workspaceRoot(), context.workstreamId(), document); }
        catch (java.io.IOException invalid) {
            return failed("VERIFICATION_EVIDENCE_INVALID", invalid.getMessage(), context,
                    allowedDecisions(context.definition().id()), read);
        }
        if ("implement.self_test_decision".equals(context.definition().id())
                && !"self_test_report".equals(document.sourceArtifactType())) {
            return failed("INVALID_CONTRACT_SIDECAR", "self-test decision requires a self_test_report",
                    context, allowedDecisions(context.definition().id()), read);
        }
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
        var facts = new LinkedHashMap<String, Object>(Map.of(
                "decision", decision,
                "source", "contract-sidecar",
                "summary", document.summary()));
        if ("self_test_report".equals(document.sourceArtifactType())) {
            if ("SELF_TEST_COMPLETE".equals(decision)) {
                var violation = AgentContractSemantics.validateOutputContract(document);
                if (violation.isPresent()) {
                    return failed("INVALID_CONTRACT_SIDECAR", violation.get().message(), context, allowed, read);
                }
            }
            facts.put("continuation_kind", switch (decision) {
                case "SELF_TEST_COMPLETE" -> "COMPLETE";
                case "SELF_TEST_INCOMPLETE" -> document.mustFix().isEmpty()
                        ? "HUMAN_REVIEW" : "IMPLEMENTATION_REPAIR";
                case "BLOCKED" -> "HUMAN_REVIEW";
                default -> throw new IllegalStateException("unsupported self-test decision: " + decision);
            });
        }
        if (java.util.Set.of("READY_FOR_ARCH_REVIEW", "READY_TO_IMPLEMENT").contains(decision)) {
            for (var stage : AuthoredStageLineageResolver.Stage.values()) {
                if (!stage.decision.equals(context.definition().id())) continue;
                var resolver = new AuthoredStageLineageResolver(context.ledger(), context.workspaceRoot(),
                        context.workstreamId(), Long.MAX_VALUE);
                facts.put("reviewed_candidate", resolver.reviewedFacts(stage, context.instance().nodeInstanceId()));
            }
        }
        if ("plan.decision".equals(context.definition().id()) && "READY_TO_IMPLEMENT".equals(decision)) {
            var paths = new dev.dual.ledger.file.WorkstreamPaths(context.workspaceRoot(),
                    new dev.dual.core.id.DualId(context.workstreamId()));
            var missing = context.ledger().inspect(paths, (ledger, files) -> {
                var resolution = TaskContractProjectionVerifier.resolve(ledger, context.workspaceRoot(),
                        context.workstreamId(), dev.dual.core.contract.TaskContract.Phase.GOAL);
                if (resolution.failure() != null || resolution.qualified().isEmpty())
                    throw new java.io.IOException("Plan has no current approved GOAL");
                var reviewed = OBJECT_MAPPER.convertValue(facts.get("reviewed_candidate"), Map.class);
                var author = OBJECT_MAPPER.convertValue(reviewed.get("author_artifact"), AuthoredExecutionContext.Reference.class);
                var authorDocument = AgentOutputContractDocument.read(AuthoredExecutionContext.verifiedPath(context.workspaceRoot(), author), OBJECT_MAPPER);
                var plan = authorDocument.map(AgentOutputContractDocument::planArtifact).orElse(null);
                if (plan == null || plan.missingRequiredField().isPresent())
                    throw new java.io.IOException("Reviewed plan is incomplete");
                try {
                    VerificationReadiness.validateBindings(ledger, context.workspaceRoot(), context.workstreamId(),
                            resolution.qualified().orElseThrow(), plan.verificationBindings());
                    return "";
                } catch (java.io.IOException failure) {
                    return failure.getMessage();
                }
            });
            if (!missing.isEmpty()) {
                facts.put("readiness_reason", VerificationReadiness.REQUIRED);
                facts.put("readiness_detail", missing);
                // Preserve the independent review; this is a program-owned routing decision.
                return NodeResult.completed(VerificationReadiness.REQUIRED, context.instance().inputRefs(), facts);
            }
        }
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
