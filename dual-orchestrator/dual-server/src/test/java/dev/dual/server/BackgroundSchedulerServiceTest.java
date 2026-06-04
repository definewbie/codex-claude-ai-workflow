package dev.dual.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackgroundSchedulerServiceTest {
    @Test
    void disabledSchedulerSkipsWithoutCallingTick() {
        var tickCalls = new AtomicInteger();
        var service = new BackgroundSchedulerService(
                new SchedulerServiceStub(tickCalls),
                false,
                "/tmp/ws-a",
                10,
                1
        );

        var result = service.runOnce();

        assertEquals("skipped", result.get("status"));
        assertEquals("disabled", result.get("reason"));
        assertEquals(0, tickCalls.get());
    }

    @Test
    void enabledSchedulerTicksConfiguredWorkdirs() {
        var tickCalls = new AtomicInteger();
        var service = new BackgroundSchedulerService(
                new SchedulerServiceStub(tickCalls),
                true,
                "/tmp/ws-a,/tmp/ws-b",
                3,
                2
        );

        var result = service.runOnce();

        assertEquals("completed", result.get("status"));
        assertEquals(2, tickCalls.get());
        assertEquals(2, result.get("resumed"));
        assertEquals(4, result.get("skipped"));
    }

    @Test
    void statusShowsLastRun() {
        var service = new BackgroundSchedulerService(
                new SchedulerServiceStub(new AtomicInteger()),
                false,
                "",
                10,
                1
        );

        service.runOnce();
        var status = service.status();

        assertEquals("scheduler.background.status", status.get("action"));
        assertEquals(false, status.get("enabled"));
        assertEquals("skipped", ((Map<?, ?>) status.get("lastRun")).get("status"));
    }

    private static final class SchedulerServiceStub extends SchedulerService {
        private final AtomicInteger calls;

        SchedulerServiceStub(AtomicInteger calls) {
            super(null, null);
            this.calls = calls;
        }

        @Override
        public Map<String, Object> tick(Path workspaceRoot, int maxWorkstreams, int maxStepsPerWorkstream) {
            calls.incrementAndGet();
            var response = new LinkedHashMap<String, Object>();
            response.put("action", "scheduler.tick");
            response.put("workdir", workspaceRoot.toString());
            response.put("resumed", 1);
            response.put("skipped", 2);
            response.put("limits", maxWorkstreams + ":" + maxStepsPerWorkstream);
            return response;
        }
    }
}
