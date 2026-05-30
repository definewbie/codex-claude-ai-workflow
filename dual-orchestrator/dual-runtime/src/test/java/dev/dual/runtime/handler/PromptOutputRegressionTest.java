package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptOutputRegressionTest {
    private static final String SAMPLES_ROOT = "dev/dual/runtime/handler/samples/";
    private final DualControlBlockParser parser = new DualControlBlockParser();

    // --- DualControlBlockParser: all fixtures parse successfully ---

    @ParameterizedTest
    @CsvSource({
        "codex-review-pass, PASS",
        "codex-review-needs-revision, NEEDS_REVISION",
        "codex-review-blocked, BLOCKED",
        "claude-design-ready, READY_FOR_ARCH_REVIEW",
        "claude-implement-ready, READY_FOR_ACCEPTANCE"
    })
    void fixtureParsesWithExpectedDecision(String sampleName, String expectedDecision) {
        var markdown = loadSample(sampleName);
        var result = parser.parse(markdown);

        assertTrue(result.valid(), sampleName + " should parse as valid, got: " + result.errorCode());
        assertEquals(expectedDecision, result.block().required("decision"));
        assertFalse(result.block().required("summary").isBlank(),
                sampleName + " summary should not be blank");
    }

    // --- Terminal block rule: only the last dual-control block counts ---

    @Test
    void multipleBlocks_onlyTerminalBlockParsed() {
        var markdown = loadSample("multiple-blocks-terminal-wins");
        var result = parser.parse(markdown);

        assertTrue(result.valid());
        assertEquals("PASS", result.block().required("decision"));
        assertEquals("Terminal block is the one that counts", result.block().required("summary"));
    }

    // --- Edge cases ---

    @Test
    void emptyInput_returnsMissing() {
        var result = parser.parse("");
        assertTrue(result.isMissing());
    }

    @Test
    void noDualControlBlock_returnsMissing() {
        var result = parser.parse("# Just markdown\n\nNo control block here.");
        assertTrue(result.isMissing());
    }

    // --- Helpers ---

    private static String loadSample(String sampleName) {
        var resource = SAMPLES_ROOT + sampleName + ".md";
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("missing sample fixture: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
