package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 读取 acpx session stream 中属于当前 prompt 的 token usage。
 *
 * <p>参数说明：</p>
 * <ul>
 *     <li>{@code promptPath}: 当前 NodeExecution 写入 acpx 的 prompt 文件。</li>
 *     <li>{@code stderrPath}: acpx stderr 文件，用于提取真实 session id。</li>
 * </ul>
 *
 * <p>归因优先级（必须严格区分，不得伪装）：</p>
 * <ol>
 *     <li>优先按 request id 链路定位本次 prompt 的 {@code result.usage}：
 *     先尝试 prompt 文本精确匹配，命中则 {@code attribution=EXACT_PROMPT_MATCH}；
 *     命中不了则取会话内最近一条 {@code session/prompt} 的 request id，再找同 id 的
 *     {@code result.usage}，{@code attribution=LATEST_PROMPT_IN_SESSION}。两者都是真实的
 *     provider run usage，允许聚合。</li>
 *     <li>若该 request id 一直没有 {@code result.usage}（部分 adapter 只回 usage_update），
 *     则把最新 {@code usage_update.used} 记录为 {@code SESSION_USED_SNAPSHOT} 会话累计快照，
 *     {@code attribution=SESSION_USAGE_UPDATE}，<b>不</b>当作单次 run 用量加总。</li>
 *     <li>都没有则返回空，交由 ledger 侧 fallback 到 artifact_estimate。</li>
 * </ol>
 *
 * <p>result.usage 是由 acpx 异步追加到 stream 的，常常晚于 agent 进程退出。本读取器会
 * 在首次没拿到 provider run usage 时按 {@code retryAttempts}×{@code retryDelayMs} 轮询
 * 重读（默认窗口远大于旧的 1.2s），<b>且</b>即便已经看到 usage_update 快照也会继续等
 * provider run usage，避免被早到的快照短路成假阴性的 measured=0。</p>
 *
 * <p>这个类只产出外部 adapter 的结构化观测事实，不参与 workflow 路由，也不解析自然语言正文。</p>
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    AcpxUsageReader() {
        this(Path.of(System.getProperty("user.home"), ".acpx", "sessions"));
    }

    AcpxUsageReader(Path sessionsDir) {
        this(sessionsDir, configuredRetryAttempts(), configuredRetryDelayMs());
    }

    AcpxUsageReader(Path sessionsDir, int retryAttempts, long retryDelayMs) {
        this.sessionsDir = sessionsDir;
        this.retryAttempts = Math.max(1, retryAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
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

    Optional<AcpxUsage> read(Path promptPath, Path stderrPath) {
        try {
            var stderr = Files.exists(stderrPath) ? Files.readString(stderrPath) : "";
            var sessionId = sessionIdFrom(stderr);
            if (sessionId.isEmpty() || !Files.isRegularFile(promptPath)) {
                return Optional.empty();
            }
            var streamPath = sessionsDir.resolve(sessionId.get() + ".stream.ndjson");
            if (!Files.isRegularFile(streamPath)) {
                return Optional.empty();
            }
            return readMatchingUsageWithRetry(streamPath, Files.readString(promptPath));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<AcpxUsage> readMatchingUsageWithRetry(Path streamPath, String promptText) throws IOException {
        for (var attempt = 0; attempt < retryAttempts; attempt++) {
            var match = readMatchingUsage(streamPath, promptText);
            if (match.hasProviderUsage() || attempt == retryAttempts - 1) {
                return Optional.ofNullable(match.best());
            }
            sleepBeforeRetry();
        }
        return Optional.empty();
    }

    /**
     * 一次扫描 stream 的结果。三档按优先级取：精确 provider &gt; 最近 provider &gt; 会话快照。
     * 包级可见以便单测直接验证归因/优先级，而不依赖重试时序。
     */
    record Match(AcpxUsage exact, AcpxUsage latest, AcpxUsage snapshot) {
        boolean hasProviderUsage() {
            return exact != null || latest != null;
        }

        AcpxUsage best() {
            return exact != null ? exact : latest != null ? latest : snapshot;
        }
    }

    Match readMatchingUsage(Path streamPath, String promptText) throws IOException {
        String matchedRequestId = null;
        String latestPromptRequestId = null;
        AcpxUsage exactUsage = null;
        AcpxUsage fallbackUsage = null;
        AcpxUsage usageUpdateFallback = null;
        BigDecimal costAmount = null;
        String costCurrency = "";
        BigDecimal matchedPromptStartingCost = null;
        BigDecimal latestPromptStartingCost = null;
        for (var line : Files.readAllLines(streamPath)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            var node = parse(line);
            if (node == null) {
                continue;
            }
            if (isPrompt(node)) {
                var firstPrompt = latestPromptRequestId == null;
                latestPromptRequestId = text(node.get("id"));
                latestPromptStartingCost = startingCostForPrompt(costAmount, firstPrompt);
                if (matchedRequestId == null && isMatchingPrompt(node, promptText)) {
                    matchedRequestId = latestPromptRequestId;
                    matchedPromptStartingCost = latestPromptStartingCost;
                }
                continue;
            }
            if (isUsageUpdate(node)) {
                var cost = node.at("/params/update/cost");
                if (!cost.isMissingNode()) {
                    costAmount = decimal(cost.get("amount"));
                    costCurrency = text(cost.get("currency"));
                }
                var used = number(node.at("/params/update/used"));
                if (used > 0) {
                    usageUpdateFallback = new AcpxUsage(
                            0,
                            0,
                            0,
                            0,
                            0,
                            null,
                            "",
                            "acpx_usage_update",
                            AcpxUsage.SESSION_USED_SNAPSHOT,
                            AcpxUsage.ATTR_SESSION_USAGE_UPDATE,
                            true,
                            used,
                            costAmount,
                            costCurrency
                    );
                }
            }
            if (node.at("/result/usage").isObject()) {
                var requestId = text(node.get("id"));
                if (matchedRequestId != null && matchedRequestId.equals(requestId)) {
                    exactUsage = usageFrom(
                            node,
                            matchedPromptStartingCost,
                            costAmount,
                            costCurrency,
                            AcpxUsage.ATTR_EXACT_PROMPT_MATCH
                    );
                }
                if (latestPromptRequestId != null && latestPromptRequestId.equals(requestId)) {
                    fallbackUsage = usageFrom(
                            node,
                            latestPromptStartingCost,
                            costAmount,
                            costCurrency,
                            AcpxUsage.ATTR_LATEST_PROMPT_IN_SESSION
                    );
                }
            }
        }
        return new Match(exactUsage, fallbackUsage, usageUpdateFallback);
    }

    private BigDecimal startingCostForPrompt(BigDecimal observedCostAmount, boolean firstPrompt) {
        if (observedCostAmount != null) {
            return observedCostAmount;
        }
        return firstPrompt ? BigDecimal.ZERO : null;
    }

    private boolean isPrompt(JsonNode node) {
        return "session/prompt".equals(text(node.get("method")));
    }

    private boolean isMatchingPrompt(JsonNode node, String promptText) {
        return "session/prompt".equals(text(node.get("method")))
                && promptText.equals(text(node.at("/params/prompt/0/text")));
    }

    private AcpxUsage usageFrom(
            JsonNode node,
            BigDecimal startingCostAmount,
            BigDecimal sessionCostAmount,
            String sessionCostCurrency,
            String attribution
    ) {
        var usage = node.at("/result/usage");
        var runCostAmount = costDelta(startingCostAmount, sessionCostAmount);
        return new AcpxUsage(
                number(usage.get("inputTokens")),
                number(usage.get("outputTokens")),
                number(usage.get("cachedReadTokens")),
                number(usage.get("cachedWriteTokens")),
                number(usage.get("totalTokens")),
                runCostAmount,
                sessionCostCurrency,
                "acpx_stream",
                AcpxUsage.PROVIDER_RUN_USAGE,
                attribution,
                false,
                0,
                sessionCostAmount,
                sessionCostCurrency
        );
    }

    private BigDecimal costDelta(BigDecimal startingCostAmount, BigDecimal sessionCostAmount) {
        if (sessionCostAmount == null || startingCostAmount == null) {
            return null;
        }
        var delta = sessionCostAmount.subtract(startingCostAmount);
        return delta.signum() < 0 ? null : delta;
    }

    private boolean isUsageUpdate(JsonNode node) {
        return "session/update".equals(text(node.get("method")))
                && "usage_update".equals(text(node.at("/params/update/sessionUpdate")));
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

    private JsonNode parse(String line) {
        try {
            return objectMapper.readTree(line);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static long number(JsonNode node) {
        return node == null || !node.isNumber() ? 0 : node.asLong();
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.decimalValue();
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
