package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class DeliveryLedgerWriter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private DeliveryLedgerWriter() {
    }

    static void update(Path deliveryDir, Map<String, Object> updates) throws IOException {
        Files.createDirectories(deliveryDir);
        var path = deliveryDir.resolve("delivery.json");
        var document = new LinkedHashMap<String, Object>();
        if (Files.exists(path)) {
            document.putAll(OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE));
        } else {
            document.put("schemaVersion", 1);
        }
        document.putAll(updates);
        document.put("updatedAt", Instant.now().toString());
        OBJECT_MAPPER.writeValue(path.toFile(), document);
    }
}
