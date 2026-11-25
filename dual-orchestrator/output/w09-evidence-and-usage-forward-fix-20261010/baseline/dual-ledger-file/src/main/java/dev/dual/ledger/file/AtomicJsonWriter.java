package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicJsonWriter {
    private final ObjectMapper objectMapper;
    private final LedgerSession session;

    public AtomicJsonWriter() {
        this(null);
    }

    AtomicJsonWriter(LedgerSession session) {
        this.session = session;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .registerModule(new EdgeConditionModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void write(Path target, Object value) throws IOException {
        writeBytes(target, objectMapper.writeValueAsBytes(value));
    }

    /** Atomic replacement only. A failed atomic move must never fall back to a visible partial write. */
    public void writeBytes(Path target, byte[] bytes) throws IOException {
        if (session != null) { session.writeBytes(target, bytes); return; }
        var absolute = target.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        var tmp = Files.createTempFile(absolute.getParent(), "." + absolute.getFileName() + ".", ".tmp");
        try {
            Files.write(tmp, bytes);
            Files.move(tmp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public <T> T read(Path path, Class<T> type) throws IOException {
        return objectMapper.readValue(readBytes(path), type);
    }

    public <T> T read(Path path, TypeReference<T> type) throws IOException {
        return objectMapper.readValue(readBytes(path), type);
    }

    public JsonNode readTree(Path path) throws IOException { return objectMapper.readTree(readBytes(path)); }
    public byte[] readBytes(Path path) throws IOException {
        return session == null ? Files.readAllBytes(path) : session.readBytes(path);
    }
    public String readString(Path path) throws IOException {
        return new String(readBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }
    public boolean exists(Path path) throws IOException {
        return session == null ? Files.exists(path) : session.exists(path);
    }
    public java.util.List<Path> list(Path path) throws IOException {
        if (session != null) return session.list(path);
        if (!Files.isDirectory(path)) return java.util.List.of();
        try (var stream = Files.list(path)) { return stream.sorted().toList(); }
    }
    void appendEvents(Path path, byte[] bytes) throws IOException {
        if (session == null) throw new IllegalStateException("event append requires an explicit ledger write session");
        session.appendEvents(path, bytes);
    }
}
