package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NodeStateWriter {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final LedgerEventLog eventLog;

    NodeStateWriter(AtomicJsonWriter jsonWriter, ObjectMapper objectMapper, LedgerEventLog eventLog) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = objectMapper;
        this.eventLog = eventLog;
    }

    NodeInstanceDocument loadNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        return objectMapper.readValue(paths.nodeJson(nodeInstanceId).toFile(), NodeInstanceDocument.class);
    }

    WorkflowCurrentDocument loadCurrent(WorkstreamPaths paths) throws IOException {
        return objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
    }

    NodeInstanceDocument markRunning(WorkstreamPaths paths, String nodeInstanceId, long pid) throws IOException {
        var node = loadNode(paths, nodeInstanceId);
        var now = Instant.now();
        var updated = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                NodeStatus.RUNNING,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                node.outputRefs(),
                LedgerUtils.mergedResult(node.result(), Map.of("running_pid", pid)),
                node.runRef(),
                now,
                null
        );
        jsonWriter.write(paths.nodeJson(nodeInstanceId), updated);
        return updated;
    }

    NodeInstanceDocument autoRetryNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        return autoRetryNode(paths, nodeInstanceId, 0);
    }

    NodeInstanceDocument autoRetryNode(WorkstreamPaths paths, String nodeInstanceId, long retryDelayMs) throws IOException {
        var node = loadNode(paths, nodeInstanceId);
        if (node.status() != NodeStatus.FAILED) {
            throw new IOException("auto-retry only applies to FAILED nodes, got: " + node.status());
        }
        var countValue = LedgerUtils.resultValueOrDefault(node.result(), "auto_retry_count", 0);
        int retryCount = (countValue instanceof Number n) ? n.intValue() : 0;
        var retry = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                NodeStatus.READY,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                List.of(),
                Map.of(
                        "retry_of_status", node.status().name(),
                        "auto_retry", true,
                        "auto_retry_count", retryCount + 1,
                        "previous_running_pid", LedgerUtils.resultValueOrDefault(node.result(), "running_pid", "")
                ),
                node.runRef(),
                null,
                null
        );
        jsonWriter.write(paths.nodeJson(node.nodeInstanceId()), retry);

        var current = loadCurrent(paths);
        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        var eventPayload = new LinkedHashMap<String, Object>();
        eventPayload.put("node", node.nodeDefinitionId());
        eventPayload.put("node_instance_id", node.nodeInstanceId());
        eventPayload.put("previous_status", node.status().name());
        eventPayload.put("auto_retry_count", retryCount + 1);
        if (retryDelayMs > 0) {
            eventPayload.put("retry_delay_ms", retryDelayMs);
        }
        eventLog.appendEvent(paths, eventId, "node_auto_retry", eventPayload);
        var now = Instant.now();
        var resumed = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.RUNNING,
                List.of(node.nodeInstanceId()),
                List.of(),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), resumed);
        eventLog.writeInstanceFromCurrent(paths, resumed, now);
        return retry;
    }

    void retryActiveNode(WorkstreamPaths paths, String note) throws IOException {
        var current = loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active node to retry");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.status() != NodeStatus.FAILED && node.status() != NodeStatus.BLOCKED) {
            throw new IOException("active node is not retryable: " + node.nodeDefinitionId() + " (" + node.status() + ")");
        }
        var retry = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                NodeStatus.READY,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                List.of(),
                Map.of(
                        "retry_of_status", node.status().name(),
                        "retry_note", note == null ? "" : note
                ),
                node.runRef(),
                null,
                null
        );
        jsonWriter.write(paths.nodeJson(node.nodeInstanceId()), retry);

        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        eventLog.appendEvent(paths, eventId, "node_retry_requested", Map.of(
                "node", node.nodeDefinitionId(),
                "node_instance_id", node.nodeInstanceId(),
                "previous_status", node.status().name(),
                "note", note == null ? "" : note,
                "recovery_gate_ids", current.openGateIds()
        ));
        var now = Instant.now();
        var resumed = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.RUNNING,
                List.of(node.nodeInstanceId()),
                List.of(),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), resumed);
        eventLog.writeInstanceFromCurrent(paths, resumed, now);
    }

    void abortRecovery(WorkstreamPaths paths, String note) throws IOException {
        var current = loadCurrent(paths);
        if (current.status() != WorkflowStatus.WAITING_FOR_RECOVERY) {
            throw new IOException("workflow is not waiting for recovery: " + current.status());
        }
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active recovery node to abort");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        eventLog.appendEvent(paths, eventId, "workflow_aborted", Map.of(
                "node", node.nodeDefinitionId(),
                "node_instance_id", node.nodeInstanceId(),
                "note", note == null ? "" : note,
                "recovery_gate_ids", current.openGateIds()
        ));
        var now = Instant.now();
        var aborted = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.FAILED,
                List.of(),
                List.of(),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), aborted);
        eventLog.writeInstanceFromCurrent(paths, aborted, now);
    }
}
