package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WorkflowFileLedger {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final LedgerEventLog eventLog;
    private final RoutingEngine routingEngine;
    private final NodeStateWriter nodeStateWriter;
    private final TimelineProjectionWriter timelineProjectionWriter;
    private final FailureSummaryWriter failureSummaryWriter;

    public WorkflowFileLedger() {
        this(new AtomicJsonWriter());
    }

    WorkflowFileLedger(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = jsonWriter.objectMapper();
        var jsonlMapper = jsonWriter.objectMapper().copy()
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.eventLog = new LedgerEventLog(jsonWriter, jsonlMapper);
        var revisionTracker = new RevisionTracker(jsonWriter, this.objectMapper, eventLog);
        this.routingEngine = new RoutingEngine(jsonWriter, this.objectMapper, eventLog, revisionTracker);
        this.nodeStateWriter = new NodeStateWriter(jsonWriter, this.objectMapper, eventLog);
        this.timelineProjectionWriter = new TimelineProjectionWriter(jsonWriter, this.objectMapper);
        this.failureSummaryWriter = new FailureSummaryWriter(this.objectMapper);
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
        var nodeInstanceId = LedgerUtils.nodeInstanceId(entry.id(), 1);
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
                LedgerUtils.stageId(entry.id()),
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        ));
        timelineProjectionWriter.write(paths);
    }

    public WorkflowCurrentDocument loadCurrent(WorkstreamPaths paths) throws IOException {
        return objectMapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
    }

    public WorkflowDefinition loadDefinition(WorkstreamPaths paths) throws IOException {
        var jsonPath = paths.workflowDefinitionJson();
        var json = Files.readString(jsonPath, StandardCharsets.UTF_8);
        var doc = objectMapper.readValue(json, WorkflowDefinitionDocument.class);

        // Migrate if schema version is older than current
        if (doc.schemaVersion() < LedgerMigrator.currentVersion()) {
            json = LedgerMigrator.migrate(json, doc.schemaVersion());
            // Write migrated version back
            Files.writeString(jsonPath, json, StandardCharsets.UTF_8);
            doc = objectMapper.readValue(json, WorkflowDefinitionDocument.class);
        }

        return new WorkflowDefinition(
                doc.workflowId(),
                doc.version(),
                doc.entryNodeId(),
                doc.nodes(),
                doc.edges()
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
        routingEngine.completeAndRoute(paths, definition, nodeInstanceId, completedStatus, outcome, outputRefs, result);
        if (completedStatus == NodeStatus.FAILED || completedStatus == NodeStatus.BLOCKED) {
            failureSummaryWriter.write(paths, nodeInstanceId);
        }
        timelineProjectionWriter.write(paths);
    }

    public void appendEvent(WorkstreamPaths paths, String eventId, String eventType, Map<String, Object> payload)
            throws IOException {
        eventLog.appendEvent(paths, eventId, eventType, payload);
    }

    public void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note)
            throws IOException {
        routingEngine.resolveOpenGate(paths, definition, action, note);
        timelineProjectionWriter.write(paths);
    }

    public NodeInstanceDocument markRunning(WorkstreamPaths paths, String nodeInstanceId, long pid) throws IOException {
        var node = nodeStateWriter.markRunning(paths, nodeInstanceId, pid);
        timelineProjectionWriter.write(paths);
        return node;
    }

    public NodeInstanceDocument autoRetryNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        var node = nodeStateWriter.autoRetryNode(paths, nodeInstanceId);
        timelineProjectionWriter.write(paths);
        return node;
    }

    public NodeInstanceDocument autoRetryNode(WorkstreamPaths paths, String nodeInstanceId, long retryDelayMs) throws IOException {
        var node = nodeStateWriter.autoRetryNode(paths, nodeInstanceId, retryDelayMs);
        timelineProjectionWriter.write(paths);
        return node;
    }

    public void retryActiveNode(WorkstreamPaths paths, String note) throws IOException {
        nodeStateWriter.retryActiveNode(paths, note);
        timelineProjectionWriter.write(paths);
    }

    public void abortRecovery(WorkstreamPaths paths, String note) throws IOException {
        nodeStateWriter.abortRecovery(paths, note);
        timelineProjectionWriter.write(paths);
    }

}
