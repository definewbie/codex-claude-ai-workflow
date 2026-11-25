package dev.dual.ledger.file;

import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GoalSatisfactionWriter {
    private final AtomicJsonWriter jsonWriter;

    GoalSatisfactionWriter(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    Optional<GoalSatisfactionDocument> evaluateAndWrite(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument node,
            NodeStatus nodeStatus,
            String outcome,
            List<String> evidenceRefs,
            Map<String, Object> result
    ) throws IOException {
        var domainOutcome = domainOutcome(node.nodeDefinitionId(), nodeStatus, outcome);
        if (domainOutcome.isEmpty()) {
            return Optional.empty();
        }
        var document = new GoalSatisfactionDocument(
                1,
                current.workstreamId(),
                current.workflowInstanceId(),
                node.nodeInstanceId(),
                node.nodeDefinitionId(),
                node.iterationId(),
                outcome,
                domainOutcome.get(),
                reason(outcome, result),
                evidenceRefs,
                result,
                Instant.now()
        );
        jsonWriter.write(paths.goalSatisfactionJson(), document);
        return Optional.of(document);
    }

    private static Optional<GoalDomainOutcome> domainOutcome(String nodeDefinitionId, NodeStatus nodeStatus, String outcome) {
        if (nodeStatus != NodeStatus.COMPLETED) {
            return Optional.empty();
        }
        if ("acceptance.decision".equals(nodeDefinitionId)) {
            return switch (outcome) {
                case "ACCEPT" -> Optional.of(GoalDomainOutcome.SATISFIED);
                case "ACCEPT_WITH_NOTES" -> Optional.of(GoalDomainOutcome.SATISFIED_WITH_NOTES);
                case "REJECT" -> Optional.of(GoalDomainOutcome.NEEDS_REPAIR);
                case "BLOCKED" -> Optional.of(GoalDomainOutcome.BLOCKED);
                default -> Optional.empty();
            };
        }
        if ("acceptance.classify_failure".equals(nodeDefinitionId)) {
            if (outcome != null && outcome.startsWith("REJECT_")) {
                return Optional.of(GoalDomainOutcome.NEEDS_REPAIR);
            }
            if (outcome != null && outcome.startsWith("NOTED_")) {
                return Optional.of(GoalDomainOutcome.SATISFIED_WITH_NOTES);
            }
        }
        if (outcome != null && "NEEDS_REVISION".equals(outcome)) {
            return Optional.of(GoalDomainOutcome.NEEDS_REDESIGN);
        }
        if (outcome != null && "BLOCKED".equals(outcome)) {
            return Optional.of(GoalDomainOutcome.BLOCKED);
        }
        return Optional.empty();
    }

    private static String reason(String outcome, Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return outcome == null ? "" : outcome;
        }
        for (var key : List.of("reason", "summary", "message", "review_decision", "decision")) {
            var value = result.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return outcome == null ? "" : outcome;
    }
}
