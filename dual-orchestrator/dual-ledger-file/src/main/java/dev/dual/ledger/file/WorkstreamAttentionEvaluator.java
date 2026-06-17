package dev.dual.ledger.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.WorkflowStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Builds the shared "where is it, why did it stop, what next" read model.
 *
 * CLI, API, and UI should consume this projection instead of recomputing status
 * hints independently. The evaluator reads authoritative ledger/domain facts
 * and returns one operator-facing attention item; it does not mutate workflow
 * state.
 */
public final class WorkstreamAttentionEvaluator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private WorkstreamAttentionEvaluator() {
    }

    public static WorkstreamAttention evaluate(WorkstreamPaths paths) {
        try {
            return evaluateOrThrow(paths);
        } catch (Exception failure) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.UNKNOWN,
                    "warning",
                    "Workflow attention unavailable",
                    failure.getMessage() == null ? "Could not read workstream attention facts." : failure.getMessage(),
                    "Inspect the workstream ledger.",
                    "",
                    false,
                    "",
                    "",
                    "",
                    "",
                    List.of()
            );
        }
    }

    private static WorkstreamAttention evaluateOrThrow(WorkstreamPaths paths) throws IOException {
        if (!Files.isRegularFile(paths.workflowCurrentJson())) {
            return unknown("Workflow current state is not available.");
        }
        var current = OBJECT_MAPPER.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
        var activeNode = activeNode(paths, current);
        var goal = readMap(paths.goalSatisfactionJson());
        var domainOutcome = text(goal.get("domainOutcome"));
        var goalReason = text(goal.get("reason"));
        var evidenceRefs = textList(goal.get("evidenceRefs"));
        var nodeId = activeNode == null ? "" : activeNode.nodeDefinitionId();
        var nodeInstanceId = activeNode == null ? "" : activeNode.nodeInstanceId();
        var base = "--workdir " + quote(paths.workspaceRoot().toAbsolutePath().normalize().toString()) + " " + paths.workstreamId().value();

        // Human gates are actionable only while current; historical gate
        // evidence belongs to review packet projections, not this attention item.
        if (current.status() == WorkflowStatus.WAITING_FOR_HUMAN) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.WAITING_FOR_HUMAN,
                    "info",
                    "Human gate is open",
                    current.openGateIds().isEmpty()
                            ? "Waiting for human review."
                            : "Waiting for human review: " + String.join(", ", current.openGateIds()),
                    "Read the review packet, then approve, revise, or complete from the current gate.",
                    "dualctl review show " + base,
                    false,
                    current.status().name(),
                    nodeId,
                    nodeInstanceId,
                    domainOutcome,
                    evidenceRefs
            );
        }
        if (current.status() == WorkflowStatus.WAITING_FOR_RECOVERY
                || current.status() == WorkflowStatus.FAILED
                || current.status() == WorkflowStatus.BLOCKED) {
            return recovery(paths, current, activeNode, domainOutcome, evidenceRefs, base);
        }
        if (current.status() == WorkflowStatus.RUNNING) {
            var stale = staleRunningAttention(paths, current, activeNode, nodeId, nodeInstanceId, domainOutcome, evidenceRefs, base);
            if (stale != null) {
                return stale;
            }
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.RUNNING,
                    "info",
                    "Workflow is running",
                    nodeId.isBlank() ? "Workflow is running." : "Workflow is running at " + nodeId + ".",
                    "Let the active node finish, or inspect the active execution if it appears stale.",
                    "",
                    false,
                    current.status().name(),
                    nodeId,
                    nodeInstanceId,
                    domainOutcome,
                    evidenceRefs
            );
        }
        // Terminal workflows can still carry goal warnings, but they must not
        // suggest run/resume. Observation and audit are the only safe actions.
        if (current.status() == WorkflowStatus.COMPLETED) {
            return completed(paths, current, nodeId, nodeInstanceId, domainOutcome, goalReason, evidenceRefs, base);
        }
        if ("NEEDS_REDESIGN".equals(domainOutcome) && isRevisionLimit(activeNode)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.SEMANTIC_REVISION_LIMIT,
                    "critical",
                    "Design loop did not converge",
                    blankDefault(goalReason, "The workflow exhausted its design revision budget. Blind retry is unsafe because it will likely repeat the same review loop."),
                    "Inspect the design review evidence, then revise the task contract, scope, or test policy before continuing.",
                    "dualctl recovery show " + base,
                    false,
                    current.status().name(),
                    nodeId,
                    nodeInstanceId,
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("NEEDS_REPAIR".equals(domainOutcome)) {
            return goalLoop(
                    WorkstreamAttentionCategory.GOAL_REPAIR_REQUIRED,
                    "Goal needs repair",
                    blankDefault(goalReason, "Verifier evidence indicates the implementation does not satisfy the goal yet."),
                    "Continue the goal loop only after reviewing the rejection evidence and repair scope.",
                    "dualctl run " + base,
                    current,
                    nodeId,
                    nodeInstanceId,
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("NEEDS_REDESIGN".equals(domainOutcome)) {
            return goalLoop(
                    WorkstreamAttentionCategory.GOAL_REDESIGN_REQUIRED,
                    "Goal needs redesign",
                    blankDefault(goalReason, "Review evidence indicates the design or plan is not ready for implementation."),
                    "Continue the goal loop with the recorded design evidence.",
                    "dualctl run " + base,
                    current,
                    nodeId,
                    nodeInstanceId,
                    domainOutcome,
                    evidenceRefs
            );
        }
        return unknown("Workflow status is " + current.status() + ".");
    }

    private static WorkstreamAttention completed(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            String nodeId,
            String nodeInstanceId,
            String domainOutcome,
            String goalReason,
            List<String> evidenceRefs,
            String base
    ) throws IOException {
        var unsatisfiedGoal = "NEEDS_REPAIR".equals(domainOutcome) || "NEEDS_REDESIGN".equals(domainOutcome);
        var deliveryMerge = readMap(paths.deliveryMergePlanJson());
        var mergeStatus = text(deliveryMerge.get("status"));
        if ("APPLIED".equals(mergeStatus)) {
            var targetRepo = text(deliveryMerge.get("targetRepo"));
            var detail = targetRepo.isBlank()
                    ? "Delivery merge has been applied to the target repo index."
                    : "Delivery merge has been applied to the target repo index: " + targetRepo + ".";
            var command = targetRepo.isBlank() ? "" : "git -C " + quote(targetRepo) + " diff --cached";
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.COMPLETED,
                    "info",
                    "Delivery merge applied",
                    detail,
                    "Review the staged target diff, then commit from the target repo when ready.",
                    command,
                    false,
                    current.status().name(),
                    nodeId,
                    nodeInstanceId,
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("FAILED".equals(mergeStatus)) {
            var failure = blankDefault(text(deliveryMerge.get("failureReason")), "Delivery merge failed. Inspect delivery logs.");
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.COMPLETED,
                    "warning",
                    "Delivery merge failed",
                    failure,
                    "Inspect merge-plan.json and apply stderr before retrying delivery merge.",
                    "dualctl summary " + base,
                    false,
                    current.status().name(),
                    nodeId,
                    nodeInstanceId,
                    domainOutcome,
                    evidenceRefs
            );
        }
        var warningDetail = "Workflow is terminal, but the latest goal outcome is " + domainOutcome
                + ". " + blankDefault(goalReason, "Inspect artifacts before delivery.");
        return new WorkstreamAttention(
                WorkstreamAttentionCategory.COMPLETED,
                unsatisfiedGoal ? "warning" : "info",
                unsatisfiedGoal ? "Workflow completed with goal warning" : "Workflow completed",
                unsatisfiedGoal ? warningDetail : "Workflow completed. Further actions should inspect artifacts only.",
                "Review final artifacts and Git status.",
                "dualctl summary " + base,
                false,
                current.status().name(),
                nodeId,
                nodeInstanceId,
                domainOutcome,
                evidenceRefs
        );
    }

    private static WorkstreamAttention staleRunningAttention(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument activeNode,
            String nodeId,
            String nodeInstanceId,
            String domainOutcome,
            List<String> evidenceRefs,
            String base
    ) {
        if (activeNode == null) {
            return null;
        }
        var runningPid = runningPid(activeNode.result().get("running_pid"));
        if (runningPid > 0 && ProcessHandle.of(runningPid).map(ProcessHandle::isAlive).orElse(false)) {
            return null;
        }
        var detail = "";
        if (runningPid > 0) {
            detail = "Workflow is marked RUNNING at " + blankDefault(nodeId, "the current node")
                    + ", but its recorded process is no longer alive (PID=" + runningPid + ").";
        } else if (Files.isRegularFile(paths.currentFailureMarkdown())) {
            detail = "Workflow is marked RUNNING, but a current failure summary exists. Inspect recovery before continuing.";
        } else {
            return null;
        }
        return new WorkstreamAttention(
                WorkstreamAttentionCategory.RUNTIME_RECOVERY,
                "critical",
                "Running state looks stale",
                detail,
                "Move the stale running node into recovery.",
                "dualctl resume " + base,
                false,
                current.status().name(),
                nodeId,
                nodeInstanceId,
                domainOutcome,
                evidenceRefs
        );
    }

    private static WorkstreamAttention recovery(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            NodeInstanceDocument activeNode,
            String domainOutcome,
            List<String> evidenceRefs,
            String base
    ) {
        if (activeNode == null) {
            return unknown("Workflow is waiting for recovery but no active node is recorded.");
        }
        var assessment = RecoveryAssessment.from(activeNode, paths.workspaceRoot(), paths.workstreamId().value());
        var classification = assessment.classification();
        if ("artifact_contract_failure".equals(classification)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.ARTIFACT_CONTRACT_FAILURE,
                    "critical",
                    "Artifact contract failed",
                    assessment.reason(),
                    "Inspect the contract diagnostic and fix the output contract before retrying.",
                    assessment.suggestedCommand(),
                    false,
                    current.status().name(),
                    activeNode.nodeDefinitionId(),
                    activeNode.nodeInstanceId(),
                    domainOutcome,
                    mergeEvidence(evidenceRefs, assessment.diagnosticJsonRef(), assessment.diagnosticMarkdownRef())
            );
        }
        if ("recoverable_agent_runtime".equals(classification)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.RUNTIME_RECOVERY,
                    "critical",
                    "Runtime recovery required",
                    assessment.reason(),
                    "Fix the external runtime cause, then retry the same active node.",
                    assessment.suggestedCommand(),
                    true,
                    current.status().name(),
                    activeNode.nodeDefinitionId(),
                    activeNode.nodeInstanceId(),
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("agent_session_recovery".equals(classification)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.RUNTIME_RECOVERY,
                    "critical",
                    "Agent session recovery required",
                    assessment.reason(),
                    "Rebuild the acpx agent session, then retry the same active node.",
                    assessment.suggestedCommand(),
                    true,
                    current.status().name(),
                    activeNode.nodeDefinitionId(),
                    activeNode.nodeInstanceId(),
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("agent_output_interrupted".equals(classification)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.RUNTIME_RECOVERY,
                    "critical",
                    "Agent output interrupted",
                    assessment.reason(),
                    "Rebuild the acpx agent session, then retry the same active node.",
                    assessment.suggestedCommand(),
                    true,
                    current.status().name(),
                    activeNode.nodeDefinitionId(),
                    activeNode.nodeInstanceId(),
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("agent_capacity_recovery".equals(classification)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.RUNTIME_RECOVERY,
                    "critical",
                    "Agent capacity recovery required",
                    assessment.reason(),
                    "Wait for quota or provider capacity to recover, then retry the same active node.",
                    assessment.suggestedCommand(),
                    true,
                    current.status().name(),
                    activeNode.nodeDefinitionId(),
                    activeNode.nodeInstanceId(),
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("agent_model_mismatch".equals(classification)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.RUNTIME_RECOVERY,
                    "critical",
                    "Agent session model mismatch",
                    assessment.reason(),
                    "Rebuild the acpx agent session, then retry the same active node.",
                    assessment.suggestedCommand(),
                    true,
                    current.status().name(),
                    activeNode.nodeDefinitionId(),
                    activeNode.nodeInstanceId(),
                    domainOutcome,
                    evidenceRefs
            );
        }
        if ("semantic_revision_limit".equals(classification)) {
            return new WorkstreamAttention(
                    WorkstreamAttentionCategory.SEMANTIC_REVISION_LIMIT,
                    "critical",
                    "Design loop did not converge",
                    assessment.reason(),
                    "Inspect the review evidence and revise the task contract, scope, or test policy before continuing.",
                    "dualctl recovery show " + base,
                    false,
                    current.status().name(),
                    activeNode.nodeDefinitionId(),
                    activeNode.nodeInstanceId(),
                    domainOutcome,
                    evidenceRefs
            );
        }
        return new WorkstreamAttention(
                WorkstreamAttentionCategory.FAILED,
                "critical",
                "Workflow needs inspection",
                assessment.reason(),
                "Inspect recovery evidence before deciding whether retry is safe.",
                assessment.suggestedCommand(),
                false,
                current.status().name(),
                activeNode.nodeDefinitionId(),
                activeNode.nodeInstanceId(),
                domainOutcome,
                evidenceRefs
        );
    }

    private static WorkstreamAttention goalLoop(
            WorkstreamAttentionCategory category,
            String title,
            String detail,
            String nextAction,
            String command,
            WorkflowCurrentDocument current,
            String nodeId,
            String nodeInstanceId,
            String domainOutcome,
            List<String> evidenceRefs
    ) {
        return new WorkstreamAttention(
                category,
                "warning",
                title,
                detail,
                nextAction,
                command,
                false,
                current.status().name(),
                nodeId,
                nodeInstanceId,
                domainOutcome,
                evidenceRefs
        );
    }

    private static NodeInstanceDocument activeNode(WorkstreamPaths paths, WorkflowCurrentDocument current) throws IOException {
        if (current.activeNodeInstanceIds().isEmpty()) {
            return null;
        }
        return OBJECT_MAPPER.readValue(paths.nodeJson(current.activeNodeInstanceIds().get(0)).toFile(), NodeInstanceDocument.class);
    }

    private static boolean isRevisionLimit(NodeInstanceDocument node) {
        return node != null && "revision_limit_reached".equals(text(node.result().get("reason")));
    }

    private static Map<String, Object> readMap(java.nio.file.Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        return OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
    }

    private static List<String> textList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(WorkstreamAttentionEvaluator::text).filter(s -> !s.isBlank()).toList();
    }

    private static List<String> mergeEvidence(List<String> refs, String... extraRefs) {
        var merged = new java.util.ArrayList<String>();
        if (refs != null) {
            merged.addAll(refs);
        }
        for (var ref : extraRefs) {
            if (ref != null && !ref.isBlank()) {
                merged.add(ref);
            }
        }
        return List.copyOf(merged);
    }

    private static long runningPid(Object value) {
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

    private static WorkstreamAttention unknown(String detail) {
        return new WorkstreamAttention(
                WorkstreamAttentionCategory.UNKNOWN,
                "warning",
                "Workflow state is unclear",
                detail,
                "Inspect the workstream ledger.",
                "",
                false,
                "",
                "",
                "",
                "",
                List.of()
        );
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String quote(String value) {
        if (value.contains(" ")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }
}
