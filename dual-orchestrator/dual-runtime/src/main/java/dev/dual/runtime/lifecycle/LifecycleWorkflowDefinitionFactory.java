package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;

import java.util.List;
import java.util.Map;

public final class LifecycleWorkflowDefinitionFactory {
    public WorkflowDefinition thinMainline() {
        return new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(
                        agentTask("intake.claude_brainstorm", "claude", "claude-main", "intake-claude-brainstorm"),
                        agentTask("intake.codex_product_review", "codex", "codex-reviewer", "intake-codex-product-review"),
                        decision("intake.decision"),
                        gate("gate.human_requirement_review"),
                        agentTask("design.claude_design", "claude", "claude-main", "design-claude"),
                        agentTask("design.codex_arch_review", "codex", "codex-reviewer", "design-codex-review"),
                        decision("design.decision"),
                        gate("gate.human_design_approval"),
                        agentTask("plan.claude_plan", "claude", "claude-main", "plan-claude"),
                        agentTask("plan.codex_plan_review", "codex", "codex-reviewer", "plan-codex-review"),
                        decision("plan.decision"),
                        gate("gate.human_plan_approval"),
                        agentTask("implement.claude_implement", "claude", "claude-main", "implement-claude"),
                        agentTask("implement.claude_self_test_report", "claude", "claude-main", "implement-claude-self-test"),
                        task("implement.artifact_validation", "artifact.validate"),
                        agentTask("acceptance.codex_verifier_review", "codex", "codex-reviewer", "acceptance-codex-review"),
                        decision("acceptance.decision"),
                        gate("gate.human_acceptance"),
                        agentTask("stage.claude_handoff", "claude", "claude-main", "stage-claude-handoff"),
                        task("stage.generate_next_prompt", "prompt.generate_next_stage"),
                        gate("gate.human_next_stage_approval")
                ),
                List.of(
                        edge("intake.claude_brainstorm", "completed", "intake.codex_product_review"),
                        edge("intake.codex_product_review", "completed", "intake.decision"),
                        edge("intake.decision", "PASS", "gate.human_requirement_review"),
                        edge("intake.decision", "NEEDS_REVISION", "intake.claude_brainstorm"),
                        edge("gate.human_requirement_review", "approved", "design.claude_design"),
                        edge("gate.human_requirement_review", "revise", "intake.claude_brainstorm"),
                        edge("design.claude_design", "completed", "design.codex_arch_review"),
                        edge("design.codex_arch_review", "completed", "design.decision"),
                        edge("design.decision", "PASS", "gate.human_design_approval"),
                        edge("design.decision", "NEEDS_REVISION", "design.claude_design"),
                        edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                        edge("gate.human_design_approval", "revise", "design.claude_design"),
                        edge("plan.claude_plan", "completed", "plan.codex_plan_review"),
                        edge("plan.codex_plan_review", "completed", "plan.decision"),
                        edge("plan.decision", "READY_TO_IMPLEMENT", "gate.human_plan_approval"),
                        edge("plan.decision", "NEEDS_REVISION", "plan.claude_plan"),
                        edge("gate.human_plan_approval", "approved", "implement.claude_implement"),
                        edge("gate.human_plan_approval", "revise", "plan.claude_plan"),
                        edge("implement.claude_implement", "completed", "implement.claude_self_test_report"),
                        edge("implement.claude_self_test_report", "completed", "implement.artifact_validation"),
                        edge("implement.artifact_validation", "completed", "acceptance.codex_verifier_review"),
                        edge("acceptance.codex_verifier_review", "completed", "acceptance.decision"),
                        edge("acceptance.decision", "ACCEPT", "gate.human_acceptance"),
                        edge("acceptance.decision", "ACCEPT_WITH_NOTES", "gate.human_acceptance"),
                        edge("acceptance.decision", "REJECT", "implement.claude_implement"),
                        edge("gate.human_acceptance", "approved", "stage.claude_handoff"),
                        edge("gate.human_acceptance", "revise", "implement.claude_implement"),
                        edge("stage.claude_handoff", "completed", "stage.generate_next_prompt"),
                        edge("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval"),
                        edge("gate.human_next_stage_approval", "revise", "stage.claude_handoff")
                )
        );
    }

    private static NodeDefinition agentTask(String id, String agent, String session, String promptTemplate) {
        return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", agent,
                "session", session,
                "prompt_template", promptTemplate
        ));
    }

    private static NodeDefinition task(String id, String handlerType) {
        return new NodeDefinition(id, NodeKind.TASK, handlerType, Map.of());
    }

    private static NodeDefinition decision(String id) {
        return new NodeDefinition(id, NodeKind.DECISION, "review.decision", Map.of());
    }

    private static NodeDefinition gate(String id) {
        return new NodeDefinition(id, NodeKind.GATE, "human.review", Map.of(
                "allowed_actions", List.of("approve", "revise")
        ));
    }

    private static EdgeDefinition edge(String from, String on, String to) {
        return new EdgeDefinition(from, on, to);
    }
}
