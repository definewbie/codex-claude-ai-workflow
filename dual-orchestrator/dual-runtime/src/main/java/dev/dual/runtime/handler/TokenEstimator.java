package dev.dual.runtime.handler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Estimates token count for text content.
 * Default implementation uses a simple character-to-token ratio (~4 chars/token for English).
 * Can be replaced with a real tokenizer (tiktoken, BPE) in the future.
 */
interface TokenEstimator {

    /** Estimate token count for the given text. */
    int estimate(String text);

    /** Estimate token count for the content of a file. */
    int estimateFile(Path path);

    /** Default estimator: ~4 chars per token (conservative for mixed CJK/English). */
    static TokenEstimator defaultEstimator() {
        return new CharRatioEstimator(4);
    }

    /** Create an estimator with a custom chars-per-token ratio. */
    static TokenEstimator withRatio(int charsPerToken) {
        return new CharRatioEstimator(charsPerToken);
    }
}

final class CharRatioEstimator implements TokenEstimator {
    private final int ratio;

    CharRatioEstimator(int ratio) {
        if (ratio <= 0) throw new IllegalArgumentException("ratio must be positive");
        this.ratio = ratio;
    }

    @Override
    public int estimate(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (text.length() + ratio - 1) / ratio;
    }

    @Override
    public int estimateFile(Path path) {
        try {
            if (!Files.exists(path)) return 0;
            var content = Files.readString(path, StandardCharsets.UTF_8);
            return estimate(content);
        } catch (IOException e) {
            return 0;
        }
    }
}
