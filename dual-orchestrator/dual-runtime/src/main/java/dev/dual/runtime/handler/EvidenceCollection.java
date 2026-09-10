package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Human-approved source identity for evidence collection, shared by inline and queued execution. */
public final class EvidenceCollection {
    public static final String FAILURE = "EVIDENCE_COLLECTION_SOURCE_INVALID";
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private EvidenceCollection() {}

    /** CLI/API already hold the workstream lock; ordinary decisions retain their existing semantics. */
    public static void resolveGate(WorkflowFileLedger ledger, WorkstreamPaths paths, WorkflowDefinition definition,
                                   String action, String note) throws IOException {
        if (!HumanGateActionSemantics.COLLECT_EVIDENCE.equals(action)) {
            ledger.resolveOpenGate(paths, definition, action, note);
            return;
        }
        var current = ledger.loadCurrent(paths);
        if (current.status() != WorkflowStatus.WAITING_FOR_HUMAN || current.activeNodeInstanceIds().size() != 1
                || note == null || note.isBlank()) throw new IOException("collection requires a waiting human gate and note");
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        var gate = ledger.loadOpenGateForNode(paths, node.nodeInstanceId());
        if (gate == null || !HumanGateActionSemantics.allowedActions(definition.node(node.nodeDefinitionId()),
                String.valueOf(gate.result().get("source_outcome"))).contains(HumanGateActionSemantics.COLLECT_EVIDENCE))
            throw new IOException("collection is not available at this gate");
        var resolution = TaskContractProjectionVerifier.resolve(paths.workspaceRoot(), paths.workstreamId().value());
        var qualified = resolution.qualified().orElseThrow(() -> new IOException(
                "collection requires a qualified approved task contract: " + resolution.failure()));
        var included = qualified.contract().scope().included().stream().map(entry -> entry.path()).toList();
        var source = EvidenceCollectionSource.capture(paths.workspaceRoot(), included);
        var baseline = new EvidenceCollectionBaseline(node.nodeInstanceId(), qualified.contractRef(), qualified.sha256(),
                source.head(), source.indexSha256(), source.sha256(), source.paths());
        verifySource(paths.workspaceRoot(), baseline);
        ledger.collectEvidence(paths, definition, note, baseline);
    }

    @FunctionalInterface public interface Execution { NodeResult run() throws Exception; }

    /** Advisory projection only; execute() and publication validation own enforcement. */
    public static Optional<String> promptInstruction(Path root, String workstreamId, String nodeId) throws IOException {
        var file = new WorkstreamPaths(root, new DualId(workstreamId)).nodeJson(nodeId);
        if (!Files.isRegularFile(file)
                || !MAPPER.readTree(file.toFile()).path("inputTransition").has("evidence_collection_gate")) return Optional.empty();
        return Optional.of("This occurrence collects evidence against a human-approved source baseline. "
                + "Do not modify product source, tests, dependencies, or Git state. "
                + "Write evidence only to the designated run artifacts. If verification needs a code change, "
                + "report the concrete must_fix or missing_evidence instead of implementing it.");
    }

    public static NodeResult execute(Path root, String workstreamId, NodeInstanceDocument node, Execution execution)
            throws Exception {
        Guard guard;
        try { guard = open(root, workstreamId, node); }
        catch (IOException | IllegalArgumentException invalid) { return rejected(invalid); }
        if (guard == null) return execution.run();
        NodeResult result;
        try { result = execution.run(); }
        catch (Exception failure) {
            try { guard.verify(); }
            catch (IOException | IllegalArgumentException invalid) { return rejected(invalid); }
            throw failure;
        }
        try { guard.verify(); }
        catch (IOException | IllegalArgumentException invalid) { return rejected(invalid); }
        return result;
    }

    /** Recheck at sidecar publication too, including cold or direct validator calls. */
    static void verify(Path root, String workstreamId, String nodeId) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var file = paths.nodeJson(nodeId);
        if (!Files.isRegularFile(file)) return; // Ordinary standalone validators have no ledger occurrence.
        open(root, workstreamId, MAPPER.readValue(file.toFile(), NodeInstanceDocument.class));
    }

    static Guard open(Path root, String workstreamId, NodeInstanceDocument node) throws IOException {
        var transition = node.inputTransition();
        var id = transition.get("evidence_collection_gate");
        var sha = transition.get("evidence_collection_gate_sha256");
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        // A cold retry must not silently become ordinary self-test if its marker was removed.
        if (Files.isRegularFile(paths.eventsJsonl())) {
            try (var lines = Files.lines(paths.eventsJsonl())) {
                for (var iterator = lines.iterator(); iterator.hasNext();) {
                    var line = iterator.next();
                    if (line.isBlank()) continue;
                    var event = MAPPER.readTree(line);
                    if (!"node_routed".equals(event.path("event_type").asText())
                            || !node.nodeInstanceId().equals(event.path("payload").path("node_instance_id").asText())) continue;
                    var routed = event.path("payload").path("transition");
                    var routedId = routed.path("evidence_collection_gate").isMissingNode() ? null
                            : routed.path("evidence_collection_gate").asText();
                    var routedSha = routed.path("evidence_collection_gate_sha256").isMissingNode() ? null
                            : routed.path("evidence_collection_gate_sha256").asText();
                    if (!Objects.equals(id, routedId) || !Objects.equals(sha, routedSha))
                        throw new IOException("collection transition differs from its persisted route");
                }
            }
        }
        if (id == null && sha == null) return null;
        if (!(id instanceof String gateId) || !gateId.matches("[A-Za-z0-9._-]+")
                || !(sha instanceof String hash) || !hash.matches("[0-9a-f]{64}"))
            throw new IOException("invalid collection approval reference");
        var file = paths.nodeJson(gateId);
        requireCanonical(root, file);
        if (!hash.equals(TaskContractArtifactWriter.sha256(file))) throw new IOException("collection approval changed");
        var approval = MAPPER.readValue(file.toFile(), NodeInstanceDocument.class);
        if (!gateId.equals(approval.nodeInstanceId()) || approval.kind() != NodeKind.GATE
                || approval.status() != NodeStatus.COMPLETED
                || !"collect_evidence".equals(approval.result().get("human_action"))
                || !(approval.result().get("note") instanceof String note) || note.isBlank())
            throw new IOException("collection approval is not a completed human decision");
        var gateFile = paths.gateJson("gate-" + gateId);
        requireCanonical(root, gateFile);
        var gate = MAPPER.readValue(gateFile.toFile(), GateDocument.class);
        if (!"resolved".equals(gate.status()) || !gateId.equals(gate.nodeInstanceId())
                || !"collect_evidence".equals(gate.result().get("human_action"))
                || !Objects.equals(gate.result().get("note"), approval.result().get("note"))
                || !Objects.equals(gate.result().get("subject_input_refs"), approval.inputRefs()))
            throw new IOException("collection gate resolution is missing");
        var baseline = MAPPER.convertValue(approval.result().get("evidence_collection_baseline"), EvidenceCollectionBaseline.class);
        if (baseline == null || !gateId.equals(baseline.gateNodeInstanceId()))
            throw new IOException("collection source baseline is missing");
        var guard = new Guard(root, paths, file, hash, gateFile, TaskContractArtifactWriter.sha256(gateFile),
                node.nodeInstanceId(), baseline);
        guard.verify();
        return guard;
    }

    record Guard(Path root, WorkstreamPaths paths, Path approvalFile, String approvalSha, Path gateFile,
                 String gateSha, String nodeId, EvidenceCollectionBaseline baseline) {
        void verify() throws IOException {
            requireCanonical(root, approvalFile);
            if (!approvalSha.equals(TaskContractArtifactWriter.sha256(approvalFile)))
                throw new IOException("collection approval changed during execution");
            requireCanonical(root, gateFile);
            if (!gateSha.equals(TaskContractArtifactWriter.sha256(gateFile)))
                throw new IOException("collection gate changed during execution");
            var node = MAPPER.readValue(paths.nodeJson(nodeId).toFile(), NodeInstanceDocument.class);
            if (!baseline.gateNodeInstanceId().equals(node.inputTransition().get("evidence_collection_gate"))
                    || !approvalSha.equals(node.inputTransition().get("evidence_collection_gate_sha256")))
                throw new IOException("collection execution lost its approval binding");
            if (!baseline.contractRef().equals(".agent/workstreams/" + paths.workstreamId().value() + "/artifacts/task-contract.json"))
                throw new IOException("collection contract belongs to another workstream");
            verifySource(root, baseline);
        }
    }

    private static void verifySource(Path root, EvidenceCollectionBaseline baseline) throws IOException {
        var contract = root.resolve(baseline.contractRef());
        requireCanonical(root, contract);
        if (!baseline.contractSha256().equals(TaskContractArtifactWriter.sha256(contract)))
            throw new IOException("collection approved contract changed");
        var actual = EvidenceCollectionSource.capture(root, baseline.paths());
        if (!actual.head().equals(baseline.head()) || !actual.indexSha256().equals(baseline.indexSha256())
                || !actual.paths().equals(baseline.paths()) || !actual.sha256().equals(baseline.sourceSha256()))
            throw new IOException("product source differs from the human-approved collection baseline");
    }

    private static void requireCanonical(Path root, Path file) throws IOException {
        if (!file.toRealPath().equals(root.toRealPath().resolve(root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()))))
            throw new IOException("noncanonical collection authority path");
    }

    private static NodeResult rejected(Exception invalid) {
        return new NodeResult(FAILURE, NodeStatus.FAILED, List.of(),
                Map.of("failure_code", FAILURE, "error", String.valueOf(invalid.getMessage())), invalid.getMessage());
    }
}
