package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AcpxFailureClassifierTest {
    private final AcpxFailureClassifier classifier = new AcpxFailureClassifier();

    @TempDir
    Path tempDir;

    @Test
    void nativeClaudeSuccessJsonWithNonZeroExitRemainsAdapterFailure() throws Exception {
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(stdout, "native claude result text\n");
        Files.writeString(stderr, "");
        var nativeTerminal = new NativeClaudeTerminal(true, false, "end_turn", "success");

        var failure = classifier.classify(stdout, stderr, 1, nativeTerminal);

        assertNotNull(failure,
                "native success JSON with non-zero exit must remain an adapter failure: "
                        + "the native terminal SoT requires exitCode == 0");
    }

    @Test
    void nativeClaudeTerminalSuccessWithZeroExitIsNotAdapterFailure() throws Exception {
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(stdout, "native claude result text\n");
        Files.writeString(stderr, "");
        var nativeTerminal = new NativeClaudeTerminal(true, false, "end_turn", "success");

        var failure = classifier.classify(stdout, stderr, 0, nativeTerminal);

        assertNull(failure,
                "exit 0 + native terminal success (parseable, is_error false, end_turn) is adapter/process success");
    }
}
