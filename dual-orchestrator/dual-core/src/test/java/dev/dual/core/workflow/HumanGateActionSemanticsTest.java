package dev.dual.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HumanGateActionSemanticsTest {
    @Test
    void scopeRevisionIsAvailableOnlyForDeclaredScopeRepairOccurrences() {
        var action = "request_scope_revision";
        var repair = new NodeDefinition("gate.human_implementation_repair", NodeKind.GATE, "human.review",
                Map.of("gate_purpose", "implementation_repair", "allowed_actions", List.of("revise", action)));
        assertEquals(List.of("revise", action),
                HumanGateActionSemantics.allowedActions(repair, "CHANGE_SCOPE_VIOLATION"));
        for (var source : List.of("BLOCKED", "SELF_TEST_INCOMPLETE", "completed", "")) {
            assertEquals(List.of("revise"), HumanGateActionSemantics.allowedActions(repair, source));
        }
        var ordinary = new NodeDefinition("gate.design", NodeKind.GATE, "human.review",
                Map.of("allowed_actions", List.of("revise", action)));
        assertEquals(List.of("revise"),
                HumanGateActionSemantics.allowedActions(ordinary, "CHANGE_SCOPE_VIOLATION"));
        var undeclared = new NodeDefinition("gate.human_implementation_repair", NodeKind.GATE, "human.review",
                Map.of("gate_purpose", "implementation_repair", "allowed_actions", List.of("revise")));
        assertEquals(List.of("revise"),
                HumanGateActionSemantics.allowedActions(undeclared, "CHANGE_SCOPE_VIOLATION"));
    }

    @Test
    void usesTheLedgerFallbackForMissingEmptyAndNonListConfiguration() {
        assertEquals(List.of("approve", "revise"), actions(Map.of()));
        assertEquals(List.of("approve", "revise"), actions(Map.of("allowed_actions", List.of())));
        assertEquals(List.of("approve", "revise"), actions(Map.of("allowed_actions", "approve")));
        assertEquals(List.of("approve", "revise"), actions(Map.of(
                "allowed_actions", List.of("approve", "", "revise"))));
    }

    @Test
    void canonicalApproveCompatibilityIsDirectional() {
        var approvedEdge = List.of(new EdgeDefinition("gate", "approved", "next"));
        var approveEdge = List.of(new EdgeDefinition("gate", "approve", "next"));
        var bothEdges = List.of(
                new EdgeDefinition("gate", "approve", "next"),
                new EdgeDefinition("gate", "approved", "legacy-next"));

        assertEquals("approved", HumanGateActionSemantics.canonicalAction(approvedEdge, "approve"));
        assertEquals("approved", HumanGateActionSemantics.canonicalAction(approveEdge, "approved"));
        assertEquals("approve", HumanGateActionSemantics.canonicalAction(approveEdge, "approve"));
        assertEquals("approve", HumanGateActionSemantics.canonicalAction(bothEdges, "approve"));
    }

    private static List<String> actions(Map<String, Object> config) {
        return HumanGateActionSemantics.allowedActions(
                new NodeDefinition("gate", NodeKind.GATE, "human.review", config));
    }
}
