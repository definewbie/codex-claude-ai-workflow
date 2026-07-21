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
        var streamEvidence = sessionStreamReader.readFromStderr(stderr).orElse(AcpxStreamEvidence.EMPTY);
        var runtimeStdoutEvidence = runtimeStdoutEvidence(read(stdoutPath));
        // streamEvidence carries typed top-level JSON-RPC errors and ordered
        // session outcome facts, never agent prose, usage metadata, or tool
        // results. Typed provider errors are classified before wrapper
        // diagnostics; a stale reconnect diagnostic can be superseded only by a
        // proven session-ready + prompt-completed outcome.
        var providerSignal = signalCatalog.match(new AcpxFailureEvidence(
                "", streamEvidence.normalizedErrorText(), exitCode));
        if (providerSignal.isPresent()) {
            return providerSignal.get().toFailure();
        }
        var wrapperSignal = signalCatalog.match(new AcpxFailureEvidence(
                runtimeStdoutEvidence, stderr, exitCode));
        if (wrapperSignal.isPresent()) {
            if (wrapperSignal.get().reason() == AcpxFailureReason.AGENT_RECONNECT
                    && streamEvidence.provesRecoveredPromptCompletion()) {
                return noTypedFailure(exitCode);
            }
            return wrapperSignal.get().toFailure();
        }
        return noTypedFailure(exitCode);
    }

    private static AcpxFailure noTypedFailure(int exitCode) {
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

    /**
     * 只从 stdout 中提取明确的运行时错误行。
     *
     * Agent stdout 可能包含需求、上下文包、历史输出和业务风险描述；这些正文
     * 不能作为 acpx/provider 故障证据。这里把 stdout 降级为辅助证据，只接收
     * acpx runtime/error 形态的行，避免把业务文本里的 "rate limit"、"quota"
     * 等词误判成外部适配器故障。
     */
    private static String runtimeStdoutEvidence(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "";
        }
        var result = new StringBuilder();
        for (var line : stdout.lines().toList()) {
            if (AgentLifecycleEvent.stdoutLine(0, line).kind() == AgentLifecycleEvent.Kind.PROVIDER_FAILURE) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    record AcpxFailure(String reason, String message, boolean recoverable, boolean interruptRunningProcess) {
        AcpxFailure(String reason, String message, boolean recoverable) {
            this(reason, message, recoverable, false);
        }
    }
}
