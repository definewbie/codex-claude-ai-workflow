package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResultEnvelopeValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final AgentResultEnvelopeParser parser = new AgentResultEnvelopeParser();
    private final AgentResultEnvelopeValidator validator = new AgentResultEnvelopeValidator();

    @Test
    void capturedV14ShapeParsesStrictlyButRejectsDuplicateTargetWithoutCorrection() throws Exception {
        var patch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch", "READY_FOR_PRODUCT_REVIEW", "revision",
                "node-prev:0123456789abcdef", "a".repeat(64),
                List.of(
                        obligation("CO-1", "AC-1", "src/main/resources/application.yml"),
                        obligation("CO-3", "C-12", "src/main/resources/application.yml")));
        var captured = new AgentResultEnvelope(
                1, "ws-1", "run-1", "intake.claude_brainstorm", patch);
        var parsed = parser.parse(JSON.writeValueAsString(captured));

        assertTrue(parsed.valid(), parsed.message());

        var result = validator.validate(
                parsed.envelope(), "ws-1", "run-1", ContractRegistry.forNode("intake.claude_brainstorm"));

        assertFalse(result.valid());
        assertEquals(List.of(
                "DUPLICATE_EVIDENCE_PATCH_TARGET: src/main/resources/application.yml"), result.errors());
        assertEquals(AgentResultEnvelopeValidator.CorrectionDisposition.NOT_REPAIRABLE,
                result.correctionDisposition());
        assertFalse(result.schemaCorrectionAllowed());

        var correctedPatch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                patch.artifactType(), patch.decision(), patch.summary(),
                patch.baseProposalId(), patch.baseProposalSha256(),
                List.of(
                        patch.obligationPatches().get(0),
                        obligation("CO-3", "C-12", null)));
        var corrected = validator.validate(
                new AgentResultEnvelope(1, "ws-1", "run-1", "intake.claude_brainstorm", correctedPatch),
                "ws-1", "run-1", ContractRegistry.forNode("intake.claude_brainstorm"));
        assertTrue(corrected.valid(), corrected.errors().toString());
    }

    private static AgentResultEnvelope.ObligationPatch obligation(
            String obligationId,
            String clauseId,
            String evidenceRef
    ) {
        return new AgentResultEnvelope.ObligationPatch(
                obligationId,
                List.of(clauseId),
                List.of(new AgentResultEnvelope.ClauseChange(
                        AgentResultEnvelope.ClauseChangeOperation.ADD,
                        AgentResultEnvelope.ClauseSection.CONSTRAINTS,
                        clauseId,
                        "text")),
                List.of(),
                evidenceRef == null ? List.of() : List.of(evidenceRef));
    }
}
