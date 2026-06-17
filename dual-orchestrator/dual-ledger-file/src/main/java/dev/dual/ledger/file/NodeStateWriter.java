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
                node.inputTransition(),
                node.outputRefs(),
                LedgerUtils.mergedResult(node.result(), Map.of("running_pid", pid)),
                node.runRef(),
                node.latestExecutionId(),
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
                node.inputTransition(),
                List.of(),
                Map.of(
                        "retry_of_status", node.status().name(),
                        "auto_retry", true,
                        "auto_retry_count", retryCount + 1,
                        "previous_running_pid", LedgerUtils.resultValueOrDefault(node.result(), "running_pid", "")
                ),
                node.runRef(),
                node.latestExecutionId(),
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
                node.inputTransition(),
                List.of(),
                Map.of(
                        "retry_of_status", node.status().name(),
                        "retry_note", note == null ? "" : note
                ),
                node.runRef(),
                node.latestExecutionId(),
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

    void recoverStaleRunningNode(WorkstreamPaths paths, String note) throws IOException {
        var current = loadCurrent(paths);
        if (current.status() != WorkflowStatus.RUNNING) {
            throw new IOException("workflow is not running: " + current.status());
        }
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active running node to recover");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.status() != NodeStatus.RUNNING) {
            throw new IOException("active node is not running: " + node.nodeDefinitionId() + " (" + node.status() + ")");
        }
        var pid = runningPid(node);
        if (pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            throw new IOException("running process is still alive: " + pid);
        }

        var now = Instant.now();
        var failed = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                NodeStatus.FAILED,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                node.inputTransition(),
                node.outputRefs(),
                LedgerUtils.mergedResult(node.result(), Map.of(
                        "reason", "STALE_RUNNING_PROCESS",
                        "message", "Running node had no live process and was moved to recovery.",
                        "recoverable", true,
                        "stale_running_pid", pid <= 0 ? "" : pid,
                        "recovery_note", note == null ? "" : note
                )),
                node.runRef(),
                node.latestExecutionId(),
                node.startedAt(),
                now
        );
        jsonWriter.write(paths.nodeJson(node.nodeInstanceId()), failed);

        var recoveryGateId = "recovery-" + node.nodeInstanceId();
        jsonWriter.write(paths.gateJson(recoveryGateId), new GateDocument(
                1,
                recoveryGateId,
                node.nodeInstanceId(),
                "recovery.review",
                "open",
                List.of("retry", "abort"),
                now,
                null,
                Map.of(
                        "node", node.nodeDefinitionId(),
                        "node_status", NodeStatus.FAILED.name(),
                        "outcome", "STALE_RUNNING_PROCESS",
                        "reason", "STALE_RUNNING_PROCESS",
                        "stale_running_pid", pid <= 0 ? "" : pid,
                        "note", note == null ? "" : note
                )
        ));

        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        eventLog.appendEvent(paths, eventId, "node_stale_running_recovered", Map.of(
                "node", node.nodeDefinitionId(),
                "node_instance_id", node.nodeInstanceId(),
                "previous_status", node.status().name(),
                "stale_running_pid", pid <= 0 ? "" : pid,
                "recovery_gate_id", recoveryGateId,
                "note", note == null ? "" : note
        ));
        var recovered = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.WAITING_FOR_RECOVERY,
                List.of(node.nodeInstanceId()),
                List.of(recoveryGateId),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), recovered);
        eventLog.writeInstanceFromCurrent(paths, recovered, now);
    }

    private static long runningPid(NodeInstanceDocument node) {
        var value = node.result() == null ? null : node.result().get("running_pid");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
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

    void closeMistakenIteration(WorkstreamPaths paths, String note) throws IOException {
        var current = loadCurrent(paths);
        if (current.status() != WorkflowStatus.WAITING_FOR_RECOVERY) {
            throw new IOException("workflow is not waiting for recovery: " + current.status());
        }
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active recovery node to close");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        var iteration = objectMapper.readValue(paths.iterationJson(node.iterationId()).toFile(), WorkflowIterationDocument.class);
        if (!"approved".equals(iteration.triggerAction())) {
            throw new IOException("active iteration was not started by a final gate approval: " + iteration.iterationId());
        }
        if (iteration.triggerNodeInstanceId().isBlank()) {
            throw new IOException("active iteration does not record a trigger node");
        }
        var trigger = loadNode(paths, iteration.triggerNodeInstanceId());
        if (!"gate.human_next_stage_approval".equals(trigger.nodeDefinitionId())) {
            throw new IOException("active iteration was not started from final next-stage gate: " + trigger.nodeDefinitionId());
        }

        var now = Instant.now();
        var skipped = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                NodeStatus.SKIPPED,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                node.inputTransition(),
                node.outputRefs(),
                LedgerUtils.mergedResult(node.result(), Map.of(
                        "closed_as_mistaken_iteration", true,
                        "close_note", note == null ? "" : note
                )),
                node.runRef(),
                node.latestExecutionId(),
                node.startedAt(),
                now
        );
        jsonWriter.write(paths.nodeJson(node.nodeInstanceId()), skipped);

        for (var gateId : current.openGateIds()) {
            var gatePath = paths.gateJson(gateId);
            if (!java.nio.file.Files.isRegularFile(gatePath)) {
                continue;
            }
            var gate = objectMapper.readValue(gatePath.toFile(), GateDocument.class);
            jsonWriter.write(gatePath, gate.resolved("close_mistaken_iteration", note));
        }

        var closeEventId = LedgerUtils.nextEventId(current.lastEventId());
        eventLog.appendEvent(paths, closeEventId, "mistaken_iteration_closed", Map.of(
                "node", node.nodeDefinitionId(),
                "node_instance_id", node.nodeInstanceId(),
                "iteration_id", node.iterationId(),
                "trigger_node_instance_id", iteration.triggerNodeInstanceId(),
                "trigger_action", iteration.triggerAction(),
                "note", note == null ? "" : note
        ));
        var completedEventId = LedgerUtils.nextEventId(closeEventId);
        eventLog.appendEvent(paths, completedEventId, "workflow_completed", Map.of(
                "last_node", trigger.nodeDefinitionId(),
                "outcome", "close_mistaken_iteration"
        ));
        var completed = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.COMPLETED,
                List.of(),
                List.of(),
                completedEventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), completed);
        eventLog.writeInstanceFromCurrent(paths, completed, now);
    }

    NodeInstanceDocument setLatestExecution(WorkstreamPaths paths, String nodeInstanceId, String nodeExecutionId) throws IOException {
        var node = loadNode(paths, nodeInstanceId);
        var updated = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                node.status(),
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                node.inputTransition(),
                node.outputRefs(),
                node.result(),
                node.runRef(),
                nodeExecutionId,
                node.startedAt(),
                node.finishedAt()
        );
        jsonWriter.write(paths.nodeJson(nodeInstanceId), updated);
        return updated;
    }
}
