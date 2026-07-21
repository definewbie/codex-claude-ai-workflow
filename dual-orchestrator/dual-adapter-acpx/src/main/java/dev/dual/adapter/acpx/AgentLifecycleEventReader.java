package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
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
    private long stdoutOffset;
    private long stderrOffset;
    private long streamOffset = -1;
    private long sequence;
    private String sessionId;
    private boolean initialStreamTail;
    private final AcpxSessionStreamProjector streamProjector = new AcpxSessionStreamProjector();

    AgentLifecycleEventReader(Path stdoutPath, Path stderrPath) {
        this(stdoutPath, stderrPath, Path.of(System.getProperty("user.home"), ".acpx", "sessions"));
    }

    AgentLifecycleEventReader(Path stdoutPath, Path stderrPath, Path sessionsDir) {
        this.stdoutPath = stdoutPath;
        this.stderrPath = stderrPath;
        this.sessionsDir = sessionsDir;
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
            readNewText(path, streamOffset).ifPresent(chunk -> {
                var initialTail = initialStreamTail;
                initialStreamTail = false;
                streamOffset += chunk.bytesRead();
                for (var line : chunk.text().lines().toList()) {
                    var event = streamProjector.accept(sequence++, AcpxStreamWireLine.parse(line));
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

    private void updateSessionId(String text) {
        var matcher = SESSION_ID_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            sessionId = matcher.group(1);
            streamOffset = -1;
        }
    }

    private Optional<Path> streamPath() {
        if (sessionId == null || sessionId.isBlank()) {
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
            streamOffset = Math.max(0, size - INITIAL_STREAM_TAIL_BYTES);
            initialStreamTail = streamOffset < size;
        } catch (IOException ignored) {
            streamOffset = 0;
            initialStreamTail = false;
        }
    }

    private Optional<Chunk> readNewText(Path path, long offset) {
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
            try (var channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
                channel.position(offset);
                channel.read(buffer);
            }
            buffer.flip();
            return Optional.of(new Chunk(StandardCharsets.UTF_8.decode(buffer).toString(), bytesToRead));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private record Chunk(String text, long bytesRead) {
    }
}
