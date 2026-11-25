package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.EdgeConditionModule;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredDefinitionTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new EdgeConditionModule());
    @TempDir Path root;

    @Test
    void fourShippedDefinitionsUseSupportedAuthoredTopologies() throws Exception {
        var loader = new WorkflowDefinitionLoader();
        for (var variant : List.of("full", "contract", "terminal-delivery"))
            assertDoesNotThrow(() -> loader.loadOrDefault(null, variant));
        var resource = getClass().getResource("/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json");
        assertNotNull(resource);
        assertDoesNotThrow(() -> loader.load(Path.of(resource.toURI())));
    }

    @Test
    void customDefinitionsCannotBypassContextOrMixDirectAndReviewedAuthority() throws Exception {
        var original = new WorkflowDefinitionLoader().loadOrDefault(null, "full");
        for (var mutation : List.of("context-target", "context-producer", "reviewer-role", "mixed-gate-source", "approval-fanout")) {
            var nodes = new ArrayList<>(original.nodes());
            var edges = new ArrayList<>(original.edges());
            if (mutation.equals("context-target") || mutation.equals("reviewer-role")) {
                for (int i = 0; i < nodes.size(); i++) {
                    var node = nodes.get(i);
                    if (mutation.equals("context-target") && node.id().equals("context.plan_codex_plan_review")) {
                        var config = new LinkedHashMap<>(node.config()); config.put("target_node", "design.codex_arch_review");
                        nodes.set(i, new NodeDefinition(node.id(), node.kind(), node.handlerType(), config));
                    } else if (mutation.equals("reviewer-role") && node.id().equals("plan.codex_plan_review"))
                        nodes.set(i, new NodeDefinition(node.id(), NodeKind.TASK, "context.build_package", node.config()));
                }
            } else if (mutation.equals("context-producer")) edges.add(new EdgeDefinition("design.claude_design", "completed", "context.plan_codex_plan_review"));
            else if (mutation.equals("mixed-gate-source")) edges.add(new EdgeDefinition("plan.claude_plan", "completed", "gate.human_plan_approval"));
            else edges.add(new EdgeDefinition("gate.human_design_approval", "approved", "implement.claude_implement"));
            var file = root.resolve(mutation + ".json");
            JSON.writeValue(file.toFile(), Map.of("workflowId", "custom", "version", 1,
                    "entryNodeId", original.entryNodeId(), "nodes", nodes, "edges", edges));
            assertThrows(java.io.IOException.class, () -> new WorkflowDefinitionLoader().load(file), mutation);
        }
    }
}
