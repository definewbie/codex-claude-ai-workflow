package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;

public final class DeliveryMergePlanWriter {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(WorkstreamPaths paths, DeliveryMergePlan plan) throws IOException {
        Files.createDirectories(paths.deliveryDir());
        objectMapper.writeValue(paths.deliveryMergePlanJson().toFile(), plan);
    }
}
