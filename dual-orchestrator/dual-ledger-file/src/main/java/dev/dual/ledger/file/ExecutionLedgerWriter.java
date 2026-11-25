package dev.dual.ledger.file;

import dev.dual.core.workflow.WorkflowStatus;

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
        var authoritative = jsonWriter.read(paths.workflowCurrentJson(), WorkflowCurrentDocument.class);
        if (!authoritative.equals(current) || authoritative.status().isTerminal())
            throw new IOException("cannot start execution from terminal or stale workflow state");
        for (var id : authoritative.activeNodeInstanceIds()) {
            var active = jsonWriter.read(paths.nodeJson(id), NodeInstanceDocument.class);
            if (active.status() == NodeStatus.COMPLETED)
                throw new IOException("active workflow points to a completed node without a pending commit");
        }
        for (var id : authoritative.openGateIds()) {
            var gate = jsonWriter.read(paths.gateJson(id), GateDocument.class);
            if (!"open".equals(gate.status()) || !authoritative.activeNodeInstanceIds().contains(gate.nodeInstanceId()))
                throw new IOException("current open gate is resolved or belongs to another occurrence");
        }
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
        var current = jsonWriter.read(paths.workflowCurrentJson(), WorkflowCurrentDocument.class);
        var actual = jsonWriter.read(paths.nodeJson(node.nodeInstanceId()), NodeInstanceDocument.class);
        if (agentTask == null || current.status().isTerminal()
                || !current.activeNodeInstanceIds().contains(node.nodeInstanceId()) || !actual.equals(node))
            throw new IOException("execution requires a task bound to the current eligible node");
        var task = jsonWriter.read(paths.agentTaskJson(agentTask.agentTaskId()), AgentTaskDocument.class);
        if (!task.equals(agentTask) || task.status() != AgentTaskStatus.QUEUED
                || !task.nodeInstanceId().equals(node.nodeInstanceId()))
            throw new IOException("task is not available for a unique claim");
        boolean queued = node.status() == NodeStatus.RUNNING
                && task.agentTaskId().equals(node.result().get("queued_agent_task_id"))
                && "QUEUED".equals(node.result().get("dispatch_state"));
        if (node.status() != NodeStatus.READY && !queued)
            throw new IOException("node is neither READY nor waiting for this queued task");
        var previous = latestNodeExecution(paths, node);
        if (previous.isPresent() && previous.get().status() == NodeExecutionStatus.RUNNING)
            throw new IOException("previous execution still owns the node");
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
                Map.of("claim", Map.of(
                        "executor_pid", pid,
                        "executor_started_at", ProcessHandle.of(pid).flatMap(p -> p.info().startInstant())
                                .orElseThrow(() -> new IOException("executor start identity unavailable")).toString(),
                        "input_identity", inputIdentity(node))),
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
        nodeExecution = currentRunningExecution(paths, nodeExecution);
        return writeFinished(paths, nodeExecution, result);
    }

    NodeExecutionDocument adoptVerifiedResult(WorkstreamPaths paths, NodeExecutionDocument execution, NodeResult result)
            throws IOException {
        var actual = jsonWriter.read(paths.nodeExecutionJson(execution.nodeExecutionId()), NodeExecutionDocument.class);
        var node = jsonWriter.read(paths.nodeJson(actual.nodeInstanceId()), NodeInstanceDocument.class);
        var current = jsonWriter.read(paths.workflowCurrentJson(), WorkflowCurrentDocument.class);
        if (!actual.equals(execution) || actual.status() != NodeExecutionStatus.FAILED
                || !"UNKNOWN".equals(actual.result().get("external_effect"))
                || current.status() != WorkflowStatus.WAITING_FOR_RECOVERY
                || !current.activeNodeInstanceIds().equals(List.of(node.nodeInstanceId()))
                || node.status() != NodeStatus.FAILED || !node.latestExecutionId().equals(actual.nodeExecutionId())
                || !(actual.result().get("claim") instanceof Map<?, ?> claim)
                || !inputIdentity(node).equals(claim.get("input_identity")))
            throw new IOException("late completion is not for the current unknown execution");
        var facts = new LinkedHashMap<String, Object>(actual.result());
        facts.put("prior_unknown_observation", Map.of("outcome", actual.outcome(), "result", actual.result(),
                "finished_at", actual.finishedAt().toString()));
        for (var key : java.util.List.of("reason", "external_effect", "provider_invocation", "automatic_retry")) facts.remove(key);
        return writeFinished(paths, withFacts(actual, facts), result);
    }

    private NodeExecutionDocument writeFinished(WorkstreamPaths paths, NodeExecutionDocument nodeExecution, NodeResult result)
            throws IOException {
        var finishedAt = Instant.now();
        var resultMap = mergeOwnedFacts(nodeExecution, result == null ? Map.of() : result.result());
        if (result == null || result.status() != NodeStatus.RUNNING) closeReplyFacts(resultMap);
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
        nodeExecution = currentRunningExecution(paths, nodeExecution);
        var finishedAt = Instant.now();
        var details = new LinkedHashMap<String, Object>(nodeExecution.result());
        closeReplyFacts(details);
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
        if (!jsonWriter.exists(paths.nodeExecutionsDir())) {
            return List.of();
        }
        var documents = new ArrayList<NodeExecutionDocument>();
        try (var stream = jsonWriter.list(paths.nodeExecutionsDir()).stream()) {
            var files = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (var file : files) {
                documents.add(jsonWriter.read(file, NodeExecutionDocument.class));
            }
        }
        return documents;
    }

    Optional<NodeExecutionDocument> latestNodeExecution(WorkstreamPaths paths, NodeInstanceDocument node) throws IOException {
        if (node.latestExecutionId().isBlank()) {
            return Optional.empty();
        }
        var path = paths.nodeExecutionJson(node.latestExecutionId());
        if (!jsonWriter.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(jsonWriter.read(path, NodeExecutionDocument.class));
    }

    NodeExecutionDocument markNodeExecutionStale(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            String note
    ) throws IOException {
        var finishedAt = Instant.now();
        var staleResult = new LinkedHashMap<String, Object>();
        staleResult.putAll(nodeExecution.result());
        closeReplyFacts(staleResult);
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

    private NodeExecutionDocument currentRunningExecution(WorkstreamPaths paths, NodeExecutionDocument expected)
            throws IOException {
        var actual = jsonWriter.read(paths.nodeExecutionJson(expected.nodeExecutionId()), NodeExecutionDocument.class);
        var node = jsonWriter.read(paths.nodeJson(expected.nodeInstanceId()), NodeInstanceDocument.class);
        var current = jsonWriter.read(paths.workflowCurrentJson(), WorkflowCurrentDocument.class);
        if (actual.status() != NodeExecutionStatus.RUNNING
                || !actual.agentTaskId().equals(expected.agentTaskId())
                || !actual.nodeInstanceId().equals(expected.nodeInstanceId())
                || !node.latestExecutionId().equals(actual.nodeExecutionId())
                || node.status() != NodeStatus.RUNNING || current.status().isTerminal()
                || !current.activeNodeInstanceIds().contains(node.nodeInstanceId())
                || !(actual.result().get("claim") instanceof Map<?, ?> claim)
                || !inputIdentity(node).equals(claim.get("input_identity")))
            throw new IOException("execution claim is no longer current");
        return actual;
    }

    NodeExecutionDocument verifyClaim(WorkstreamPaths paths, NodeExecutionDocument expected) throws IOException {
        var value = currentRunningExecution(paths, expected);
        if (value.result().get("permission_reply") instanceof Map<?, ?> reply && Boolean.TRUE.equals(reply.get("closed")))
            throw new IOException("permission reply submission is closed");
        return value;
    }

    void bindPreparedRun(WorkstreamPaths paths, NodeExecutionDocument expected, Map<String, String> reference)
            throws IOException {
        var value = verifyClaim(paths, expected);
        if (!reference.keySet().equals(java.util.Set.of("ref", "sha256"))
                || reference.get("ref").isBlank() || !reference.get("sha256").matches("[a-f0-9]{64}"))
            throw new IOException("invalid prepared run reference");
        var prior = value.result().get("prepared_run");
        if (prior != null && !prior.equals(reference)) throw new IOException("claim already binds another preparation");
        var facts = new LinkedHashMap<String, Object>(value.result());
        facts.put("prepared_run", reference);
        jsonWriter.write(paths.nodeExecutionJson(value.nodeExecutionId()), withFacts(value, facts));
    }

    private String inputIdentity(NodeInstanceDocument node) throws IOException {
        try {
            var bytes = jsonWriter.objectMapper().writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(Map.of(
                    "node", node.nodeInstanceId(), "iteration", node.iterationId(),
                    "input_refs", node.inputRefs(), "input_transition", node.inputTransition()));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static NodeExecutionDocument withFacts(NodeExecutionDocument value, Map<String, Object> facts) {
        return new NodeExecutionDocument(value.schemaVersion(), value.nodeExecutionId(), value.agentTaskId(),
                value.workflowExecutionId(), value.nodeInstanceId(), value.nodeDefinitionId(), value.handlerType(),
                value.status(), value.pid(), value.outcome(), value.nodeStatus(), value.outputRefs(), facts,
                value.errorMessage(), value.runRef(), value.agentRunRef(), value.agent(), value.session(),
                value.exitCode(), value.startedAt(), value.finishedAt(), value.durationMs());
    }

    /** Plans C only. The live caller must complete the surrounding D06 mutation before sending. */
    boolean commitPermissionReply(WorkstreamPaths paths, NodeExecutionDocument expected, String key, Map<String, Object> commit)
            throws IOException {
        if (!key.matches("[a-f0-9-]{36}")) throw new IOException("invalid permission request key");
        var value = jsonWriter.read(paths.nodeExecutionJson(expected.nodeExecutionId()), NodeExecutionDocument.class);
        var reply = objectMap(value.result().get("permission_reply"));
        var commits = objectMap(reply.get("commits"));
        var prior = commits.get(key);
        if (prior != null) {
            if (!jsonWriter.objectMapper().valueToTree(prior).equals(jsonWriter.objectMapper().valueToTree(commit)))
                throw new IOException("permission commit identity conflict");
            return false; // Neither restart nor idempotent read grants a new send.
        }
        value = verifyClaim(paths, expected);
        if (!(commit.get("deadline_ms") instanceof Number deadline) || deadline.longValue() <= System.currentTimeMillis())
            throw new IOException("permission request expired before C");
        for (var name : List.of("request", "decision")) {
            var ref = objectMap(commit.get(name));
            if (!ref.keySet().equals(java.util.Set.of("ref", "sha256")) || text(ref.get("ref")).isBlank()
                    || !text(ref.get("sha256")).matches("[a-f0-9]{64}")) throw new IOException("permission commit lacks immutable material identity");
        }
        if (text(commit.get("option_id")).isBlank()) throw new IOException("permission commit has no exact option");
        commits.put(key, Map.copyOf(commit));
        reply.put("commits", commits);
        reply.put("closed", false);
        var facts = new LinkedHashMap<String, Object>(value.result());
        facts.put("permission_reply", reply);
        jsonWriter.write(paths.nodeExecutionJson(value.nodeExecutionId()), withFacts(value, facts));
        return true;
    }

    void closeLivePermissionReplies(WorkstreamPaths paths, String executionId) throws IOException {
        var value = jsonWriter.read(paths.nodeExecutionJson(executionId), NodeExecutionDocument.class);
        var facts = new LinkedHashMap<String, Object>(value.result());
        closeReplyFacts(facts);
        jsonWriter.write(paths.nodeExecutionJson(executionId), withFacts(value, facts));
    }

    private static void closeReplyFacts(Map<String, Object> facts) {
        var reply = objectMap(facts.get("permission_reply"));
        reply.put("closed", true);
        facts.put("permission_reply", reply);
    }

    private static LinkedHashMap<String, Object> objectMap(Object value) {
        var map = new LinkedHashMap<String, Object>();
        if (value instanceof Map<?, ?> old) old.forEach((key, item) -> map.put(String.valueOf(key), item));
        return map;
    }

    void closePermissionReply(WorkstreamPaths paths, String executionId) throws IOException {
        var value = jsonWriter.read(paths.nodeExecutionJson(executionId), NodeExecutionDocument.class);
        if (value.status() == NodeExecutionStatus.RUNNING || value.status() == NodeExecutionStatus.CREATED
                || value.status() == NodeExecutionStatus.READY)
            throw new IOException("in-flight execution must be resolved before replacing readiness");
        closeLivePermissionReplies(paths, executionId);
    }

    private static Map<String, Object> mergeOwnedFacts(NodeExecutionDocument execution, Map<String, Object> result)
            throws IOException {
        var merged = new LinkedHashMap<String, Object>(execution.result());
        for (var entry : result.entrySet()) {
            if (java.util.Set.of("claim", "authorization", "permission_reply", "prepared_run")
                    .contains(entry.getKey()))
                throw new IOException("handler result cannot replace execution-owned fact: " + entry.getKey());
            merged.put(entry.getKey(), entry.getValue());
        }
        return merged;
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
