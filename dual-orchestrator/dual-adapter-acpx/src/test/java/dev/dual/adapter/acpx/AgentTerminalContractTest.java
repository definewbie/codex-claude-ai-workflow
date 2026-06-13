package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTerminalContractTest {
    private final AgentTerminalContract contract = new AgentTerminalContract();

    @Test
    void requiresDoneMarkerAndTerminalControlBlock() {
        assertTrue(contract.isComplete("""
                Body.

                ```dual-control
                decision: READY
                summary: done
                ```

                [done] end_turn
                """));
    }

    @Test
    void rejectsOutputWithoutDoneMarker() {
        assertFalse(contract.isComplete("""
                ```dual-control
                decision: READY
                summary: done
                ```
                """));
    }

    @Test
    void rejectsControlBlockWithoutRequiredFields() {
        assertFalse(contract.isComplete("""
                ```dual-control
                decision: READY
                ```

                [done] end_turn
                """));
    }
}
