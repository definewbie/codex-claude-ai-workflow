package dev.dual.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationServiceTest {
    @Test
    void classifiesSchedulerBoundaries() {
        assertEquals("gate", NotificationService.eventFor(Map.of(
                "workstreamId", "ws-gate",
                "status", "WAITING_FOR_HUMAN"
        )));
        assertEquals("failure", NotificationService.eventFor(Map.of(
                "workstreamId", "ws-recovery",
                "status", "WAITING_FOR_RECOVERY"
        )));
        assertEquals("completion", NotificationService.eventFor(Map.of(
                "workstreamId", "ws-done",
                "status", "COMPLETED"
        )));
    }

    @Test
    void readsSkippedReasonAsEffectiveStatus() {
        assertEquals("gate", NotificationService.eventFor(Map.of(
                "workstreamId", "ws-gate",
                "result", "skipped",
                "reason", "WAITING_FOR_HUMAN"
        )));
    }

    @Test
    void disabledNotificationDoesNotExecuteCommand() {
        var service = NotificationService.disabled();

        var result = service.notifySchedulerResult(Path.of("/tmp"), Map.of(
                "workstreams", List.of(Map.of(
                        "workstreamId", "ws-gate",
                        "status", "WAITING_FOR_HUMAN"
                ))
        ));

        assertEquals(false, result.get("enabled"));
        assertEquals(0, result.get("sent"));
        assertEquals(0, result.get("failed"));
    }
}
