package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IterationRefTest {
    @Test
    void formatsIterationIdAndDirectoryName() {
        var ref = IterationRef.ofNumber(2);

        assertEquals(2, ref.number());
        assertEquals("iter-002", ref.id());
        assertEquals("002", ref.dirName());
        assertEquals("002", ref.artifactSuffix());
    }

    @Test
    void parsesIterationIdAndComputesNextIteration() {
        var ref = IterationRef.parse("iter-009").next();

        assertEquals(10, ref.number());
        assertEquals("iter-010", ref.id());
        assertEquals("010", ref.dirName());
    }

    @Test
    void rejectsInvalidIterationId() {
        assertThrows(IllegalArgumentException.class, () -> IterationRef.parse("001"));
    }
}
