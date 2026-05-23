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
        return agentDir().resolve("runs");
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

    public Path currentStageJson() {
        return workstreamDir().resolve("current").resolve("stage.json");
    }

    public Path currentIterationJson() {
        return workstreamDir().resolve("current").resolve("iteration.json");
    }

    public Path currentCheckpointJson() {
        return workstreamDir().resolve("current").resolve("checkpoint.json");
    }

    public Path currentHumanGateJson() {
        return workstreamDir().resolve("current").resolve("human-gate.json");
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
}
