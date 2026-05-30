package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenEstimatorTest {

    @Test
    void defaultEstimator_4charsPerToken() {
        var est = TokenEstimator.defaultEstimator();
        assertEquals(0, est.estimate(""));
        assertEquals(0, est.estimate(null));
        assertEquals(1, est.estimate("Hi"));
        assertEquals(1, est.estimate("abcd"));
        assertEquals(2, est.estimate("abcde"));
        assertEquals(3, est.estimate("abcdefghijkl"));
    }

    @Test
    void customRatio() {
        var est = TokenEstimator.withRatio(2);
        assertEquals(2, est.estimate("abcd"));
        assertEquals(1, est.estimate("ab"));
    }

    @Test
    void rejectsNonPositiveRatio() {
        assertThrows(IllegalArgumentException.class, () -> TokenEstimator.withRatio(0));
        assertThrows(IllegalArgumentException.class, () -> TokenEstimator.withRatio(-1));
    }

    @Test
    void estimateFile_readsContent(@TempDir Path tempDir) throws Exception {
        var est = TokenEstimator.defaultEstimator();
        var file = tempDir.resolve("test.txt");
        Files.writeString(file, "a".repeat(80), StandardCharsets.UTF_8);
        assertEquals(20, est.estimateFile(file));
    }

    @Test
    void estimateFile_missingFileReturnsZero() {
        var est = TokenEstimator.defaultEstimator();
        assertEquals(0, est.estimateFile(Path.of("/nonexistent/file.txt")));
    }
}
