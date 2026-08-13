package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RevisionTracker {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final LedgerEventLog eventLog;
    private final FailureSummaryWriter failureSummaryWriter;

    RevisionTracker(AtomicJsonWriter jsonWriter, ObjectMapper objectMapper, LedgerEventLog eventLog) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = objectMapper;
        this.eventLog = eventLog;
        this.failureSummaryWriter = new FailureSummaryWriter(objectMapper);
    }

    WorkflowIterationDocument nextRevisionIteration(
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
                LedgerUtils.revisionReason(result),
                Instant.now()
        );
    }

    void blockOnRevisionLimit(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            List<String> outputRefs,
            Map<String, Object> result,
            String outcome,
            int maxRevisions,
            int currentRevisionCount
    ) throws IOException {
        var blockedResult = new LinkedHashMap<String, Object>();
        if (result != null) {
            blockedResult.putAll(result);
        }
        blockedResult.put("reason", "revision_limit_reached");
        blockedResult.put("outcome", outcome);
        blockedResult.put("max_revisions", maxRevisions);
        blockedResult.put("current_revision_count", currentRevisionCount);
        var now = Instant.now();
        jsonWriter.write(paths.nodeJson(node.nodeInstanceId()), new NodeInstanceDocument(
                node.schemaVersion(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.kind(),
                node.handlerType(),
                NodeStatus.BLOCKED,
                node.stageId(),
                node.iterationId(),
                node.inputRefs(),
                node.inputTransition(),
                outputRefs,
                blockedResult,
                node.runRef(),
                node.latestExecutionId(),
                node.startedAt(),
                now
        ));

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
                        "node_status", NodeStatus.BLOCKED.name(),
                        "outcome", outcome,
                        "reason", "revision_limit_reached",
                        "max_revisions", maxRevisions,
                        "current_revision_count", currentRevisionCount
                )
        ));
        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        eventLog.appendEvent(paths, eventId, "revision_limit_reached", Map.of(
                "node", node.nodeDefinitionId(),
                "node_instance_id", node.nodeInstanceId(),
                "outcome", outcome,
                "max_revisions", maxRevisions,
                "current_revision_count", currentRevisionCount,
                "recovery_gate_id", recoveryGateId
        ));
        var stopped = new WorkflowCurrentDocument(
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
        jsonWriter.write(paths.workflowCurrentJson(), stopped);
        eventLog.writeInstanceFromCurrent(paths, stopped, now);
        failureSummaryWriter.write(paths, node.nodeInstanceId());
    }

    int maxIterationNumber(WorkstreamPaths paths) throws IOException {
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

    int revisionCount(
            WorkstreamPaths paths,
            String triggerNodeDefinitionId,
            String targetNodeDefinitionId
    ) throws IOException {
        if (!Files.exists(paths.iterationsDir())) {
            return 0;
        }
        try (var stream = Files.list(paths.iterationsDir())) {
            var iterations = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("iter-\\d{3}\\.json"))
                    .map(path -> readIterationOrNull(path))
                    .filter(iteration -> iteration != null)
                    .filter(iteration -> targetNodeDefinitionId.equals(iteration.targetNodeDefinitionId()))
                    .filter(iteration -> !"workflow_started".equals(iteration.triggerAction()))
                    .toList();
            var count = 0;
            for (var iteration : iterations) {
                var triggerNode = objectMapper.readValue(
                        paths.nodeJson(iteration.triggerNodeInstanceId()).toFile(),
                        NodeInstanceDocument.class
                );
                if (triggerNodeDefinitionId.equals(triggerNode.nodeDefinitionId())) {
                    count++;
                }
            }
            return count;
        }
    }

    private WorkflowIterationDocument readIterationOrNull(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), WorkflowIterationDocument.class);
        } catch (IOException e) {
            return null;
        }
    }

    static boolean startsNewIteration(String nodeDefinitionId, String outcome, String targetNodeDefinitionId) {
        return IterationSemantics.startsNewIteration(outcome, targetNodeDefinitionId);
    }

    static boolean isRevisionAction(String outcome) {
        return IterationSemantics.isRevisionTrigger(outcome);
    }

    static int maxRevisions(NodeDefinition definition) {
        var configured = definition.config().get("max_revisions");
        if (configured instanceof Number number) {
            return number.intValue();
        }
        if (configured instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }
}
