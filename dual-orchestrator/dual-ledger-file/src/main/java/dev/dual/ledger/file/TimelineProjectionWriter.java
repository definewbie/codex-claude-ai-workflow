package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TimelineProjectionWriter {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;

    TimelineProjectionWriter(AtomicJsonWriter jsonWriter, ObjectMapper objectMapper) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = objectMapper;
    }

    void write(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.workflowCurrentJson())) {
            return;
        }
        var current = objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
        var nodes = readNodes(paths).stream()
                .sorted(TimelineProjectionWriter::compareNodes)
                .map(this::nodeView)
                .toList();

        var projection = new LinkedHashMap<String, Object>();
        projection.put("schemaVersion", 1);
        projection.put("workstreamId", current.workstreamId());
        projection.put("workflowInstanceId", current.workflowInstanceId());
        projection.put("workflowId", current.workflowId());
        projection.put("definitionVersion", current.definitionVersion());
        projection.put("workflowStatus", current.status());
        projection.put("activeNodeInstanceIds", current.activeNodeInstanceIds());
        projection.put("openGateIds", current.openGateIds());
        projection.put("updatedAt", current.updatedAt());
        projection.put("nodes", nodes);

        jsonWriter.write(paths.timelineJson(), projection);
    }

    private List<NodeInstanceDocument> readNodes(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.nodesDir())) {
            return List.of();
        }
        try (var stream = Files.list(paths.nodesDir())) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> {
                        try {
                            return objectMapper.readValue(path.toFile(), NodeInstanceDocument.class);
                        } catch (IOException e) {
                            throw new TimelineProjectionException(e);
                        }
                    })
                    .toList();
        } catch (TimelineProjectionException e) {
            throw e.unwrap();
        }
    }

    private Map<String, Object> nodeView(NodeInstanceDocument node) {
        var view = new LinkedHashMap<String, Object>();
        view.put("nodeInstanceId", node.nodeInstanceId());
        view.put("nodeDefinitionId", node.nodeDefinitionId());
        view.put("kind", node.kind());
        view.put("handlerType", node.handlerType());
        view.put("status", node.status());
        view.put("stageId", node.stageId());
        view.put("iterationId", node.iterationId());
        view.put("inputRefs", node.inputRefs());
        view.put("outputRefs", node.outputRefs());
        view.put("runRef", node.runRef());
        view.put("startedAt", node.startedAt());
        view.put("finishedAt", node.finishedAt());
        view.put("durationMs", durationMs(node.startedAt(), node.finishedAt()));
        view.put("result", node.result());
        return view;
    }

    private Long durationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private static int compareNodes(NodeInstanceDocument left, NodeInstanceDocument right) {
        var leftInstant = timelineInstant(left);
        var rightInstant = timelineInstant(right);
        var byTime = leftInstant.compareTo(rightInstant);
        if (byTime != 0) {
            return byTime;
        }
        return left.nodeInstanceId().compareTo(right.nodeInstanceId());
    }

    private static Instant timelineInstant(NodeInstanceDocument node) {
        if (node.startedAt() != null) {
            return node.startedAt();
        }
        if (node.finishedAt() != null) {
            return node.finishedAt();
        }
        return Instant.MAX;
    }

    private static final class TimelineProjectionException extends RuntimeException {
        TimelineProjectionException(IOException cause) {
            super(cause);
        }

        IOException unwrap() {
            return (IOException) getCause();
        }
    }
}
