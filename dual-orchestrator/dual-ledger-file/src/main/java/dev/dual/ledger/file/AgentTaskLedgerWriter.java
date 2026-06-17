package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes the AgentTask dispatch ledger.
 *
 * AgentTask captures "which external agent/tool should execute this node and
 * with which prompt/context contract". It links workflow execution to node
 * execution, but it must not own workflow routing, retry policy, or
 * agent-internal tool calls.
 */
final class AgentTaskLedgerWriter {
    private final AtomicJsonWriter jsonWriter;

    AgentTaskLedgerWriter(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    AgentTaskDocument create(
            WorkstreamPaths paths,
            String workflowExecutionId,
            NodeDefinition definition,
            NodeInstanceDocument node
    ) throws IOException {
        var now = Instant.now();
        var dispatch = new LinkedHashMap<String, Object>();
        dispatch.put("kind", definition.kind().name());
        dispatch.put("handler_type", definition.handlerType());
        dispatch.put("agent", text(definition.config().get("agent")));
        dispatch.put("session", text(definition.config().get("session")));
        dispatch.put("prompt_template", text(definition.config().get("prompt_template")));
        dispatch.put("context_package_ref", latestInputRef(node, "context/").orElse(""));
        dispatch.put("expected_contract", text(definition.config().get("expected_contract")));
        dispatch.put("node_config", definition.config());

        var document = new AgentTaskDocument(
                1,
                nextId("agt"),
                workflowExecutionId,
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                AgentTaskStatus.QUEUED,
                text(definition.config().get("agent")),
                text(definition.config().get("session")),
                text(definition.config().get("prompt_template")),
                latestInputRef(node, "context/").orElse(""),
                text(definition.config().get("expected_contract")),
                dispatch,
                "",
                "",
                now,
                null,
                null
        );
        jsonWriter.write(paths.agentTaskJson(document.agentTaskId()), document);
        return document;
    }

    AgentTaskDocument markRunning(WorkstreamPaths paths, AgentTaskDocument task, String nodeExecutionId)
            throws IOException {
        var document = new AgentTaskDocument(
                task.schemaVersion(),
                task.agentTaskId(),
                task.workflowExecutionId(),
                task.nodeInstanceId(),
                task.nodeDefinitionId(),
                task.nodeKind(),
                task.handlerType(),
                AgentTaskStatus.RUNNING,
                task.agent(),
                task.session(),
                task.promptTemplate(),
                task.contextPackageRef(),
                task.expectedContract(),
                task.dispatch(),
                nodeExecutionId,
                "",
                task.createdAt(),
                Instant.now(),
                null
        );
        jsonWriter.write(paths.agentTaskJson(document.agentTaskId()), document);
        return document;
    }

    AgentTaskDocument markSucceeded(WorkstreamPaths paths, AgentTaskDocument task, NodeResult result)
            throws IOException {
        var dispatch = new LinkedHashMap<String, Object>();
        dispatch.putAll(task.dispatch());
        if (result != null) {
            dispatch.put("outcome", result.outcome());
            dispatch.put("node_status", result.status() == null ? "" : result.status().name());
            dispatch.put("output_ref_count", result.outputRefs() == null ? 0 : result.outputRefs().size());
        }
        var document = complete(task, AgentTaskStatus.SUCCEEDED, "", dispatch);
        jsonWriter.write(paths.agentTaskJson(document.agentTaskId()), document);
        return document;
    }

    AgentTaskDocument markFailed(WorkstreamPaths paths, AgentTaskDocument task, Exception failure)
            throws IOException {
        var dispatch = new LinkedHashMap<String, Object>();
        dispatch.putAll(task.dispatch());
        dispatch.put("exception_type", failure == null ? "" : failure.getClass().getName());
        dispatch.put("exception_message", failure == null ? "" : nullToEmpty(failure.getMessage()));
        var document = complete(task, AgentTaskStatus.FAILED, failure == null ? "" : nullToEmpty(failure.getMessage()), dispatch);
        jsonWriter.write(paths.agentTaskJson(document.agentTaskId()), document);
        return document;
    }

    List<AgentTaskDocument> readAgentTasks(WorkstreamPaths paths) throws IOException {
        if (!java.nio.file.Files.isDirectory(paths.agentTasksDir())) {
            return List.of();
        }
        var documents = new ArrayList<AgentTaskDocument>();
        try (var stream = java.nio.file.Files.list(paths.agentTasksDir())) {
            var files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (var file : files) {
                documents.add(jsonWriter.objectMapper().readValue(file.toFile(), AgentTaskDocument.class));
            }
        }
        return documents;
    }

    private AgentTaskDocument complete(
            AgentTaskDocument task,
            AgentTaskStatus status,
            String errorMessage,
            LinkedHashMap<String, Object> dispatch
    ) {
        return new AgentTaskDocument(
                task.schemaVersion(),
                task.agentTaskId(),
                task.workflowExecutionId(),
                task.nodeInstanceId(),
                task.nodeDefinitionId(),
                task.nodeKind(),
                task.handlerType(),
                status,
                task.agent(),
                task.session(),
                task.promptTemplate(),
                task.contextPackageRef(),
                task.expectedContract(),
                dispatch,
                task.nodeExecutionId(),
                errorMessage,
                task.createdAt(),
                task.claimedAt(),
                Instant.now()
        );
    }

    private static Optional<String> latestInputRef(NodeInstanceDocument node, String marker) {
        if (node.inputRefs() == null || node.inputRefs().isEmpty()) {
            return Optional.empty();
        }
        for (int i = node.inputRefs().size() - 1; i >= 0; i--) {
            var ref = node.inputRefs().get(i);
            if (ref != null && ref.contains(marker)) {
                return Optional.of(ref);
            }
        }
        return Optional.empty();
    }

    private static String nextId(String prefix) {
        return prefix + "-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
