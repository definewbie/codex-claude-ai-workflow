package dev.dual.ledger.file;

import dev.dual.core.id.DualId;

import java.nio.file.Path;

public record WorkstreamPaths(Path workspaceRoot, DualId workstreamId) {
    public Path agentDir() {
        return workspaceRoot.resolve(".agent");
    }

    public Path workstreamDir() {
        return agentDir().resolve("workstreams").resolve(workstreamId.value());
    }

    public Path runsDir() {
        return workstreamDir().resolve("runs");
    }

    public Path executionsDir() {
        return workstreamDir().resolve("executions");
    }

    public Path workflowExecutionsDir() {
        return executionsDir().resolve("workflow");
    }

    public Path nodeExecutionsDir() {
        return executionsDir().resolve("nodes");
    }

    public Path agentTasksDir() {
        return workstreamDir().resolve("queue").resolve("agent-tasks");
    }

    public Path workflowExecutionJson(String workflowExecutionId) {
        return workflowExecutionsDir().resolve(workflowExecutionId + ".json");
    }

    public Path nodeExecutionJson(String nodeExecutionId) {
        return nodeExecutionsDir().resolve(nodeExecutionId + ".json");
    }

    public Path agentTaskJson(String agentTaskId) {
        return agentTasksDir().resolve(agentTaskId + ".json");
    }

    public Path runDir(dev.dual.core.id.DualId runId) {
        return runsDir().resolve(runId.value());
    }

    public Path runJson(dev.dual.core.id.DualId runId) {
        return runDir(runId).resolve("run.json");
    }

    public Path runPromptsDir(dev.dual.core.id.DualId runId) {
        return runDir(runId).resolve("prompts");
    }

    public Path runOutputsDir(dev.dual.core.id.DualId runId) {
        return runDir(runId).resolve("outputs");
    }

    public Path runPrompt(dev.dual.core.id.DualId runId, String name) {
        return runPromptsDir(runId).resolve(name);
    }

    public Path workstreamJson() {
        return workstreamDir().resolve("workstream.json");
    }

    public Path settingsJson() {
        return workstreamDir().resolve("settings.json");
    }

    public Path currentStageJson() {
        return workstreamDir().resolve("current").resolve("stage.json");
    }

    public Path currentIterationJson() {
        return workstreamDir().resolve("current").resolve("iteration.json");
    }

    public Path currentCheckpointJson() {
        return workstreamDir().resolve("current").resolve("checkpoint.json");
    }

    public Path workflowDir() {
        return workstreamDir().resolve("workflow");
    }

    public Path workflowDefinitionJson() {
        return workflowDir().resolve("definition.json");
    }

    public Path workflowInstanceJson() {
        return workflowDir().resolve("instance.json");
    }

    public Path workflowCurrentJson() {
        return workflowDir().resolve("current.json");
    }

    public Path nodesDir() {
        return workstreamDir().resolve("nodes");
    }

    public Path iterationsDir() {
        return workstreamDir().resolve("iterations");
    }

    public Path iterationJson(String iterationId) {
        return iterationsDir().resolve(iterationId + ".json");
    }

    public Path nodeJson(String nodeInstanceId) {
        return nodesDir().resolve(nodeInstanceId + ".json");
    }

    public Path gatesDir() {
        return workstreamDir().resolve("gates");
    }

    public Path gateJson(String gateId) {
        return gatesDir().resolve(gateId + ".json");
    }

    public Path eventsJsonl() {
        return workstreamDir().resolve("events.jsonl");
    }

    public Path designStageJson() {
        return workstreamDir().resolve("stages").resolve("01-design").resolve("stage.json");
    }

    public Path designIterationJson(String iteration) {
        return workstreamDir().resolve("stages").resolve("01-design").resolve("iterations").resolve(iteration).resolve("iteration.json");
    }

    public Path workspaceJson(DualId workspaceId) {
        return workstreamDir().resolve("workspaces").resolve(workspaceId.value() + ".json");
    }

    public Path requirementMarkdown() {
        return workstreamDir().resolve("requirement.md");
    }

    public Path timelineJsonl() {
        return workstreamDir().resolve("timeline.jsonl");
    }

    public Path timelineJson() {
        return workstreamDir().resolve("timeline.json");
    }

    public Path metricsDir() {
        return workstreamDir().resolve("metrics");
    }

    public Path domainDir() {
        return workstreamDir().resolve("domain");
    }

    public Path goalSatisfactionJson() {
        return domainDir().resolve("goal-satisfaction.json");
    }

    public Path completionAuditJson() {
        return domainDir().resolve("completion-audit.json");
    }

    public Path workstreamMetricsJson() {
        return metricsDir().resolve("workstream-metrics.json");
    }

    public Path workstreamMetricsMarkdown() {
        return metricsDir().resolve("workstream-metrics.md");
    }

    public Path recoveryDir() {
        return workstreamDir().resolve("recovery");
    }

    public Path currentFailureMarkdown() {
        return recoveryDir().resolve("current-failure.md");
    }

    public Path deliveryDir() {
        return workstreamDir().resolve("delivery");
    }

    public Path deliveryCandidatesDir() {
        return deliveryDir().resolve("candidates");
    }

    public Path deliveryCandidateDir(String producerNodeInstanceId) {
        return deliveryCandidatesDir().resolve(producerNodeInstanceId);
    }

    public Path deliveryMergePlanJson() {
        return deliveryDir().resolve("merge-plan.json");
    }

    public Path deliveryMergePatch() {
        return deliveryDir().resolve("merge.patch");
    }

    public Path deliveryChangedFilesTxt() {
        return deliveryDir().resolve("changed-files.txt");
    }

    public Path deliveryExcludedFilesTxt() {
        return deliveryDir().resolve("excluded-files.txt");
    }

    public Path deliveryApplyStdoutLog() {
        return deliveryDir().resolve("apply.stdout.log");
    }

    public Path deliveryApplyStderrLog() {
        return deliveryDir().resolve("apply.stderr.log");
    }
}
