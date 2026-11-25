package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredRevisionContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FIRST = "sha256:" + "a".repeat(64) + "#must_fix:0";
    private static final String SECOND = "sha256:" + "b".repeat(64) + "#human-note:0";
    private static final String OLD = "sha256:" + "c".repeat(64) + "#human-note:0";
    private static final List<String> ACTIVE = List.of(FIRST, SECOND);

    @Test
    void bothAuthorsRepairOnlyMalformedSlotsFromTheCurrentActiveSet() throws Exception {
        for (var node : List.of("design.claude_design", "plan.claude_plan")) {
            for (var ids : List.of(List.of(FIRST, SECOND + ".old"),
                    List.of(FIRST.replace("a".repeat(64), "a".repeat(63)), SECOND.replace("b".repeat(64), "b".repeat(65))))) {
                var original = authored(node, ids);
                var result = validateWithActive(original, node, ACTIVE);
                assertFalse(result.valid());
                assertTrue(result.schemaCorrectionAllowed(), result.errors().toString());
                assertEquals(ids.getFirst().equals(FIRST) ? 1 : 2, result.correctionTargets().size());
                assertTrue(result.correctionTargets().stream().allMatch(target -> target.path().endsWith(".obligation_id")));
                assertTrue(result.errors().stream().anyMatch(error -> error.contains("index=1") && error.contains("length=")));
                var corrected = authored(node, ACTIVE);
                assertTrue(correct(original, corrected, node, ACTIVE).valid());
                assertFalse(validate(parsed(original), node).schemaCorrectionAllowed(),
                        "without trusted active IDs the old entry point remains strict");
            }
        }
    }

    @Test
    void referenceCorrectionCannotChangeAnyOtherValueOrReassignValidIds() throws Exception {
        for (var node : List.of("design.claude_design", "plan.claude_plan")) {
            var original = authored(node, List.of(FIRST, SECOND + ".old"));
            var mutations = new ArrayList<ObjectNode>();
            var changedEvidence = authored(node, ACTIVE);
            response(changedEvidence, 1).put("evidence", "Invented repair"); mutations.add(changedEvidence);
            var disposition = authored(node, ACTIVE);
            response(disposition, 1).put("disposition", "NOT_ADDRESSED"); mutations.add(disposition);
            var summary = authored(node, ACTIVE);
            ((ObjectNode) summary.get("result")).put("summary", "Different conclusion"); mutations.add(summary);
            var reordered = authored(node, ACTIVE);
            var rows = (ArrayNode) reordered.at("/result/revision_responses");
            var first = rows.remove(0); rows.add(first); mutations.add(reordered);
            mutations.add(authored(node, List.of(SECOND, FIRST)));
            mutations.add(authored(node, List.of(FIRST)));
            mutations.add(authored(node, List.of(FIRST, SECOND, OLD)));
            for (var changed : mutations) {
                assertFalse(correct(original, changed, node, ACTIVE).valid(), changed.toString());
            }
            var missingChecks = original.deepCopy();
            ((ObjectNode) missingChecks.get("result")).remove("checks");
            assertFalse(validateWithActive(missingChecks, node, ACTIVE).schemaCorrectionAllowed(),
                    "reference repair must not combine with unrelated nullable-field repair");
        }
    }

    @Test
    void incompleteOrAmbiguousOriginalResponsesDoNotBecomeReferenceRepairable() throws Exception {
        for (var node : List.of("design.claude_design", "plan.claude_plan")) {
            for (var ids : List.of(List.<String>of(), List.of(FIRST), List.of(FIRST, FIRST),
                    List.of(OLD, SECOND + ".old"), List.of(FIRST, ""))) {
                assertFalse(validateWithActive(authored(node, ids), node, ACTIVE).schemaCorrectionAllowed(), ids.toString());
            }
            for (var field : List.of("obligation_id", "disposition", "evidence")) {
                var missing = authored(node, List.of(FIRST, SECOND + ".old"));
                response(missing, 1).remove(field);
                assertFalse(validateWithActive(missing, node, ACTIVE).schemaCorrectionAllowed(), field);
            }
            var missing = authored(node, ACTIVE);
            ((ObjectNode) missing.get("result")).remove("revision_responses");
            assertFalse(validateWithActive(missing, node, ACTIVE).schemaCorrectionAllowed());
            var malformed = authored(node, List.of(FIRST, SECOND + ".old"));
            for (var active : List.of(List.<String>of(), List.of(FIRST, FIRST), List.of(FIRST, "bad")))
                assertFalse(validateWithActive(malformed, node, active).schemaCorrectionAllowed());
        }
    }

    @Test
    void repairedCoverageRejectsStaleDuplicateOrStillMalformedReferences() throws Exception {
        for (var node : List.of("design.claude_design", "plan.claude_plan")) {
            var original = authored(node, List.of(FIRST, SECOND + ".old"));
            for (var ids : List.of(List.of(FIRST, OLD), List.of(FIRST, FIRST), List.of(FIRST, SECOND + ".old")))
                assertFalse(correct(original, authored(node, ids), node, ACTIVE).valid(), ids.toString());
            var staleButWellFormed = authored(node, List.of(FIRST, OLD));
            assertTrue(validateWithActive(staleButWellFormed, node, ACTIVE).valid(),
                    "ordinary coverage remains a publication check, not a new correction trigger");
            assertFalse(AuthoredRevisionValidator.validate(List.of(
                    new AgentResultEnvelope.AuthoredRevisionResponse(FIRST, AgentResultEnvelope.AuthoredRevisionDisposition.ADDRESSED, "A"),
                    new AgentResultEnvelope.AuthoredRevisionResponse(OLD, AgentResultEnvelope.AuthoredRevisionDisposition.ADDRESSED, "B")), ACTIVE).isEmpty());
        }
    }

    private ObjectNode authored(String node, List<String> ids) throws Exception {
        var result = example(node);
        var rows = ((ObjectNode) result.get("result")).putArray("revision_responses");
        for (int i = 0; i < ids.size(); i++) rows.addObject().put("obligation_id", ids.get(i))
                .put("disposition", "ADDRESSED").put("evidence", "Response content " + i);
        return result;
    }

    private ObjectNode response(ObjectNode envelope, int index) {
        return (ObjectNode) envelope.at("/result/revision_responses/" + index);
    }

    private AgentResultEnvelope parsed(ObjectNode value) {
        var parsed = new AgentResultEnvelopeParser().parse(value.toString());
        assertTrue(parsed.valid(), parsed.message());
        return parsed.envelope();
    }

    private NodeOutputContract contract(String node, AgentResultEnvelope envelope) {
        return new NodeOutputContract(node, Set.of(), Set.of(envelope.result().decision()), Set.of(),
                Set.of(envelope.result().artifactType()));
    }

    private AgentResultEnvelopeValidator.ValidationResult validateWithActive(ObjectNode value, String node, List<String> active) {
        var envelope = parsed(value);
        return new AgentResultEnvelopeValidator().validate(envelope, "ws-authored", "run-authored", contract(node, envelope), active);
    }

    private AgentResultEnvelopeValidator.ValidationResult correct(ObjectNode original, ObjectNode corrected, String node, List<String> active) {
        var envelope = parsed(original);
        var initial = validateWithActive(original, node, active);
        assertTrue(initial.schemaCorrectionAllowed(), "negative correction cases must start with a repairable original: " + initial.errors());
        return new AgentResultEnvelopeValidator().validateCorrection(envelope, parsed(corrected), "ws-authored", "run-authored",
                contract(node, envelope), initial, active);
    }

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
