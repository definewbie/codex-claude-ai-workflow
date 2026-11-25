package dev.dual.runtime.lifecycle;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallWorkflowProfileDefinitionTest {
    static final String EXPERIMENT_RESOURCE =
            "/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json";
    @Test
    void smallAlsoPreservesIndependentPlanReviewAndTheSameAuthorizationChain() throws Exception {
        var baseline = new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery");
        var small = loadExperiment();
        assertEquals("agent-lifecycle-small-plan-human-review-experiment", small.workflowId());
        assertEquals(baseline.entryNodeId(), small.entryNodeId());
        assertEquals(baseline.nodes().stream().collect(java.util.stream.Collectors.toMap(n -> n.id(), n -> n)),
                small.nodes().stream().collect(java.util.stream.Collectors.toMap(n -> n.id(), n -> n)));
        var json = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new dev.dual.ledger.file.EdgeConditionModule());
        assertEquals(baseline.edges().stream().map(json::valueToTree).collect(java.util.stream.Collectors.toSet()),
                small.edges().stream().map(json::valueToTree).collect(java.util.stream.Collectors.toSet()));
        assertEquals("acpx.agent_run", small.node("plan.codex_plan_review").handlerType());
        assertEquals("review.decision", small.node("plan.decision").handlerType());
        assertEquals("EXECUTION", small.node("contract.compile_execution").config().get("contract_phase"));
    }

    @Test
    void planHumanDispositionRequiresAuthorAndIndependentReviewAgain() throws Exception {
        var small = loadExperiment();
        assertEquals(3, small.node("gate.human_plan_approval").config().get("max_revisions"));
        assertEquals("plan.claude_plan", small.edgesFrom("gate.human_plan_approval").stream()
                .filter(e -> "approved".equals(e.on())).findFirst().orElseThrow().to());
        assertEquals("plan.claude_plan", small.edgesFrom("gate.human_plan_approval").stream()
                .filter(e -> "revise".equals(e.on())).findFirst().orElseThrow().to());
    }

    @Test
    void allProfilesSeparateGoalAndExecutionApproval() throws Exception {
        for (var definition : List.of(new LifecycleWorkflowDefinitionFactory().forVariant("full"),
                new LifecycleWorkflowDefinitionFactory().forVariant("contract"),
                new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery"), loadExperiment())) {
            assertEquals("GOAL", definition.node("contract.compile").config().get("contract_phase"));
            assertEquals("GOAL", definition.node("design.claude_design").config().get("required_contract_phase"));
            assertEquals("GOAL", definition.node("plan.claude_plan").config().get("required_contract_phase"));
            assertEquals("EXECUTION", definition.node("implement.claude_implement").config().get("required_contract_phase"));
            assertEquals("context.implement_claude", definition.edgesFrom("gate.contract_review").stream()
                    .filter(e -> "approved".equals(e.on())).findFirst().orElseThrow().to());
        }
    }

    @Test
    void allAcceptanceProfilesDeclareTheSameRequiredSelfTestEvidence() throws Exception {
        var definitions = List.of(
                new LifecycleWorkflowDefinitionFactory().forVariant("full"),
                new LifecycleWorkflowDefinitionFactory().forVariant("contract"),
                new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery"),
                loadExperiment());

        for (var definition : definitions) {
            var context = definition.node("context.acceptance_codex_verifier_review");
            assertEquals("acceptance", context.config().get("profile"), definition.workflowId());
            assertEquals("acceptance.codex_verifier_review", context.config().get("target_node"),
                    definition.workflowId());
            assertEquals(Map.of(
                            "node_id", "implement.claude_self_test_report",
                            "source_artifact_type", "self_test_report",
                            "decision", "SELF_TEST_COMPLETE"),
                    context.config().get("required_agent_evidence"), definition.workflowId());
        }
    }

    @Test
    void allDesignProfilesDeclareTypedDesignAndTheSameReviewConsumer() throws Exception {
        for (var definition : List.of(
                new LifecycleWorkflowDefinitionFactory().forVariant("full"),
                new LifecycleWorkflowDefinitionFactory().forVariant("contract"),
                new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery"), loadExperiment())) {
            var producer = definition.node("design.claude_design");
            assertEquals("design_result", producer.config().get("expected_artifact"));
            assertEquals("GOAL", producer.config().get("required_contract_phase"));
            assertEquals(java.util.Set.of("design_result"),
                    dev.dual.runtime.handler.ContractRegistry.forNode(producer.id()).outputArtifactTypes());
            var consumer = definition.node("context.design_codex_arch_review");
            assertEquals("architecture_review", consumer.config().get("profile"));
            assertEquals("design.codex_arch_review", consumer.config().get("target_node"));
            assertEquals("context.design_codex_arch_review", definition.edgesFrom(producer.id()).getFirst().to());
        }
    }

    @Test
    void allLifecycleProfilesDeclareTheSameChangeScopeRepairRoute() throws Exception {
        var definitions = List.of(
                new LifecycleWorkflowDefinitionFactory().forVariant("full"),
                new LifecycleWorkflowDefinitionFactory().forVariant("contract"),
                new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery"),
                loadExperiment());

        for (var definition : definitions) {
            assertEquals(true, definition.node("implement.artifact_validation")
                    .config().get("route_scope_violation"), definition.workflowId());
            var route = definition.edgesFrom("implement.artifact_validation").stream()
                    .filter(edge -> "CHANGE_SCOPE_VIOLATION".equals(edge.on()))
                    .findFirst().orElseThrow();
            assertEquals("gate.human_implementation_repair", route.to(), definition.workflowId());
            var repair = definition.edgesFrom("gate.human_implementation_repair").stream()
                    .filter(edge -> "revise".equals(edge.on()))
                    .findFirst().orElseThrow();
            assertEquals("Human authorized implementation repair", repair.metadata().loopReason(),
                    definition.workflowId());
        }
    }

    static dev.dual.core.workflow.WorkflowDefinition loadExperiment() throws Exception {
        return new WorkflowDefinitionLoader().load(resourcePath(EXPERIMENT_RESOURCE));
    }

    private static Path resourcePath(String resource) throws URISyntaxException {
        return Path.of(SmallWorkflowProfileDefinitionTest.class.getResource(resource).toURI());
    }

}
