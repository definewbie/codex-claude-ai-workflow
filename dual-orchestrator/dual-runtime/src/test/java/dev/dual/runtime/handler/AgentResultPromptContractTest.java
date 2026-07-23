package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResultPromptContractTest {
    private final AgentResultEnvelopeParser parser = new AgentResultEnvelopeParser();
    private final AgentResultEnvelopeValidator validator = new AgentResultEnvelopeValidator();

    @Test
    void everyAgentNodePromptExampleIsStrictAndMatchesItsNodeContract() {
        var decisions = Map.ofEntries(
                Map.entry("intake.claude_brainstorm", "READY_FOR_PRODUCT_REVIEW"),
                Map.entry("intake.codex_product_review", "READY_FOR_PRODUCT_REVIEW"),
                Map.entry("design.claude_design", "READY_FOR_ARCH_REVIEW"),
                Map.entry("design.codex_arch_review", "READY_FOR_ARCH_REVIEW"),
                Map.entry("plan.claude_plan", "READY_FOR_PLAN_REVIEW"),
                Map.entry("plan.codex_plan_review", "READY_TO_IMPLEMENT"),
                Map.entry("implement.claude_implement", "IMPLEMENTED"),
                Map.entry("implement.claude_self_test_report", "SELF_TEST_COMPLETE"),
                Map.entry("acceptance.codex_verifier_review", "ACCEPT"),
                Map.entry("stage.claude_handoff", "HANDOFF_READY")
        );

        decisions.forEach((nodeId, decision) -> {
            var contractText = AgentResultPromptContract.render(
                    "ws-1", "run-1", nodeId, decision, Set.of(decision));
            assertFalse(contractText.contains("dual-control"));
            assertFalse(contractText.contains("dual-artifact"));
            var parsed = parser.parse(contractText.substring(contractText.indexOf('{')));
            assertTrue(parsed.valid(), () -> nodeId + ": " + parsed.message());
            var validation = validator.validate(parsed.envelope(), "ws-1", "run-1", ContractRegistry.forNode(nodeId));
            assertTrue(validation.valid(), () -> nodeId + ": " + validation.errors());
        });
    }

    @Test
    void promptMakesNodeSpecificRereadReasonShapeUnambiguous() {
        var productReview = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.codex_product_review",
                "READY_FOR_PRODUCT_REVIEW", Set.of("READY_FOR_PRODUCT_REVIEW"));
        var implementation = AgentResultPromptContract.render(
                "ws-1", "run-1", "implement.claude_implement", "IMPLEMENTED", Set.of("IMPLEMENTED"));

        assertTrue(productReview.contains("reread_reasons` must remain exactly `[]`"));
        assertTrue(implementation.contains("reread_reasons` entry must be an object"));
        ReadReuseContractReasons.ALLOWED_REPEATED_READ_REASONS.forEach(
                reason -> assertTrue(implementation.contains(reason)));
    }

    @Test
    void intakePromptUsesTypedScopeEntriesForModifyAndCreate() {
        var intake = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.claude_brainstorm",
                "READY_FOR_PRODUCT_REVIEW", Set.of("READY_FOR_PRODUCT_REVIEW"));

        assertTrue(intake.contains("\"operation\" : \"MODIFY\""));
        assertTrue(intake.contains("\"operation\" : \"CREATE\""));
        assertFalse(intake.contains("new:repo/"));
    }
}
