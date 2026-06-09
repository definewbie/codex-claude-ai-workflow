package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.EdgeCondition;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;

import java.util.List;
import java.util.Map;

public final class LifecycleWorkflowDefinitionFactory {
    private static final int DEFAULT_MAX_REVISIONS = 1;

    public WorkflowDefinition baselineMainline() {
        return new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(
                        agentTask("intake.claude_brainstorm", "claude", "claude-main", "intake-claude-brainstorm"),
                        contextTask("context.intake_codex_product_review", "intake.codex_product_review",
                                "Package Claude intake output for Codex product review.",
                                "product_review"),
                        agentTask("intake.codex_product_review", "codex", "codex-reviewer", "intake-codex-product-review"),
                        decision("intake.decision"),
                        gate("gate.human_requirement_review"),
                        agentTask("design.claude_design", "claude", "claude-main", "design-claude"),
                        contextTask("context.design_codex_arch_review", "design.codex_arch_review",
                                "Package Claude design and prior review context for Codex architecture review.",
                                "architecture_review"),
                        agentTask("design.codex_arch_review", "codex", "codex-reviewer", "design-codex-review"),
                        decision("design.decision"),
                        gate("gate.human_design_approval"),
                        agentTask("plan.claude_plan", "claude", "claude-main", "plan-claude"),
                        contextTask("context.plan_codex_plan_review", "plan.codex_plan_review",
                                "Package Claude plan and design context for Codex plan review.",
                                "plan_review"),
                        agentTask("plan.codex_plan_review", "codex", "codex-reviewer", "plan-codex-review"),
                        decision("plan.decision"),
                        gate("gate.human_plan_approval"),
                        contextTask("context.implement_claude", "implement.claude_implement",
                                "Package approved plan and review history for Claude implementation.",
                                "implementation"),
                        writableAgentTask("implement.claude_implement", "claude", "claude-main", "implement-claude"),
                        writableAgentTask("implement.claude_self_test_report", "claude", "claude-main", "implement-claude-self-test"),
                        task("implement.artifact_validation", "artifact.validate"),
                        contextTask("context.acceptance_codex_verifier_review", "acceptance.codex_verifier_review",
                                "Package implementation output, self-test report, and Git evidence for Codex acceptance.",
                                "acceptance"),
                        agentTask("acceptance.codex_verifier_review", "codex", "codex-reviewer", "acceptance-codex-review"),
                        decision("acceptance.decision"),
                        task("acceptance.generate_final_report", "report.generate_final"),
                        task("delivery.prepare_pr", "delivery.prepare_pr"),
                        gate("gate.human_acceptance"),
                        agentTask("stage.claude_handoff", "claude", "claude-main", "stage-claude-handoff"),
                        task("stage.generate_next_prompt", "prompt.generate_next_stage"),
                        gate("gate.human_next_stage_approval", List.of("approve", "revise", "complete"))
                ),
                List.of(
                        edge("intake.claude_brainstorm", "completed", "context.intake_codex_product_review"),
                        edge("context.intake_codex_product_review", "completed", "intake.codex_product_review"),
                        edge("intake.codex_product_review", "completed", "intake.decision"),
                        edge("intake.decision", "READY_FOR_PRODUCT_REVIEW", "gate.human_requirement_review"),
                        edge("intake.decision", "NEEDS_REVISION", "intake.claude_brainstorm"),
                        edge("gate.human_requirement_review", "approved", "design.claude_design"),
                        edge("gate.human_requirement_review", "revise", "intake.claude_brainstorm"),
                        edge("design.claude_design", "completed", "context.design_codex_arch_review"),
                        edge("context.design_codex_arch_review", "completed", "design.codex_arch_review"),
                        edge("design.codex_arch_review", "completed", "design.decision"),
                        edge("design.decision", "READY_FOR_ARCH_REVIEW", "gate.human_design_approval"),
                        edge("design.decision", "NEEDS_REVISION", "design.claude_design"),
                        edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                        edge("gate.human_design_approval", "revise", "design.claude_design"),
                        edge("plan.claude_plan", "completed", "context.plan_codex_plan_review"),
                        edge("context.plan_codex_plan_review", "completed", "plan.codex_plan_review"),
                        edge("plan.codex_plan_review", "completed", "plan.decision"),
                        edge("plan.decision", "READY_TO_IMPLEMENT", "gate.human_plan_approval"),
                        edge("plan.decision", "NEEDS_REVISION", "plan.claude_plan"),
                        edge("gate.human_plan_approval", "approved", "context.implement_claude"),
                        edge("gate.human_plan_approval", "revise", "plan.claude_plan"),
                        edge("context.implement_claude", "completed", "implement.claude_implement"),
                        edge("implement.claude_implement", "completed", "implement.claude_self_test_report"),
                        edge("implement.claude_self_test_report", "completed", "implement.artifact_validation"),
                        edge("implement.artifact_validation", "completed", "context.acceptance_codex_verifier_review"),
                        edge("context.acceptance_codex_verifier_review", "completed", "acceptance.codex_verifier_review"),
                        edge("acceptance.codex_verifier_review", "completed", "acceptance.decision"),
                        edge("acceptance.decision", "ACCEPT", "acceptance.generate_final_report"),
                        edge("acceptance.decision", "ACCEPT_WITH_NOTES", "acceptance.generate_final_report"),
                        edge("acceptance.decision", "REJECT", "context.implement_claude"),
                        edge("acceptance.generate_final_report", "completed", "delivery.prepare_pr"),
                        edge("delivery.prepare_pr", "completed", "gate.human_acceptance"),
                        edge("gate.human_acceptance", "approved", "stage.claude_handoff"),
                        edge("gate.human_acceptance", "revise", "context.implement_claude"),
                        edge("stage.claude_handoff", "completed", "stage.generate_next_prompt"),
                        edge("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval"),
                        edge("gate.human_next_stage_approval", "approved", "intake.claude_brainstorm"),
                        edge("gate.human_next_stage_approval", "revise", "stage.claude_handoff"),
                        // Example conditional edge: "complete" routes via condition
                        edge("gate.human_next_stage_approval", "complete", "acceptance.generate_final_report",
                                EdgeCondition.configEquals("completion_mode", "final_report"))
                )
        );
    }

    public WorkflowDefinition contractMainline() {
        return new WorkflowDefinition(
                "agent-lifecycle-contract",
                2,
                "intake.claude_brainstorm",
                List.of(
                        agentTask("intake.claude_brainstorm", "claude", "claude-main", "intake-claude-brainstorm"),
                        contextTask("context.intake_codex_product_review", "intake.codex_product_review",
                                "Package Claude intake output for Codex product review.",
                                "product_review"),
                        agentTask("intake.codex_product_review", "codex", "codex-reviewer", "intake-codex-product-review"),
                        decision("intake.decision"),
                        gate("gate.human_requirement_review"),
                        task("contract.compile", "contract.compile"),
                        gate("gate.contract_review", List.of("approve", "clarify", "reduce_scope", "reject")),
                        agentTask("design.claude_design", "claude", "claude-main", "design-claude"),
                        contextTask("context.design_codex_arch_review", "design.codex_arch_review",
                                "Package Claude design and prior review context for Codex architecture review.",
                                "architecture_review"),
                        agentTask("design.codex_arch_review", "codex", "codex-reviewer", "design-codex-review"),
                        decision("design.decision"),
                        gate("gate.human_design_approval"),
                        agentTask("plan.claude_plan", "claude", "claude-main", "plan-claude"),
                        contextTask("context.plan_codex_plan_review", "plan.codex_plan_review",
                                "Package Claude plan and design context for Codex plan review.",
                                "plan_review"),
                        agentTask("plan.codex_plan_review", "codex", "codex-reviewer", "plan-codex-review"),
                        decision("plan.decision"),
                        gate("gate.human_plan_approval"),
                        contextTask("context.implement_claude", "implement.claude_implement",
                                "Package approved plan and review history for Claude implementation.",
                                "implementation"),
                        writableAgentTask("implement.claude_implement", "claude", "claude-main", "implement-claude"),
                        writableAgentTask("implement.claude_self_test_report", "claude", "claude-main", "implement-claude-self-test"),
                        task("implement.artifact_validation", "artifact.validate"),
                        contextTask("context.acceptance_codex_verifier_review", "acceptance.codex_verifier_review",
                                "Package implementation output, self-test report, and Git evidence for Codex acceptance.",
                                "acceptance"),
                        agentTask("acceptance.codex_verifier_review", "codex", "codex-reviewer", "acceptance-codex-review"),
                        decision("acceptance.decision"),
                        task("acceptance.generate_final_report", "report.generate_final"),
                        task("delivery.prepare_pr", "delivery.prepare_pr"),
                        gate("gate.human_acceptance"),
                        agentTask("stage.claude_handoff", "claude", "claude-main", "stage-claude-handoff"),
                        task("stage.generate_next_prompt", "prompt.generate_next_stage"),
                        gate("gate.human_next_stage_approval", List.of("approve", "revise", "complete"))
                ),
                List.of(
                        edge("intake.claude_brainstorm", "completed", "context.intake_codex_product_review"),
                        edge("context.intake_codex_product_review", "completed", "intake.codex_product_review"),
                        edge("intake.codex_product_review", "completed", "intake.decision"),
                        edge("intake.decision", "READY_FOR_PRODUCT_REVIEW", "gate.human_requirement_review"),
                        edge("intake.decision", "NEEDS_REVISION", "intake.claude_brainstorm"),
                        edge("gate.human_requirement_review", "approved", "contract.compile"),
                        edge("gate.human_requirement_review", "revise", "intake.claude_brainstorm"),
                        edge("contract.compile", "completed", "gate.contract_review"),
                        edge("gate.contract_review", "approved", "design.claude_design"),
                        edge("gate.contract_review", "clarify", "contract.compile"),
                        edge("gate.contract_review", "reduce_scope", "contract.compile"),
                        edge("design.claude_design", "completed", "context.design_codex_arch_review"),
                        edge("context.design_codex_arch_review", "completed", "design.codex_arch_review"),
                        edge("design.codex_arch_review", "completed", "design.decision"),
                        edge("design.decision", "READY_FOR_ARCH_REVIEW", "gate.human_design_approval"),
                        edge("design.decision", "NEEDS_REVISION", "design.claude_design"),
                        edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                        edge("gate.human_design_approval", "revise", "design.claude_design"),
                        edge("plan.claude_plan", "completed", "context.plan_codex_plan_review"),
                        edge("context.plan_codex_plan_review", "completed", "plan.codex_plan_review"),
                        edge("plan.codex_plan_review", "completed", "plan.decision"),
                        edge("plan.decision", "READY_TO_IMPLEMENT", "gate.human_plan_approval"),
                        edge("plan.decision", "NEEDS_REVISION", "plan.claude_plan"),
                        edge("gate.human_plan_approval", "approved", "context.implement_claude"),
                        edge("gate.human_plan_approval", "revise", "plan.claude_plan"),
                        edge("context.implement_claude", "completed", "implement.claude_implement"),
                        edge("implement.claude_implement", "completed", "implement.claude_self_test_report"),
                        edge("implement.claude_self_test_report", "completed", "implement.artifact_validation"),
                        edge("implement.artifact_validation", "completed", "context.acceptance_codex_verifier_review"),
                        edge("context.acceptance_codex_verifier_review", "completed", "acceptance.codex_verifier_review"),
                        edge("acceptance.codex_verifier_review", "completed", "acceptance.decision"),
                        edge("acceptance.decision", "ACCEPT", "acceptance.generate_final_report"),
                        edge("acceptance.decision", "ACCEPT_WITH_NOTES", "acceptance.generate_final_report"),
                        edge("acceptance.decision", "REJECT", "context.implement_claude"),
                        edge("acceptance.generate_final_report", "completed", "delivery.prepare_pr"),
                        edge("delivery.prepare_pr", "completed", "gate.human_acceptance"),
                        edge("gate.human_acceptance", "approved", "stage.claude_handoff"),
                        edge("gate.human_acceptance", "revise", "context.implement_claude"),
                        edge("stage.claude_handoff", "completed", "stage.generate_next_prompt"),
                        edge("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval"),
                        edge("gate.human_next_stage_approval", "approved", "intake.claude_brainstorm"),
                        edge("gate.human_next_stage_approval", "revise", "stage.claude_handoff"),
                        edge("gate.human_next_stage_approval", "complete", "acceptance.generate_final_report",
                                EdgeCondition.configEquals("completion_mode", "final_report"))
                )
        );
    }

    public WorkflowDefinition fullMainline() {
        return new WorkflowDefinition(
                "agent-lifecycle-full",
                3,
                "intake.claude_brainstorm",
                List.of(
                        writableAgentTask("intake.claude_brainstorm", "claude", "claude-main", "intake-claude-brainstorm"),
                        contextTask("context.intake_codex_product_review", "intake.codex_product_review",
                                "Package Claude intake output for Codex product review.",
                                "product_review"),
                        writableAgentTask("intake.codex_product_review", "codex", "codex-reviewer", "intake-codex-product-review"),
                        decision("intake.decision"),
                        gate("gate.human_requirement_review"),
                        task("contract.compile", "contract.compile"),
                        gate("gate.contract_review", List.of("approve", "clarify", "reduce_scope", "reject")),
                        writableAgentTask("design.claude_design", "claude", "claude-main", "design-claude"),
                        contextTask("context.design_codex_arch_review", "design.codex_arch_review",
                                "Package Claude design and prior review context for Codex architecture review.",
                                "architecture_review"),
                        writableAgentTask("design.codex_arch_review", "codex", "codex-reviewer", "design-codex-review"),
                        decision("design.decision"),
                        gate("gate.human_design_approval"),
                        writableAgentTask("plan.claude_plan", "claude", "claude-main", "plan-claude"),
                        contextTask("context.plan_codex_plan_review", "plan.codex_plan_review",
                                "Package Claude plan and design context for Codex plan review.",
                                "plan_review"),
                        writableAgentTask("plan.codex_plan_review", "codex", "codex-reviewer", "plan-codex-review"),
                        decision("plan.decision"),
                        gate("gate.human_plan_approval"),
                        contextTask("context.implement_claude", "implement.claude_implement",
                                "Package approved plan and review history for Claude implementation.",
                                "implementation"),
                        writableAgentTask("implement.claude_implement", "claude", "claude-main", "implement-claude"),
                        writableAgentTask("implement.claude_self_test_report", "claude", "claude-main", "implement-claude-self-test"),
                        task("implement.artifact_validation", "artifact.validate"),
                        contextTask("context.acceptance_codex_verifier_review", "acceptance.codex_verifier_review",
                                "Package implementation output, self-test report, and Git evidence for Codex acceptance.",
                                "acceptance"),
                        writableAgentTask("acceptance.codex_verifier_review", "codex", "codex-reviewer", "acceptance-codex-review"),
                        decision("acceptance.decision"),
                        task("acceptance.classify_failure", "acceptance.failure_classify"),
                        task("acceptance.generate_final_report", "report.generate_final"),
                        task("delivery.prepare_pr", "delivery.prepare_pr"),
                        gate("gate.human_acceptance"),
                        writableAgentTask("stage.claude_handoff", "claude", "claude-main", "stage-claude-handoff"),
                        task("stage.generate_next_prompt", "prompt.generate_next_stage"),
                        gate("gate.human_next_stage_approval", List.of("approve", "revise", "complete"))
                ),
                List.of(
                        edge("intake.claude_brainstorm", "completed", "context.intake_codex_product_review"),
                        edge("context.intake_codex_product_review", "completed", "intake.codex_product_review"),
                        edge("intake.codex_product_review", "completed", "intake.decision"),
                        edge("intake.decision", "READY_FOR_PRODUCT_REVIEW", "gate.human_requirement_review"),
                        edge("intake.decision", "NEEDS_REVISION", "intake.claude_brainstorm"),
                        edge("gate.human_requirement_review", "approved", "contract.compile"),
                        edge("gate.human_requirement_review", "revise", "intake.claude_brainstorm"),
                        edge("contract.compile", "completed", "gate.contract_review"),
                        edge("gate.contract_review", "approved", "design.claude_design"),
                        edge("gate.contract_review", "clarify", "contract.compile"),
                        edge("gate.contract_review", "reduce_scope", "contract.compile"),
                        edge("design.claude_design", "completed", "context.design_codex_arch_review"),
                        edge("context.design_codex_arch_review", "completed", "design.codex_arch_review"),
                        edge("design.codex_arch_review", "completed", "design.decision"),
                        edge("design.decision", "READY_FOR_ARCH_REVIEW", "gate.human_design_approval"),
                        edge("design.decision", "NEEDS_REVISION", "design.claude_design"),
                        edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                        edge("gate.human_design_approval", "revise", "design.claude_design"),
                        edge("plan.claude_plan", "completed", "context.plan_codex_plan_review"),
                        edge("context.plan_codex_plan_review", "completed", "plan.codex_plan_review"),
                        edge("plan.codex_plan_review", "completed", "plan.decision"),
                        edge("plan.decision", "READY_TO_IMPLEMENT", "gate.human_plan_approval"),
                        edge("plan.decision", "NEEDS_REVISION", "plan.claude_plan"),
                        edge("gate.human_plan_approval", "approved", "context.implement_claude"),
                        edge("gate.human_plan_approval", "revise", "plan.claude_plan"),
                        edge("context.implement_claude", "completed", "implement.claude_implement"),
                        edge("implement.claude_implement", "completed", "implement.claude_self_test_report"),
                        edge("implement.claude_self_test_report", "completed", "implement.artifact_validation"),
                        edge("implement.artifact_validation", "completed", "context.acceptance_codex_verifier_review"),
                        edge("context.acceptance_codex_verifier_review", "completed", "acceptance.codex_verifier_review"),
                        edge("acceptance.codex_verifier_review", "completed", "acceptance.decision"),
                        // Acceptance decision routing
                        edge("acceptance.decision", "ACCEPT", "acceptance.generate_final_report"),
                        edge("acceptance.decision", "ACCEPT_WITH_NOTES", "acceptance.classify_failure"),
                        edge("acceptance.decision", "REJECT", "acceptance.classify_failure"),
                        // REJECT recovery edges from classify_failure
                        edge("acceptance.classify_failure", "REJECT_CONTRACT_GAP", "contract.compile"),
                        edge("acceptance.classify_failure", "REJECT_CONTRACT_AMBIGUITY", "contract.compile"),
                        edge("acceptance.classify_failure", "REJECT_IMPLEMENTATION_BUG", "context.implement_claude"),
                        edge("acceptance.classify_failure", "REJECT_CONTEXT_INSUFFICIENT", "context.implement_claude"),
                        edge("acceptance.classify_failure", "REJECT_TOOL_LIMITATION", "context.implement_claude"),
                        edge("acceptance.classify_failure", "REJECT_REVIEW_NOISE", "context.acceptance_codex_verifier_review"),
                        // NOTED edges route to report while preserving classification data for calibration.
                        edge("acceptance.classify_failure", "NOTED_CONTRACT_GAP", "acceptance.generate_final_report"),
                        edge("acceptance.classify_failure", "NOTED_CONTRACT_AMBIGUITY", "acceptance.generate_final_report"),
                        edge("acceptance.classify_failure", "NOTED_IMPLEMENTATION_BUG", "acceptance.generate_final_report"),
                        edge("acceptance.classify_failure", "NOTED_CONTEXT_INSUFFICIENT", "acceptance.generate_final_report"),
                        edge("acceptance.classify_failure", "NOTED_TOOL_LIMITATION", "acceptance.generate_final_report"),
                        edge("acceptance.classify_failure", "NOTED_REVIEW_NOISE", "acceptance.generate_final_report"),
                        // Post-report
                        edge("acceptance.generate_final_report", "completed", "delivery.prepare_pr"),
                        edge("delivery.prepare_pr", "completed", "gate.human_acceptance"),
                        edge("gate.human_acceptance", "approved", "stage.claude_handoff"),
                        edge("gate.human_acceptance", "revise", "context.implement_claude"),
                        edge("stage.claude_handoff", "completed", "stage.generate_next_prompt"),
                        edge("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval"),
                        edge("gate.human_next_stage_approval", "approved", "intake.claude_brainstorm"),
                        edge("gate.human_next_stage_approval", "revise", "stage.claude_handoff"),
                        edge("gate.human_next_stage_approval", "complete", "acceptance.generate_final_report",
                                EdgeCondition.configEquals("completion_mode", "final_report"))
                )
        );
    }

    public WorkflowDefinition forVariant(String variant) {
        String normalized = variant == null ? "" : variant.trim().toLowerCase();
        return switch (normalized) {
            case "contract" -> contractMainline();
            case "full" -> fullMainline();
            case "", "baseline" -> baselineMainline();
            default -> throw new IllegalArgumentException(
                    "unknown workflow variant: " + variant
                            + ". Supported: baseline, contract, full");
        };
    }

    private static NodeDefinition agentTask(String id, String agent, String session, String promptTemplate) {
        return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", agent,
                "session", session,
                "prompt_template", promptTemplate
        ));
    }

    private static NodeDefinition writableAgentTask(String id, String agent, String session, String promptTemplate) {
        return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", agent,
                "session", session,
                "prompt_template", promptTemplate,
                "approve_all", true
        ));
    }

    private static NodeDefinition task(String id, String handlerType) {
        return new NodeDefinition(id, NodeKind.TASK, handlerType, Map.of());
    }

    private static NodeDefinition contextTask(String id, String targetNode, String purpose, String profile) {
        return new NodeDefinition(id, NodeKind.TASK, "context.build_package", Map.of(
                "target_node", targetNode,
                "purpose", purpose,
                "profile", profile
        ));
    }

    private static NodeDefinition decision(String id) {
        return new NodeDefinition(id, NodeKind.DECISION, "review.decision", Map.of(
                "max_revisions", DEFAULT_MAX_REVISIONS
        ));
    }

    private static NodeDefinition gate(String id) {
        return gate(id, List.of("approve", "revise"));
    }

    private static NodeDefinition gate(String id, List<String> allowedActions) {
        return new NodeDefinition(id, NodeKind.GATE, "human.review", Map.of(
                "allowed_actions", allowedActions,
                "max_revisions", DEFAULT_MAX_REVISIONS
        ));
    }

    private static EdgeDefinition edge(String from, String on, String to) {
        return new EdgeDefinition(from, on, to);
    }

    private static EdgeDefinition edge(String from, String on, String to, EdgeCondition condition) {
        return new EdgeDefinition(from, on, to, condition);
    }
}
