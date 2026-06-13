package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AcpxFailureClassifier {
    private final AcpxFailureSignalCatalog signalCatalog;

    AcpxFailureClassifier() {
        this(new AcpxFailureSignalCatalog());
    }

    AcpxFailureClassifier(AcpxFailureSignalCatalog signalCatalog) {
        this.signalCatalog = signalCatalog;
    }

    AcpxFailure classify(Path stderrPath, int exitCode) {
        return classify(null, stderrPath, exitCode);
    }

    AcpxFailure classify(Path stdoutPath, Path stderrPath, int exitCode) {
        var evidence = new AcpxFailureEvidence(read(stdoutPath), read(stderrPath), exitCode);
        var signal = signalCatalog.match(evidence);
        if (signal.isPresent()) {
            return signal.get().toFailure();
        }
        return new AcpxFailure(
                "ACPX_EXIT_" + exitCode,
                "acpx exited with " + exitCode,
                false
        );
    }

    private String read(Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException ignored) {
            return "";
        }
    }

    record AcpxFailure(String reason, String message, boolean recoverable) {
    }
}
