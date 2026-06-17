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
 * acpx 的 stderr 有时只暴露 "agent needs reconnect" 这类包装层症状，
 * 真正的 API 错误会写入 ~/.acpx/sessions/<session-id>.stream.ndjson。
 * 这个读取器只把有限大小的 stream 尾部作为分类证据，不修改 run ledger，
 * 也不把 provider 原始日志复制到业务产物中。
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

    Optional<String> readFromStderr(String stderr) {
        var sessionId = sessionIdFrom(stderr);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }
        var streamPath = sessionsDir.resolve(sessionId.get() + ".stream.ndjson");
        if (!Files.isRegularFile(streamPath)) {
            return Optional.empty();
        }
        try {
            var tail = readTail(streamPath);
            return tail.isBlank() ? Optional.empty() : Optional.of(tail);
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
}
