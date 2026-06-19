package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.flow.FlowOrchestrationKernel;
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
import java.util.Optional;

public final class WorkflowFileLedger {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final LedgerEventLog eventLog;
    private final RoutingEngine routingEngine;
    private final NodeStateWriter nodeStateWriter;
    private final TimelineProjectionWriter timelineProjectionWriter;
    private final MetricsProjectionWriter metricsProjectionWriter;
    private final FailureSummaryWriter failureSummaryWriter;
    private final ExecutionLedgerWriter executionLedgerWriter;
    private final AgentTaskLedgerWriter agentTaskLedgerWriter;

    public WorkflowFileLedger() {
        this(new BuiltInFlowOrchestrationKernel());
    }

    public WorkflowFileLedger(FlowOrchestrationKernel flowKernel) {
        this(new AtomicJsonWriter(), flowKernel);
    }

    WorkflowFileLedger(AtomicJsonWriter jsonWriter) {
        this(jsonWriter, new BuiltInFlowOrchestrationKernel());
    }

    WorkflowFileLedger(AtomicJsonWriter jsonWriter, FlowOrchestrationKernel flowKernel) {
        if (flowKernel == null) {
            throw new IllegalArgumentException("flow orchestration kernel is required");
        }
        this.jsonWriter = jsonWriter;
        this.objectMapper = jsonWriter.objectMapper();
        var jsonlMapper = jsonWriter.objectMapper().copy()
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.eventLog = new LedgerEventLog(jsonWriter, jsonlMapper);
        var revisionTracker = new RevisionTracker(jsonWriter, this.objectMapper, eventLog);
        this.routingEngine = new RoutingEngine(jsonWriter, this.objectMapper, eventLog, revisionTracker, flowKernel);
        this.nodeStateWriter = new NodeStateWriter(jsonWriter, this.objectMapper, eventLog);
        this.timelineProjectionWriter = new TimelineProjectionWriter(jsonWriter, this.objectMapper);
        this.metricsProjectionWriter = new MetricsProjectionWriter(jsonWriter, this.objectMapper);
        this.failureSummaryWriter = new FailureSummaryWriter(this.objectMapper);
        this.executionLedgerWriter = new ExecutionLedgerWriter(jsonWriter);
        this.agentTaskLedgerWriter = new AgentTaskLedgerWriter(jsonWriter);
    }

    public void initialize(WorkstreamPaths paths, String workflowInstanceId, WorkflowDefinition definition)
            throws IOException {
        if (Files.exists(paths.workflowCurrentJson()) || Files.exists(paths.eventsJsonl())) {
            throw new IOException("workflow ledger already initialized: " + paths.workstreamDir());
        }
        Files.createDirectories(paths.workflowDir());
        Files.createDirectories(paths.workflowExecutionsDir());
        Files.createDirectories(paths.nodeExecutionsDir());
        Files.createDirectories(paths.agentTasksDir());
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
                Map.of(),
                List.of(),
                Map.of(),
                null,
                "",
                null,
                null
        ));
        refreshProjections(paths);
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
        refreshProjections(paths);
    }

    public void appendEvent(WorkstreamPaths paths, String eventId, String eventType, Map<String, Object> payload)
            throws IOException {
        eventLog.appendEvent(paths, eventId, eventType, payload);
    }

    public WorkflowExecutionDocument startWorkflowExecution(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            String trigger
    ) throws IOException {
        return executionLedgerWriter.startWorkflowExecution(paths, current, trigger);
    }

    public WorkflowExecutionDocument finishWorkflowExecution(
            WorkstreamPaths paths,
            WorkflowExecutionDocument workflowExecution,
            List<String> nodeExecutionIds,
            WorkflowExecutionStatus status,
            String stopReason
    ) throws IOException {
        return executionLedgerWriter.finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, status, stopReason);
    }

    public AgentTaskDocument createAgentTask(
            WorkstreamPaths paths,
            String workflowExecutionId,
            dev.dual.core.workflow.NodeDefinition definition,
            NodeInstanceDocument node
    ) throws IOException {
        var task = agentTaskLedgerWriter.create(paths, workflowExecutionId, definition, node);
        appendEvent(paths, "agent-task-" + task.agentTaskId(), "agent_task_queued", Map.of(
                "agent_task_id", task.agentTaskId(),
                "workflow_execution_id", workflowExecutionId,
                "node", node.nodeDefinitionId(),
                "node_instance_id", node.nodeInstanceId(),
                "handler_type", node.handlerType(),
                "agent", task.agent(),
                "session", task.session()
        ));
        refreshProjections(paths);
        return task;
    }

    public AgentTaskDocument markAgentTaskRunning(
            WorkstreamPaths paths,
            AgentTaskDocument task,
            String nodeExecutionId
    ) throws IOException {
        var updated = agentTaskLedgerWriter.markRunning(paths, task, nodeExecutionId);
        appendEvent(paths, "agent-task-running-" + updated.agentTaskId(), "agent_task_started", Map.of(
                "agent_task_id", updated.agentTaskId(),
                "node_execution_id", nodeExecutionId,
                "node", updated.nodeDefinitionId(),
                "node_instance_id", updated.nodeInstanceId()
        ));
        refreshProjections(paths);
        return updated;
    }

    public AgentTaskDocument markAgentTaskFinished(
            WorkstreamPaths paths,
            AgentTaskDocument task,
            dev.dual.core.workflow.NodeResult result
    ) throws IOException {
        var updated = agentTaskLedgerWriter.markFinished(paths, task, result);
        var failed = updated.status() == AgentTaskStatus.FAILED;
        appendEvent(paths, (failed ? "agent-task-failed-" : "agent-task-succeeded-") + updated.agentTaskId(),
                failed ? "agent_task_failed" : "agent_task_completed", Map.of(
                "agent_task_id", updated.agentTaskId(),
                "node_execution_id", updated.nodeExecutionId(),
                "node", updated.nodeDefinitionId(),
                "node_instance_id", updated.nodeInstanceId(),
                "status", updated.status(),
                "error", failed ? updated.errorMessage() : ""
        ));
        refreshProjections(paths);
        return updated;
    }

    public AgentTaskDocument markAgentTaskFailed(
            WorkstreamPaths paths,
            AgentTaskDocument task,
            Exception failure
    ) throws IOException {
        var updated = agentTaskLedgerWriter.markFailed(paths, task, failure);
        appendEvent(paths, "agent-task-failed-" + updated.agentTaskId(), "agent_task_failed", Map.of(
                "agent_task_id", updated.agentTaskId(),
                "node_execution_id", updated.nodeExecutionId(),
                "node", updated.nodeDefinitionId(),
                "node_instance_id", updated.nodeInstanceId(),
                "error", failure == null ? "" : String.valueOf(failure.getMessage())
        ));
        refreshProjections(paths);
        return updated;
    }

    public List<AgentTaskDocument> readAgentTasks(WorkstreamPaths paths) throws IOException {
        return agentTaskLedgerWriter.readAgentTasks(paths);
    }

    public Optional<AgentTaskDocument> readAgentTask(WorkstreamPaths paths, String agentTaskId) throws IOException {
        return agentTaskLedgerWriter.readAgentTask(paths, agentTaskId);
    }

    public Optional<AgentTaskDocument> firstQueuedAgentTask(WorkstreamPaths paths) throws IOException {
        return agentTaskLedgerWriter.firstQueued(paths);
    }

    public NodeExecutionDocument startNodeExecution(
            WorkstreamPaths paths,
            String workflowExecutionId,
            NodeInstanceDocument node,
            AgentTaskDocument agentTask,
            long pid
    ) throws IOException {
        var execution = executionLedgerWriter.startNodeExecution(paths, workflowExecutionId, node, agentTask, pid);
        nodeStateWriter.setLatestExecution(paths, node.nodeInstanceId(), execution.nodeExecutionId());
        refreshProjections(paths);
        return execution;
    }

    public NodeExecutionDocument finishNodeExecution(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            dev.dual.core.workflow.NodeResult result
    ) throws IOException {
        var execution = executionLedgerWriter.finishNodeExecution(paths, nodeExecution, result);
        refreshProjections(paths);
        return execution;
    }

    public NodeExecutionDocument failNodeExecution(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            Exception failure
    ) throws IOException {
        var execution = executionLedgerWriter.failNodeExecution(paths, nodeExecution, failure);
        refreshProjections(paths);
        return execution;
    }

    public List<NodeExecutionDocument> readNodeExecutions(WorkstreamPaths paths) throws IOException {
        return executionLedgerWriter.readNodeExecutions(paths);
    }

    public Optional<NodeExecutionDocument> latestNodeExecution(WorkstreamPaths paths, NodeInstanceDocument node) throws IOException {
        return executionLedgerWriter.latestNodeExecution(paths, node);
    }

    public NodeExecutionDocument markNodeExecutionStale(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            String note
    ) throws IOException {
        var execution = executionLedgerWriter.markNodeExecutionStale(paths, nodeExecution, note);
        refreshProjections(paths);
        return execution;
    }

    public void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note)
            throws IOException {
        routingEngine.resolveOpenGate(paths, definition, action, note);
        refreshProjections(paths);
    }

    public NodeInstanceDocument markRunning(WorkstreamPaths paths, String nodeInstanceId, long pid) throws IOException {
        var node = nodeStateWriter.markRunning(paths, nodeInstanceId, pid);
        refreshProjections(paths);
        return node;
    }

    public NodeInstanceDocument markQueuedForAgentTask(WorkstreamPaths paths, String nodeInstanceId, String agentTaskId)
            throws IOException {
        var node = nodeStateWriter.markQueuedForAgentTask(paths, nodeInstanceId, agentTaskId);
        appendEvent(paths, "agent-task-queued-node-" + agentTaskId, "node_agent_task_queued", Map.of(
                "agent_task_id", agentTaskId,
                "node", node.nodeDefinitionId(),
                "node_instance_id", node.nodeInstanceId()
        ));
        refreshProjections(paths);
        return node;
    }

    public NodeInstanceDocument autoRetryNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        var node = nodeStateWriter.autoRetryNode(paths, nodeInstanceId);
        refreshProjections(paths);
        return node;
    }

    public NodeInstanceDocument autoRetryNode(WorkstreamPaths paths, String nodeInstanceId, long retryDelayMs) throws IOException {
        var node = nodeStateWriter.autoRetryNode(paths, nodeInstanceId, retryDelayMs);
        refreshProjections(paths);
        return node;
    }

    public void retryActiveNode(WorkstreamPaths paths, String note) throws IOException {
        nodeStateWriter.retryActiveNode(paths, note);
        refreshProjections(paths);
    }

    public void recoverStaleRunningNode(WorkstreamPaths paths, String note) throws IOException {
        nodeStateWriter.recoverStaleRunningNode(paths, note);
        var current = loadCurrent(paths);
        if (!current.activeNodeInstanceIds().isEmpty()) {
            failureSummaryWriter.write(paths, current.activeNodeInstanceIds().get(0));
        }
        refreshProjections(paths);
    }

    public void abortRecovery(WorkstreamPaths paths, String note) throws IOException {
        nodeStateWriter.abortRecovery(paths, note);
        refreshProjections(paths);
    }

    public void closeMistakenIteration(WorkstreamPaths paths, String note) throws IOException {
        nodeStateWriter.closeMistakenIteration(paths, note);
        refreshProjections(paths);
    }

    private void refreshProjections(WorkstreamPaths paths) throws IOException {
        timelineProjectionWriter.write(paths);
        metricsProjectionWriter.write(paths);
    }
}
