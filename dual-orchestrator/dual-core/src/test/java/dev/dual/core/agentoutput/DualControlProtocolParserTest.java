package dev.dual.core.agentoutput;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualControlProtocolParserTest {
    private final DualControlProtocolParser parser = new DualControlProtocolParser();
    private final DualControlTerminalPolicy terminalPolicy = new DualControlTerminalPolicy();

    @Test
    void canonicalBlockIsValidAndTerminalWhenMarkerFollowsIt() {
        var result = parser.parse("""
                Body.
                ```dual-control
                decision: READY
                summary: done
                ```
                [done] end_turn
                """);

        assertTrue(result.valid());
        assertTrue(terminalPolicy.isComplete(result));
        assertEquals("READY", result.block().values().get("decision"));
        assertEquals("```dual-control\ndecision: READY\nsummary: done\n```",
                result.source().substring(result.controlSpan().start(), result.controlSpan().endExclusive()));
    }

    @Test
    void duplicateKeyIsInvalidForEveryConsumer() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                decision: PASS
                summary: done
                ```
                [done] end_turn
                """);

        assertEquals("DUPLICATE_DUAL_CONTROL_KEY", result.errorCode());
        assertFalse(terminalPolicy.isComplete(result));
    }

    @Test
    void malformedLineIsInvalidForEveryConsumer() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                malformed
                summary: done
                ```
                [done] end_turn
                """);

        assertEquals("MALFORMED_DUAL_CONTROL_LINE", result.errorCode());
        assertFalse(terminalPolicy.isComplete(result));
    }

    @Test
    void quotedAndExampleBlocksAreIgnored() {
        var result = parser.parse("""
                > ```dual-control
                > decision: FAKE
                > summary: quoted
                > ```

                ```markdown
                ```dual-control
                decision: EXAMPLE
                summary: nested
                ```
                ```
                """);

        assertTrue(result.isMissing());
    }

    @Test
    void unclosedFenceIsInvalid() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                summary: done
                [done] end_turn
                """);

        assertEquals("UNCLOSED_DUAL_CONTROL", result.errorCode());
        assertFalse(terminalPolicy.isComplete(result));
    }

    @Test
    void doneMarkerInsideFenceIsNotTerminal() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                summary: done
                [done] end_turn
                ```
                """);

        assertEquals("MALFORMED_DUAL_CONTROL_LINE", result.errorCode());
        assertFalse(terminalPolicy.isComplete(result));
    }

    @Test
    void blockCanBeValidWithoutBeingTerminal() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                summary: done
                ```
                """);

        assertTrue(result.valid());
        assertFalse(terminalPolicy.isComplete(result));
    }

    @Test
    void lastTopLevelBlockIsCanonical() {
        var result = parser.parse("""
                ```dual-control
                decision: OLD
                summary: old
                ```

                ```dual-control
                decision: NEW
                summary: new
                ```
                [done] end_turn
                """);

        assertTrue(result.valid());
        assertEquals("NEW", result.block().values().get("decision"));
        assertEquals(2, result.candidateSpans().size());
        assertTrue(terminalPolicy.isComplete(result));
    }

    @Test
    void markerBeforeControlDoesNotCompleteTurn() {
        var result = parser.parse("""
                [done] end_turn
                ```dual-control
                decision: READY
                summary: done
                ```
                """);

        assertTrue(result.valid());
        assertFalse(terminalPolicy.isComplete(result));
    }

    @Test
    void contentAfterMarkerPreventsTerminalCompletion() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                summary: done
                ```
                [done] end_turn
                [client] session/prompt (completed)
                """);

        assertTrue(result.valid());
        assertFalse(terminalPolicy.isComplete(result));
    }

    @Test
    void indentedMarkerIsNotCanonical() {
        var result = parser.parse("""
                ```dual-control
                decision: READY
                summary: done
                ```
                  [done] end_turn
                """);

        assertTrue(result.valid());
        assertFalse(terminalPolicy.isComplete(result));
    }
}
