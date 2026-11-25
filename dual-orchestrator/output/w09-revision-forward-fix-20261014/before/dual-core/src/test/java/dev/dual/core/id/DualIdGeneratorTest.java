package dev.dual.core.id;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DualIdGeneratorTest {
    @Test
    void createsReadableUtcIdsWithTypePrefix() {
        var generator = new DualIdGenerator(Clock.fixed(Instant.parse("2026-05-22T10:15:00Z"), ZoneOffset.UTC));

        assertTrue(generator.workstreamId().value().matches("ws-20260522T101500Z-[23456789A-Z]{4}"));
        assertTrue(generator.workspaceId().value().matches("wsp-20260522T101500Z-[23456789A-Z]{4}"));
        assertTrue(generator.runId().value().matches("run-20260522T101500Z-[23456789A-Z]{4}"));
    }
}
