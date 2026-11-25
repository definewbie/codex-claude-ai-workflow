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
        resolveGate(ledger, paths, definition, action, note, null);
    }

    static void resolveGate(WorkflowFileLedger ledger, WorkstreamPaths paths, WorkflowDefinition definition,
                            String action, String note, String expectedGate) throws IOException {
        if (!HumanGateActionSemantics.COLLECT_EVIDENCE.equals(action)) {
            if (expectedGate == null) ledger.resolveOpenGate(paths, definition, action, note);
            else ledger.resolveOpenGate(paths, definition, action, note, expectedGate);
            return;
        }
        if (note == null || note.isBlank()) throw new IOException("collection requires an explicit note");
        var baseline = captureBaseline(ledger, paths, definition, action);
        ledger.collectEvidence(paths, definition, note, baseline);
    }

    static EvidenceCollectionBaseline captureBaseline(WorkflowFileLedger ledger, WorkstreamPaths paths,
                                                       WorkflowDefinition definition, String action) throws IOException {
        var current = ledger.loadCurrent(paths);
        if (current.status() != WorkflowStatus.WAITING_FOR_HUMAN || current.activeNodeInstanceIds().size() != 1
                ) throw new IOException("collection requires a waiting human gate");
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        var gate = ledger.loadOpenGateForNode(paths, node.nodeInstanceId());
        if (gate == null || !HumanGateActionSemantics.allowedActions(definition.node(node.nodeDefinitionId()),
                String.valueOf(gate.result().get("source_outcome"))).contains(action))
            throw new IOException("collection is not available at this gate");
        var resolution = TaskContractProjectionVerifier.resolve(ledger, paths.workspaceRoot(), paths.workstreamId().value());
        var qualified = resolution.qualified().orElseThrow(() -> new IOException(
                "collection requires a qualified approved task contract: " + resolution.failure()));
        var included = qualified.contract().scope().included().stream().map(entry -> entry.path()).toList();
        var source = EvidenceCollectionSource.capture(paths.workspaceRoot(), included);
        var baseline = new EvidenceCollectionBaseline(node.nodeInstanceId(), qualified.contractRef(), qualified.sha256(),
                source.head(), source.indexSha256(), source.sha256(), source.paths());
        verifySource(paths.workspaceRoot(), baseline);
        return baseline;
    }

    @FunctionalInterface public interface Execution { NodeResult run() throws Exception; }

    /** Advisory projection only; execute() and publication validation own enforcement. */
    public static Optional<String> promptInstruction(WorkflowFileLedger ledger, Path root, String workstreamId, String nodeId) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var refs = requiredReferences(ledger, root, workstreamId, nodeId);
        var collecting = ledger.inspect(paths, (scoped, files) ->
                scoped.loadNode(paths, nodeId).inputTransition().containsKey("evidence_collection_gate"));
        if (!collecting) return Optional.empty();
        return Optional.of("This occurrence collects evidence against a human-approved source baseline. "
                + "Do not modify product source, tests, dependencies, or Git state. "
                + "Write evidence only to the designated run artifacts. If verification needs a code change, "
                + "report the concrete must_fix or missing_evidence instead of implementing it."
                + (refs.isEmpty() ? "" : "\nExternal verification materials (operator-declared execution, not automatic proof of success):\n"
                + String.join("\n", refs) + "\nRead these exact files. Include each exact ref as a separate string in result.evidence. "
                + "Assess their relevance and sufficiency; missing or failed verification must remain incomplete/rejected. "
                + "Do not replace refs with summaries or treat a submission as approval."));
    }

    static List<String> requiredReferences(WorkflowFileLedger ledger, Path root, String workstreamId, String nodeId) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.exists(paths.workstreamJson()) && !Files.exists(paths.nodeJson(nodeId))) return List.of();
        return ledger.inspect(paths, (scoped, files) -> {
            if (!files.exists(paths.nodeJson(nodeId))) return List.of();
            var guard = openSnapshot(ledger, scoped, root, workstreamId, scoped.loadNode(paths, nodeId));
            return guard == null || guard.submission() == null ? List.of() : ExternalVerificationEvidence.refs(paths, guard.submission());
        });
    }

    static void verifyResult(WorkflowFileLedger ledger, Path root, String workstreamId, String nodeId, AgentResultEnvelope.AgentResult result) throws IOException {
        var refs = requiredReferences(ledger, root, workstreamId, nodeId);
        if (refs.isEmpty()) return;
        List<String> evidence = result instanceof AgentResultEnvelope.SelfTestResult self ? self.evidence()
                : result instanceof AgentResultEnvelope.AcceptanceReviewResult acceptance ? acceptance.evidence() : null;
        if ((result instanceof AgentResultEnvelope.SelfTestResult || result instanceof AgentResultEnvelope.AcceptanceReviewResult)
                && (evidence == null || !evidence.containsAll(refs)))
            throw new IOException("result.evidence must contain every exact bound material ref");
    }

    public static NodeResult execute(WorkflowFileLedger ledger, Path root, String workstreamId, NodeInstanceDocument node, Execution execution)
            throws Exception {
        Guard guard;
        try { guard = open(ledger, root, workstreamId, node); }
        catch (IOException | IllegalArgumentException invalid) { return AgentExecutionResultFacts.inputPreparationFailed(FAILURE, FAILURE, List.of(String.valueOf(invalid.getMessage()))); }
        if (guard == null) return execution.run();
        NodeResult result;
        try { result = execution.run(); }
        catch (Exception failure) {
            try { guard.verify(); }
            catch (IOException | IllegalArgumentException invalid) { failure.addSuppressed(invalid); }
            throw failure;
        }
        try { guard.verify(); }
        catch (IOException | IllegalArgumentException invalid) { return ArtifactContractValidator.evidenceFailureResult(result, invalid); }
        return result;
    }

    /** Recheck at sidecar publication too, including cold or direct validator calls. */
    static void verify(WorkflowFileLedger ledger, Path root, String workstreamId, String nodeId) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.exists(paths.workstreamJson()) && !Files.exists(paths.nodeJson(nodeId))) return;
        ledger.inspect(paths, (scoped, files) -> {
            if (files.exists(paths.nodeJson(nodeId))) openSnapshot(ledger, scoped, root, workstreamId, scoped.loadNode(paths, nodeId));
            return null;
        });
    }

    static Guard open(WorkflowFileLedger ledger, Path root, String workstreamId, NodeInstanceDocument node) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        return ledger.inspect(paths, (scoped, files) -> {
            var actual = scoped.loadNode(paths, node.nodeInstanceId());
            if (!actual.nodeDefinitionId().equals(node.nodeDefinitionId()) || !actual.iterationId().equals(node.iterationId())
                    || !actual.inputTransition().equals(node.inputTransition()) || !actual.inputRefs().equals(node.inputRefs()))
                throw new IOException("collection input identity changed before execution");
            return openSnapshot(ledger, scoped, root, workstreamId, actual);
        });
    }

    private static Guard openSnapshot(WorkflowFileLedger owner, WorkflowFileLedger scoped, Path root,
                                      String workstreamId, NodeInstanceDocument node) throws IOException {
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
                || !("collect_evidence".equals(approval.result().get("human_action"))
                || "submit_evidence".equals(approval.result().get("human_action")))
                || !(approval.result().get("note") instanceof String note) || note.isBlank())
            throw new IOException("collection approval is not a completed human decision");
        var gateFile = paths.gateJson("gate-" + gateId);
        requireCanonical(root, gateFile);
        var gate = MAPPER.readValue(gateFile.toFile(), GateDocument.class);
        if (!"resolved".equals(gate.status()) || !gateId.equals(gate.nodeInstanceId())
                || !Objects.equals(approval.result().get("human_action"), gate.result().get("human_action"))
                || !Objects.equals(gate.result().get("note"), approval.result().get("note"))
                || !Objects.equals(gate.result().get("subject_input_refs"), approval.inputRefs()))
            throw new IOException("collection gate resolution is missing");
        var baseline = MAPPER.convertValue(approval.result().get("evidence_collection_baseline"), EvidenceCollectionBaseline.class);
        if (baseline == null || !gateId.equals(baseline.gateNodeInstanceId()))
            throw new IOException("collection source baseline is missing");
        var submission = MAPPER.convertValue(approval.result().get("external_evidence"), EvidenceSubmission.class);
        if ("submit_evidence".equals(approval.result().get("human_action")) != (submission != null)
                || (submission != null && !baseline.equals(submission.baseline())))
            throw new IOException("missing or inconsistent external material binding");
        var guard = new Guard(owner, root, paths, file, hash, gateFile, TaskContractArtifactWriter.sha256(gateFile),
                node.nodeInstanceId(), baseline, submission);
        guard.verifySnapshot(scoped);
        return guard;
    }

    record Guard(WorkflowFileLedger ledger, Path root, WorkstreamPaths paths, Path approvalFile, String approvalSha, Path gateFile,
                 String gateSha, String nodeId, EvidenceCollectionBaseline baseline, EvidenceSubmission submission) {
        void verify() throws IOException {
            ledger.inspect(paths, (scoped, files) -> { verifySnapshot(scoped); return null; });
        }
        private void verifySnapshot(WorkflowFileLedger scoped) throws IOException {
            requireCanonical(root, approvalFile);
            if (!approvalSha.equals(TaskContractArtifactWriter.sha256(approvalFile)))
                throw new IOException("collection approval changed during execution");
            requireCanonical(root, gateFile);
            if (!gateSha.equals(TaskContractArtifactWriter.sha256(gateFile)))
                throw new IOException("collection gate changed during execution");
            var node = scoped.loadNode(paths, nodeId);
            if (!baseline.gateNodeInstanceId().equals(node.inputTransition().get("evidence_collection_gate"))
                    || !approvalSha.equals(node.inputTransition().get("evidence_collection_gate_sha256")))
                throw new IOException("collection execution lost its approval binding");
            if (!baseline.contractRef().startsWith(".agent/workstreams/" + paths.workstreamId().value() + "/artifacts/contracts/"))
                throw new IOException("collection contract belongs to another workstream");
            verifySource(root, baseline);
            if (submission != null) {
                var manifest = ExternalVerificationEvidence.verify(paths, submission);
                var approval = MAPPER.readValue(approvalFile.toFile(), NodeInstanceDocument.class);
                if (!manifest.note().equals(approval.result().get("note"))) throw new IOException("material note mismatch");
            }
        }
    }

    static void verifySource(Path root, EvidenceCollectionBaseline baseline) throws IOException {
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
