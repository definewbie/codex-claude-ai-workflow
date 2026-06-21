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
 * <p>关键步骤：</p>
 * <ol>
 *     <li>从 stderr 的 {@code [acpx] session ... (<uuid>)} 中定位 stream 文件。</li>
 *     <li>优先在 stream 中按 prompt 文本匹配本次 {@code session/prompt}。</li>
 *     <li>如果 acpx/Claude Code 改写 prompt 导致无法等值匹配，则回退读取最新
 *     {@code session/prompt} 对应的 {@code result.usage}。</li>
 *     <li>如果某个 ACP adapter 不返回 {@code result.usage}，则使用最新
 *     {@code usage_update.used} 作为 adapter 观测到的总 usage。</li>
 * </ol>
 *
 * <p>这个类只产出外部 adapter 的结构化观测事实，不参与 workflow 路由。</p>
 */
final class AcpxUsageReader {
    private static final int DEFAULT_RETRY_ATTEMPTS = 8;
    private static final long DEFAULT_RETRY_DELAY_MS = 150L;
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
        this(sessionsDir, DEFAULT_RETRY_ATTEMPTS, DEFAULT_RETRY_DELAY_MS);
    }

    AcpxUsageReader(Path sessionsDir, int retryAttempts, long retryDelayMs) {
        this.sessionsDir = sessionsDir;
        this.retryAttempts = Math.max(1, retryAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
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
        Optional<AcpxUsage> usage = Optional.empty();
        for (var attempt = 0; attempt < retryAttempts; attempt++) {
            usage = readMatchingUsage(streamPath, promptText);
            if (usage.isPresent() || attempt == retryAttempts - 1) {
                return usage;
            }
            sleepBeforeRetry();
        }
        return usage;
    }

    private Optional<AcpxUsage> readMatchingUsage(Path streamPath, String promptText) throws IOException {
        String matchedRequestId = null;
        String latestPromptRequestId = null;
        AcpxUsage exactUsage = null;
        AcpxUsage fallbackUsage = null;
        AcpxUsage usageUpdateFallback = null;
        BigDecimal costAmount = null;
        String costCurrency = "";
        for (var line : Files.readAllLines(streamPath)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            var node = parse(line);
            if (node == null) {
                continue;
            }
            if (isPrompt(node)) {
                latestPromptRequestId = text(node.get("id"));
                if (matchedRequestId == null && isMatchingPrompt(node, promptText)) {
                    matchedRequestId = latestPromptRequestId;
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
                            used,
                            costAmount,
                            costCurrency,
                            "acpx_usage_update"
                    );
                }
            }
            if (node.at("/result/usage").isObject()) {
                var requestId = text(node.get("id"));
                var usage = usageFrom(node, costAmount, costCurrency);
                if (matchedRequestId != null && matchedRequestId.equals(requestId)) {
                    exactUsage = usage;
                }
                if (latestPromptRequestId != null && latestPromptRequestId.equals(requestId)) {
                    fallbackUsage = usage;
                }
            }
        }
        if (exactUsage != null) {
            return Optional.of(exactUsage);
        }
        if (fallbackUsage != null) {
            return Optional.of(fallbackUsage);
        }
        return Optional.ofNullable(usageUpdateFallback);
    }

    private boolean isPrompt(JsonNode node) {
        return "session/prompt".equals(text(node.get("method")));
    }

    private boolean isMatchingPrompt(JsonNode node, String promptText) {
        return "session/prompt".equals(text(node.get("method")))
                && promptText.equals(text(node.at("/params/prompt/0/text")));
    }

    private AcpxUsage usageFrom(JsonNode node, BigDecimal costAmount, String costCurrency) {
        var usage = node.at("/result/usage");
        return new AcpxUsage(
                number(usage.get("inputTokens")),
                number(usage.get("outputTokens")),
                number(usage.get("cachedReadTokens")),
                number(usage.get("cachedWriteTokens")),
                number(usage.get("totalTokens")),
                costAmount,
                costCurrency,
                "acpx_stream"
        );
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
