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
        assertEquals(46, experiment.edges().size());
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
        assertEquals("4b359ae884fc18d6933215570c8c0cb3cd52654751dc2382f51532404e684f83",
                sha256("/dev/dual/runtime/lifecycle/agent-lifecycle.full.json"));
        assertEquals("fce06b1e5c7506bdb9bdade9a2daf2e91ee9bda5bbb2efa32e2d3e1c97e1ff19",
                sha256("/dev/dual/runtime/lifecycle/agent-lifecycle.contract.json"));
        assertEquals("bcf1763a75cc03a118c636590441d5e7f447dd71d157e388934de1f6ce54fcdb",
                sha256("/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json"));
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
