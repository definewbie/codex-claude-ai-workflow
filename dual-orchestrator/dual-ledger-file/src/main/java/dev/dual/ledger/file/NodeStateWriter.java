package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
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

    NodeInstanceDocument markQueuedForAgentTask(WorkstreamPaths paths, String nodeInstanceId, String agentTaskId)
            throws IOException {
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
                LedgerUtils.mergedResult(node.result(), Map.of(
                        "queued_agent_task_id", agentTaskId,
                        "dispatch_state", "QUEUED"
                )),
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
        retryActiveNode(paths, note, Map.of(), "node_retry_requested");
    }

    void approvePublicWebSearch(WorkstreamPaths paths, String note, boolean publicRepositoryAttested)
            throws IOException {
        if (!publicRepositoryAttested) {
            throw new IOException("public repository attestation is required to approve WebSearch");
        }
        if (note == null || note.isBlank()) {
            throw new IOException("a human approval note is required to approve WebSearch");
        }
        var current = loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active node awaiting permission approval");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.status() != NodeStatus.FAILED) {
            throw new IOException("active node is not awaiting permission approval: "
                    + node.nodeDefinitionId() + " (" + node.status() + ")");
        }
        var assessment = RecoveryAssessment.from(
                node,
                paths.workspaceRoot(),
                paths.workstreamId().value());
        if (!assessment.allowedActions().contains("approve_public_websearch")) {
            throw new IOException("current failure does not allow public WebSearch approval");
        }
        var request = publicWebSearchEscalation(paths, node);
        var approval = new LinkedHashMap<String, Object>();
        approval.put("schema_version", 1);
        approval.put("type", "PUBLIC_WEBSEARCH");
        approval.put("workstream_id", paths.workstreamId().value());
        approval.put("node_instance_id", node.nodeInstanceId());
        approval.put("tool_name", "WebSearch");
        approval.put("tool_kind", "fetch");
        approval.put("source_run_ref", request.runRef());
        approval.put("source_tool_call_id", request.toolCallId());
        approval.put("source_query_sha256", sha256(request.resource()));
        approval.put("public_repository_attested", true);
        approval.put("note", note.trim());
        approval.put("approved_at", Instant.now().toString());
        retryActiveNode(
                paths,
                note,
                Map.of("permission_approval", Map.copyOf(approval)),
                "public_websearch_approved");
    }

    private void retryActiveNode(
            WorkstreamPaths paths,
            String note,
            Map<String, Object> additionalResult,
            String eventType
    ) throws IOException {
        var current = loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active node to retry");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.status() != NodeStatus.FAILED && node.status() != NodeStatus.BLOCKED) {
            throw new IOException("active node is not retryable: " + node.nodeDefinitionId() + " (" + node.status() + ")");
        }
        var retryResult = new LinkedHashMap<String, Object>();
        retryResult.put("retry_of_status", node.status().name());
        retryResult.put("retry_note", note == null ? "" : note);
        retryResult.putAll(additionalResult);
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
                retryResult,
                node.runRef(),
                node.latestExecutionId(),
                null,
                null
        );
        jsonWriter.write(paths.nodeJson(node.nodeInstanceId()), retry);

        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        var eventDetails = new LinkedHashMap<String, Object>();
        eventDetails.put("node", node.nodeDefinitionId());
        eventDetails.put("node_instance_id", node.nodeInstanceId());
        eventDetails.put("previous_status", node.status().name());
        eventDetails.put("note", note == null ? "" : note);
        eventDetails.put("recovery_gate_ids", current.openGateIds());
        if (additionalResult.containsKey("permission_approval")) {
            eventDetails.put("permission_approval", additionalResult.get("permission_approval"));
        }
        eventLog.appendEvent(paths, eventId, eventType, Map.copyOf(eventDetails));
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

    private PermissionEscalation publicWebSearchEscalation(WorkstreamPaths paths, NodeInstanceDocument node)
            throws IOException {
        var runRef = text(node.result().get("run_metadata"));
        if (runRef.isBlank()) {
            throw new IOException("permission escalation is missing run metadata");
        }
        var root = paths.workspaceRoot().toAbsolutePath().normalize();
        var runPath = root.resolve(runRef).normalize();
        if (!runPath.startsWith(paths.workstreamDir().toAbsolutePath().normalize())
                || !Files.isRegularFile(runPath)) {
            throw new IOException("permission escalation run metadata is invalid: " + runRef);
        }
        var run = objectMapper.readTree(runPath.toFile());
        var timeline = run.path("executionTimeline");
        if (!timeline.isArray()) {
            throw new IOException("permission escalation execution timeline is missing");
        }
        for (var index = timeline.size() - 1; index >= 0; index--) {
            var event = timeline.get(index);
            if (!"permission_decision".equals(event.path("kind").asText())) {
                continue;
            }
            var details = event.path("details");
            if ("ESCALATION_REQUIRED".equals(details.path("authorityDisposition").asText())
                    && "fetch".equals(details.path("toolKind").asText())
                    && "WebSearch".equals(details.path("toolName").asText())
                    && !details.path("toolCallId").asText().isBlank()
                    && !details.path("resource").asText().isBlank()) {
                return new PermissionEscalation(
                        runRef,
                        details.path("toolCallId").asText(),
                        details.path("resource").asText());
            }
        }
        throw new IOException("no exact WebSearch permission escalation is awaiting approval");
    }

    private static String sha256(String value) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IOException("SHA-256 is unavailable", failure);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private record PermissionEscalation(String runRef, String toolCallId, String resource) {
    }

    void replayDeterministicClassification(WorkstreamPaths paths, String note) throws IOException {
        if (note == null || note.isBlank()) {
            throw new IOException("classification replay note is required");
        }
        var current = loadCurrent(paths);
        if (current.status() != WorkflowStatus.WAITING_FOR_RECOVERY
                || current.activeNodeInstanceIds().size() != 1) {
            throw new IOException("classification replay requires exactly one active recovery node");
        }
        var failed = loadNode(paths, current.activeNodeInstanceIds().getFirst());
        if (failed.status() != NodeStatus.FAILED
                || !"contract.compile".equals(failed.nodeDefinitionId())
                || !"TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING".equals(
                LedgerUtils.text(LedgerUtils.resultValue(failed.result(), "reason")))) {
            throw new IOException("active recovery node is not a replayable classification misroute");
        }

        var iteration = objectMapper.readValue(
                paths.iterationJson(failed.iterationId()).toFile(), WorkflowIterationDocument.class);
        if (!failed.nodeDefinitionId().equals(iteration.targetNodeDefinitionId())
                || iteration.triggerNodeInstanceId().isBlank()) {
            throw new IOException("failed node does not have an authoritative classification trigger");
        }
        var classifier = loadNode(paths, iteration.triggerNodeInstanceId());
        if (!"acceptance.classify_failure".equals(classifier.nodeDefinitionId())
                || !"acceptance.failure_classify".equals(classifier.handlerType())
                || classifier.status() != NodeStatus.COMPLETED
                || !classifier.outputRefs().equals(failed.inputRefs())) {
            throw new IOException("failed node is not lineage-bound to a completed acceptance classifier");
        }

        GateDocument recoveryGate = null;
        for (var gateId : current.openGateIds()) {
            var gatePath = paths.gateJson(gateId);
            if (!java.nio.file.Files.isRegularFile(gatePath)) {
                continue;
            }
            var candidate = objectMapper.readValue(gatePath.toFile(), GateDocument.class);
            if (failed.nodeInstanceId().equals(candidate.nodeInstanceId()) && "open".equals(candidate.status())) {
                recoveryGate = candidate;
                break;
            }
        }
        if (recoveryGate == null) {
            throw new IOException("classification replay requires the failed node recovery gate");
        }

        var now = Instant.now();
        jsonWriter.write(paths.nodeJson(failed.nodeInstanceId()), new NodeInstanceDocument(
                failed.schemaVersion(),
                failed.nodeInstanceId(),
                failed.nodeDefinitionId(),
                failed.kind(),
                failed.handlerType(),
                NodeStatus.SKIPPED,
                failed.stageId(),
                failed.iterationId(),
                failed.inputRefs(),
                failed.inputTransition(),
                failed.outputRefs(),
                LedgerUtils.mergedResult(failed.result(), Map.of(
                        "classification_replay_compensated", true,
                        "classification_node_instance_id", classifier.nodeInstanceId(),
                        "compensation_note", note
                )),
                failed.runRef(),
                failed.latestExecutionId(),
                failed.startedAt(),
                now
        ));
        jsonWriter.write(paths.nodeJson(classifier.nodeInstanceId()), new NodeInstanceDocument(
                classifier.schemaVersion(),
                classifier.nodeInstanceId(),
                classifier.nodeDefinitionId(),
                classifier.kind(),
                classifier.handlerType(),
                NodeStatus.READY,
                classifier.stageId(),
                classifier.iterationId(),
                classifier.inputRefs(),
                classifier.inputTransition(),
                List.of(),
                Map.of(
                        "replay_of_status", classifier.status().name(),
                        "recovery_note", note,
                        "superseded_node_instance_id", failed.nodeInstanceId()
                ),
                classifier.runRef(),
                classifier.latestExecutionId(),
                null,
                null
        ));
        jsonWriter.write(
                paths.gateJson(recoveryGate.gateId()),
                recoveryGate.resolved("replay_classification", note)
        );

        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        eventLog.appendEvent(paths, eventId, "deterministic_classification_replay_requested", Map.of(
                "classification_node_instance_id", classifier.nodeInstanceId(),
                "superseded_node_instance_id", failed.nodeInstanceId(),
                "recovery_gate_id", recoveryGate.gateId(),
                "note", note
        ));
        var resumed = new WorkflowCurrentDocument(
                current.schemaVersion(),
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.RUNNING,
                List.of(classifier.nodeInstanceId()),
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
