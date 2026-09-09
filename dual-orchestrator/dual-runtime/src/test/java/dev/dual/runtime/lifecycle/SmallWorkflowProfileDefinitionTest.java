package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.TransitionMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallWorkflowProfileDefinitionTest {
    static final String EXPERIMENT_RESOURCE =
            "/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json";
    private static final Set<String> REMOVED_PLAN_REVIEW_NODES = Set.of(
            "context.plan_codex_plan_review",
            "plan.codex_plan_review",
            "plan.decision"
    );

    @Test
    void externalDefinitionEqualsTerminalDeliveryPlusTheApprovedPlanReviewDelta() throws Exception {
        var baseline = new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery");
        var experiment = loadExperiment();

        var expectedNodes = baseline.nodes().stream()
                .filter(node -> !REMOVED_PLAN_REVIEW_NODES.contains(node.id()))
                .toList();
        var expectedEdges = new ArrayList<EdgeDefinition>();
        var directEdgeAdded = false;
        for (var edge : baseline.edges()) {
            if (REMOVED_PLAN_REVIEW_NODES.contains(edge.from())
                    || REMOVED_PLAN_REVIEW_NODES.contains(edge.to())) {
                continue;
            }
            if (!directEdgeAdded && "gate.human_plan_approval".equals(edge.from())) {
                expectedEdges.add(new EdgeDefinition(
                        "plan.claude_plan",
                        "completed",
                        "gate.human_plan_approval",
                        null,
                        TransitionMetadata.normal()));
                directEdgeAdded = true;
            }
            expectedEdges.add(edge);
        }

        assertEquals("agent-lifecycle-small-plan-human-review-experiment", experiment.workflowId());
        assertEquals(1, experiment.version());
        assertEquals(baseline.entryNodeId(), experiment.entryNodeId());
        assertEquals(expectedNodes, experiment.nodes());
        assertEquals(expectedEdges, experiment.edges());
        assertTrue(directEdgeAdded);
        assertEquals(28, experiment.nodes().size());
        assertEquals(48, experiment.edges().size());
        assertEquals(8, experiment.nodes().stream()
                .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                .count());
        assertEquals(6, experiment.nodes().stream()
                .filter(node -> node.kind() == NodeKind.GATE)
                .count());
    }

    @Test
    void experimentPreservesPlanProducerBlockedBehaviorAndHumanGateBudget() throws Exception {
        var baseline = new LifecycleWorkflowDefinitionFactory().forVariant("terminal-delivery");
        var experiment = loadExperiment();

        assertEquals(baseline.node("plan.claude_plan"), experiment.node("plan.claude_plan"));
        assertEquals(baseline.edgesFrom("plan.claude_plan", "BLOCKED", Map.of()),
                experiment.edgesFrom("plan.claude_plan", "BLOCKED", Map.of()));
        assertEquals(3, experiment.node("gate.human_plan_approval").config().get("max_revisions"));
        assertEquals(baseline.node("gate.human_plan_approval"),
                experiment.node("gate.human_plan_approval"));
        assertEquals("context.implement_claude", experiment.edgesFrom("gate.human_plan_approval").stream()
                .filter(edge -> "approved".equals(edge.on()))
                .findFirst().orElseThrow().to());
        assertEquals("plan.claude_plan", experiment.edgesFrom("gate.human_plan_approval").stream()
                .filter(edge -> "revise".equals(edge.on()))
                .findFirst().orElseThrow().to());
        for (var removed : REMOVED_PLAN_REVIEW_NODES) {
            assertThrows(IllegalArgumentException.class, () -> experiment.node(removed));
        }
    }

    @Test
    void experimentDoesNotChangeBundledWorkflowResources() throws Exception {
        // Baseline includes bounded human scope revision on all lifecycle variants.
        assertEquals("4364f663cd80e1a4ba4eafe3dcf8f6d1bfecdd00ac11dddcd3c7122d6bcb24cb",
                sha256("/dev/dual/runtime/lifecycle/agent-lifecycle.full.json"));
        assertEquals("f97a7f48015f367d10dea7976f772d1a70563a28c209bc3927267f674b838b3b",
                sha256("/dev/dual/runtime/lifecycle/agent-lifecycle.contract.json"));
        assertEquals("e17efde4c54bf5dd6753b55a083a3768c9d166e6da9cc94f741a9c27099fc542",
                sha256("/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json"));
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
            assertEquals(true, producer.config().get("requires_approved_task_contract"));
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

    private static String sha256(String resource) throws Exception {
        try (InputStream input = SmallWorkflowProfileDefinitionTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("missing resource: " + resource);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }
}
