package dev.dual.runtime.handler;

import java.util.Map;
import java.util.Set;

public final class ContractRegistry {
    private ContractRegistry() {}

    // --- Task nodes (agent output contracts) ---
    private static final NodeOutputContract INTAKE_BRAINSTORM = new NodeOutputContract(
            "intake.claude_brainstorm",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_PRODUCT_REVIEW"),
            Set.of("needs_human_input"),
            Set.of("task_contract_proposal", "task_contract_revision_patch")
    );
    private static final NodeOutputContract INTAKE_PRODUCT_REVIEW = new NodeOutputContract(
            "intake.codex_product_review",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_PRODUCT_REVIEW", "NEEDS_REVISION", "BLOCKED"),
            Set.of(),
            Set.of("agent_output_contract")
    );
    private static final NodeOutputContract DESIGN_CLAUDE = new NodeOutputContract(
            "design.claude_design",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_ARCH_REVIEW"),
            Set.of(),
            Set.of("agent_output_contract")
    );
    private static final NodeOutputContract DESIGN_ARCH_REVIEW = new NodeOutputContract(
            "design.codex_arch_review",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_ARCH_REVIEW", "NEEDS_REVISION", "BLOCKED"),
            Set.of(),
            Set.of("agent_output_contract")
    );
    private static final NodeOutputContract PLAN_CLAUDE = new NodeOutputContract(
            "plan.claude_plan",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_PLAN_REVIEW"),
            Set.of(),
            Set.of("plan_result")
    );
    private static final NodeOutputContract PLAN_REVIEW = new NodeOutputContract(
            "plan.codex_plan_review",
            Set.of("decision", "summary"),
            Set.of("READY_TO_IMPLEMENT", "NEEDS_REVISION", "BLOCKED"),
            Set.of(),
            Set.of("agent_output_contract")
    );
    private static final NodeOutputContract IMPLEMENT_CLAUDE = new NodeOutputContract(
            "implement.claude_implement",
            Set.of("decision", "summary"),
            Set.of("IMPLEMENTED", "BLOCKED"),
            Set.of(),
            Set.of("agent_output_contract"),
            Set.of("reread_reasons")
    );
    private static final NodeOutputContract IMPLEMENT_SELF_TEST = new NodeOutputContract(
            "implement.claude_self_test_report",
            Set.of("decision", "summary"),
            Set.of("SELF_TEST_COMPLETE", "SELF_TEST_INCOMPLETE", "BLOCKED"),
            Set.of(),
            Set.of("self_test_report")
    );
    private static final NodeOutputContract ACCEPTANCE_REVIEW = new NodeOutputContract(
            "acceptance.codex_verifier_review",
            Set.of("decision", "summary"),
            Set.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT"),
            Set.of("contract_gaps", "out_of_scope_files", "missing_tests", "context_gaps"),
            Set.of("acceptance_review")
    );
    private static final NodeOutputContract STAGE_HANDOFF = new NodeOutputContract(
            "stage.claude_handoff",
            Set.of("decision", "summary"),
            Set.of("HANDOFF_READY"),
            Set.of(),
            Set.of("stage_handoff")
    );

    // --- Contract nodes (task contract compilation) ---
    private static final NodeOutputContract CONTRACT_COMPILE = new NodeOutputContract(
            "contract.compile",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_REVIEW", "NEEDS_CLARIFICATION", "NEEDS_SCOPE_REDUCTION", "REJECTED"),
            Set.of("contract_id", "contract_status", "contract_version")
    );

    // --- Arbitration nodes (failure classification) ---
    private static final NodeOutputContract ACCEPTANCE_CLASSIFY_FAILURE = new NodeOutputContract(
            "acceptance.classify_failure",
            Set.of("decision", "summary"),
            Set.of(
                    "REJECT_CONTRACT_GAP", "REJECT_CONTRACT_AMBIGUITY",
                    "REJECT_IMPLEMENTATION_BUG", "REJECT_CONTEXT_INSUFFICIENT",
                    "REJECT_VERIFICATION_INSUFFICIENT", "REJECT_TOOL_LIMITATION", "REJECT_REVIEW_NOISE"
            ),
            Set.of("category", "reasoning", "matched_rule", "classification_ref")
    );

    // --- Decision nodes (edge routing contracts) ---
    private static final NodeOutputContract INTAKE_DECISION = new NodeOutputContract(
            "intake.decision",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_PRODUCT_REVIEW", "NEEDS_REVISION", "BLOCKED"),
            Set.of()
    );
    private static final NodeOutputContract DESIGN_DECISION = new NodeOutputContract(
            "design.decision",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_ARCH_REVIEW", "NEEDS_REVISION", "BLOCKED"),
            Set.of()
    );
    private static final NodeOutputContract PLAN_DECISION = new NodeOutputContract(
            "plan.decision",
            Set.of("decision", "summary"),
            Set.of("READY_TO_IMPLEMENT", "NEEDS_REVISION", "BLOCKED"),
            Set.of()
    );
    private static final NodeOutputContract SELF_TEST_DECISION = new NodeOutputContract(
            "implement.self_test_decision",
            Set.of("decision", "summary"),
            Set.of("SELF_TEST_COMPLETE", "SELF_TEST_INCOMPLETE", "BLOCKED"),
            Set.of()
    );
    private static final NodeOutputContract ACCEPTANCE_DECISION = new NodeOutputContract(
            "acceptance.decision",
            Set.of("decision", "summary"),
            Set.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT", "BLOCKED"),
            Set.of()
    );

    private static final Map<String, NodeOutputContract> CONTRACTS = Map.ofEntries(
            Map.entry("intake.claude_brainstorm", INTAKE_BRAINSTORM),
            Map.entry("intake.codex_product_review", INTAKE_PRODUCT_REVIEW),
            Map.entry("design.claude_design", DESIGN_CLAUDE),
            Map.entry("design.codex_arch_review", DESIGN_ARCH_REVIEW),
            Map.entry("plan.claude_plan", PLAN_CLAUDE),
            Map.entry("plan.codex_plan_review", PLAN_REVIEW),
            Map.entry("implement.claude_implement", IMPLEMENT_CLAUDE),
            Map.entry("implement.claude_self_test_report", IMPLEMENT_SELF_TEST),
            Map.entry("acceptance.codex_verifier_review", ACCEPTANCE_REVIEW),
            Map.entry("stage.claude_handoff", STAGE_HANDOFF),
            Map.entry("intake.decision", INTAKE_DECISION),
            Map.entry("design.decision", DESIGN_DECISION),
            Map.entry("plan.decision", PLAN_DECISION),
            Map.entry("implement.self_test_decision", SELF_TEST_DECISION),
            Map.entry("acceptance.decision", ACCEPTANCE_DECISION),
            Map.entry("contract.compile", CONTRACT_COMPILE),
            Map.entry("acceptance.classify_failure", ACCEPTANCE_CLASSIFY_FAILURE)
    );

    public static NodeOutputContract forNode(String nodeId) {
        var contract = CONTRACTS.get(nodeId);
        if (contract == null) {
            throw new IllegalArgumentException("no output contract for node: " + nodeId);
        }
        return contract;
    }

    public static boolean hasContract(String nodeId) {
        return CONTRACTS.containsKey(nodeId);
    }

    public static boolean supportsRereadReasonField(String nodeId) {
        return hasContract(nodeId) && forNode(nodeId).supportsArtifactField("reread_reasons");
    }

    public static Set<String> allNodeIds() {
        return CONTRACTS.keySet();
    }
}
