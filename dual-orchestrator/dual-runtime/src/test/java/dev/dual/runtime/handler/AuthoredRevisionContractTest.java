package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredRevisionContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void bothAuthorsRequireExplicitResponsesWithoutInventingAnEmptyRepair() throws Exception {
        for (var node : Set.of("design.claude_design", "plan.claude_plan")) {
            var envelope = example(node);
            assertTrue(envelope.path("result").path("revision_responses").isArray(), node);
            ((ObjectNode) envelope.get("result")).remove("revision_responses");
            var parsed = new AgentResultEnvelopeParser().parse(envelope.toString());
            assertTrue(parsed.valid(), parsed.message());
            var validation = validate(parsed.envelope(), node);
            assertFalse(validation.valid());
            assertFalse(validation.schemaCorrectionAllowed(), "missing history must not be repaired to []");
        }
    }

    @Test
    void honestUnaddressedResponseIsTypedButUnknownDispositionIsRejected() throws Exception {
        for (var node : Set.of("design.claude_design", "plan.claude_plan")) {
            var envelope = example(node);
            var response = ((ObjectNode) envelope.get("result")).putArray("revision_responses").addObject();
            response.put("obligation_id", "sha256:" + "a".repeat(64) + "#human-note:0")
                    .put("disposition", "NOT_ADDRESSED").put("evidence", "Requires a human trade-off.");
            var parsed = new AgentResultEnvelopeParser().parse(envelope.toString());
            assertTrue(parsed.valid(), parsed.message());
            assertTrue(validate(parsed.envelope(), node).valid());
            response.put("disposition", "CLOSED");
            assertFalse(new AgentResultEnvelopeParser().parse(envelope.toString()).valid());
        }
    }

    @Test
    void otherResultsCannotSmuggleAuthoredResponses() throws Exception {
        var envelope = example("design.codex_arch_review");
        ((ObjectNode) envelope.get("result")).putArray("revision_responses");
        assertFalse(new AgentResultEnvelopeParser().parse(envelope.toString()).valid());
    }

    private ObjectNode example(String node) throws Exception {
        var decision = node.equals("plan.claude_plan") ? "READY_FOR_PLAN_REVIEW" : "READY_FOR_ARCH_REVIEW";
        var prompt = AgentResultPromptContract.render("ws-authored", "run-authored", node, decision, Set.of(decision));
        return (ObjectNode) JSON.readTree(prompt.substring(prompt.indexOf('{')));
    }

    private AgentResultEnvelopeValidator.ValidationResult validate(AgentResultEnvelope envelope, String node) {
        return new AgentResultEnvelopeValidator().validate(envelope, "ws-authored", "run-authored",
                new NodeOutputContract(node, Set.of(), Set.of(envelope.result().decision()), Set.of(),
                        Set.of(envelope.result().artifactType())));
    }
}
