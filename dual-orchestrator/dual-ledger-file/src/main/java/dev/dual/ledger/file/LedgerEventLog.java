package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class LedgerEventLog {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper jsonlMapper;

    LedgerEventLog(AtomicJsonWriter jsonWriter, ObjectMapper jsonlMapper) {
        this.jsonWriter = jsonWriter;
        this.jsonlMapper = jsonlMapper;
    }

    void appendEvent(WorkstreamPaths paths, String eventId, String eventType, Map<String, Object> payload)
            throws IOException {
        var event = new LinkedHashMap<String, Object>();
        event.put("event_id", eventId);
        event.put("ts", Instant.now().toString());
        event.put("event_type", eventType);
        event.put("payload", payload == null ? Map.of() : payload);
        Files.createDirectories(paths.workstreamDir());
        var line = jsonlMapper.writeValueAsString(event);
        Files.writeString(
                paths.eventsJsonl(),
                line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    void writeInstanceFromCurrent(WorkstreamPaths paths, WorkflowCurrentDocument current, Instant updatedAt)
            throws IOException {
        var objectMapper = jsonWriter.objectMapper();
        var existing = objectMapper.readValue(paths.workflowInstanceJson().toFile(), WorkflowInstanceDocument.class);
        jsonWriter.write(paths.workflowInstanceJson(), new WorkflowInstanceDocument(
                existing.schemaVersion(),
                existing.workflowInstanceId(),
                existing.workflowId(),
                existing.definitionVersion(),
                current.status(),
                current.activeNodeInstanceIds(),
                current.lastEventId(),
                existing.createdAt(),
                updatedAt
        ));
    }
}
