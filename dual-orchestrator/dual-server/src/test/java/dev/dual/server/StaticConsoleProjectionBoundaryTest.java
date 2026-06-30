package dev.dual.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticConsoleProjectionBoundaryTest {
    @Test
    void staticConsolesDoNotInferPrimaryAttentionFromWorkflowStatus() throws Exception {
        var console = Files.readString(Path.of("src/main/resources/static/console.js"));
        var mgt = Files.readString(Path.of("src/main/resources/static/mgt/app.js"));

        assertTrue(console.contains("overview.whyStopped"));
        assertTrue(console.contains("overview.nextAction"));
        assertTrue(mgt.contains("data.whyStopped"));
        assertTrue(mgt.contains("data.nextAction"));

        assertFalse(console.contains("friendlyWhy("),
                "primary console must consume WorkstreamAttention via API, not infer whyStopped from status");
        assertFalse(console.contains("friendlyNext("),
                "primary console must consume WorkstreamAttention via API, not infer nextAction from status");
        assertFalse(mgt.contains("friendlyWhyStopped("),
                "management console must consume WorkstreamAttention via API, not infer whyStopped from status");
        assertFalse(mgt.contains("friendlyNextAction("),
                "management console must consume WorkstreamAttention via API, not infer nextAction from status");
    }
}
