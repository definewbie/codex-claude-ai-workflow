package dev.dual.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerApiControllerTest {
    @Test
    void tickDelegatesToSchedulerService() throws Exception {
        var controller = new SchedulerApiController(new SchedulerServiceStub(), backgroundSchedulerStub());

        var response = controller.tick(Path.of("/tmp/work"), 3, 2);

        assertEquals("scheduler.tick", response.get("action"));
        assertEquals("3:2", response.get("limits"));
    }

    @Test
    void backgroundStatusDelegatesToBackgroundSchedulerService() {
        var controller = new SchedulerApiController(new SchedulerServiceStub(), backgroundSchedulerStub());

        var response = controller.backgroundStatus();

        assertEquals("scheduler.background.status", response.get("action"));
    }

    private static BackgroundSchedulerService backgroundSchedulerStub() {
        return new BackgroundSchedulerService(new SchedulerServiceStub(), false, "", 10, 1);
    }

    private static final class SchedulerServiceStub extends SchedulerService {
        SchedulerServiceStub() {
            super(null, null);
        }

        @Override
        public Map<String, Object> tick(Path workspaceRoot, int maxWorkstreams, int maxStepsPerWorkstream) {
            return Map.of(
                    "action", "scheduler.tick",
                    "workdir", workspaceRoot.toString(),
                    "limits", maxWorkstreams + ":" + maxStepsPerWorkstream
            );
        }
    }
}
