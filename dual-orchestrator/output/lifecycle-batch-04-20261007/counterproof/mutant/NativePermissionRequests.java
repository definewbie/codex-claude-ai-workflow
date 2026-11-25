package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static dev.dual.runtime.handler.AgentRunCommitProtocol.*;

/** R owns immutable materials; L owns C/X. This is not another grant store or replay sender. */
public final class NativePermissionRequests {
    private static final AtomicJsonWriter JSON = new AtomicJsonWriter();
    private NativePermissionRequests() {}

    public static void record(TaskHandler.TaskContext context, Prepared prepared, Map<String, Object> nativeRequest) throws IOException {
        qualifyDispatch(context, prepared);
        var request = JSON.objectMapper().valueToTree(nativeRequest);
        validateRequest(request, prepared);
        var run = run(prepared);
        try (var ignored = lock(run, context.workstreamId())) {
            requireLiveOwner(prepared, true);
            immutable(material(prepared, request.path("requestKey").asText(), "request"),
                    Map.of("schema", "dual.run.permission.request.v1", "prepared", prepared.reference(), "native", request));
        }
    }

    /** Human action saves A, never C. It performs no native command and holds no L while writing R. */
    public static Map<String, Object> decide(WorkflowFileLedger ledger, WorkstreamPaths paths, String executionId,
                                            String key, String optionId, String note) throws IOException {
        if (executionId == null || executionId.isBlank() || key == null || optionId == null || optionId.isBlank())
            throw new IllegalArgumentException("decision requires exact execution, request and option ids");
        Prepared prepared;
        try (var read = ledger.readSession(paths)) {
            var execution = read.ledger().readNodeExecutions(paths).stream().filter(e -> e.nodeExecutionId().equals(executionId))
                    .findFirst().orElseThrow(() -> new IOException("permission execution is absent"));
            read.ledger().verifyExecutionClaim(paths, execution);
            prepared = boundPreparation(paths, execution);
        }
        var run = run(prepared);
        try (var ignored = lock(run, paths.workstreamId().value())) {
            requireLiveOwner(prepared, false);
            var requestPath = material(prepared, key, "request");
            var record = JSON.readTree(requestPath);
            verifyPrepared(record, prepared);
            validateRequest(record.path("native"), prepared);
            validateOption(record.path("native"), optionId);
            var requestRef = referencePair(paths.workspaceRoot(), requestPath);
            var value = Map.of("schema", "dual.run.permission.decision.v1", "prepared", prepared.reference(),
                    "request", requestRef, "option_id", optionId, "operator_note", note == null ? "" : note);
            var decision = material(prepared, key, "decision");
            immutable(decision, value);
            return Map.of("status", "DECISION_SAVED", "execution", executionId, "request_key", key,
                    "decision", referencePair(paths.workspaceRoot(), decision), "send_authority", false);
        }
    }

    /** Returns a payload only to the original call that just completed C. Existing C is never resent. */
    public static Optional<Map<String, Object>> commit(TaskHandler.TaskContext context, Prepared prepared, String key) throws IOException {
        Map<String, String> requestRef;
        Map<String, String> decisionRef;
        JsonNode nativeRequest;
        String option;
        try (var ignored = lock(run(prepared), context.workstreamId())) {
            requireLiveOwner(prepared, true);
            var decisionPath = material(prepared, key, "decision");
            if (!Files.isRegularFile(decisionPath)) return Optional.empty();
            var requestPath = material(prepared, key, "request");
            var request = JSON.readTree(requestPath);
            var decision = JSON.readTree(decisionPath);
            verifyPrepared(request, prepared); verifyPrepared(decision, prepared);
            requestRef = referencePair(context.workspaceRoot(), requestPath);
            decisionRef = referencePair(context.workspaceRoot(), decisionPath);
            if (!JSON.objectMapper().valueToTree(requestRef).equals(decision.path("request"))) throw new IOException("permission request identity drift");
            nativeRequest = request.path("native").deepCopy();
            option = decision.path("option_id").asText();
        }
        var paths = paths(context);
        var commit = new LinkedHashMap<String, Object>();
        commit.put("request", requestRef); commit.put("decision", decisionRef);
        commit.put("option_id", option); commit.put("deadline_ms", nativeRequest.path("deadlineMs").asLong());
        commit.put("prepared", prepared.reference());
        var commitSha = hash(canonical(commit));
        try (var write = context.ledger().writeSession(paths, "native permission reply C", key)) {
            var scoped = new TaskHandler.TaskContext(context.workspaceRoot(), context.workstreamId(), context.definition(), context.instance(), write.ledger());
            qualifyDispatch(scoped, prepared);
            requireLiveOwner(prepared, true); // Fixed R bytes only; never acquire R under L.
            verifyReference(context.workspaceRoot(), requestRef); verifyReference(context.workspaceRoot(), decisionRef);
            validateRequest(nativeRequest, prepared); validateOption(nativeRequest, option);
            var fresh = write.ledger().commitPermissionReply(paths, prepared.execution(), key, commit);
            write.commit();
            // Counterproof only: incorrectly return old C as fresh send qualification.
        }
        var reply = new LinkedHashMap<String, Object>();
        for (var name : List.of("requestKey", "sessionId", "promptId", "ownerGeneration", "rpcId", "rawSha256", "deadlineMs"))
            reply.put(name, JSON.objectMapper().convertValue(nativeRequest.path(name), Object.class));
        reply.put("optionId", option);
        reply.put("commitRef", reference(context.workspaceRoot(), paths.nodeExecutionJson(prepared.execution().nodeExecutionId())) + "#permission_reply/commits/" + key);
        reply.put("commitSha256", commitSha);
        return Optional.of(Map.copyOf(reply));
    }

    /** R records a send intent/result, not permission to send. Failed/unknown IPC never resets C. */
    public static void observe(TaskHandler.TaskContext context, Prepared prepared, String key, String phase,
                                Map<String, Object> observation) throws IOException {
        if (!List.of("send-intent", "send-result").contains(phase)) throw new IOException("unknown permission observation");
        try (var ignored = lock(run(prepared), context.workstreamId())) {
            requireLiveOwner(prepared, true);
            var request = material(prepared, key, "request");
            if (!Files.isRegularFile(request)) throw new IOException("permission observation has no request");
            immutable(material(prepared, key, phase), Map.of("schema", "dual.run.permission.observation.v1", "prepared", prepared.reference(),
                    "request", referencePair(context.workspaceRoot(), request), "phase", phase, "observation", observation));
        }
    }

    public static void close(WorkflowFileLedger ledger, WorkstreamPaths paths, String executionId, String reason) throws IOException {
        try (var write = ledger.writeSession(paths, "native permission reply X", executionId)) {
            write.ledger().closeLivePermissionReplies(paths, executionId, reason);
            write.commit();
        }
    }

    public static Map<String, Object> cancel(WorkflowFileLedger ledger, WorkstreamPaths paths, String executionId, String reason) throws IOException {
        if (executionId == null || executionId.isBlank()) throw new IllegalArgumentException("cancel requires exact execution id");
        try (var write = ledger.writeSession(paths, "cancel current native permission wait", executionId)) {
            var execution = write.ledger().readNodeExecutions(paths).stream().filter(e -> e.nodeExecutionId().equals(executionId)).findFirst()
                    .orElseThrow(() -> new IOException("execution absent"));
            var node = write.ledger().loadNode(paths, execution.nodeInstanceId());
            if (execution.status() != NodeExecutionStatus.RUNNING || !node.latestExecutionId().equals(executionId)
                    || !write.ledger().loadCurrent(paths).activeNodeInstanceIds().contains(node.nodeInstanceId()))
                throw new IOException("historical or ended permission execution cannot be cancelled as current");
            write.ledger().closeLivePermissionReplies(paths, executionId, reason);
            write.commit();
        }
        return Map.of("status", "STOP_UNCONFIRMED", "execution", executionId,
                "message", "New submissions closed; original executor must confirm stop. One already committed reply may be in flight.");
    }

    public static boolean closed(TaskHandler.TaskContext context, Prepared prepared) throws IOException {
        var paths = paths(context);
        try (var read = context.ledger().readSession(paths)) {
            var execution = read.ledger().readNodeExecutions(paths).stream().filter(e -> e.nodeExecutionId().equals(prepared.execution().nodeExecutionId()))
                    .findFirst().orElseThrow(() -> new IOException("execution absent"));
            if (execution.result().get("permission_reply") instanceof Map<?, ?> reply && Boolean.TRUE.equals(reply.get("closed"))) return true;
            read.ledger().verifyExecutionClaim(paths, prepared.execution());
            return false;
        }
    }

    /** Shared current/history projection. It carries no callback or sending capability. */
    public static Map<String, Object> view(WorkflowFileLedger ledger, WorkstreamPaths paths) throws IOException {
        List<NodeExecutionDocument> executions;
        Set<String> active;
        Set<String> latest;
        long sequence;
        try (var read = ledger.readSession(paths)) {
            executions = read.ledger().readNodeExecutions(paths);
            var current = read.ledger().loadCurrent(paths);
            sequence = current.commitSequence();
            active = Set.copyOf(current.activeNodeInstanceIds());
            latest = new HashSet<>();
            for (var nodeId : active) latest.add(read.ledger().loadNode(paths, nodeId).latestExecutionId());
        }
        var rows = new ArrayList<Map<String, Object>>();
        for (var execution : executions) {
            if (!(execution.result().get("prepared_run") instanceof Map<?, ?>)) continue;
            var prepared = boundPreparation(paths, execution);
            try (var ignored = lock(run(prepared), paths.workstreamId().value())) {
                try (var files = Files.list(prepared.path().getParent())) {
                    for (var path : files.filter(f -> f.getFileName().toString().startsWith(execution.nodeExecutionId() + "-permission-")
                            && f.getFileName().toString().endsWith("-request.json")).sorted().toList()) {
                        var record = JSON.readTree(path); verifyPrepared(record, prepared);
                        var request = record.path("native"); var key = request.path("requestKey").asText();
                        var reply = JSON.objectMapper().valueToTree(execution.result().get("permission_reply"));
                        boolean shut = reply != null && reply.path("closed").asBoolean();
                        boolean current = execution.status() == NodeExecutionStatus.RUNNING && active.contains(execution.nodeInstanceId()) && latest.contains(execution.nodeExecutionId()) && executorOwnsInvocation(paths, execution) && !observeRunEnded(prepared);
                        var decisionPath = material(prepared, key, "decision");
                        var observedPath = material(prepared, key, "send-result");
                        var committed = reply != null && reply.path("commits").has(key);
                        boolean expired = request.path("deadlineMs").asLong() <= System.currentTimeMillis();
                        String status = Files.isRegularFile(observedPath) ? observationStatus(JSON.readTree(observedPath))
                                : shut && current ? "STOP_UNCONFIRMED" : committed ? "COMMITTED_MAY_BE_IN_FLIGHT"
                                : !current ? "HISTORICAL" : expired ? "EXPIRED" : Files.isRegularFile(decisionPath) ? "DECISION_SAVED" : "REQUEST_PENDING";
                        var row = new LinkedHashMap<String, Object>();
                        row.put("execution", execution.nodeExecutionId()); row.put("node", execution.nodeInstanceId());
                        row.put("request_key", key); row.put("request", request); row.put("request_ref", referencePair(paths.workspaceRoot(), path));
                        row.put("status", status); row.put("current", current);
                        var raw = JSON.objectMapper().readTree(request.path("rawJson").asText());
                        row.put("raw_request", raw);
                        row.put("options", java.util.stream.StreamSupport.stream(raw.path("options").spliterator(), false)
                                .filter(o -> List.of("allow_once", "reject_once").contains(o.path("kind").asText())).toList());
                        row.put("can_decide", current && !shut && !expired && !committed && !Files.isRegularFile(decisionPath) && !Files.isRegularFile(observedPath));
                        row.put("can_cancel", current && !shut);
                        row.put("business_approved", false); // Native selection never replaces a business gate.
                        if (Files.isRegularFile(decisionPath)) row.put("decision", JSON.readTree(decisionPath));
                        if (committed) row.put("commit", reply.path("commits").path(key));
                        if (Files.isRegularFile(observedPath)) row.put("observation", JSON.readTree(observedPath));
                        rows.add(row);
                    }
                }
            }
        }
        return Map.of("schema", "dual.native.permissions.view.v1", "workstreamId", paths.workstreamId().value(), "commitSequence", sequence, "requests", rows,
                "notice", "Saved decisions are not sent approvals; committed replies may be in flight; native approval does not approve business scope or delivery.");
    }

    private static String observationStatus(JsonNode observed) {
        return switch (observed.path("observation").path("delivery").asText()) {
            case "ACKNOWLEDGED" -> "REPLY_CONSUMED";
            case "REJECTED" -> "REPLY_REJECTED";
            case "REQUEST_ENDED" -> "REQUEST_ENDED";
            default -> "REPLY_UNKNOWN";
        };
    }

    private static void validateRequest(JsonNode request, Prepared prepared) throws IOException {
        if (!"dual.native.permission.request.v1".equals(request.path("schema").asText())) throw new IOException("invalid native permission schema");
        for (var name : List.of("requestKey", "sessionId", "promptId", "rawJson", "rawSha256"))
            if (!request.path(name).isTextual() || request.path(name).asText().isBlank()) throw new IOException("native permission identity missing: " + name);
        if (!request.path("requestKey").asText().matches("[a-f0-9-]{36}") || !request.path("ownerGeneration").isIntegralNumber()
                || request.path("ownerGeneration").asLong() <= 0 || !(request.path("rpcId").isTextual() || request.path("rpcId").isNumber()))
            throw new IOException("invalid native RPC or owner identity");
        var rawJson = request.path("rawJson").asText();
        if (!hash(rawJson.getBytes(StandardCharsets.UTF_8)).equals(request.path("rawSha256").asText())) throw new IOException("native raw request SHA differs");
        var raw = JSON.objectMapper().readTree(rawJson);
        if (!raw.path("sessionId").equals(request.path("sessionId")) || !raw.path("options").isArray() || raw.path("options").isEmpty())
            throw new IOException("native request has no matching session/options");
        var ids = new HashSet<String>();
        for (var option : raw.path("options")) if (!option.path("optionId").isTextual() || !ids.add(option.path("optionId").asText()))
            throw new IOException("native options are ambiguous");
        var deadline = request.path("deadlineMs").asLong();
        var budget = JSON.readTree(prepared.path()).path("binding").path("native_permission_budget_ms").asLong();
        if (budget <= 0 || deadline <= System.currentTimeMillis() || deadline > System.currentTimeMillis() + budget)
            throw new IOException("permission ask lacks an unexpired finite prepared budget");
    }

    private static void validateOption(JsonNode request, String optionId) throws IOException {
        var raw = JSON.objectMapper().readTree(request.path("rawJson").asText());
        for (var option : raw.path("options")) if (option.path("optionId").asText().equals(optionId)
                && List.of("allow_once", "reject_once").contains(option.path("kind").asText())) return;
        throw new IOException("choice is not an exact nonpersistent offered option");
    }

    private static void requireLiveOwner(Prepared prepared, boolean originalCaller) throws IOException {
        if (!hash(Files.readAllBytes(prepared.path())).equals(prepared.reference().get("sha256"))) throw new IOException("prepared identity differs");
        var fixed = JSON.readTree(prepared.path()); var claim = fixed.path("claim"); var self = ProcessHandle.current();
        if (originalCaller && (self.pid() != claim.path("executor_pid").asLong()
                || !self.info().startInstant().map(Object::toString).orElse("").equals(claim.path("executor_started_at").asText())))
            throw new IOException("permission send belongs to the original live executor only");
        // Human saving runs in another process; liveness belongs to the original PID and birth, not this UI process.
        var owner = ProcessHandle.of(claim.path("executor_pid").asLong());
        if (owner.isEmpty() || !owner.get().isAlive() || !owner.get().info().startInstant().map(Object::toString).orElse("").equals(claim.path("executor_started_at").asText())
                || observeRunEnded(prepared)) throw new IOException("permission request has no live original executor");
    }
    private static boolean observeRunEnded(Prepared prepared) {
        var prefix = prepared.path().getFileName().toString().replace("-prepared.json", "");
        return !Files.isRegularFile(prepared.path().resolveSibling(prefix + "-intent.json"))
                || Files.exists(prepared.path().resolveSibling(prefix + "-released.json"))
                || Files.exists(prepared.path().resolveSibling(prefix + "-completion.json"))
                || Files.exists(prepared.path().getParent().resolve("execution-outcome.json"));
    }
    private static void verifyPrepared(JsonNode record, Prepared prepared) throws IOException {
        if (!record.path("prepared").equals(JSON.objectMapper().valueToTree(prepared.reference()))) throw new IOException("permission material binds another preparation");
    }
    private static Path run(Prepared prepared) { return prepared.path().getParent().getParent(); }
    private static Path material(Prepared prepared, String key, String kind) throws IOException {
        if (!key.matches("[a-f0-9-]{36}")) throw new IOException("invalid permission request key");
        return prepared.path().getParent().resolve(prepared.execution().nodeExecutionId() + "-permission-" + key + "-" + kind + ".json");
    }
    private static Map<String, String> referencePair(Path root, Path file) throws IOException {
        return Map.of("ref", reference(root, file), "sha256", hash(Files.readAllBytes(file)));
    }
    private static void verifyReference(Path root, Map<String, String> reference) throws IOException {
        if (!hash(Files.readAllBytes(inside(root, reference.get("ref")))).equals(reference.get("sha256"))) throw new IOException("permission material changed before C");
    }
    private static byte[] canonical(Object value) throws IOException {
        return JSON.objectMapper().writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(value);
    }
}
