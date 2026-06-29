package dev.dual.runtime.governance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorationPolicyEvaluatorTest {
    @Test
    void warnsWhenIntakePerformsImplementationLevelDirectReads() {
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

        assertEquals("warn", result.get("status"));
        assertTrue(result.get("violations") instanceof List<?> violations
                && violations.toString().contains("DIRECT_READ_BUDGET_EXCEEDED")
                && violations.toString().contains("READ_OUTSIDE_EXPLORATION_SCOPE"));
    }

    @Test
    void passesWhenReadsStayWithinPlan() {
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
                List.of("README.md", "docs/product.md")
        );

        assertEquals("pass", result.get("status"));
        assertEquals(List.of(), result.get("violations"));
    }
}
