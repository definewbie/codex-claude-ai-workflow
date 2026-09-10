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
    void selfTestRequiresExplicitProductIssuesWithoutAcceptanceFailureSchema() throws Exception {
        var prompt = AgentResultPromptContract.render("ws-1", "run-1",
                "implement.claude_self_test_report", "SELF_TEST_COMPLETE", Set.of("SELF_TEST_COMPLETE"));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
                prompt.substring(prompt.indexOf('{')));
        var result = (com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("result");
        assertTrue(result.has("must_fix"));
        assertFalse(result.has("failure"));
        result.putArray("missing_evidence").add("External verification missing");
        var parsed = parser.parse(mapper.writeValueAsString(envelope));
        assertTrue(parsed.valid(), parsed.message());
        assertFalse(validator.validate(parsed.envelope(), "ws-1", "run-1",
                ContractRegistry.forNode("implement.claude_self_test_report")).valid());
    }

    @Test
    void designDeclaresPlannedFilesInsteadOfGenericOutput() {
        var prompt = AgentResultPromptContract.render(
                "ws-1", "run-1", "design.claude_design",
                "READY_FOR_ARCH_REVIEW", Set.of("READY_FOR_ARCH_REVIEW"));
        var parsed = parser.parse(prompt.substring(prompt.indexOf('{')));
        assertTrue(parsed.valid(), parsed.message());
        assertTrue("design_result".equals(parsed.envelope().result().artifactType()));
        assertTrue(prompt.contains("planned_files"));
        assertTrue(prompt.contains("\"revision_responses\" : [ ]"));
        assertTrue(prompt.contains("NOT_ADDRESSED"));
    }

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
        assertTrue(intake.contains("Each scope path must name one exact file, not a directory"));
        assertTrue(intake.contains("must not end with `/`"));
        assertTrue(intake.contains("no `./`, `..`, repeated separators"));
        assertTrue(intake.contains(
                "Acceptance criteria, constraints, risks, and other prose cannot authorize an undeclared path"));
        assertTrue(intake.contains("Never describe the typed scope as illustrative or non-exhaustive"));
        assertTrue(intake.contains(
                "When a revision obligation identifies a scope gap, update the typed path entries directly"));
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
    void intakePromptDeclaresPathsWhileDualOwnsScopeOperations() {
        var intake = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.claude_brainstorm",
                "READY_FOR_PRODUCT_REVIEW", Set.of("READY_FOR_PRODUCT_REVIEW"));

        assertTrue(intake.contains("Dual derives CREATE/MODIFY from the effective worktree"));
        assertTrue(intake.contains("\"path\" : \"repo/relative/existing.ext\""));
        assertFalse(intake.contains("\"operation\""));
        assertFalse(intake.contains("new:repo/"));
    }

    @Test
    void intakeOldScopeOperationShapeIsRejectedAsUnknownInput() throws Exception {
        var intake = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.claude_brainstorm",
                "READY_FOR_PRODUCT_REVIEW", Set.of("READY_FOR_PRODUCT_REVIEW"));
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                intake.substring(intake.indexOf('{')));
        ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("result").path("scope")
                .path("included").get(0)).put("operation", "MODIFY");

        assertFalse(parser.parse(objectMapper.writeValueAsString(envelope)).valid());
    }

    @Test
    void acceptancePromptExplainsTypedVerificationRemediation() {
        var acceptance = AgentResultPromptContract.render(
                "ws-1", "run-1", "acceptance.codex_verifier_review",
                "REJECT", Set.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT"));

        assertTrue(acceptance.contains(
                "Allowed `result.failure.verification_remediation` values for this node: "
                        + "[EVIDENCE_COLLECTION, NONE, TEST_CODE_CHANGE]"));
        assertTrue(acceptance.contains("verification_remediation"));
        assertTrue(acceptance.contains("EVIDENCE_COLLECTION"));
        assertTrue(acceptance.contains("TEST_CODE_CHANGE"));
    }

    @Test
    void productReviewAllowsOnlyNoVerificationRemediation() {
        var review = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.codex_product_review",
                "NEEDS_REVISION", Set.of("BLOCKED", "NEEDS_REVISION", "READY_FOR_PRODUCT_REVIEW"));

        assertTrue(review.contains(
                "Allowed `result.failure.verification_remediation` values for this node: [NONE]"));
        assertTrue(review.contains("`result.failure.verification_remediation` must remain `NONE`"));
        assertFalse(review.contains("REVISE_PROPOSAL"));
    }

    @Test
    void nonAcceptanceResultsRejectKnownButUnauthorizedVerificationRemediation() throws Exception {
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var nodes = Map.of(
                "intake.codex_product_review", "NEEDS_REVISION",
                "plan.claude_plan", "READY_FOR_PLAN_REVIEW");

        nodes.forEach((nodeId, decision) -> {
            try {
                var prompt = AgentResultPromptContract.render(
                        "ws-1", "run-1", nodeId, decision, Set.of(decision));
                var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(
                        prompt.substring(prompt.indexOf('{')));
                ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("result").path("failure"))
                        .put("verification_remediation", "TEST_CODE_CHANGE");

                var parsed = parser.parse(objectMapper.writeValueAsString(envelope));
                assertTrue(parsed.valid(), parsed.message());
                var validation = validator.validate(
                        parsed.envelope(), "ws-1", "run-1", ContractRegistry.forNode(nodeId));

                assertFalse(validation.valid());
                assertTrue(validation.errors().contains(
                        "result.failure.verification_remediation must be NONE outside acceptance review"));
            } catch (Exception failure) {
                throw new AssertionError(nodeId, failure);
            }
        });
    }

    @Test
    void revisionPromptRendersOnlyLineageBoundPatchAndParsesStrictly() {
        var context = new AgentResultPromptContract.ProposalEmissionContext(
                AgentResultPromptContract.EmissionMode.REVISION_PATCH,
                "node-prev:0123456789abcdef",
                "a".repeat(64),
                java.util.List.of(
                        new AgentResultEnvelope.ReviewObligation("CO-1", "Fix behavior"),
                        new AgentResultEnvelope.ReviewObligation("CO-2", "Add scope")));

        var prompt = AgentResultPromptContract.render(
                "ws-1", "run-1", "intake.claude_brainstorm",
                "READY_FOR_PRODUCT_REVIEW", Set.of("READY_FOR_PRODUCT_REVIEW"), context);
        var parsed = parser.parse(prompt.substring(prompt.indexOf("{\n  \"schema_version\"")));

        assertTrue(parsed.valid(), parsed.message());
        assertTrue(parsed.envelope().result() instanceof AgentResultEnvelope.TaskContractRevisionPatchResult);
        assertTrue(prompt.contains("Return only `task_contract_revision_patch`"));
        assertTrue(prompt.contains("node-prev:0123456789abcdef"));
        assertTrue(prompt.contains("CO-1"));
        assertTrue(prompt.contains("CO-2"));
        assertTrue(prompt.contains("Fix behavior"));
        assertTrue(prompt.contains("Add scope"));
        assertTrue(prompt.contains("Each scope path must name one exact file, not a directory"));
        assertTrue(prompt.contains("must not end with `/`"));
        assertTrue(prompt.contains("no `./`, `..`, repeated separators"));
        assertTrue(prompt.contains("Across the entire `result.obligation_patches` array"));
        assertTrue(prompt.contains("each `clause_changes[].id`, `scope_additions[].path`, and "
                + "`evidence_ref_additions[]` target must appear at most once"));
        assertTrue(prompt.contains("use each obligation's `resolved_by_clause_ids` to express coverage"));
        assertFalse(prompt.contains("\"revision_responses\""));
        assertFalse(prompt.contains("copying its `id` and `text` character-for-character"));
        assertTrue(validator.validate(
                parsed.envelope(), "ws-1", "run-1", ContractRegistry.forNode("intake.claude_brainstorm"))
                .valid());
    }
}
