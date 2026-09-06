package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkflowIterationDocument;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.dual.runtime.handler.AuthoredExecutionContext.*;

/** Resolves the two registered authored stages from declared edges and routed occurrences. */
final class AuthoredStageLineageResolver {
    enum Stage {
        DESIGN("design.claude_design", "design.codex_arch_review", "design.decision", "design_result", "design"),
        PLAN("plan.claude_plan", "plan.codex_plan_review", "plan.decision", "plan_result", "plan");

        final String author, reviewer, decision, artifact, purpose;
        Stage(String author, String reviewer, String decision, String artifact, String purpose) {
            this.author = author; this.reviewer = reviewer; this.decision = decision;
            this.artifact = artifact; this.purpose = purpose;
        }
    }

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private final Path root;
    private final String ws;
    private final String prefix;
    private final WorkstreamPaths paths;
    private final WorkflowFileLedger ledger = new WorkflowFileLedger();
    private final WorkflowDefinition definition;
    private final List<Event> events;
    private final long through;

    AuthoredStageLineageResolver(Path root, String ws, long through) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.ws = ws;
        this.prefix = ".agent/workstreams/" + ws + "/";
        this.paths = new WorkstreamPaths(root, new DualId(ws));
        this.definition = ledger.loadDefinition(paths);
        var history = new ArrayList<Event>();
        long previous = -1;
        for (var line : Files.readAllLines(paths.eventsJsonl())) {
            if (line.isBlank()) continue;
            var event = JSON.readValue(line, MAP);
            var id = text(event.get("event_id"));
            if (!id.matches("evt-[0-9]{6,}")) {
                require(!"node_routed".equals(text(event.get("event_type"))), "AUTHORED_EVENT_ID_INVALID");
                continue;
            }
            long sequence;
            try { sequence = Long.parseLong(id.substring(4)); }
            catch (NumberFormatException failure) { throw new IOException("AUTHORED_EVENT_ID_INVALID", failure); }
            require(sequence > previous, "AUTHORED_EVENT_ORDER_INVALID");
            previous = sequence;
            if (sequence <= through) history.add(new Event(sequence, id, text(event.get("event_type")), map(event.get("payload"))));
        }
        this.events = List.copyOf(history);
        this.through = history.isEmpty() ? 0 : history.getLast().sequence();
    }

    static boolean participates(String nodeId) {
        return nodeId.equals(Stage.DESIGN.author) || nodeId.equals(Stage.DESIGN.reviewer)
                || nodeId.equals(Stage.PLAN.author) || nodeId.equals(Stage.PLAN.reviewer)
                || Set.of("implement.claude_implement", "implement.claude_self_test_report").contains(nodeId);
    }

    static void validateDefinition(WorkflowDefinition definition) {
        if (definition.nodes().stream().noneMatch(node -> participates(node.id()))) return;
        for (var stage : Stage.values()) {
            var author = definition.node(stage.author);
            definitionRequire(author.kind() == NodeKind.TASK && author.handlerType().equals("acpx.agent_run"), "author role mismatch");
            var gates = definition.nodes().stream().filter(node -> stage.purpose.equals(text(node.config().get("gate_purpose")))).toList();
            definitionRequire(gates.size() == 1, "one " + stage.purpose + " gate is required");
            var gate = gates.getFirst();
            definitionRequire(gate.kind() == NodeKind.GATE && gate.handlerType().equals("human.review"), "gate role mismatch");
            var incoming = definition.edges().stream().filter(edge -> edge.to().equals(gate.id())).toList();
            definitionRequire(!incoming.isEmpty(), "gate must have a candidate source");
            var next = soleSuccessor(definition, author.id(), "completed");
            boolean direct = stage == Stage.PLAN && next.equals(gate.id());
            if (direct) {
                definitionRequire(incoming.stream().allMatch(edge -> edge.from().equals(stage.author) && edge.on().equals("completed")),
                        "direct plan gate cannot mix reviewed sources");
                definitionRequire(definition.nodes().stream().noneMatch(node -> node.id().equals(stage.reviewer) || node.id().equals(stage.decision)),
                        "direct plan topology cannot retain orphan reviewer roles");
            } else {
                var context = definition.node(next);
                definitionRequire(context.handlerType().equals("context.build_package") && context.kind() == NodeKind.TASK
                        && stage.reviewer.equals(text(context.config().get("target_node"))), "review context role mismatch");
                definitionRequire(soleSuccessor(definition, next, "completed").equals(stage.reviewer), "review context successor mismatch");
                var reviewer = definition.node(stage.reviewer);
                definitionRequire(reviewer.kind() == NodeKind.TASK && reviewer.handlerType().equals("acpx.agent_run"), "reviewer role mismatch");
                definitionRequire(soleSuccessor(definition, stage.reviewer, "completed").equals(stage.decision), "review decision successor mismatch");
                var decision = definition.node(stage.decision);
                definitionRequire(decision.kind() == NodeKind.DECISION && decision.handlerType().equals("review.decision"), "decision role mismatch");
                definitionRequire(incoming.stream().allMatch(edge -> edge.from().equals(stage.decision)), "reviewed gate source mismatch");
                definitionRequire(definition.edges().stream().filter(edge -> edge.to().equals(next))
                        .allMatch(edge -> edge.from().equals(stage.author) && edge.on().equals("completed")), "review context has an unrelated producer");
                definitionRequire(definition.edges().stream().filter(edge -> edge.to().equals(stage.reviewer))
                        .allMatch(edge -> edge.from().equals(next) && edge.on().equals("completed")), "reviewer bypasses its context");
                definitionRequire(definition.edges().stream().filter(edge -> edge.to().equals(stage.decision))
                        .allMatch(edge -> edge.from().equals(stage.reviewer) && edge.on().equals("completed")), "decision bypasses its reviewer");
                var revision = soleSuccessor(definition, stage.decision, "NEEDS_REVISION");
                definitionRequire(revision.equals(gate.id()) || (stage == Stage.PLAN && revision.equals(stage.author)), "unsupported revision topology");
            }
            definitionRequire(soleSuccessor(definition, gate.id(), "revise").equals(stage.author), "human revise must return to its author");
            var approved = soleSuccessor(definition, gate.id(), "approved");
            if (stage == Stage.DESIGN) definitionRequire(approved.equals(Stage.PLAN.author), "approved design must feed the planner");
            else {
                var target = definition.node(approved);
                definitionRequire(approved.equals("implement.claude_implement") || (target.handlerType().equals("context.build_package")
                        && "implement.claude_implement".equals(text(target.config().get("target_node")))
                        && soleSuccessor(definition, approved, "completed").equals("implement.claude_implement")),
                        "approved plan must feed implementation");
            }
        }
    }

    private static String soleSuccessor(WorkflowDefinition definition, String source, String outcome) {
        var edges = definition.edgesFrom(source).stream().filter(edge -> edge.on().equals(outcome)).toList();
        definitionRequire(edges.size() == 1, "exactly one " + outcome + " edge required for " + source);
        return edges.getFirst().to();
    }

    private static void definitionRequire(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException("AUTHORED_DEFINITION_INVALID: " + message);
    }

    Snapshot prepare(String nodeInstanceId, String runId) throws IOException {
        var node = node(nodeInstanceId);
        require(participates(node.nodeDefinitionId()), "AUTHORED_ROLE_UNSUPPORTED");
        var contractResolution = TaskContractProjectionVerifier.resolve(root, ws);
        require(contractResolution.failure() == null && contractResolution.qualified().isPresent(), "AUTHORED_CONTRACT_NOT_QUALIFIED");
        var qualified = contractResolution.qualified().orElseThrow();
        var contract = new ContractIdentity(qualified.compileNodeInstanceId(), qualified.approvalGateRef(), qualified.approvalEventId(),
                new Reference(qualified.contractRef(), qualified.sha256()), new Reference(qualified.resolutionRef(), qualified.resolutionSha256()));
        require(events.stream().anyMatch(event -> event.id().equals(contract.approvalEventId())), "AUTHORED_CONTRACT_OUTSIDE_HISTORY");
        Approval design = null, plan = null;
        Candidate parent = null, subject = null;
        Reference trigger = null;
        List<Obligation> obligations = List.of();
        var id = node.nodeDefinitionId();
        if (!id.equals(Stage.DESIGN.author) && !id.equals(Stage.DESIGN.reviewer)) design = approval(Stage.DESIGN);
        if (id.equals("implement.claude_implement") || id.equals("implement.claude_self_test_report")) plan = approval(Stage.PLAN);
        for (var stage : Stage.values()) {
            if (id.equals(stage.author)) {
                var baseline = baseline(stage, node, new HashSet<>());
                parent = baseline.parent(); trigger = baseline.trigger(); obligations = baseline.obligations();
            } else if (id.equals(stage.reviewer)) {
                subject = reviewSubject(stage, node);
                obligations = baseline(stage, node(subject.nodeInstanceId()), new HashSet<>()).obligations();
            }
        }
        var snapshot = new Snapshot(ws, nodeInstanceId, id, runId, through, contract, design, plan, parent, subject, trigger, obligations);
        var origins = events.stream().filter(event -> event.type().equals("node_routed")
                && nodeInstanceId.equals(text(event.payload().get("node_instance_id")))).toList();
        require(origins.size() <= 1, "AUTHORED_NODE_ORIGIN_NOT_UNIQUE");
        if (!origins.isEmpty() && origins.getFirst().sequence() < through) {
            // Node creation, not execution retry, fixes the semantic input epoch.
            var original = new AuthoredStageLineageResolver(root, ws, origins.getFirst().sequence()).prepare(nodeInstanceId, runId);
            require(snapshot.withHistoryThrough(original.historyThrough()).equals(original), "AUTHORED_NODE_AUTHORITY_CHANGED");
            return original;
        }
        return snapshot;
    }

    Approval approval(Stage stage) throws IOException {
        Event last = null;
        for (var event : events) {
            if (!event.type().equals("node_routed")) continue;
            var to = text(event.payload().get("to"));
            var target = definition.node(to);
            if (target.kind() == NodeKind.GATE && stage.purpose.equals(text(target.config().get("gate_purpose")))) last = event;
        }
        require(last != null, "AUTHORED_APPROVAL_MISSING: " + stage.purpose);
        var gate = gate(last);
        require(gate.node().status() == NodeStatus.COMPLETED && gate.document().status().equals("resolved"), "AUTHORED_APPROVAL_NOT_COMPLETED");
        require(text(gate.document().result().get("human_action")).equals("approved"), "AUTHORED_NOT_APPROVED");
        require(routedAfter(last, gate.node(), "approved"), "AUTHORED_APPROVAL_NOT_ROUTED");
        return new Approval(gate.reference(), last.id(), gateSubject(stage, gate));
    }

    private boolean routedAfter(Event origin, NodeInstanceDocument source, String outcome) throws IOException {
        for (var event : events) {
            if (event.sequence() <= origin.sequence() || !event.type().equals("node_routed")) continue;
            // A later occurrence of the same gate cannot supply this occurrence's completion.
            if (source.nodeDefinitionId().equals(text(event.payload().get("to")))) return false;
            if (!source.nodeDefinitionId().equals(text(event.payload().get("from")))
                    || !outcome.equals(text(event.payload().get("outcome")))) continue;
            var successor = node(text(event.payload().get("node_instance_id")));
            require(successor.nodeDefinitionId().equals(text(event.payload().get("to")))
                    && successor.iterationId().equals(source.iterationId())
                    && successor.inputRefs().equals(source.outputRefs()), "AUTHORED_APPROVAL_SUCCESSOR_MISMATCH");
            edge(source.nodeDefinitionId(), outcome, successor.nodeDefinitionId());
            return true;
        }
        return false;
    }

    private Gate gate(Event event) throws IOException {
        var payload = event.payload();
        var node = node(text(payload.get("node_instance_id")));
        require(node.kind() == NodeKind.GATE && node.nodeDefinitionId().equals(text(payload.get("to"))), "AUTHORED_GATE_NODE_MISMATCH");
        var ref = reference(root, prefix + "gates/" + text(payload.get("gate_id")) + ".json");
        var gate = JSON.readValue(verifiedPath(root, ref).toFile(), GateDocument.class);
        require(gate.gateId().equals(text(payload.get("gate_id")))
                && gate.nodeInstanceId().equals(node.nodeInstanceId()), "AUTHORED_GATE_INSTANCE_MISMATCH");
        require(strings(gate.result().get("subject_input_refs")).equals(node.inputRefs()), "AUTHORED_GATE_SUBJECT_MISMATCH");
        var source = text(payload.get("source_node_instance_id"));
        require(!source.isBlank() && source.equals(text(gate.result().get("source_node_instance_id")))
                && source.equals(text(node.result().get("source_node_instance_id"))), "AUTHORED_GATE_SOURCE_MISMATCH");
        require(text(gate.result().get("human_action")).equals(text(node.result().get("human_action")))
                && text(gate.result().get("note")).equals(text(node.result().get("note"))), "AUTHORED_GATE_ACTION_MISMATCH");
        var producer = node(source);
        completed(producer);
        require(producer.nodeDefinitionId().equals(text(payload.get("from"))), "AUTHORED_GATE_EVENT_SOURCE_MISMATCH");
        require(producer.iterationId().equals(node.iterationId()), "AUTHORED_GATE_ITERATION_MISMATCH");
        edge(producer.nodeDefinitionId(), text(payload.get("outcome")), node.nodeDefinitionId());
        return new Gate(node, gate, ref, producer);
    }

    private Candidate gateSubject(Stage stage, Gate gate) throws IOException {
        if (gate.source().nodeDefinitionId().equals(stage.author)) {
            require(stage == Stage.PLAN, "AUTHORED_DIRECT_DESIGN_GATE_UNSUPPORTED");
            return candidate(stage, gate.node().inputRefs(), gate.source());
        }
        require(gate.source().nodeDefinitionId().equals(stage.decision), "AUTHORED_GATE_SOURCE_ROLE_INVALID");
        var review = unique(gate.node().inputRefs(), stage.reviewer, "agent_output_contract");
        require(gate.source().inputRefs().contains(review.sidecar().ref()), "AUTHORED_DECISION_INPUT_MISMATCH");
        var reviewer = node(review.nodeInstanceId());
        require(reviewer.iterationId().equals(gate.source().iterationId()), "AUTHORED_REVIEW_ITERATION_MISMATCH");
        var doc = document(review);
        require(doc.decision().equals(text(gate.source().result().get("decision"))), "AUTHORED_DECISION_MISMATCH");
        edge(stage.reviewer, "completed", stage.decision);
        var subject = reviewSubject(stage, reviewer);
        var inputs = verifiedCandidateSnapshot(root, ws, review);
        require(subject.equals(inputs.subject()), "AUTHORED_REVIEW_INPUT_MISMATCH");
        return subject;
    }

    GateView gateView(String nodeInstanceId) throws IOException {
        var event = gateEvent(nodeInstanceId);
        var gate = gate(event);
        Stage stage = null;
        var purpose = text(definition.node(gate.node().nodeDefinitionId()).config().get("gate_purpose"));
        for (var value : Stage.values()) if (value.purpose.equals(purpose)) stage = value;
        require(stage != null, "AUTHORED_GATE_ROLE_UNSUPPORTED");
        var candidate = gateSubject(stage, gate);
        var inputs = verifiedCandidateSnapshot(root, ws, candidate);
        var latest = gate.source().nodeDefinitionId().equals(stage.author) ? candidate
                : unique(gate.node().inputRefs(), stage.reviewer, "agent_output_contract");
        var candidateDocument = JSON.readValue(verifiedPath(root, candidate.sidecar()).toFile(), MAP);
        var projection = new LinkedHashMap<String, Object>();
        projection.put("candidate", candidate);
        projection.put("contract", inputs.contract());
        projection.put("approvedDesign", inputs.approvedDesign());
        projection.put("activeObligations", inputs.obligations());
        projection.put("revisionResponses", candidateDocument.get("revision_responses"));
        projection.put("plannedFiles", candidateDocument.get("design"));
        projection.put("plan", candidateDocument.get("plan_artifact"));
        projection.put("newMustFix", latest.equals(candidate) ? List.of() : document(latest).mustFix());
        return new GateView(java.util.Collections.unmodifiableMap(projection), document(latest), latest.sidecar().ref());
    }

    record GateView(Map<String, Object> projection, AgentOutputContractDocument decision, String sidecarRef) { }

    String contextBody(TaskHandler.TaskContext context) throws IOException {
        var target = text(context.definition().config().get("target_node"));
        for (var stage : Stage.values()) {
            if (!target.equals(stage.reviewer)) continue;
            var subject = unique(context.instance().inputRefs(), stage.author, stage.artifact);
            var inputs = verifiedCandidateSnapshot(root, ws, subject);
            var body = new StringBuilder(inputs.render(root));
            body.append("\n## Current review candidate\n\n").append(subject.sidecar().ref()).append("\n\n")
                    .append(Files.readString(verifiedPath(root, subject.sidecar())));
            return body.toString();
        }
        if (target.equals("implement.claude_implement")) {
            var approved = approval(Stage.PLAN);
            var inputs = verifiedCandidateSnapshot(root, ws, approved.candidate());
            return inputs.render(root) + "\n## Approved implementation plan\n\n"
                    + Files.readString(verifiedPath(root, approved.candidate().sidecar()));
        }
        return "";
    }

    private Candidate reviewSubject(Stage stage, NodeInstanceDocument reviewer) throws IOException {
        var matches = new LinkedHashMap<String, NodeInstanceDocument>();
        for (var event : events) {
            if (!event.type().equals("node_routed")) continue;
            var publisher = node(text(event.payload().get("node_instance_id")));
            if (!publisher.handlerType().equals("context.build_package")) continue;
            var config = definition.node(publisher.nodeDefinitionId()).config();
            if (!stage.reviewer.equals(text(config.get("target_node")))) continue;
            if (publisher.outputRefs().stream().noneMatch(reviewer.inputRefs()::contains)) continue;
            completed(publisher);
            require(publisher.iterationId().equals(reviewer.iterationId()), "AUTHORED_CONTEXT_ITERATION_MISMATCH");
            edge(publisher.nodeDefinitionId(), "completed", stage.reviewer);
            edge(stage.author, "completed", publisher.nodeDefinitionId());
            matches.put(publisher.nodeInstanceId(), publisher);
        }
        require(matches.size() == 1, "AUTHORED_CONTEXT_PUBLISHER_NOT_UNIQUE");
        var publisher = matches.values().iterator().next();
        var candidate = unique(publisher.inputRefs(), stage.author, stage.artifact);
        require(node(candidate.nodeInstanceId()).iterationId().equals(publisher.iterationId()), "AUTHORED_CANDIDATE_ITERATION_MISMATCH");
        return candidate;
    }

    private Baseline baseline(Stage stage, NodeInstanceDocument author, Set<String> visiting) throws IOException {
        require(visiting.add(author.nodeInstanceId()), "AUTHORED_REVISION_CYCLE");
        var iterationRef = prefix + "iterations/" + author.iterationId() + ".json";
        var iteration = JSON.readValue(canonicalPath(root, iterationRef).toFile(), WorkflowIterationDocument.class);
        require(iteration.iterationId().equals(author.iterationId()), "AUTHORED_ITERATION_MISMATCH");
        if (!stage.author.equals(iteration.targetNodeDefinitionId()) || iteration.triggerNodeInstanceId().isBlank())
            return new Baseline(null, null, List.of());
        var source = node(iteration.triggerNodeInstanceId());
        Candidate parent;
        Reference trigger;
        var added = new ArrayList<Obligation>();
        if (source.kind() == NodeKind.GATE) {
            require(stage.purpose.equals(text(definition.node(source.nodeDefinitionId()).config().get("gate_purpose"))), "AUTHORED_REVISION_STAGE_MISMATCH");
            require(iteration.triggerAction().equals("revise"), "AUTHORED_REVISION_ACTION_INVALID");
            var event = gateEvent(source.nodeInstanceId());
            var gate = gate(event);
            require(source.status() == NodeStatus.COMPLETED && gate.document().status().equals("resolved")
                    && text(gate.document().result().get("human_action")).equals("revise"), "AUTHORED_REVISION_GATE_UNRESOLVED");
            var note = text(gate.document().result().get("note"));
            require(!note.isBlank(), "AUTHORED_REVISION_NOTE_MISSING");
            edge(source.nodeDefinitionId(), "revise", stage.author);
            parent = gateSubject(stage, gate); trigger = gate.reference();
            if (gate.source().nodeDefinitionId().equals(stage.decision)) {
                addReview(added, unique(gate.node().inputRefs(), stage.reviewer, "agent_output_contract"));
            }
            added.add(new Obligation("sha256:" + trigger.sha256() + "#human-note:0", trigger, note));
        } else {
            require(stage == Stage.PLAN && source.nodeDefinitionId().equals(stage.decision)
                    && iteration.triggerAction().equals("NEEDS_REVISION"), "AUTHORED_REVISION_TRIGGER_INVALID");
            completed(source);
            require(text(source.result().get("decision")).equals("NEEDS_REVISION"), "AUTHORED_REVISION_DECISION_MISMATCH");
            edge(source.nodeDefinitionId(), "NEEDS_REVISION", stage.author);
            var review = unique(source.inputRefs(), stage.reviewer, "agent_output_contract");
            require(document(review).decision().equals("NEEDS_REVISION"), "AUTHORED_REVIEW_DECISION_MISMATCH");
            parent = reviewSubject(stage, node(review.nodeInstanceId())); trigger = review.sidecar();
            require(parent.equals(verifiedCandidateSnapshot(root, ws, review).subject()), "AUTHORED_REVIEW_INPUT_MISMATCH");
            addReview(added, review);
        }
        require(node(parent.nodeInstanceId()).iterationId().equals(iteration.parentIterationId())
                && source.iterationId().equals(iteration.parentIterationId()), "AUTHORED_REVISION_PARENT_MISMATCH");
        var previous = baseline(stage, node(parent.nodeInstanceId()), visiting);
        var all = new LinkedHashMap<String, Obligation>();
        previous.obligations().forEach(value -> all.put(value.id(), value));
        added.forEach(value -> all.putIfAbsent(value.id(), value));
        return new Baseline(parent, trigger, List.copyOf(all.values()));
    }

    private void addReview(List<Obligation> result, Candidate review) throws IOException {
        var issues = document(review).mustFix();
        for (int i = 0; i < issues.size(); i++) result.add(new Obligation(
                "sha256:" + review.sidecar().sha256() + "#must_fix:" + i, review.sidecar(), issues.get(i)));
    }

    private Event gateEvent(String id) throws IOException {
        var matches = events.stream().filter(event -> event.type().equals("node_routed")
                && id.equals(text(event.payload().get("node_instance_id")))).toList();
        require(matches.size() == 1, "AUTHORED_GATE_EVENT_NOT_UNIQUE");
        return matches.getFirst();
    }

    private Candidate candidate(Stage stage, List<String> refs, NodeInstanceDocument producer) throws IOException {
        var candidate = unique(refs, stage.author, stage.artifact);
        require(candidate.nodeInstanceId().equals(producer.nodeInstanceId()), "AUTHORED_CANDIDATE_SOURCE_MISMATCH");
        return candidate;
    }

    private Candidate unique(List<String> refs, String expectedNode, String type) throws IOException {
        var matches = new LinkedHashMap<String, Candidate>();
        for (var ref : refs) {
            if (!ref.endsWith(".contract.json")) continue;
            var path = canonicalPath(root, ref);
            var document = AgentOutputContractDocument.read(path, JSON).orElseThrow(() -> new IOException("AUTHORED_SIDECAR_INVALID"));
            if (!document.nodeId().equals(expectedNode) || !document.sourceArtifactType().equals(type)) continue;
            require(document.hasRequiredTypedPayload(), "AUTHORED_TYPED_PAYLOAD_MISSING");
            var violation = AgentOutputLineageVerifier.violation(root, ws, path, document);
            require(violation.isEmpty(), "AUTHORED_CANDIDATE_LINEAGE_INVALID: " + violation.orElse(""));
            matches.put(ref, new Candidate(reference(root, ref), document.nodeInstanceId(), document.nodeId(), document.runId(), type));
        }
        require(matches.size() == 1, "AUTHORED_CANDIDATE_NOT_UNIQUE: " + expectedNode);
        return matches.values().iterator().next();
    }

    private AgentOutputContractDocument document(Candidate candidate) throws IOException {
        return AgentOutputContractDocument.read(verifiedPath(root, candidate.sidecar()), JSON)
                .orElseThrow(() -> new IOException("AUTHORED_SIDECAR_INVALID"));
    }

    private NodeInstanceDocument node(String id) throws IOException {
        require(!id.isBlank(), "AUTHORED_NODE_ID_MISSING");
        var node = JSON.readValue(canonicalPath(root, prefix + "nodes/" + id + ".json").toFile(), NodeInstanceDocument.class);
        require(node.nodeInstanceId().equals(id), "AUTHORED_NODE_ID_MISMATCH");
        return node;
    }

    private void edge(String from, String outcome, String to) throws IOException {
        require(definition.edgesFrom(from).stream().anyMatch(edge -> edge.on().equals(outcome) && edge.to().equals(to)), "AUTHORED_EDGE_NOT_DECLARED");
    }

    private static void completed(NodeInstanceDocument node) throws IOException {
        require(node.status() == NodeStatus.COMPLETED, "AUTHORED_SOURCE_NOT_COMPLETED");
    }

    static void require(boolean valid, String message) throws IOException { if (!valid) throw new IOException(message); }
    private static String text(Object value) { return value instanceof String text ? text : ""; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
    private static List<String> strings(Object value) { return value instanceof List<?> list ? list.stream().map(AuthoredStageLineageResolver::text).toList() : List.of(); }
    private record Event(long sequence, String id, String type, Map<String, Object> payload) { }
    private record Gate(NodeInstanceDocument node, GateDocument document, Reference reference, NodeInstanceDocument source) { }
    private record Baseline(Candidate parent, Reference trigger, List<Obligation> obligations) { }
}
