package dev.dual.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

class DualServerApplicationContextTest {
    @Test
    void applicationContextStarts() {
        var application = new SpringApplication(DualServerApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var ignored = application.run()) {
            // Context startup verifies Spring constructor wiring without opening a web port.
        }
    }
}
