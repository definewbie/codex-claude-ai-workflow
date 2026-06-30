package dev.dual.adapter.acpx;

import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcpxExecutionEventTest {

    @Test
    void serializesTypedKindToStableWireName() {
        var event = new AcpxExecutionEvent(
                AgentExecutionTimelineEventKind.TERMINAL_CONTROL_DETECTED,
                "stdout",
                3,
                Instant.parse("2026-06-30T00:00:00Z"),
                1200
        );

        var map = event.toMap();

        assertEquals("terminal_control_detected", map.get("kind"));
        assertEquals("stdout", map.get("source"));
        assertEquals(3L, map.get("sequence"));
        assertEquals("2026-06-30T00:00:00Z", map.get("observedAt"));
        assertEquals(1200L, map.get("elapsedMs"));
    }

    @Test
    void rejectsMissingKind() {
        assertThrows(IllegalArgumentException.class, () -> new AcpxExecutionEvent(
                null,
                "adapter",
                0,
                Instant.parse("2026-06-30T00:00:00Z"),
                0
        ));
    }
}
