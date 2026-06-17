package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes durable execution facts for workflow and node executions.
 *
 * This writer is intentionally narrow: it records runtime evidence such as
 * status, pid, duration, output refs, adapter metadata, and exceptions. It does
 * not route workflow state and does not decide whether the product goal is
 * satisfied; those belong to workflow routing and domain artifacts.
 */
final class ExecutionLedgerWriter {
    private final AtomicJsonWriter jsonWriter;

    ExecutionLedgerWriter(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    WorkflowExecutionDocument startWorkflowExecution(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            String trigger
    ) throws IOException {
        var now = Instant.now();
        var document = new WorkflowExecutionDocument(
                1,
                nextId("wfe"),
                current.workflowInstanceId(),
                paths.workstreamId().value(),
                textOrDefault(trigger, "resume"),
                WorkflowExecutionStatus.RUNNING,
                List.of(),
                "",
                now,
                null,
                null
        );
        jsonWriter.write(paths.workflowExecutionJson(document.workflowExecutionId()), document);
        return document;
    }

    WorkflowExecutionDocument finishWorkflowExecution(
            WorkstreamPaths paths,
            WorkflowExecutionDocument workflowExecution,
            List<String> nodeExecutionIds,
            WorkflowExecutionStatus status,
            String stopReason
    ) throws IOException {
        var finishedAt = Instant.now();
        var document = new WorkflowExecutionDocument(
                workflowExecution.schemaVersion(),
                workflowExecution.workflowExecutionId(),
                workflowExecution.workflowInstanceId(),
                workflowExecution.workstreamId(),
                workflowExecution.trigger(),
                status == null ? WorkflowExecutionStatus.STOPPED : status,
                List.copyOf(nodeExecutionIds == null ? List.of() : nodeExecutionIds),
                stopReason == null ? "" : stopReason,
                workflowExecution.startedAt(),
                finishedAt,
                durationMs(workflowExecution.startedAt(), finishedAt)
        );
        jsonWriter.write(paths.workflowExecutionJson(document.workflowExecutionId()), document);
        return document;
    }

    NodeExecutionDocument startNodeExecution(
            WorkstreamPaths paths,
            String workflowExecutionId,
            NodeInstanceDocument node,
            AgentTaskDocument agentTask,
            long pid
    ) throws IOException {
        var now = Instant.now();
        var document = new NodeExecutionDocument(
                1,
                nextId("nxe"),
                agentTask == null ? "" : agentTask.agentTaskId(),
                workflowExecutionId,
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.handlerType(),
                NodeExecutionStatus.RUNNING,
                pid,
                "",
                null,
                List.of(),
                Map.of(),
                "",
                node.runRef(),
                "",
                "",
                "",
                null,
                now,
                null,
                null
        );
        jsonWriter.write(paths.nodeExecutionJson(document.nodeExecutionId()), document);
        return document;
    }

    NodeExecutionDocument finishNodeExecution(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            NodeResult result
    ) throws IOException {
        var finishedAt = Instant.now();
        var resultMap = result == null ? Map.<String, Object>of() : result.result();
        var document = new NodeExecutionDocument(
                nodeExecution.schemaVersion(),
                nodeExecution.nodeExecutionId(),
                nodeExecution.agentTaskId(),
                nodeExecution.workflowExecutionId(),
                nodeExecution.nodeInstanceId(),
                nodeExecution.nodeDefinitionId(),
                nodeExecution.handlerType(),
                executionStatus(result == null ? null : result.status()),
                nodeExecution.pid(),
                result == null ? "" : result.outcome(),
                result == null ? null : result.status(),
                result == null ? List.of() : result.outputRefs(),
                resultMap,
                result == null ? "" : nullToEmpty(result.errorMessage()),
                text(resultMap.get("run_id")),
                text(resultMap.get("run_metadata")),
                text(resultMap.get("agent")),
                text(resultMap.get("session")),
                intValue(resultMap.get("exit_code")),
                nodeExecution.startedAt(),
                finishedAt,
                durationMs(nodeExecution.startedAt(), finishedAt)
        );
        jsonWriter.write(paths.nodeExecutionJson(document.nodeExecutionId()), document);
        return document;
    }

    NodeExecutionDocument failNodeExecution(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            Exception failure
    ) throws IOException {
        var finishedAt = Instant.now();
        var details = new LinkedHashMap<String, Object>();
        details.put("exception_type", failure == null ? "" : failure.getClass().getName());
        details.put("exception_message", failure == null ? "" : nullToEmpty(failure.getMessage()));
        var document = new NodeExecutionDocument(
                nodeExecution.schemaVersion(),
                nodeExecution.nodeExecutionId(),
                nodeExecution.agentTaskId(),
                nodeExecution.workflowExecutionId(),
                nodeExecution.nodeInstanceId(),
                nodeExecution.nodeDefinitionId(),
                nodeExecution.handlerType(),
                NodeExecutionStatus.FAILED,
                nodeExecution.pid(),
                "exception",
                NodeStatus.FAILED,
                List.of(),
                details,
                failure == null ? "" : nullToEmpty(failure.getMessage()),
                nodeExecution.runRef(),
                nodeExecution.agentRunRef(),
                nodeExecution.agent(),
                nodeExecution.session(),
                null,
                nodeExecution.startedAt(),
                finishedAt,
                durationMs(nodeExecution.startedAt(), finishedAt)
        );
        jsonWriter.write(paths.nodeExecutionJson(document.nodeExecutionId()), document);
        return document;
    }

    List<NodeExecutionDocument> readNodeExecutions(WorkstreamPaths paths) throws IOException {
        if (!java.nio.file.Files.isDirectory(paths.nodeExecutionsDir())) {
            return List.of();
        }
        var documents = new ArrayList<NodeExecutionDocument>();
        try (var stream = java.nio.file.Files.list(paths.nodeExecutionsDir())) {
            var files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (var file : files) {
                documents.add(jsonWriter.objectMapper().readValue(file.toFile(), NodeExecutionDocument.class));
            }
        }
        return documents;
    }

    Optional<NodeExecutionDocument> latestNodeExecution(WorkstreamPaths paths, NodeInstanceDocument node) throws IOException {
        if (node.latestExecutionId().isBlank()) {
            return Optional.empty();
        }
        var path = paths.nodeExecutionJson(node.latestExecutionId());
        if (!java.nio.file.Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(jsonWriter.objectMapper().readValue(path.toFile(), NodeExecutionDocument.class));
    }

    NodeExecutionDocument markNodeExecutionStale(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            String note
    ) throws IOException {
        var finishedAt = Instant.now();
        var staleResult = new LinkedHashMap<String, Object>();
        staleResult.putAll(nodeExecution.result());
        staleResult.put("reason", "STALE_RUNNING_PROCESS");
        staleResult.put("stale_running_pid", nodeExecution.pid() == null ? "" : nodeExecution.pid());
        staleResult.put("recovery_note", nullToEmpty(note));
        var document = new NodeExecutionDocument(
                nodeExecution.schemaVersion(),
                nodeExecution.nodeExecutionId(),
                nodeExecution.agentTaskId(),
                nodeExecution.workflowExecutionId(),
                nodeExecution.nodeInstanceId(),
                nodeExecution.nodeDefinitionId(),
                nodeExecution.handlerType(),
                NodeExecutionStatus.STALE,
                nodeExecution.pid(),
                "STALE_RUNNING_PROCESS",
                NodeStatus.FAILED,
                nodeExecution.outputRefs(),
                staleResult,
                "Running node had no live process.",
                nodeExecution.runRef(),
                nodeExecution.agentRunRef(),
                nodeExecution.agent(),
                nodeExecution.session(),
                nodeExecution.exitCode(),
                nodeExecution.startedAt(),
                finishedAt,
                durationMs(nodeExecution.startedAt(), finishedAt)
        );
        jsonWriter.write(paths.nodeExecutionJson(document.nodeExecutionId()), document);
        return document;
    }

    private static NodeExecutionStatus executionStatus(NodeStatus status) {
        if (status == null) {
            return NodeExecutionStatus.FAILED;
        }
        return switch (status) {
            case COMPLETED -> NodeExecutionStatus.SUCCEEDED;
            case FAILED, BLOCKED -> NodeExecutionStatus.FAILED;
            case RUNNING -> NodeExecutionStatus.RUNNING;
            case WAITING -> NodeExecutionStatus.WAITING;
            case READY -> NodeExecutionStatus.READY;
            case CREATED -> NodeExecutionStatus.CREATED;
            case SKIPPED -> NodeExecutionStatus.SKIPPED;
        };
    }

    private static Long durationMs(Instant startedAt, Instant finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private static String nextId(String prefix) {
        return prefix + "-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
