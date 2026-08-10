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
        assertTrue(implementation.contains(
                "result.reread_reasons` item is a closed object with exactly these fields: "
                        + "[path, reason, stage, evidence]"));
        ReadReuseContractReasons.ALLOWED_REPEATED_READ_REASONS.forEach(
                reason -> assertTrue(implementation.contains(reason)));
    }

    @Test
    void promptProjectsClosedStructuredItemFieldsWithoutAddingAnotherJsonObject() {
        var intake = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.claude_brainstorm",
                "READY_FOR_PRODUCT_REVIEW", Set.of("READY_FOR_PRODUCT_REVIEW"));
        var productReview = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.codex_product_review",
                "NEEDS_REVISION", Set.of("READY_FOR_PRODUCT_REVIEW", "NEEDS_REVISION"));

        assertTrue(intake.contains(
                "result.revision_responses` item is a closed object with exactly these fields: "
                        + "[review_obligation_id, resolved_by_clause_ids]"));
        assertTrue(intake.contains(
                "copying its `id` and `text` character-for-character from the verified previous proposal"));
        assertTrue(intake.contains("including whitespace, punctuation, capitalization, and Markdown backticks"));
        assertTrue(intake.contains(
                "Formatting normalization is a modification and is prohibited unless that clause ID is declared"));
        assertTrue(intake.contains("do not delete existing clause IDs"));
        assertTrue(intake.contains(
                "`result.scope.included` is the exhaustive authorization set for permitted repository changes"));
        assertTrue(intake.contains(
                "Acceptance criteria, constraints, risks, and other prose cannot authorize an undeclared path"));
        assertTrue(intake.contains("Never describe the typed scope as illustrative or non-exhaustive"));
        assertTrue(intake.contains(
                "When a revision obligation identifies a scope gap, update the typed path-and-operation entries directly"));
        assertTrue(productReview.contains(
                "result.contract_obligations` item is a closed object with exactly these fields: [id, text]"));
        assertTrue(intake.contains(
                "Every JSON object is closed. Do not add keys that are not declared for that object."));
        assertTrue(intake.contains("\"revision_responses\" : [ ]"));

        var parsed = parser.parse(intake.substring(intake.indexOf('{')));
        assertTrue(parsed.valid(), "the contract prompt must contain only its final envelope JSON object");
    }

    @Test
    void nonIntakeAgentOutputPromptsKeepReviewObligationsEmpty() {
        var decisions = Map.of(
                "design.claude_design", "READY_FOR_ARCH_REVIEW",
                "design.codex_arch_review", "READY_FOR_ARCH_REVIEW",
                "plan.codex_plan_review", "READY_TO_IMPLEMENT",
                "implement.claude_implement", "IMPLEMENTED"
        );

        decisions.forEach((nodeId, decision) -> {
            var prompt = AgentResultPromptContract.render(
                    "ws-1", "run-1", nodeId, decision, Set.of(decision));
            assertTrue(prompt.contains(
                    "For this node, `result.contract_obligations` must remain exactly `[]`"), nodeId);
            assertTrue(prompt.contains("\"contract_obligations\" : [ ]"), nodeId);
        });
    }

    @Test
    void revisionResponseRejectsUndeclaredNoteButAcceptsTheClosedRecordShape() throws Exception {
        var intake = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.claude_brainstorm",
                "READY_FOR_PRODUCT_REVIEW", Set.of("READY_FOR_PRODUCT_REVIEW"));
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                intake.substring(intake.indexOf('{')));
        var responses = ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("result"))
                .putArray("revision_responses");
        var response = responses.addObject();
        response.put("review_obligation_id", "CO-1");
        response.putArray("resolved_by_clause_ids").add("AC-1");
        response.put("note", "undeclared rationale");

        assertFalse(parser.parse(objectMapper.writeValueAsString(envelope)).valid());

        response.remove("note");
        var parsed = parser.parse(objectMapper.writeValueAsString(envelope));
        assertTrue(parsed.valid());
        assertTrue(validator.validate(
                parsed.envelope(), "ws-1", "run-1", ContractRegistry.forNode("intake.claude_brainstorm"))
                .valid());
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

    @Test
    void acceptancePromptExplainsTypedVerificationRemediation() {
        var acceptance = AgentResultPromptContract.render(
                "ws-1", "run-1", "acceptance.codex_verifier_review",
                "REJECT", Set.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT"));

        assertTrue(acceptance.contains("verification_remediation"));
        assertTrue(acceptance.contains("EVIDENCE_COLLECTION"));
        assertTrue(acceptance.contains("TEST_CODE_CHANGE"));
    }
}
