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
    private final RevisionTracker revisionTracker;
    private final NodeStateWriter nodeStateWriter;
    private final TimelineProjectionWriter timelineProjectionWriter;
    private final MetricsProjectionWriter metricsProjectionWriter;
    private final FailureSummaryWriter failureSummaryWriter;
    private final ExecutionLedgerWriter executionLedgerWriter;
    private final AgentTaskLedgerWriter agentTaskLedgerWriter;
    private final FlowOrchestrationKernel flowKernel;
    private final Map<String, String> runtimeIdentity;
    private boolean projectionRefreshRequested;
    private final LedgerSession session;
    private LedgerSession.FaultPoint faultPoint = (point, target) -> {};

    public WorkflowFileLedger(FlowOrchestrationKernel flowKernel, Map<String, String> runtimeIdentity) {
        this(new AtomicJsonWriter(), flowKernel, Map.copyOf(runtimeIdentity), null);
        if (runtimeIdentity.isEmpty()) throw new IllegalArgumentException("runtime identity is required");
    }

    WorkflowFileLedger(FlowOrchestrationKernel flowKernel, Map<String, String> runtimeIdentity,
                       LedgerSession.FaultPoint faultPoint) {
        this(flowKernel, runtimeIdentity);
        this.faultPoint = java.util.Objects.requireNonNull(faultPoint);
    }

    private WorkflowFileLedger(AtomicJsonWriter jsonWriter, FlowOrchestrationKernel flowKernel,
                               Map<String, String> runtimeIdentity, LedgerSession session) {
        if (flowKernel == null) {
            throw new IllegalArgumentException("flow orchestration kernel is required");
        }
        this.jsonWriter = jsonWriter;
        this.flowKernel = flowKernel;
        this.runtimeIdentity = runtimeIdentity;
        this.session = session;
        this.objectMapper = jsonWriter.objectMapper();
        var jsonlMapper = jsonWriter.objectMapper().copy()
                .disable(SerializationFeature.INDENT_OUTPUT);
        this.eventLog = new LedgerEventLog(jsonWriter, jsonlMapper);
        this.revisionTracker = new RevisionTracker(jsonWriter, this.objectMapper, eventLog);
        this.routingEngine = new RoutingEngine(jsonWriter, this.objectMapper, eventLog, revisionTracker, flowKernel, runtimeIdentity);
        this.nodeStateWriter = new NodeStateWriter(jsonWriter, this.objectMapper, eventLog);
        this.timelineProjectionWriter = new TimelineProjectionWriter(jsonWriter, this.objectMapper);
        this.metricsProjectionWriter = new MetricsProjectionWriter(jsonWriter, this.objectMapper);
        this.failureSummaryWriter = new FailureSummaryWriter(this.objectMapper);
        this.executionLedgerWriter = new ExecutionLedgerWriter(jsonWriter);
        this.agentTaskLedgerWriter = new AgentTaskLedgerWriter(jsonWriter);
    }

    @FunctionalInterface
    public interface ReadOperation<T> { T read(WorkflowFileLedger ledger, LedgerSession files) throws IOException; }

    /** Reuses an explicitly passed session; never installs an ambient/default ledger. */
    public <T> T inspect(WorkstreamPaths paths, ReadOperation<T> operation) throws IOException {
        if (session != null) {
            if (!session.paths().workstreamDir().toAbsolutePath().normalize()
                    .equals(paths.workstreamDir().toAbsolutePath().normalize()))
                throw new IOException("session belongs to another workstream");
            return operation.read(this, session);
        }
        try (var snapshot = readSession(paths)) { return operation.read(snapshot.ledger(), snapshot.files()); }
    }

    boolean hasSession() { return session != null; }

    public LedgerSession files() {
        if (session == null) throw new IllegalStateException("an explicit ledger session is required");
        return session;
    }

    public FileLedgerStore store() {
        if (session == null) throw new IllegalStateException("store access requires an explicit ledger session");
        return new FileLedgerStore(jsonWriter, runtimeIdentity);
    }

    public Map<String, Object> timelineSnapshot(WorkstreamPaths paths) throws IOException {
        return inspect(paths, (scoped, files) -> new TimelineProjectionWriter(new AtomicJsonWriter(files), objectMapper).snapshot(paths));
    }

    public String failureSummarySnapshot(WorkstreamPaths paths) throws IOException {
        return inspect(paths, (scoped, files) -> {
            var current = scoped.loadCurrent(paths);
            if (!java.util.Set.of(WorkflowStatus.WAITING_FOR_RECOVERY, WorkflowStatus.FAILED, WorkflowStatus.BLOCKED)
                    .contains(current.status()) || current.activeNodeInstanceIds().size() != 1) return "";
            var node = scoped.loadNode(paths, current.activeNodeInstanceIds().getFirst());
            return new FailureSummaryWriter(objectMapper).render(paths, current, node);
        });
    }

    /** Cached metrics are observations only; a cache from another commit cannot describe this snapshot. */
    public Map<String, Object> metricsSnapshot(WorkstreamPaths paths) throws IOException {
        return inspect(paths, (scoped, files) -> {
            if (!files.exists(paths.workstreamMetricsJson()))
                return Map.of("status", "STALE", "reason", "metrics projection is unavailable");
            var metrics = files.read(paths.workstreamMetricsJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            var sequence = metrics.get("commitSequence");
            if (!(sequence instanceof Number number) || number.longValue() != scoped.loadCurrent(paths).commitSequence())
                return Map.of("status", "STALE", "reason", "metrics projection belongs to another ledger commit");
            return metrics;
        });
    }

    /** L04: registration cannot resolve the subject or authorize an execution. */
    public void recordReadiness(WorkstreamPaths paths, String subject,
                                dev.dual.core.contract.TaskContract.Reference reference, byte[] bytes) throws IOException {
        if (session == null) throw new IOException("readiness requires the caller's validated write session");
        var current = loadCurrent(paths);
        if (!java.util.Set.of(WorkflowStatus.WAITING_FOR_HUMAN, WorkflowStatus.WAITING_FOR_RECOVERY).contains(current.status())
                || !current.activeNodeInstanceIds().equals(List.of(subject)))
            throw new IOException("readiness subject is no longer current");
        var node = loadNode(paths, subject);
        if (!java.util.Set.of(NodeStatus.WAITING, NodeStatus.FAILED).contains(node.status()))
            throw new IOException("readiness cannot replace running inputs");
        var prefix = ".agent/workstreams/" + paths.workstreamId().value() + "/artifacts/verification-readiness/";
        if (!reference.ref().startsWith(prefix)
                || !reference.ref().substring(prefix.length()).matches("[A-Za-z0-9._-]{1,80}\\.json")
                || !reference.sha256().equals(sha256(bytes))) throw new IOException("invalid readiness bytes or path");
        if (!node.latestExecutionId().isBlank()) executionLedgerWriter.closePermissionReply(paths, node.latestExecutionId());
        var target = paths.workspaceRoot().resolve(reference.ref());
        if (session.exists(target)) throw new IOException("readiness record already exists");
        session.writeBytes(target, bytes);
        var result = new java.util.LinkedHashMap<String, Object>(node.result());
        var refs = new java.util.ArrayList<Object>();
        if (result.get("readiness_refs") instanceof List<?> existing) refs.addAll(existing);
        refs.add(Map.of("ref", reference.ref(), "sha256", reference.sha256()));
        result.put("readiness_refs", List.copyOf(refs));
        jsonWriter.write(paths.nodeJson(subject), new NodeInstanceDocument(node.schemaVersion(), node.nodeInstanceId(),
                node.nodeDefinitionId(), node.kind(), node.handlerType(), node.status(), node.stageId(), node.iterationId(),
                node.inputRefs(), node.inputTransition(), node.outputRefs(), result, node.runRef(), node.latestExecutionId(),
                node.startedAt(), node.finishedAt()));
        appendEvent(paths, "readiness-" + target.getFileName(), "verification_readiness_recorded",
                Map.of("subject", subject, "reference", Map.of("ref", reference.ref(), "sha256", reference.sha256())));
    }

    /** Commit a runtime-validated recovery decision with its single local transition. */
    public void adjudicateUnknownExecution(WorkstreamPaths paths, String subject, String executionId,
                                           Map<String, Object> decision) throws IOException {
        adjudicateUnknownExecution(paths, subject, executionId, decision, null);
    }

    public void adjudicateUnknownExecution(WorkstreamPaths paths, String subject, String executionId,
                                           Map<String, Object> decision, dev.dual.core.workflow.NodeResult adopted) throws IOException {
        if (session == null) throw new IOException("recovery adjudication requires an explicit write session");
        var current = loadCurrent(paths);
        var node = loadNode(paths, subject);
        var execution = jsonWriter.read(paths.nodeExecutionJson(executionId), NodeExecutionDocument.class);
        if (current.status() != WorkflowStatus.WAITING_FOR_RECOVERY || !current.activeNodeInstanceIds().equals(List.of(subject))
                || node.status() != NodeStatus.FAILED || !node.latestExecutionId().equals(executionId)
                || !execution.nodeInstanceId().equals(subject) || execution.status() == NodeExecutionStatus.RUNNING
                || !"UNKNOWN".equals(node.result().get("external_effect")))
            throw new IOException("recovery decision does not match the current failed execution");
        var disposition = String.valueOf(decision.get("disposition"));
        if (!java.util.Set.of("AUTHORIZE_NEW_ATTEMPT", "ABORT", "ADOPT_VERIFIED_RESULT").contains(disposition))
            throw new IOException("unsupported unknown-execution disposition");
        if (disposition.equals("ADOPT_VERIFIED_RESULT") != (adopted != null)) throw new IOException("adoption requires a verified fixed result");
        if (adopted != null && (!java.util.Objects.equals(decision.get("completion_ref"), adopted.result().get("completion_ref"))
                || !java.util.Objects.equals(decision.get("completion_sha256"), adopted.result().get("completion_sha256"))))
            throw new IOException("adoption and fixed completion differ");
        if (!Boolean.TRUE.equals(decision.get("executor_stopped"))) throw new IOException("executor stop verification missing");
        executionLedgerWriter.closePermissionReply(paths, executionId);
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) jsonWriter.readTree(paths.nodeExecutionJson(executionId));
        var result = (com.fasterxml.jackson.databind.node.ObjectNode) document.get("result");
        var decisions = result.withObject("recovery_decisions");
        var requestId = String.valueOf(decision.get("request_id"));
        if (!requestId.matches("[A-Za-z0-9._-]{1,80}") || decisions.has(requestId)) throw new IOException("invalid or duplicate recovery request");
        decisions.set(requestId, jsonWriter.objectMapper().valueToTree(decision));
        jsonWriter.write(paths.nodeExecutionJson(executionId), document);
        appendEvent(paths, "recovery-" + executionId + "-" + requestId, "execution_recovery_adjudicated", decision);
        var note = String.valueOf(decision.get("operator_note"));
        if (adopted != null) {
            var facts = new java.util.LinkedHashMap<String, Object>(adopted.result());
            facts.put("recovery_decision", decision);
            facts.put("prior_unknown_observation", node.result());
            var resultValue = new dev.dual.core.workflow.NodeResult(adopted.outcome(), adopted.status(), adopted.outputRefs(), facts, adopted.errorMessage());
            agentTaskLedgerWriter.adoptVerifiedResult(paths, readAgentTask(paths, execution.agentTaskId()).orElseThrow(), resultValue);
            executionLedgerWriter.adoptVerifiedResult(paths, jsonWriter.read(paths.nodeExecutionJson(executionId), NodeExecutionDocument.class), resultValue);
            routingEngine.completeAndRoute(paths, loadDefinition(paths), subject, resultValue.status(), resultValue.outcome(), resultValue.outputRefs(), resultValue.result());
        } else if (disposition.equals("AUTHORIZE_NEW_ATTEMPT")) nodeStateWriter.retryAdjudicatedNode(paths, note, decision);
        else nodeStateWriter.abortRecovery(paths, note, true);
    }

    /** L06: preserve a readiness failure and create a fresh, bounded plan-author occurrence. */
    public String reviseReadinessFailure(WorkstreamPaths paths, String subject, String note,
                                         Map<String, Object> parentCandidate, List<String> evidenceRefs) throws IOException {
        if (session == null) throw new IOException("readiness revision requires an explicit validated write session");
        var current = loadCurrent(paths);
        var source = loadNode(paths, subject);
        var preparation = source.result().get("input_preparation_failure");
        boolean failedPreparation = preparation instanceof Map<?, ?> fact
                && "VERIFICATION_READINESS_REQUIRED".equals(fact.get("code")) && Boolean.FALSE.equals(fact.get("providerInvoked"));
        if (current.status() != WorkflowStatus.WAITING_FOR_RECOVERY
                || !current.activeNodeInstanceIds().equals(List.of(subject)) || source.status() != NodeStatus.FAILED
                || !("VERIFICATION_READINESS_REQUIRED".equals(source.result().get("readiness_reason")) || failedPreparation)
                || note == null || note.isBlank() || "UNKNOWN".equals(source.result().get("external_effect")))
            throw new IOException("subject is not a current, quiescent readiness failure");
        var execution = latestNodeExecution(paths, source);
        if (execution.isPresent() && execution.get().status() == NodeExecutionStatus.RUNNING)
            throw new IOException("running execution must be resolved before plan revision");
        var definition = loadDefinition(paths);
        var target = definition.node("plan.claude_plan");
        var budget = RevisionTracker.maxRevisions(definition.node("plan.decision"));
        var used = revisionTracker.revisionCount(paths, "plan.decision", target.id());
        if (budget >= 0 && used >= budget) throw new IOException("revision_limit_reached; explicit existing budget disposition is required");
        if (execution.isPresent()) executionLedgerWriter.closePermissionReply(paths, execution.get().nodeExecutionId());
        var repair = Map.<String, Object>of("action", "revise_readiness", "note", note,
                "parent_candidate", parentCandidate, "evidence_refs", List.copyOf(evidenceRefs));
        var facts = new java.util.LinkedHashMap<String, Object>(source.result());
        facts.put("readiness_repair", repair);
        jsonWriter.write(paths.nodeJson(subject), new NodeInstanceDocument(source.schemaVersion(), source.nodeInstanceId(),
                source.nodeDefinitionId(), source.kind(), source.handlerType(), source.status(), source.stageId(), source.iterationId(),
                source.inputRefs(), source.inputTransition(), source.outputRefs(), facts, source.runRef(), source.latestExecutionId(),
                source.startedAt(), source.finishedAt()));
        var iteration = revisionTracker.nextRevisionIteration(paths, source, "revise_readiness", target.id(), Map.of("note", note));
        jsonWriter.write(paths.iterationJson(iteration.iterationId()), iteration);
        var eventId = LedgerUtils.nextEventId(current.lastEventId());
        var nextId = LedgerUtils.nodeInstanceId(target.id(), LedgerUtils.currentEventNumber(eventId));
        var transition = Map.<String, Object>of("source_node_instance_id", subject,
                "source_node_sha256", sha256(jsonWriter.readBytes(paths.nodeJson(subject))), "readiness_repair", true);
        var refs = new java.util.LinkedHashSet<>(source.inputRefs());
        refs.addAll(evidenceRefs);
        jsonWriter.write(paths.nodeJson(nextId), new NodeInstanceDocument(1, nextId, target.id(), target.kind(), target.handlerType(),
                NodeStatus.READY, LedgerUtils.stageId(target.id()), iteration.iterationId(), List.copyOf(refs), transition,
                List.of(), Map.of(), null, "", null, null));
        for (var id : current.openGateIds()) {
            var gate = jsonWriter.read(paths.gateJson(id), GateDocument.class);
            jsonWriter.write(paths.gateJson(id), gate.resolved("revise_readiness", note));
        }
        eventLog.appendEvent(paths, eventId, "node_routed", Map.of("from", source.nodeDefinitionId(), "to", target.id(),
                "outcome", "revise_readiness", "source_node_instance_id", subject, "node_instance_id", nextId,
                "transition", transition, "repair", repair));
        var now = java.time.Instant.now();
        var next = new WorkflowCurrentDocument(current.schemaVersion(), current.workstreamId(), current.workflowInstanceId(),
                current.workflowId(), current.definitionVersion(), WorkflowStatus.RUNNING, List.of(nextId), List.of(), eventId, now);
        jsonWriter.write(paths.workflowCurrentJson(), next);
        eventLog.writeInstanceFromCurrent(paths, next, now);
        return nextId;
    }

    private static String sha256(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public Session readSession(WorkstreamPaths paths) throws IOException {
        return openSession(paths, false, false, "read snapshot", java.util.UUID.randomUUID().toString());
    }

    public Session writeSession(WorkstreamPaths paths, String operation, String requestId) throws IOException {
        return openSession(paths, true, false, operation, requestId);
    }

    private Session openSession(WorkstreamPaths paths, boolean write, boolean initialize,
                                String operation, String requestId) throws IOException {
        if (session != null) throw new IllegalStateException("pass the existing session; nested sessions are forbidden");
        var files = new LedgerSession(paths, jsonWriter, runtimeIdentity, write, initialize,
                operation, requestId, faultPoint);
        return new Session(files, new WorkflowFileLedger(new AtomicJsonWriter(files), flowKernel, runtimeIdentity, files));
    }

    public void initialize(WorkstreamPaths paths, dev.dual.core.domain.Workstream workstream,
                           dev.dual.core.domain.Workspace workspace, String requirement,
                           WorkstreamSettingsDocument settings, String workflowInstanceId,
                           WorkflowDefinition definition) throws IOException {
        try (var transaction = openSession(paths, true, true, "initialize", workflowInstanceId)) {
            new FileLedgerStore(transaction.ledger.jsonWriter, runtimeIdentity)
                    .initializeWorkstream(paths, workstream, workspace, requirement, settings);
            transaction.ledger.initialize(paths, workflowInstanceId, definition);
            transaction.commit();
        }
    }

    public final class Session implements AutoCloseable {
        private final LedgerSession files;
        private final WorkflowFileLedger ledger;
        private Session(LedgerSession files, WorkflowFileLedger ledger) { this.files = files; this.ledger = ledger; }
        public WorkflowFileLedger ledger() { return ledger; }
        public LedgerSession files() { return files; }
        public FileLedgerStore store() { return new FileLedgerStore(ledger.jsonWriter, runtimeIdentity); }
        public void commit() throws IOException {
            files.commit();
            if (files.appliedMutation() || ledger.projectionRefreshRequested) refreshCommittedProjections(files.paths());
        }
        @Override public void close() throws IOException { files.close(); }
    }

    private void refreshCommittedProjections(WorkstreamPaths paths) {
        // Derived output failure must not convert a completed authoritative commit into a failed transition.
        var raw = new AtomicJsonWriter();
        var marker = paths.workstreamDir().resolve("projection-status.json");
        try {
            new TimelineProjectionWriter(raw, raw.objectMapper()).write(paths);
            new MetricsProjectionWriter(raw, raw.objectMapper()).write(paths);
            var current = raw.read(paths.workflowCurrentJson(), WorkflowCurrentDocument.class);
            for (var id : current.activeNodeInstanceIds()) {
                var node = raw.read(paths.nodeJson(id), NodeInstanceDocument.class);
                if (node.status() == NodeStatus.FAILED || node.status() == NodeStatus.BLOCKED)
                    new FailureSummaryWriter(raw.objectMapper()).write(paths, id);
            }
            raw.write(marker, Map.of("status", "CURRENT", "sourceEventId", current.lastEventId()));
        } catch (IOException | RuntimeException failure) {
            try { raw.write(marker, Map.of("status", "STALE", "reason", failure.getClass().getSimpleName())); }
            catch (IOException ignored) { /* readers also compare projection source identity to the committed snapshot */ }
        }
    }

    public void initialize(WorkstreamPaths paths, String workflowInstanceId, WorkflowDefinition definition)
            throws IOException {
        if (session == null) throw new IllegalStateException("initialize requires the complete application initialization session");
        if (jsonWriter.exists(paths.workflowCurrentJson()) || jsonWriter.exists(paths.eventsJsonl())) {
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
                2,
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
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().loadCurrent(paths);
                return value;
            }
        }
        return jsonWriter.read(paths.workflowCurrentJson(), WorkflowCurrentDocument.class);
    }

    public WorkflowDefinition loadDefinition(WorkstreamPaths paths) throws IOException {
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().loadDefinition(paths);
                return value;
            }
        }
        var jsonPath = paths.workflowDefinitionJson();
        var json = jsonWriter.readString(jsonPath);
        var doc = objectMapper.readValue(json, WorkflowDefinitionDocument.class);

        if (doc.schemaVersion() != 2) {
            throw new IOException("INVALID_LEDGER_SCHEMA: automatic migration is not supported");
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
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().loadNode(paths, nodeInstanceId);
                return value;
            }
        }
        return jsonWriter.read(paths.nodeJson(nodeInstanceId), NodeInstanceDocument.class);
    }

    public GateDocument loadOpenGateForNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().loadOpenGateForNode(paths, nodeInstanceId);
                return value;
            }
        }
        var current = loadCurrent(paths);
        for (var gateId : current.openGateIds()) {
            var gatePath = paths.gateJson(gateId);
            if (!jsonWriter.exists(gatePath)) {
                continue;
            }
            var gate = jsonWriter.read(gatePath, GateDocument.class);
            if (nodeInstanceId.equals(gate.nodeInstanceId()) && "open".equals(gate.status())) {
                return gate;
            }
        }
        throw new IOException("no matching open gate document for node: " + nodeInstanceId);
    }

    public NodeInstance loadCoreNode(WorkstreamPaths paths, String nodeInstanceId) throws IOException {
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().loadCoreNode(paths, nodeInstanceId);
                return value;
            }
        }
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
        if (session == null) {
            try (var transaction = writeSession(paths, "completeAndRoute", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().completeAndRoute(paths, definition, nodeInstanceId, completedStatus, outcome, outputRefs, result);
                transaction.commit();
                return;
            }
        }
        routingEngine.completeAndRoute(paths, definition, nodeInstanceId, completedStatus, outcome, outputRefs, result);
        refreshProjections(paths);
    }

    public void appendEvent(WorkstreamPaths paths, String eventId, String eventType, Map<String, Object> payload)
            throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "appendEvent", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().appendEvent(paths, eventId, eventType, payload);
                transaction.commit();
                return;
            }
        }
        eventLog.appendEvent(paths, eventId, eventType, payload);
    }

    public WorkflowExecutionDocument startWorkflowExecution(
            WorkstreamPaths paths,
            WorkflowCurrentDocument current,
            String trigger
    ) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "startWorkflowExecution", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().startWorkflowExecution(paths, current, trigger);
                transaction.commit();
                return value;
            }
        }
        return executionLedgerWriter.startWorkflowExecution(paths, current, trigger);
    }

    public WorkflowExecutionDocument finishWorkflowExecution(
            WorkstreamPaths paths,
            WorkflowExecutionDocument workflowExecution,
            List<String> nodeExecutionIds,
            WorkflowExecutionStatus status,
            String stopReason
    ) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "finishWorkflowExecution", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, status, stopReason);
                transaction.commit();
                return value;
            }
        }
        return executionLedgerWriter.finishWorkflowExecution(paths, workflowExecution, nodeExecutionIds, status, stopReason);
    }

    public AgentTaskDocument createAgentTask(
            WorkstreamPaths paths,
            String workflowExecutionId,
            dev.dual.core.workflow.NodeDefinition definition,
            NodeInstanceDocument node
    ) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "createAgentTask", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().createAgentTask(paths, workflowExecutionId, definition, node);
                transaction.commit();
                return value;
            }
        }
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
        if (session == null) {
            try (var transaction = writeSession(paths, "markAgentTaskRunning", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().markAgentTaskRunning(paths, task, nodeExecutionId);
                transaction.commit();
                return value;
            }
        }
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
        if (session == null) {
            try (var transaction = writeSession(paths, "markAgentTaskFinished", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().markAgentTaskFinished(paths, task, result);
                transaction.commit();
                return value;
            }
        }
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
        if (session == null) {
            try (var transaction = writeSession(paths, "markAgentTaskFailed", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().markAgentTaskFailed(paths, task, failure);
                transaction.commit();
                return value;
            }
        }
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
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().readAgentTasks(paths);
                return value;
            }
        }
        return agentTaskLedgerWriter.readAgentTasks(paths);
    }

    public Optional<AgentTaskDocument> readAgentTask(WorkstreamPaths paths, String agentTaskId) throws IOException {
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().readAgentTask(paths, agentTaskId);
                return value;
            }
        }
        return agentTaskLedgerWriter.readAgentTask(paths, agentTaskId);
    }

    public Optional<AgentTaskDocument> firstQueuedAgentTask(WorkstreamPaths paths) throws IOException {
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().firstQueuedAgentTask(paths);
                return value;
            }
        }
        return agentTaskLedgerWriter.firstQueued(paths);
    }

    public NodeExecutionDocument startNodeExecution(
            WorkstreamPaths paths,
            String workflowExecutionId,
            NodeInstanceDocument node,
            AgentTaskDocument agentTask,
            long pid
    ) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "startNodeExecution", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().startNodeExecution(paths, workflowExecutionId, node, agentTask, pid);
                transaction.commit();
                return value;
            }
        }
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
        if (session == null) {
            try (var transaction = writeSession(paths, "finishNodeExecution", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().finishNodeExecution(paths, nodeExecution, result);
                transaction.commit();
                return value;
            }
        }
        var execution = executionLedgerWriter.finishNodeExecution(paths, nodeExecution, result);
        refreshProjections(paths);
        return execution;
    }

    /** Explicit L02 binding. The caller publishes immutable R01 bytes before entering this session. */
    public void bindPreparedRun(WorkstreamPaths paths, NodeExecutionDocument execution, Map<String, String> reference)
            throws IOException {
        if (session == null) throw new IOException("prepared run binding requires an explicit write session");
        executionLedgerWriter.bindPreparedRun(paths, execution, reference);
    }

    public NodeExecutionDocument verifyExecutionClaim(WorkstreamPaths paths, NodeExecutionDocument execution)
            throws IOException {
        if (session == null) throw new IOException("execution qualification requires an explicit session");
        return executionLedgerWriter.verifyClaim(paths, execution);
    }

    /** C and X share the same existing L write session, never a separate permission lock. */
    public boolean commitPermissionReply(WorkstreamPaths paths, NodeExecutionDocument execution, String key,
                                         Map<String, Object> commit) throws IOException {
        if (session == null) throw new IOException("permission reply commit requires an explicit write session");
        var fresh = executionLedgerWriter.commitPermissionReply(paths, execution, key, commit);
        if (fresh) appendEvent(paths, "permission-reply-C-" + execution.nodeExecutionId() + "-" + key,
                "native_permission_reply_committed", Map.of("execution", execution.nodeExecutionId(), "request_key", key, "commit", commit));
        return fresh;
    }

    public void closeLivePermissionReplies(WorkstreamPaths paths, String executionId, String reason) throws IOException {
        if (session == null) throw new IOException("permission closure requires an explicit write session");
        var prior = executionLedgerWriter.readNodeExecutions(paths).stream()
                .filter(e -> e.nodeExecutionId().equals(executionId)).findFirst().orElseThrow(() -> new IOException("execution absent"));
        if (prior.result().get("permission_reply") instanceof Map<?, ?> reply && Boolean.TRUE.equals(reply.get("closed"))) return;
        executionLedgerWriter.closeLivePermissionReplies(paths, executionId);
        appendEvent(paths, "permission-reply-X-" + executionId, "native_permission_reply_closed",
                Map.of("execution", executionId, "reason", reason));
    }

    public Map<String, String> frozenRuntimeIdentity() { return runtimeIdentity; }

    public NodeExecutionDocument failNodeExecution(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            Exception failure
    ) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "failNodeExecution", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().failNodeExecution(paths, nodeExecution, failure);
                transaction.commit();
                return value;
            }
        }
        var execution = executionLedgerWriter.failNodeExecution(paths, nodeExecution, failure);
        refreshProjections(paths);
        return execution;
    }

    public List<NodeExecutionDocument> readNodeExecutions(WorkstreamPaths paths) throws IOException {
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().readNodeExecutions(paths);
                return value;
            }
        }
        return executionLedgerWriter.readNodeExecutions(paths);
    }

    public Optional<NodeExecutionDocument> latestNodeExecution(WorkstreamPaths paths, NodeInstanceDocument node) throws IOException {
        if (session == null) {
            try (var transaction = readSession(paths)) {
                var value = transaction.ledger().latestNodeExecution(paths, node);
                return value;
            }
        }
        return executionLedgerWriter.latestNodeExecution(paths, node);
    }

    public NodeExecutionDocument markNodeExecutionStale(
            WorkstreamPaths paths,
            NodeExecutionDocument nodeExecution,
            String note
    ) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "markNodeExecutionStale", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().markNodeExecutionStale(paths, nodeExecution, note);
                transaction.commit();
                return value;
            }
        }
        var execution = executionLedgerWriter.markNodeExecutionStale(paths, nodeExecution, note);
        refreshProjections(paths);
        return execution;
    }

    public Map<String, Object> requestAction(WorkstreamPaths paths, HumanActionRequest request) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "requestAction", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().requestAction(paths, request);
                transaction.commit();
                return value;
            }
        }
        return routingEngine.requestAction(paths, loadDefinition(paths), request);
    }

    public void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note)
            throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "resolveOpenGate", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().resolveOpenGate(paths, definition, action, note);
                transaction.commit();
                return;
            }
        }
        routingEngine.resolveOpenGate(paths, definition, action, note, null, null, null);
        refreshProjections(paths);
    }

    /** Runtime supplies actual observations; no UI/model decision request can carry this parameter. */
    public void resolveOpenGate(WorkstreamPaths paths, WorkflowDefinition definition, String action, String note,
                                ContractQualificationChecks.Decision checks) throws IOException {
        if (checks == null || checks.familyCoverage() != ContractQualificationChecks.Coverage.COVERED)
            throw new IOException("covered contract decision requires actual check observations");
        if (session == null) {
            try (var transaction = writeSession(paths, "resolveOpenGate", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().resolveOpenGate(paths, definition, action, note, checks);
                transaction.commit();
                return;
            }
        }
        routingEngine.resolveOpenGate(paths, definition, action, note, null, null, checks);
        refreshProjections(paths);
    }

    /** Caller holds the workstream lock and supplies a freshly captured machine baseline. */
    public void collectEvidence(WorkstreamPaths paths, WorkflowDefinition definition, String note,
                                EvidenceCollectionBaseline baseline) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "collectEvidence", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().collectEvidence(paths, definition, note, baseline);
                transaction.commit();
                return;
            }
        }
        routingEngine.resolveOpenGate(paths, definition, "collect_evidence", note, baseline);
        refreshProjections(paths);
    }

    /** Caller holds the lock and has verified the prepared source and copied material identities. */
    public void submitEvidence(WorkstreamPaths paths, WorkflowDefinition definition, String note,
                               EvidenceSubmission submission) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "submitEvidence", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().submitEvidence(paths, definition, note, submission);
                transaction.commit();
                return;
            }
        }
        routingEngine.resolveOpenGate(paths, definition, "submit_evidence", note, submission.baseline(), submission);
        refreshProjections(paths);
    }

    public NodeInstanceDocument markRunning(WorkstreamPaths paths, String nodeInstanceId, long pid) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "markRunning", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().markRunning(paths, nodeInstanceId, pid);
                transaction.commit();
                return value;
            }
        }
        var node = nodeStateWriter.markRunning(paths, nodeInstanceId, pid);
        refreshProjections(paths);
        return node;
    }

    public NodeInstanceDocument markQueuedForAgentTask(WorkstreamPaths paths, String nodeInstanceId, String agentTaskId)
            throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "markQueuedForAgentTask", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().markQueuedForAgentTask(paths, nodeInstanceId, agentTaskId);
                transaction.commit();
                return value;
            }
        }
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
        if (session == null) {
            try (var transaction = writeSession(paths, "autoRetryNode", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().autoRetryNode(paths, nodeInstanceId);
                transaction.commit();
                return value;
            }
        }
        var node = nodeStateWriter.autoRetryNode(paths, nodeInstanceId);
        refreshProjections(paths);
        return node;
    }

    public NodeInstanceDocument autoRetryNode(WorkstreamPaths paths, String nodeInstanceId, long retryDelayMs) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "autoRetryNode", java.util.UUID.randomUUID().toString())) {
                var value = transaction.ledger().autoRetryNode(paths, nodeInstanceId, retryDelayMs);
                transaction.commit();
                return value;
            }
        }
        var node = nodeStateWriter.autoRetryNode(paths, nodeInstanceId, retryDelayMs);
        refreshProjections(paths);
        return node;
    }

    public void retryActiveNode(WorkstreamPaths paths, String note) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "retryActiveNode", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().retryActiveNode(paths, note);
                transaction.commit();
                return;
            }
        }
        nodeStateWriter.retryActiveNode(paths, note);
        refreshProjections(paths);
    }

    public void approvePublicWebSearch(
            WorkstreamPaths paths,
            String note,
            boolean publicRepositoryAttested
    ) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "approvePublicWebSearch", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().approvePublicWebSearch(paths, note, publicRepositoryAttested);
                transaction.commit();
                return;
            }
        }
        nodeStateWriter.approvePublicWebSearch(paths, note, publicRepositoryAttested);
        refreshProjections(paths);
    }

    public void replayDeterministicClassification(WorkstreamPaths paths, String note) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "replayDeterministicClassification", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().replayDeterministicClassification(paths, note);
                transaction.commit();
                return;
            }
        }
        nodeStateWriter.replayDeterministicClassification(paths, note);
        refreshProjections(paths);
    }

    public void recoverStaleRunningNode(WorkstreamPaths paths, String note) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "recoverStaleRunningNode", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().recoverStaleRunningNode(paths, note);
                transaction.commit();
                return;
            }
        }
        nodeStateWriter.recoverStaleRunningNode(paths, note);
        refreshProjections(paths);
    }

    public void abortRecovery(WorkstreamPaths paths, String note) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "abortRecovery", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().abortRecovery(paths, note);
                transaction.commit();
                return;
            }
        }
        nodeStateWriter.abortRecovery(paths, note);
        refreshProjections(paths);
    }

    public void closeMistakenIteration(WorkstreamPaths paths, String note) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "closeMistakenIteration", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().closeMistakenIteration(paths, note);
                transaction.commit();
                return;
            }
        }
        nodeStateWriter.closeMistakenIteration(paths, note);
        refreshProjections(paths);
    }

    public void refreshProjections(WorkstreamPaths paths) throws IOException {
        if (session == null) {
            try (var transaction = writeSession(paths, "refreshProjections", java.util.UUID.randomUUID().toString())) {
                transaction.ledger().refreshProjections(paths);
                transaction.commit();
                return;
            }
        }
        if (session == null) throw new IllegalStateException("projection refresh requires a session");
        // Rebuilt after commit, while the same session still excludes readers/writers.
        projectionRefreshRequested = true;
    }
}
