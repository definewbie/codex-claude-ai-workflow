package dev.dual.adapter.acpx;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/** Adapter-local usage observation, scoped first by the current invocation transcript and live completion identity.
 * No prompt-text or latest-request fallback. Session snapshots are not request totals.
 * Async stream writes retain the existing bounded wait; ambiguous identity fails attribution.
 */
final class AcpxUsageReader {
    private static final int DEFAULT_RETRY_ATTEMPTS = 50;
    private static final long DEFAULT_RETRY_DELAY_MS = 200L;
    private static final String RETRY_ATTEMPTS_ENV = "DUAL_ACPX_USAGE_READ_ATTEMPTS";
    private static final String RETRY_DELAY_ENV = "DUAL_ACPX_USAGE_READ_DELAY_MS";
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "\\(([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)"
    );

    private final Path sessionsDir;
    private final int retryAttempts;
    private final long retryDelayMs;
    private final AcpxProviderMetadata fallbackMetadata;

    AcpxUsageReader() {
        this(Path.of(System.getProperty("user.home"), ".acpx", "sessions"));
    }

    AcpxUsageReader(Path sessionsDir) {
        this(sessionsDir, configuredRetryAttempts(), configuredRetryDelayMs());
    }

    AcpxUsageReader(Path sessionsDir, int retryAttempts, long retryDelayMs) {
        this(sessionsDir, retryAttempts, retryDelayMs, null);
    }

    AcpxUsageReader(Path sessionsDir, int retryAttempts, long retryDelayMs, AcpxProviderMetadata fallbackMetadata) {
        this.sessionsDir = sessionsDir;
        this.retryAttempts = Math.max(1, retryAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
        this.fallbackMetadata = fallbackMetadata;
    }

    private static int configuredRetryAttempts() {
        return envInt(RETRY_ATTEMPTS_ENV, DEFAULT_RETRY_ATTEMPTS);
    }

    private static long configuredRetryDelayMs() {
        return envLong(RETRY_DELAY_ENV, DEFAULT_RETRY_DELAY_MS);
    }

    private static int envInt(String name, int fallback) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            var parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long envLong(String name, long fallback) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            var parsed = Long.parseLong(value.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    Optional<AcpxUsage> read(String agent, AcpxPromptCompletion completion, Path transcriptPath, Path stderrPath) {
        try {
            var stderr = Files.exists(stderrPath) ? Files.readString(stderrPath) : "";
            var sessionId = sessionIdFrom(stderr);
            if (sessionId.isEmpty()) {
                var metadata = fallbackMetadata != null ? fallbackMetadata : agentFallbackMetadata(agent);
                return metadata.isEmpty()
                        ? Optional.empty()
                        : Optional.of(AcpxUsage.unavailable("NO_ACPX_SESSION_ID", metadata));
            }
            var streamPath = sessionsDir.resolve(sessionId.get() + ".stream.ndjson");
            var metadata = AcpxProviderMetadata.read(sessionsDir.resolve(sessionId.get() + ".json"));
            if (completion == null || completion.requestId().isBlank() || completion.sessionId().isBlank()) {
                return Optional.of(AcpxUsage.unavailable("NO_PROMPT_COMPLETION_IDENTITY", metadata));
            }
            return readMatchingUsageWithRetry(transcriptPath, streamPath, completion, metadata);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Identity fallback when no current-agent session metadata is observable. Only
     * Claude may reuse Claude settings/env as a labelled fallback; any other agent
     * (e.g. Codex) must remain {@code unknown} rather than be projected through a
     * Claude-specific config. This affects metrics/metadata projection only.
     */
    private static AcpxProviderMetadata agentFallbackMetadata(String agent) {
        return "claude".equals(agent) ? AcpxProviderMetadata.runtimeDefault() : AcpxProviderMetadata.empty();
    }

    private Optional<AcpxUsage> readMatchingUsageWithRetry(
            Path transcriptPath,
            Path streamPath,
            AcpxPromptCompletion completion,
            AcpxProviderMetadata metadata
    ) throws IOException {
        for (var attempt = 0; attempt < retryAttempts; attempt++) {
            var current = readCurrentUsage(transcriptPath, completion);
            if (current != null) {
                // Shared history may enrich money only; it cannot revoke current tokens.
                if (AcpxUsage.PROVIDER_RUN_USAGE.equals(current.measurementType()) && Files.isRegularFile(streamPath)) {
                    try {
                        var history = readMatchingUsage(streamPath, completion);
                        if (!history.ambiguous() && sameTokens(current, history.exact())) {
                            var exact = history.exact();
                            current = new AcpxUsage(current.inputTokens(), current.outputTokens(),
                                    current.cachedReadTokens(), current.cachedWriteTokens(), current.totalTokens(),
                                    exact.costAmount(), exact.costCurrency(), current.source(), current.measurementType(),
                                    current.attribution(), false, 0, exact.sessionCostAmount(), exact.sessionCostCurrency(),
                                    "", "", "", "", "");
                        }
                    } catch (IOException ignored) {
                        // Optional monetary context is not the token source of truth.
                    }
                }
                return Optional.of(current.withProviderMetadata(metadata));
            }
            var match = Files.isRegularFile(streamPath) ? readMatchingUsage(streamPath, completion)
                    : new Match(null, null, false);
            if (match.exact() != null || match.ambiguous() || attempt == retryAttempts - 1) {
                var best = match.best();
                return best == null ? Optional.empty() : Optional.of(best.withProviderMetadata(metadata));
            }
            sleepBeforeRetry();
        }
        return Optional.empty();
    }

    /** Null means no prompt response yet; an invalid/partial response never falls back. */
    private AcpxUsage readCurrentUsage(Path transcriptPath, AcpxPromptCompletion completion) throws IOException {
        if (!Files.isRegularFile(transcriptPath)) return null;
        AcpxStreamWireLine.Result result = null;
        BigDecimal sessionCost = null;
        String currency = "";
        int responses = 0;
        for (var line : Files.readAllLines(transcriptPath)) {
            if (line.isBlank()) continue;
            var wire = AcpxStreamWireLine.parse(line);
            if (line.stripLeading().startsWith("{") && wire.kind() == AcpxStreamWireLine.Kind.MALFORMED)
                return AcpxUsage.unavailable("INVALID_CURRENT_TRANSCRIPT");
            if (isPrompt(wire) && !matches(wire, completion))
                return AcpxUsage.unavailable("CURRENT_PROMPT_IDENTITY_MISMATCH");
            if (isUsageUpdate(wire) && completion.sessionId().equals(wire.notification().sessionId())) {
                var update = wire.notification().usageUpdate();
                if (update.costAmount() != null) {
                    sessionCost = update.costAmount();
                    currency = update.costCurrency();
                }
            }
            if (wire.kind() == AcpxStreamWireLine.Kind.JSONRPC_ERROR
                    && completion.requestId().equals(wire.requestId()))
                return AcpxUsage.unavailable("CURRENT_PROMPT_ERROR");
            if (wire.kind() != AcpxStreamWireLine.Kind.JSONRPC_OK || wire.result() == null) continue;
            var candidate = wire.result();
            if (candidate.stopReason().isBlank() && candidate.usage() == null
                    && !completion.requestId().equals(wire.requestId())) continue;
            if (!completion.requestId().equals(wire.requestId())
                    || !completion.stopReason().equals(candidate.stopReason())
                    || (!candidate.sessionId().isBlank() && !completion.sessionId().equals(candidate.sessionId())))
                return AcpxUsage.unavailable("CURRENT_PROMPT_IDENTITY_MISMATCH");
            if (++responses > 1) return AcpxUsage.unavailable("AMBIGUOUS_CURRENT_PROMPT_RESULT");
            result = candidate;
        }
        if (result == null) return null;
        if (result.usage() == null) return AcpxUsage.unavailable("CURRENT_PROMPT_USAGE_INCOMPLETE");
        return usageFrom(result.usage(), null, sessionCost, currency,
                AcpxUsage.ATTR_EXACT_REQUEST_MATCH, "acpx_current_transcript");
    }

    private boolean sameTokens(AcpxUsage left, AcpxUsage right) {
        return right != null && left.inputTokens() == right.inputTokens()
                && left.outputTokens() == right.outputTokens()
                && left.cachedReadTokens() == right.cachedReadTokens()
                && left.cachedWriteTokens() == right.cachedWriteTokens()
                && left.totalTokens() == right.totalTokens();
    }

    record Match(AcpxUsage exact, AcpxUsage snapshot, boolean ambiguous) {
        AcpxUsage best() {
            if (ambiguous) return AcpxUsage.unavailable("AMBIGUOUS_PROMPT_REQUEST_IDENTITY");
            if (exact != null) return exact;
            return snapshot == null ? AcpxUsage.unavailable("PROMPT_USAGE_UNAVAILABLE")
                    : snapshot.withUnavailableReason("PROMPT_USAGE_UNAVAILABLE");
        }
    }

    Match readMatchingUsage(Path streamPath, AcpxPromptCompletion completion) throws IOException {
        var pending = new java.util.HashMap<String, AcpxStreamWireLine>();
        var costs = new java.util.HashMap<String, BigDecimal>();
        var currencies = new java.util.HashMap<String, String>();
        var seenPromptSessions = new java.util.HashSet<String>();
        BigDecimal startingCost = null;
        AcpxUsage exact = null;
        AcpxUsage snapshot = null;
        int matchingRequests = 0;
        int matchingResults = 0;
        boolean ambiguous = false;
        for (var line : Files.readAllLines(streamPath)) {
            if (line == null || line.isBlank()) continue;
            var wire = AcpxStreamWireLine.parse(line);
            if (wire.kind() == AcpxStreamWireLine.Kind.JSONRPC_REQUEST) {
                var previous = pending.put(wire.requestId(), wire);
                if (previous != null && (matches(previous, completion) || matches(wire, completion))) ambiguous = true;
                if (isPrompt(wire)) {
                    var session = wire.request().sessionId();
                    var firstPrompt = seenPromptSessions.add(session);
                    if (matches(wire, completion)) {
                        matchingRequests++;
                        startingCost = startingCostForPrompt(costs.get(session), firstPrompt);
                    }
                }
                continue;
            }
            if (isUsageUpdate(wire)) {
                var session = wire.notification().sessionId();
                var update = wire.notification().usageUpdate();
                if (update.costAmount() != null) {
                    costs.put(session, update.costAmount());
                    currencies.put(session, update.costCurrency());
                }
                if (completion.sessionId().equals(session) && update.used() > 0) {
                    snapshot = new AcpxUsage(0, 0, 0, 0, 0, null, "", "acpx_usage_update",
                            AcpxUsage.SESSION_USED_SNAPSHOT, AcpxUsage.ATTR_SESSION_USAGE_UPDATE, true,
                            update.used(), costs.get(session), currencies.getOrDefault(session, ""),
                            "", "", "", "", "");
                }
            }
            if (wire.kind() == AcpxStreamWireLine.Kind.JSONRPC_OK
                    || wire.kind() == AcpxStreamWireLine.Kind.JSONRPC_ERROR) {
                var request = pending.remove(wire.requestId());
                if (request == null && matchingResults > 0 && completion.requestId().equals(wire.requestId())) {
                    ambiguous = true;
                }
                if (matches(request, completion)) {
                    matchingResults++;
                    if (wire.kind() == AcpxStreamWireLine.Kind.JSONRPC_OK && wire.result() != null
                            && wire.result().usage() != null) {
                        exact = usageFrom(wire.result().usage(), startingCost,
                                costs.get(completion.sessionId()), currencies.getOrDefault(completion.sessionId(), ""),
                                AcpxUsage.ATTR_EXACT_REQUEST_MATCH, "acpx_stream");
                    }
                }
            }
        }
        return new Match(exact, snapshot, ambiguous || matchingRequests > 1 || matchingResults > 1);
    }

    private boolean matches(AcpxStreamWireLine wire, AcpxPromptCompletion completion) {
        return wire != null && isPrompt(wire) && wire.request() != null
                && completion.requestId().equals(wire.requestId())
                && completion.sessionId().equals(wire.request().sessionId());
    }

    private BigDecimal startingCostForPrompt(BigDecimal observedCostAmount, boolean firstPrompt) {
        if (observedCostAmount != null) {
            return observedCostAmount;
        }
        return firstPrompt ? BigDecimal.ZERO : null;
    }

    private boolean isPrompt(AcpxStreamWireLine wire) {
        return wire.kind() == AcpxStreamWireLine.Kind.JSONRPC_REQUEST
                && "session/prompt".equals(wire.method());
    }

    private AcpxUsage usageFrom(
            AcpxStreamWireLine.Usage usage,
            BigDecimal startingCostAmount,
            BigDecimal sessionCostAmount,
            String sessionCostCurrency,
            String attribution,
            String source
    ) {
        var runCostAmount = costDelta(startingCostAmount, sessionCostAmount);
        return new AcpxUsage(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cachedReadTokens(),
                usage.cachedWriteTokens(),
                usage.totalTokens(),
                runCostAmount,
                sessionCostCurrency,
                source,
                AcpxUsage.PROVIDER_RUN_USAGE,
                attribution,
                false,
                0,
                sessionCostAmount,
                sessionCostCurrency,
                "",
                "",
                "",
                "",
                ""
        );
    }

    private BigDecimal costDelta(BigDecimal startingCostAmount, BigDecimal sessionCostAmount) {
        if (sessionCostAmount == null || startingCostAmount == null) {
            return null;
        }
        var delta = sessionCostAmount.subtract(startingCostAmount);
        return delta.signum() < 0 ? null : delta;
    }

    private boolean isUsageUpdate(AcpxStreamWireLine wire) {
        return wire.kind() == AcpxStreamWireLine.Kind.NOTIFICATION
                && "session/update".equals(wire.method())
                && wire.notification() != null
                && wire.notification().usageUpdate() != null;
    }

    private Optional<String> sessionIdFrom(String stderr) {
        if (stderr == null || stderr.isBlank() || !stderr.contains("[acpx] session")) {
            return Optional.empty();
        }
        var matcher = SESSION_ID_PATTERN.matcher(stderr);
        String latest = null;
        while (matcher.find()) {
            latest = matcher.group(1);
        }
        return Optional.ofNullable(latest);
    }

    private void sleepBeforeRetry() {
        if (retryDelayMs == 0) {
            return;
        }
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
