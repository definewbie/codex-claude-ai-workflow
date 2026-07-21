package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 从 acpx 自己的 session stream 中提取底层 provider 错误。
 *
 * <p>acpx 的 stderr 有时只暴露 "agent needs reconnect" 这类包装层症状，真正的
 * API 错误会写入 {@code ~/.acpx/sessions/<session-id>.stream.ndjson}。该 stream 是
 * 换行分隔的 JSON-RPC：agent 正文、usage 元数据、tool result 都在 notification
 * {@code params.update} 内部，它们不是协议级错误。
 *
 * <p>本读取器用 Jackson 结构化解析每条完整 NDJSON 行（{@link AcpxStreamWireLine}），
 * 只把<b>顶层 JSON-RPC error 响应</b>归一化成 {@link AcpxStreamEvidence}。
 * 通过 JSON-RPC request id 关联 method；已被 {@code session/new} 成功恢复的
 * {@code session/resume} 错误被丢弃。agent 正文里的 {@code error}/{@code quota}/
 * {@code rate limit}、{@code is_error=false} 的 tool result、{@code _meta.quota}
 * usage、以及残缺 NDJSON 行一律不作为证据。这样下游
 * {@link AcpxFailureSignalCatalog} 只在真实 provider 错误上匹配，不会把业务正文
 * 误判成 rate limit。
 *
 * <p>本读取器只读取有限大小的 stream 尾部作为分类证据，不修改 run ledger，也不把
 * provider 原始日志复制到业务产物中。
 */
final class AcpxSessionStreamFailureEvidenceReader {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "\\(([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)"
    );
    private static final int MAX_TAIL_BYTES = 128 * 1024;

    private final Path sessionsDir;

    AcpxSessionStreamFailureEvidenceReader() {
        this(Path.of(System.getProperty("user.home"), ".acpx", "sessions"));
    }

    AcpxSessionStreamFailureEvidenceReader(Path sessionsDir) {
        this.sessionsDir = sessionsDir;
    }

    Optional<AcpxStreamEvidence> readFromStderr(String stderr) {
        var sessionId = sessionIdFrom(stderr);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }
        var streamPath = sessionsDir.resolve(sessionId.get() + ".stream.ndjson");
        if (!Files.isRegularFile(streamPath)) {
            return Optional.empty();
        }
        try {
            var evidence = normalize(readTail(streamPath));
            return evidence.isEmpty() ? Optional.empty() : Optional.of(evidence);
        } catch (IOException ignored) {
            return Optional.empty();
        }
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

    private String readTail(Path path) throws IOException {
        var size = Files.size(path);
        if (size <= MAX_TAIL_BYTES) {
            return Files.readString(path);
        }
        var buffer = ByteBuffer.allocate(MAX_TAIL_BYTES);
        try (var channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            channel.position(size - MAX_TAIL_BYTES);
            channel.read(buffer);
        }
        buffer.flip();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    /**
     * 按 NDJSON 顺序进行 request/response 关联的结构化归一化。
     *
     * <p>单次顺序扫描：用一个<b>增量</b>的 pending-requests 映射（仅记录当前行
     * 之前已见到的 request）为 response 关联 method —— 不预先生成全局 id→method
     * 映射，也不使用全局 {@code sessionNewSucceeded} 标志。只保留顶层 JSON-RPC
     * error 响应或明确 typed prompt-failure event。只有<b>后续</b>成功的
     * {@code session/new} 才能消除它<b>之前</b>已收集的 {@code session/resume}
     * 错误（顺序约束：恢复必须晚于被恢复的错误）。
     */
    private static AcpxStreamEvidence normalize(String streamTail) {
        if (streamTail == null || streamTail.isBlank()) {
            return AcpxStreamEvidence.EMPTY;
        }
        var projector = new AcpxSessionStreamProjector();
        long sequence = 0;
        for (var line : streamTail.lines().toList()) {
            projector.accept(sequence++, AcpxStreamWireLine.parse(line));
        }
        return projector.evidence();
    }
}
