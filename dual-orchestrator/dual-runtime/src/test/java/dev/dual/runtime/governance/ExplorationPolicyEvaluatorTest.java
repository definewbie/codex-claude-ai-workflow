package dev.dual.runtime.governance;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorationPolicyEvaluatorTest {
    @Test
    void blocksWhenIntakePerformsAnyDirectRead() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("README.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "intake.claude_brainstorm", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of(
                        "README.md",
                        "src/a.js",
                        "src/b.js",
                        "src/c.js",
                        "src/d.js"
                )
        );

        assertEquals("block", result.get("mode"));
        assertEquals("failed", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("DIRECT_READ_FORBIDDEN")
                && violations.toString().contains("READ_OUTSIDE_EXPLORATION_SCOPE"));
    }

    @Test
    void passesWhenIntakeUsesNoDirectReads() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("README.md", "docs/product.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "intake.claude_brainstorm", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of()
        );

        assertEquals("pass", result.get("status"));
        assertEquals(List.of(), result.get("violations"));
    }

    @Test
    void emptyCanonicalScopeDoesNotFlagReadsWhenNoAllowedScope() {
        // Direction B: reads are observation, not authority. When allowedDirectReadFiles is empty,
        // the out-of-scope check is not entered — reads are not flagged as violations.
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "design",
                "design.claude_design",
                "node-001",
                "iter-001",
                List.of(),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "design.claude_design", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("src/unapproved.java")
        );

        assertEquals("pass", result.get("status"),
                "empty allowedDirectReadFiles skips the out-of-scope check (Direction B: reads are observation)");
    }

    @Test
    void emptyCanonicalScopeExpansionNotCheckedWhenNoAllowedScope() {
        // Direction B: when allowedDirectReadFiles is empty, the out-of-scope check is skipped
        // entirely (reads are observation). Scope expansion requests have no effect on read authority.
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "design",
                "design.claude_design",
                "node-001",
                "iter-001",
                List.of(),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "design.claude_design", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("src/requested.java", "src/sibling.java"),
                List.of("direct_file_read"),
                List.of("src/requested.java")
        );

        assertEquals("pass", result.get("status"),
                "empty allowedDirectReadFiles skips out-of-scope check (Direction B: reads are observation)");
    }

    @Test
    void intakeScopeExpansionDoesNotPermitDirectReadWhenRunnerForbidsIt() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("README.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "intake.claude_brainstorm", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("README.md", "src/newEvidence.js"),
                List.of("direct_file_read"),
                List.of("src/newEvidence.js")
        );

        assertEquals("failed", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("DIRECT_READ_FORBIDDEN"));
    }

    @Test
    void scopeExpansionOnlyAllowsRequestedFiles() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("README.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "intake.claude_brainstorm", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("README.md", "docs/api.md", "src/secret.js"),
                List.of("direct_file_read"),
                List.of("docs/api.md"),
                Map.of("README.md", "doc", "docs/api.md", "documentation", "src/secret.js", "source")
        );

        assertEquals("failed", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("src/secret.js")
                && !violations.toString().contains("docs/api.md\"]"));
    }

    @Test
    void scopeExpansionPreventsBudgetFailureButDoesNotWaiveUnrequestedFiles() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "design",
                "design.claude_design",
                "node-001",
                "iter-001",
                List.of("README.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "design.claude_design", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("README.md", "docs/api.md", "docs/auth.md", "docs/roles.md"),
                List.of("direct_file_read"),
                List.of("docs/api.md", "docs/auth.md", "docs/roles.md"),
                Map.of("README.md", "doc", "docs/api.md", "documentation", "docs/auth.md", "documentation",
                        "docs/roles.md", "documentation")
        );

        assertEquals("pass", result.get("status"));
        assertEquals(List.of(), result.get("violations"));
    }

    @Test
    void designDirectReadBudgetExceededFailsInsteadOfWarns() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "design",
                "design.claude_design",
                "node-001",
                "iter-001",
                List.of("README.md", "docs/api.md", "docs/auth.md", "docs/roles.md", "src/a.js", "src/b.js"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "design.claude_design", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("README.md", "docs/api.md", "docs/auth.md", "docs/roles.md", "src/a.js", "src/b.js", "src/c.js"),
                List.of("direct_file_read"),
                List.of()
        );

        assertEquals("block", result.get("mode"));
        assertEquals("failed", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("DIRECT_READ_BUDGET_EXCEEDED")
                && violations.toString().contains("severity=block"));
    }

    @Test
    void warnsForIntakeImplementationLevelReadsBasedOnRepoMapKinds() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("README.md", "src/newEvidence.js"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "intake.claude_brainstorm", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("README.md", "src/newEvidence.js"),
                List.of("direct_file_read"),
                List.of(),
                Map.of("README.md", "doc", "src/newEvidence.js", "source")
        );

        assertEquals("failed", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("IMPLEMENTATION_LEVEL_EXPLORATION_IN_INTAKE"));
    }

    @Test
    void intakeDirectReadIsForbiddenEvenWhenRepoMapKindIsDocumentation() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("src/newEvidence.js"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "intake.claude_brainstorm", List.of())
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("src/newEvidence.js"),
                List.of("direct_file_read"),
                List.of(),
                Map.of("src/newEvidence.js", "doc")
        );

        assertEquals("failed", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("DIRECT_READ_FORBIDDEN"));
    }

    @Test
    void warnsWhenPreferredCapabilityWasAvailableButNotUsedBeforeBroadReads() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "design",
                "design.claude_design",
                "node-001",
                "iter-001",
                List.of("README.md", "docs/product.md", "docs/api.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "design.claude_design",
                        List.of("codegraph", "rtk"))
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("README.md", "docs/product.md", "docs/api.md"),
                List.of("direct_file_read"),
                List.of()
        );

        assertEquals("warn", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("PREFERRED_CAPABILITY_NOT_USED"));
    }

    @Test
    void preservesActualCapabilityUsageProviderAndSource() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "intake",
                "intake.claude_brainstorm",
                "node-001",
                "iter-001",
                List.of("README.md", "docs/product.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands("ws-001", "intake.claude_brainstorm",
                        List.of("codegraph"))
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("README.md", "docs/product.md"),
                List.of(),
                List.of(),
                Map.of(),
                List.of(Map.of("kind", "symbol_index", "provider", "codegraph", "source", "command_executable"))
        );

        assertEquals(List.of("symbol_index"), result.get("actualCapabilityKinds"));
        assertEquals(List.of(Map.of("kind", "symbol_index", "provider", "codegraph", "source", "command_executable")),
                result.get("actualCapabilityUsages"));
    }

    @Test
    void acceptanceOutsideScopeReadFailsInsteadOfWarns() {
        var plan = ExplorationPlan.fromScope(
                "ws-001",
                "acceptance",
                "acceptance.codex_verifier_review",
                "node-030",
                "iter-001",
                List.of("docs/diff-summary.md"),
                List.of(),
                AgentCapabilityCatalog.fromAvailableCommands(
                        "ws-001",
                        "acceptance.codex_verifier_review",
                        List.of("codegraph", "rtk")
                )
        );

        var result = ExplorationPolicyEvaluator.evaluate(
                plan.toDocument(),
                List.of("docs/diff-summary.md", "src/unplanned.js"),
                List.of("direct_file_read"),
                List.of(),
                Map.of("docs/diff-summary.md", "doc", "src/unplanned.js", "source")
        );

        assertEquals("block", result.get("mode"));
        assertEquals("failed", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("READ_OUTSIDE_EXPLORATION_SCOPE")
                && violations.toString().contains("block"));
    }
}
