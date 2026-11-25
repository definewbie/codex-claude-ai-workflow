package dev.dual.runtime.handler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RevisionIdentityWireTest {
    static final String WIRE = """
        {"schema_version":1,"workstream_id":"ws","run_id":"run","node_id":"intake.claude_brainstorm",
         "result":{"artifact_type":"task_contract_revision_patch","decision":"READY_FOR_PRODUCT_REVIEW",
         "summary":"revise","base_proposal_id":"base","obligation_patches":[{"review_obligation_id":"CO-1",
         "resolved_by_clause_ids":["AC-1"],"clause_changes":[],"scope_additions":[],"evidence_ref_additions":[]}]}}
        """;
    @Test void idOnlyPatchParsesAndPassesShapeValidation() {
        var parsed=new AgentResultEnvelopeParser().parse(WIRE);
        assertTrue(parsed.valid(),parsed.message());
        var validated=new AgentResultEnvelopeValidator().validate(parsed.envelope(),"ws","run",ContractRegistry.forNode("intake.claude_brainstorm"));
        assertTrue(validated.valid(),validated.errors().toString());
    }
    @Test void oldModelShaKeyIsRejectedForBothHistoricalValues() {
        for (var sha : java.util.List.of(
                "517fc15b11bd16761e1c694d0625156981e8e515f239f6243186735ff00de94c",
                "517fc15b11bd16761e1c694d0625156981e8e515f237f6243186735ff00de94c")) {
            var parsed = new AgentResultEnvelopeParser().parse(WIRE.replace("\"base_proposal_id\":\"base\"",
                    "\"base_proposal_id\":\"base\",\"base_proposal_sha256\":\"" + sha + "\""));
            assertFalse(parsed.valid());
        }
    }
    @Test void patchAndEligibleFullInstructionsRequireIdWithoutShaTranscription() {
        var context = new AgentResultPromptContract.ProposalEmissionContext(
                AgentResultPromptContract.EmissionMode.REVISION_PATCH, "exact-base", "b".repeat(64),
                java.util.List.of(new AgentResultEnvelope.ReviewObligation("CO-1", "Clarify goal")));
        for (var full : java.util.List.of(false, true)) {
            var rendered = AgentResultPromptContract.render("ws", "run", "intake.claude_brainstorm",
                    "READY_FOR_PRODUCT_REVIEW", java.util.Set.of("READY_FOR_PRODUCT_REVIEW"), context.withGoalReproposal(full));
            assertTrue(rendered.contains("exact-base"));
            assertFalse(rendered.contains("b".repeat(64)));
            assertTrue(rendered.contains("never emit base_proposal_sha256"));
            assertFalse(rendered.substring(rendered.lastIndexOf("\n{") + 1).contains("\"base_proposal_sha256\""));
        }
    }
}
