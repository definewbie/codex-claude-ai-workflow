package dev.dual.runtime.governance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorationPlanTest {
    @Test
    void intakePlanDisablesDirectReadsAndFramesDiscoveryAsContractScopeNotImplementation() {
        var catalog = AgentCapabilityCatalog.fromAvailableCommands(
                "ws-001",
                "intake.claude_brainstorm",
                Set.of("rtk", "codegraph"),
                false,
                false
        );

        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("README.md", "docs/product.md"),
                List.of(),
                catalog
        );

        var document = plan.toDocument();

        assertEquals("agent_exploration_plan", document.get("artifactType"));
        assertEquals("contract_discovery", document.get("explorationIntent"));
        assertEquals("block", document.get("failureMode"));
        assertEquals(0, ((Number) document.get("maxDirectReadFiles")).intValue());
        assertTrue(document.get("preferredCapabilityKinds") instanceof List<?> kinds
                && !kinds.contains("symbol_index")
                && !kinds.contains("token_filtered_shell")
                && !kinds.contains("direct_file_read"));
        assertTrue(document.get("forbiddenExplorationIntents") instanceof List<?> forbidden
                && forbidden.contains("implementation_design")
                && forbidden.contains("full_repo_audit"));
    }

    @Test
    void designPlanBlocksBroadExplorationInsteadOfOnlyWarning() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "design",
                "design.claude_design",
                "node-009",
                "iter-001",
                List.of("docs/product.md", "src/app.js"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands(
                        "ws-001",
                        "design.claude_design",
                        Set.of("rtk", "codegraph"),
                        true,
                        false
                )
        );

        var document = plan.toDocument();

        assertEquals("architecture_discovery", document.get("explorationIntent"));
        assertEquals("block", document.get("failureMode"));
        assertEquals(6, ((Number) document.get("maxDirectReadFiles")).intValue());
    }

    @Test
    void implementPlanAllowsEditExecutionButStillKeepsWarnMode() {
        var catalog = AgentCapabilityCatalog.fromAvailableCommands(
                "ws-001",
                "implement.claude_implement",
                Set.of("rtk")
        );

        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "implement",
                "implement.claude_implement",
                "node-010",
                "iter-001",
                List.of("src/app.js", "src/app.test.js"),
                List.of("src/app.js"),
                catalog
        );

        var document = plan.toDocument();

        assertEquals("edit_execution", document.get("explorationIntent"));
        assertEquals("warn", document.get("failureMode"));
        assertEquals(List.of("src/app.js"), document.get("expectedEditFiles"));
    }

    @Test
    void acceptancePlanUsesBlockModeForIncrementalReviewScope() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "acceptance",
                "acceptance.codex_verifier_review",
                "node-020",
                "iter-001",
                List.of("docs/diff-summary.md", "src/app.js"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands(
                        "ws-001",
                        "acceptance.codex_verifier_review",
                        Set.of("rtk", "codegraph")
                )
        );

        var document = plan.toDocument();

        assertEquals("incremental_review", document.get("explorationIntent"));
        assertEquals("block", document.get("failureMode"));
        assertEquals(4, ((Number) document.get("maxDirectReadFiles")).intValue());
        assertTrue(document.get("forbiddenExplorationIntents") instanceof List<?> forbidden
                && forbidden.contains("full_repo_audit"));
    }
}
