package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AtomicJsonWriter {
    private final ObjectMapper objectMapper;

    public AtomicJsonWriter() {
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void write(Path target, Object value) throws IOException {
        Files.createDirectories(target.getParent());
        var tmp = target.resolveSibling(target.getFileName() + ".tmp");
        objectMapper.writeValue(tmp.toFile(), value);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveFailure) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
