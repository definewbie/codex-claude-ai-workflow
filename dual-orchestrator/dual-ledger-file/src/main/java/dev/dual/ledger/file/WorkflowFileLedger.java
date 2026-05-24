package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowFileLedger {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final ObjectMapper jsonlMapper;

    public WorkflowFileLedger() {
        this(new AtomicJsonWriter());
    }

    WorkflowFileLedger(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = jsonWriter.objectMapper();
        this.jsonlMapper = jsonWriter.objectMapper().copy()
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    public void initialize(WorkstreamPaths paths, String workflowInstanceId, WorkflowDefinition definition)
            throws IOException {
        if (Files.exists(paths.workflowCurrentJson()) || Files.exists(paths.eventsJsonl())) {
            throw new IOException("workflow ledger already initialized: " + paths.workstreamDir());
        }
        Files.createDirectories(paths.workflowDir());
        Files.createDirectories(paths.nodesDir());
        Files.createDirectories(paths.gatesDir());
        Files.createDirectories(paths.iterationsDir());

        var now = Instant.now();
        var entry = definition.node(definition.entryNodeId());
        var nodeInstanceId = nodeInstanceId(entry.id(), 1);
        var eventId = "evt-000001";

        appendEvent(paths, eventId, "workflow_started", Map.of(
                "workflow_id", definition.workflowId(),
                "entry_node", entry.id()
        ));
        jsonWriter.write(paths.workflowDefinitionJson(), new WorkflowDefinitionDocument(
                1,
                definition.workflowId(),
                definition.version(),
                definition.entryNodeId(),
                definition.nodes(),
                definition.edges()
        ));
        jsonWriter.write(paths.iterationJson("iter-001"), WorkflowIterationDocument.initial(entry.id(), now));
        jsonWriter.write(paths.workflowInstanceJson(), new WorkflowInstanceDocument(
                1,
                workflowInstanceId,
                definition.workflowId(),
                definition.version(),
                WorkflowStatus.RUNNING,
                List.of(nodeInstanceId),
                eventId,
                now,
                now
        ));
        jsonWriter.write(paths.workflowCurrentJson(), new WorkflowCurrentDocument(
                1,
                paths.workstreamId().value(),
                workflowInstanceId,
                definition.workflowId(),
                definition.version(),
                WorkflowStatus.RUNNING,
                List.of(nodeInstanceId),
                List.of(),
                eventId,
                now
        ));
        jsonWriter.write(paths.nodeJson(nodeInstanceId), new NodeInstanceDocument(
                1,
                nodeInstanceId,
                entry.id(),
                entry.kind(),
                entry.handlerType(),
                NodeStatus.READY,
                stageId(entry.id()),
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        ));
    }

    public WorkflowCurrentDocument loadCurrent(WorkstreamPaths paths) throws IOException {
        return objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
    }

    public WorkflowDefinition loadDefinition(WorkstreamPaths paths) throws IOException {
        var document = objectMapper.readValue(paths.workflowDefinitionJson().toFile(), WorkflowDefinitionDocument.class);
        return new WorkflowDefinition(
                document.workflowId(),
                document.version(),
                document.entryNodeId(),
                document.nodes(),
                document.edges()
        );
    }

    public NodeInstanceDocument loadNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        return objectMapper.readValue(paths.nodeJson(nodeInstanceId).toFile(), NodeInstanceDocument.class);
    }

    public NodeInstance loadCoreNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        var node = loadNode(paths, nodeInstanceId);
        return new NodeInstance(
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                node.status(),
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                node.outputRefs(),
                node.result(),
                node.runRef(),
                node.startedAt(),
                node.finishedAt()
        );
    }

    public void completeAndRoute(
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            String nodeInstanceId,
            NodeStatus completedStatus,
            String outcome,
            List<String> outputRefs,
            Map<String, Object> result
    ) throws IOException {
        var current = loadCurrent(paths);
        if (!current.activeNodeInstanceIds().contains(nodeInstanceId)) {
            throw new IOException("node is not active: " + nodeInstanceId);
        }

        var node = loadNode(paths, nodeInstanceId);
        var finishedAt = Instant.now();
        var finished = new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                completedStatus,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                outputRefs,
                result,
                node.runRef(),
                node.startedAt(),
                finishedAt
        );
        jsonWriter.write(paths.nodeJson(nodeInstanceId), finished);

        if (completedStatus != NodeStatus.COMPLETED) {
            stopOnNonCompletedNode(paths, current, node, completedStatus, outcome);
            return;
        }

        var edge = definition.edgesFrom(node.nodeDefinitionId()).stream()
                .filter(candidate -> candidate.on().equals(outcome))
                .findFirst();
        if (edge.isEmpty()) {
            completeWorkflow(paths, current, node, outcome);
            return;
        }

        var nextDefinition = definition.node(edge.get().to());
        var nextNodeInstanceId = nodeInstanceId(nextDefinition.id(), currentEventNumber(current.lastEventId()) + 1);
        var nextStatus = nextDefinition.kind() == NodeKind.GATE ? NodeStatus.WAITING : NodeStatus.READY;
        var nextIterationId = node.iterationId();
        var iterationStartedPayload = Map.<String, Object>of();
        if (startsRevisionIteration(outcome)) {
            var nextIteration = nextRevisionIteration(paths, node, outcome, nextDefinition.id(), result);
            Files.createDirectories(paths.iterationsDir());
            jsonWriter.write(paths.iterationJson(nextIteration.iterationId()), nextIteration);
            nextIterationId = nextIteration.iterationId();
            iterationStartedPayload = Map.of(
                    "iteration_id", nextIteration.iterationId(),
                    "parent_iteration_id", nextIteration.parentIterationId(),
                    "trigger_node_instance_id", nextIteration.triggerNodeInstanceId(),
                    "trigger_action", nextIteration.triggerAction(),
                    "target_node_definition_id", nextIteration.targetNodeDefinitionId(),
                    "reason", nextIteration.reason()
            );
        }
        var nextNode = new NodeInstanceDocument(
                1,
                nextNodeInstanceId,
                nextDefinition.id(),
                nextDefinition.kind(),
                nextDefinition.handlerType(),
                nextStatus,
                stageId(nextDefinition.id()),
                nextIterationId,
                outputRefs,
                List.of(),
                Map.of(),
                null,
                null,
                null
        );
        jsonWriter.write(paths.nodeJson(nextNodeInstanceId), nextNode);

        var status = nextDefinition.kind() == NodeKind.GATE ? WorkflowStatus.WAITING_FOR_HUMAN : WorkflowStatus.RUNNING;
        var openGateIds = List.<String>of();
        var gateId = "";
        if (nextDefinition.kind() == NodeKind.GATE) {
            gateId = "gate-" + nextNodeInstanceId;
            openGateIds = List.of(gateId);
            jsonWriter.write(paths.gateJson(gateId), new GateDocument(
                    1,
                    gateId,
                    nextNodeInstanceId,
                    nextDefinition.handlerType(),
                    "open",
                    List.of("approve", "revise"),
                    Instant.now(),
                    null,
                    Map.of()
                ));
        }

        var eventId = nextEventId(current.lastEventId());
        appendEvent(paths, eventId, "node_routed", Map.of(
                "from", node.nodeDefinitionId(),
                "outcome", outcome,
                "to", nextDefinition.id(),
                "node_instance_id", nextNodeInstanceId,
                "gate_id", gateId
        ));
        if (!iterationStartedPayload.isEmpty()) {
            appendEvent(paths, nextEventId(eventId), "iteration_started", iterationStartedPayload);
            eventId = nextEventId(eventId);
        }
        var now = Instant.now();
        var next = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                status,
                List.of(nextNodeInstanceId),
                openGateIds,
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), next);
        writeInstanceFromCurrent(paths, next, now);
    }

    public void appendEvent(WorkstreamPaths paths, String eventId, String eventType, Map<String, Object> payload)
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

    private void completeWorkflow(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            String outcome
    ) throws IOException {
        var eventId = nextEventId(current.lastEventId());
        appendEvent(paths, eventId, "workflow_completed", Map.of(
                "last_node", node.nodeDefinitionId(),
                "outcome", outcome
        ));
        var now = Instant.now();
        var completed = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.COMPLETED,
                List.of(),
                List.of(),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), completed);
        writeInstanceFromCurrent(paths, completed, now);
    }

    private void stopOnNonCompletedNode(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            NodeStatus nodeStatus,
            String outcome
    ) throws IOException {
        var eventId = nextEventId(current.lastEventId());
        var workflowStatus = WorkflowStatus.WAITING_FOR_RECOVERY;
        var recoveryGateId = "recovery-" + node.nodeInstanceId();
        jsonWriter.write(paths.gateJson(recoveryGateId), new GateDocument(
                1,
                recoveryGateId,
                node.nodeInstanceId(),
                "recovery.review",
                "open",
                List.of("retry", "abort"),
                Instant.now(),
                null,
                Map.of(
                        "node", node.nodeDefinitionId(),
                        "node_status", nodeStatus.name(),
                        "outcome", outcome
                )
        ));
        appendEvent(paths, eventId, "node_" + nodeStatus.name().toLowerCase(), Map.of(
                "node", node.nodeDefinitionId(),
                "node_status", nodeStatus.name(),
                "outcome", outcome,
                "recovery_gate_id", recoveryGateId
        ));
        var now = Instant.now();
        var stopped = new WorkflowCurrentDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                workflowStatus,
                List.of(node.nodeInstanceId()),
                List.of(recoveryGateId),
                eventId,
                now
        );
        jsonWriter.write(paths.workflowCurrentJson(), stopped);
        writeInstanceFromCurrent(paths, stopped, now);
    }

    public void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note)
            throws IOException {
        if (action == null || action.isBlank()) {
            throw new IOException("human gate action is required");
        }
        var current = loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active gate node");
        }
        var node = loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.kind() != NodeKind.GATE) {
            throw new IOException("active node is not a gate: " + node.nodeDefinitionId());
        }
        if (definition.edgesFrom(node.nodeDefinitionId()).stream().noneMatch(edge -> edge.on().equals(action))) {
            throw new IOException("unsupported gate action for " + node.nodeDefinitionId() + ": " + action);
        }
        completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, action, List.of(), Map.of(
                "human_action", action,
                "note", note == null ? "" : note
        ));
    }

    public void retryActiveNode(WorkstreamPaths paths, String note) throws IOException {
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

        var eventId = nextEventId(current.lastEventId());
        appendEvent(paths, eventId, "node_retry_requested", Map.of(
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
        writeInstanceFromCurrent(paths, resumed, now);
    }

    private void writeInstanceFromCurrent(WorkstreamPaths paths, WorkflowCurrentDocument current, Instant updatedAt)
            throws IOException {
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

    private static String nodeInstanceId(String nodeDefinitionId, int sequence) {
        return "node-" + nodeDefinitionId.replace('.', '-') + "-" + String.format("%03d", sequence);
    }

    private static String nextEventId(String lastEventId) {
        return "evt-" + String.format("%06d", currentEventNumber(lastEventId) + 1);
    }

    private static int currentEventNumber(String eventId) {
        if (eventId != null && eventId.startsWith("evt-")) {
            try {
                return Integer.parseInt(eventId.substring("evt-".length()));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }

    private WorkflowIterationDocument nextRevisionIteration(
            WorkstreamPaths paths,
            NodeInstanceDocument triggerNode,
            String triggerAction,
            String targetNodeDefinitionId,
            Map<String, Object> result
    ) throws IOException {
        var nextNumber = maxIterationNumber(paths) + 1;
        var iterationId = "iter-" + String.format("%03d", nextNumber);
        return WorkflowIterationDocument.revision(
                iterationId,
                nextNumber,
                triggerNode.iterationId(),
                triggerNode.nodeInstanceId(),
                triggerAction,
                targetNodeDefinitionId,
                revisionReason(result),
                Instant.now()
        );
    }

    private int maxIterationNumber(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.iterationsDir())) {
            return 1;
        }
        try (var stream = Files.list(paths.iterationsDir())) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("iter-\\d{3}\\.json"))
                    .mapToInt(name -> Integer.parseInt(name.substring("iter-".length(), "iter-000".length())))
                    .max()
                    .orElse(1);
        }
    }

    private static boolean startsRevisionIteration(String outcome) {
        return "revise".equals(outcome) || "NEEDS_REVISION".equals(outcome) || "REJECT".equals(outcome);
    }

    private static String revisionReason(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        var note = text(result.get("note"));
        if (!note.isBlank()) {
            return note;
        }
        var summary = text(result.get("summary"));
        if (!summary.isBlank()) {
            return summary;
        }
        var reason = text(result.get("reason"));
        if (!reason.isBlank()) {
            return reason;
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stageId(String nodeDefinitionId) {
        var separator = nodeDefinitionId.indexOf('.');
        var prefix = separator > 0 ? nodeDefinitionId.substring(0, separator) : "workflow";
        return "stage-" + prefix;
    }
}
