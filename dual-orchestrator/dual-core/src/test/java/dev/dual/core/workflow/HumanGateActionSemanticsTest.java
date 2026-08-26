package dev.dual.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HumanGateActionSemanticsTest {
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
