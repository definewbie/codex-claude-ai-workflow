package dev.dual.cli;

import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRunCommandTest {
    @Test
    void autorunApprovesIntermediateHumanGates() {
        assertEquals("approved", AutoRunCommand.gateActionFor("gate.human_requirement_review"));
        assertEquals("approved", AutoRunCommand.gateActionFor("gate.contract_review"));
        assertEquals("approved", AutoRunCommand.gateActionFor("gate.human_acceptance"));
    }

    @Test
    void autorunCompletesFinalNextStageGate() {
        assertEquals("complete", AutoRunCommand.gateActionFor("gate.human_next_stage_approval"));
    }

    @Test
    void autorunStopsWhenAGateRepeatsInTheSameRun() {
        var seen = new LinkedHashSet<String>();
        seen.add("gate.contract_review@iter-001");

        assertTrue(AutoRunCommand.isRepeatedGate(gate("gate.contract_review", "iter-001"), seen));
        assertFalse(AutoRunCommand.isRepeatedGate(gate("gate.human_design_approval", "iter-001"), seen));
    }

    @Test
    void autorunAllowsSameGateDefinitionInANewRepairIteration() {
        var seen = new LinkedHashSet<String>();
        seen.add("gate.contract_review@iter-001");

        assertFalse(AutoRunCommand.isRepeatedGate(gate("gate.contract_review", "iter-002"), seen));
    }

    private static NodeInstanceDocument gate(String nodeDefinitionId) {
        return gate(nodeDefinitionId, "iter-001");
    }

    private static NodeInstanceDocument gate(String nodeDefinitionId, String iterationId) {
        return new NodeInstanceDocument(
                1,
                "node-" + nodeDefinitionId.replace('.', '-'),
                nodeDefinitionId,
                NodeKind.GATE,
                "human.review",
                NodeStatus.WAITING,
                "gate",
                iterationId,
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );
    }
}
