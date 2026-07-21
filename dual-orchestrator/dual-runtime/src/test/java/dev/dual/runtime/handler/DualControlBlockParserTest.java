package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualControlBlockParserTest {
    private final DualControlBlockParser parser = new DualControlBlockParser();

    @Test
    void rejectsTerminalUnclosedControlBlockEvenWhenDoneMarkerExists() {
        var result = parser.parse("""
                Design body.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Design is ready.

                [done] end_turn
                """);

        assertEquals("UNCLOSED_DUAL_CONTROL", result.errorCode());
    }

    @Test
    void rejectsUnclosedControlBlockWhenTextFollowsDoneMarker() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                summary: done

                [done] end_turn
                trailing text
                """);

        assertEquals("UNCLOSED_DUAL_CONTROL", result.errorCode());
    }

    @Test
    void acceptsTerminalControlBlockAfterOrphanFenceCloser() {
        var result = parser.parse("""
                <dual-artifact type="agent_output_contract" version="1">
                {"decision":"READY_FOR_ARCH_REVIEW"}
                </dual-artifact>
                ```

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Design is ready.
                ```
                """);

        assertTrue(result.valid());
        assertEquals("READY_FOR_ARCH_REVIEW", result.block().required("decision"));
        assertEquals("Design is ready.", result.block().required("summary"));
    }
}
