package dev.dual.runtime.delivery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.DeliveryCandidateWriter;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Resolves the final accepted candidate from authoritative ledger history. */
public final class AcceptedDeliveryCandidateResolver {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final ObjectMapper objectMapper;
    private final WorkflowFileLedger ledger;
    private final DeliveryCandidateWriter candidateWriter;

    public AcceptedDeliveryCandidateResolver() {
        this(new ObjectMapper().findAndRegisterModules(), new WorkflowFileLedger(), new DeliveryCandidateWriter());
    }

    AcceptedDeliveryCandidateResolver(ObjectMapper objectMapper, WorkflowFileLedger ledger, DeliveryCandidateWriter candidateWriter) {
        this.objectMapper = objectMapper;
        this.ledger = ledger;
        this.candidateWriter = candidateWriter;
    }

    public Accepted resolve(WorkstreamPaths paths) throws IOException {
        var occurrence = lastAcceptanceOccurrence(paths);
        var node = ledger.loadNode(paths, occurrence.nodeInstanceId());
        if (!"gate.human_acceptance".equals(node.nodeDefinitionId()) || node.status() != NodeStatus.COMPLETED) {
            throw new IOException("final acceptance node is not completed");
        }
        var gatePath = paths.gateJson(occurrence.gateId());
        if (!Files.isRegularFile(gatePath)) throw new IOException("final acceptance gate document is missing");
        var gate = objectMapper.readValue(gatePath.toFile(), GateDocument.class);
        if (!gate.nodeInstanceId().equals(node.nodeInstanceId()) || !"resolved".equals(gate.status())) {
            throw new IOException("final acceptance gate lineage or status is invalid");
        }
        var action = text(gate.result().get("human_action"));
        if (!"approve".equals(action) && !"approved".equals(action)) {
            throw new IOException("final acceptance occurrence was not approved: " + action);
        }
        var gateRefs = textList(gate.result().get("subject_input_refs"));
        if (!gateRefs.equals(node.inputRefs())) {
            throw new IOException("final acceptance gate subject refs do not match node input refs");
        }
        var candidateRefs = gateRefs.stream().filter(AcceptedDeliveryCandidateResolver::candidateRef).toList();
        if (candidateRefs.isEmpty()) {
            throw new MissingBindingException("final acceptance gate does not bind a delivery candidate");
        }
        if (candidateRefs.size() != 1) {
            throw new IOException("final acceptance gate must bind exactly one delivery candidate");
        }
        var candidateRef = candidateRefs.get(0);
        var candidatePath = resolveInsideWorkspace(paths, candidateRef);
        var occurrenceDir = candidatePath.getParent();
        if (occurrenceDir == null) throw new IOException("delivery candidate occurrence directory is missing");
        var published = candidateWriter.existing(paths, occurrenceDir.getFileName().toString());
        if (published == null || !published.path().toAbsolutePath().normalize().equals(candidatePath)) {
            throw new IOException("final acceptance candidate ref does not identify the published candidate");
        }
        var candidate = published.document();
        if (!candidate.workstreamId().equals(paths.workstreamId().value())
                || !candidate.iterationId().equals(node.iterationId())) {
            throw new IOException("delivery candidate workstream or iteration lineage does not match acceptance");
        }
        var producer = ledger.loadNode(paths, candidate.producerNodeInstanceId());
        if (!"delivery.prepare_pr".equals(producer.nodeDefinitionId())
                || producer.status() != NodeStatus.COMPLETED
                || !producer.iterationId().equals(candidate.iterationId())
                || !producer.outputRefs().contains(candidateRef)) {
            throw new IOException("delivery candidate producer lineage is invalid");
        }
        return new Accepted(candidateRef, published.sha256(), candidate, gate, node, producer);
    }

    private Occurrence lastAcceptanceOccurrence(WorkstreamPaths paths) throws IOException {
        if (!Files.isRegularFile(paths.eventsJsonl())) throw new IOException("workflow event history is missing");
        var occurrences = new ArrayList<Occurrence>();
        var previousEventId = "";
        for (var line : Files.readAllLines(paths.eventsJsonl(), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            Map<String, Object> event;
            try {
                event = objectMapper.readValue(line, MAP_TYPE);
            } catch (Exception failure) {
                throw new IOException("workflow event history contains invalid JSON", failure);
            }
            var eventId = text(event.get("event_id"));
            if (eventId.isBlank() || (!previousEventId.isBlank() && eventId.compareTo(previousEventId) <= 0)) {
                throw new IOException("workflow event history is not strictly ordered");
            }
            previousEventId = eventId;
            if (!"node_routed".equals(text(event.get("event_type")))) continue;
            var payload = map(event.get("payload"));
            if (!"gate.human_acceptance".equals(text(payload.get("to")))) continue;
            var gateId = text(payload.get("gate_id"));
            var nodeInstanceId = text(payload.get("node_instance_id"));
            if (gateId.isBlank() || nodeInstanceId.isBlank()) {
                throw new IOException("acceptance routing event is missing gate lineage");
            }
            occurrences.add(new Occurrence(eventId, gateId, nodeInstanceId));
        }
        if (occurrences.isEmpty()) throw new MissingBindingException("final acceptance occurrence is missing");
        return occurrences.get(occurrences.size() - 1);
    }

    private static boolean candidateRef(String ref) {
        return ref != null && ref.matches(
                "\\.agent/workstreams/[^/]+/delivery/candidates/[^/]+/candidate-[0-9a-f]{64}\\.json"
        );
    }

    private static java.nio.file.Path resolveInsideWorkspace(WorkstreamPaths paths, String ref) throws IOException {
        var root = paths.workspaceRoot().toAbsolutePath().normalize();
        var resolved = root.resolve(ref).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            throw new IOException("delivery candidate ref is missing or outside workspace: " + ref);
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<String> textList(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record Accepted(
            String candidateRef,
            String candidateSha256,
            dev.dual.ledger.file.DeliveryCandidateDocument candidate,
            GateDocument gate,
            NodeInstanceDocument gateNode,
            NodeInstanceDocument producerNode
    ) {
    }

    private record Occurrence(String eventId, String gateId, String nodeInstanceId) {
    }

    public static final class MissingBindingException extends IOException {
        public MissingBindingException(String message) {
            super(message);
        }
    }
}
