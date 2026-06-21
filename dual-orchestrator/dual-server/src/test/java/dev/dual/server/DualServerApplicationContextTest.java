package dev.dual.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import java.nio.file.Path;

class DualServerApplicationContextTest {
    @TempDir
    Path tempDir;

    @Test
    void applicationContextStarts() {
        System.setProperty("dual.operator.registry.path", tempDir.resolve("operator-workspaces.json").toString());
        var application = new SpringApplication(DualServerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var ignored = application.run()) {
            // Context startup verifies Spring constructor wiring without opening a web port.
        } finally {
            System.clearProperty("dual.operator.registry.path");
        }
    }
}
