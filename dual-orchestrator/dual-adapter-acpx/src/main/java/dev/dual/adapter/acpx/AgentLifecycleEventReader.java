package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Per-run incremental reader for acpx stdout, stderr, and session stream events.
 *
 * stdout/stderr files are owned by one dual run, so they are consumed from byte
 * offset zero. acpx session streams are long-lived per acpx session, but acpx
 * can write recovery evidence before stderr exposes the session id. On first
 * discovery this reader consumes only a bounded tail so current session/new or
 * provider failure events can supersede wrapper reconnect text without loading
 * the whole session history.
 */
final class AgentLifecycleEventReader {
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "\\(([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)"
    );
    private static final long INITIAL_STREAM_TAIL_BYTES = 128 * 1024;

    private final Path stdoutPath;
    private final Path stderrPath;
    private final Path sessionsDir;
    private final Map<String, Long> streamStartOffsets;
    private final boolean streamBaselineCaptured;
    private long stdoutOffset;
    private long stderrOffset;
    private long streamOffset = -1;
    private long sequence;
    private String sessionId;
    private boolean initialStreamTail;
    private byte[] pendingStreamBytes = new byte[0];
    private AcpxSessionStreamProjector streamProjector = new AcpxSessionStreamProjector();

    AgentLifecycleEventReader(Path stdoutPath, Path stderrPath) {
        this(stdoutPath, stderrPath, Path.of(System.getProperty("user.home"), ".acpx", "sessions"));
    }

    AgentLifecycleEventReader(Path stdoutPath, Path stderrPath, Path sessionsDir) {
        this(stdoutPath, stderrPath, sessionsDir, null);
    }

    AgentLifecycleEventReader(
            Path stdoutPath,
            Path stderrPath,
            Path sessionsDir,
            Map<String, Long> streamStartOffsets
    ) {
        this.stdoutPath = stdoutPath;
        this.stderrPath = stderrPath;
        this.sessionsDir = sessionsDir;
        this.streamBaselineCaptured = streamStartOffsets != null;
        this.streamStartOffsets = streamStartOffsets == null ? Map.of() : Map.copyOf(streamStartOffsets);
    }

    List<AgentLifecycleEvent> readNewEvents() {
        var events = new ArrayList<AgentLifecycleEvent>();
        readNewText(stdoutPath, stdoutOffset).ifPresent(chunk -> {
            stdoutOffset += chunk.bytesRead();
            for (var line : chunk.text().lines().toList()) {
                events.add(AgentLifecycleEvent.stdoutLine(sequence++, line));
            }
        });
        readNewText(stderrPath, stderrOffset).ifPresent(chunk -> {
            stderrOffset += chunk.bytesRead();
            updateSessionId(chunk.text());
            for (var line : chunk.text().lines().toList()) {
                events.add(AgentLifecycleEvent.stderrLine(sequence++, line));
            }
        });
        streamPath().ifPresent(path -> {
            initializeStreamOffset(path);
            readNewBytes(path, streamOffset).ifPresent(chunk -> {
                var initialTail = initialStreamTail;
                initialStreamTail = false;
                streamOffset += chunk.bytesRead();
                for (var line : completeStreamLines(chunk.bytes())) {
                    var event = streamProjector.accept(
                            sequence++, AcpxStreamWireLine.parse(line), !initialTail);
                    event.filter(value -> !initialTail || value.kind() != AgentLifecycleEvent.Kind.PROVIDER_FAILURE)
                            .ifPresent(events::add);
                }
            });
        });
        return events;
    }

    long nextSequence() {
        return sequence++;
    }

    AcpxPromptCompletion promptCompletion() {
        return streamProjector.promptCompletion();
    }

    private void updateSessionId(String text) {
        var matcher = SESSION_ID_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            var discovered = matcher.group(1);
            if (!discovered.equals(sessionId)) {
                sessionId = discovered;
                streamOffset = -1;
                initialStreamTail = false;
                pendingStreamBytes = new byte[0];
                streamProjector = new AcpxSessionStreamProjector();
            }
        }
    }

    private Optional<Path> streamPath() {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        if (streamBaselineCaptured
                && !streamStartOffsets.isEmpty()
                && !streamStartOffsets.containsKey(sessionId)) {
            return Optional.empty();
        }
        var path = sessionsDir.resolve(sessionId + ".stream.ndjson");
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    private void initializeStreamOffset(Path path) {
        if (streamOffset >= 0) {
            return;
        }
        try {
            var size = Files.size(path);
            if (streamBaselineCaptured) {
                streamOffset = Math.min(size, Math.max(0, streamStartOffsets.getOrDefault(sessionId, 0L)));
                initialStreamTail = false;
                return;
            }
            streamOffset = Math.max(0, size - INITIAL_STREAM_TAIL_BYTES);
            initialStreamTail = streamOffset < size;
        } catch (IOException ignored) {
            streamOffset = 0;
            initialStreamTail = false;
        }
    }

    private Optional<Chunk> readNewText(Path path, long offset) {
        return readNewBytes(path, offset)
                .map(chunk -> new Chunk(new String(chunk.bytes(), StandardCharsets.UTF_8), chunk.bytesRead()));
    }

    private Optional<ByteChunk> readNewBytes(Path path, long offset) {
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            var size = Files.size(path);
            if (size <= offset) {
                return Optional.empty();
            }
            var bytesToRead = size - offset;
            if (bytesToRead > Integer.MAX_VALUE) {
                offset = size - Integer.MAX_VALUE;
                bytesToRead = Integer.MAX_VALUE;
            }
            var buffer = ByteBuffer.allocate((int) bytesToRead);
            var bytesRead = 0;
            try (var channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
                channel.position(offset);
                while (buffer.hasRemaining()) {
                    var count = channel.read(buffer);
                    if (count <= 0) {
                        break;
                    }
                    bytesRead += count;
                }
            }
            return bytesRead == 0
                    ? Optional.empty()
                    : Optional.of(new ByteChunk(Arrays.copyOf(buffer.array(), bytesRead), bytesRead));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private List<String> completeStreamLines(byte[] chunk) {
        var incoming = chunk == null ? new byte[0] : chunk;
        var combined = Arrays.copyOf(pendingStreamBytes, pendingStreamBytes.length + incoming.length);
        System.arraycopy(incoming, 0, combined, pendingStreamBytes.length, incoming.length);
        var lastNewline = -1;
        for (int index = combined.length - 1; index >= 0; index--) {
            if (combined[index] == '\n') {
                lastNewline = index;
                break;
            }
        }
        if (lastNewline < 0) {
            pendingStreamBytes = combined;
            return List.of();
        }
        pendingStreamBytes = Arrays.copyOfRange(combined, lastNewline + 1, combined.length);
        var lines = new ArrayList<String>();
        var lineStart = 0;
        for (int index = 0; index <= lastNewline; index++) {
            if (combined[index] != '\n') {
                continue;
            }
            var lineEnd = index > lineStart && combined[index - 1] == '\r' ? index - 1 : index;
            lines.add(new String(combined, lineStart, lineEnd - lineStart, StandardCharsets.UTF_8));
            lineStart = index + 1;
        }
        return List.copyOf(lines);
    }

    private record Chunk(String text, long bytesRead) {
    }

    private record ByteChunk(byte[] bytes, long bytesRead) {
    }
}
