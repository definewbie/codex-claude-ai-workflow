package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AcpxFailureClassifier {
    private final AcpxFailureSignalCatalog signalCatalog;
    private final AcpxSessionStreamFailureEvidenceReader sessionStreamReader;

    AcpxFailureClassifier() {
        this(new AcpxFailureSignalCatalog(), new AcpxSessionStreamFailureEvidenceReader());
    }

    AcpxFailureClassifier(AcpxFailureSignalCatalog signalCatalog) {
        this(signalCatalog, new AcpxSessionStreamFailureEvidenceReader());
    }

    AcpxFailureClassifier(
            AcpxFailureSignalCatalog signalCatalog,
            AcpxSessionStreamFailureEvidenceReader sessionStreamReader
    ) {
        this.signalCatalog = signalCatalog;
        this.sessionStreamReader = sessionStreamReader;
    }

    AcpxFailure classify(Path stderrPath, int exitCode) {
        return classify(null, stderrPath, exitCode);
    }

    AcpxFailure classify(Path stdoutPath, Path stderrPath, int exitCode) {
        var stderr = read(stderrPath);
        var providerEvidence = sessionStreamReader.readFromStderr(stderr).orElse("");
        var evidence = new AcpxFailureEvidence(read(stdoutPath), stderr + "\n" + providerEvidence, exitCode);
        var signal = signalCatalog.match(evidence);
        if (signal.isPresent()) {
            return signal.get().toFailure();
        }
        return new AcpxFailure(
                "ACPX_EXIT_" + exitCode,
                "acpx exited with " + exitCode,
                false,
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

    record AcpxFailure(String reason, String message, boolean recoverable, boolean interruptRunningProcess) {
        AcpxFailure(String reason, String message, boolean recoverable) {
            this(reason, message, recoverable, false);
        }
    }
}
