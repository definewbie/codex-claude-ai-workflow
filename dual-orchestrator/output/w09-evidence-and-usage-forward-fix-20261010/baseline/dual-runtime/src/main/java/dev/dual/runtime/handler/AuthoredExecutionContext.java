package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

/** Runtime-owned provenance of required authored inputs; it never grants an approval. */
public final class AuthoredExecutionContext {
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    public static final String SECTION_NAME = "authored-context";
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private AuthoredExecutionContext() { }

    public static void validateDefinition(dev.dual.core.workflow.WorkflowDefinition definition) {
        AuthoredStageLineageResolver.validateDefinition(definition);
    }

    public static Optional<Snapshot> prepare(TaskHandler.TaskContext context, String runId) throws IOException {
        if (!AuthoredStageLineageResolver.participates(context.definition().id())) return Optional.empty();
        var paths = new dev.dual.ledger.file.WorkstreamPaths(context.workspaceRoot(), new dev.dual.core.id.DualId(context.workstreamId()));
        return context.ledger().inspect(paths, (scoped, files) -> {
            try {
                var actual = scoped.loadNode(paths, context.instance().nodeInstanceId());
                var current = scoped.loadCurrent(paths);
                require(!current.status().isTerminal() && current.activeNodeInstanceIds().contains(actual.nodeInstanceId())
                        && java.util.Set.of(dev.dual.core.workflow.NodeStatus.READY, dev.dual.core.workflow.NodeStatus.RUNNING).contains(actual.status()),
                        "AUTHORED_NODE_NOT_CURRENT");
                var snapshot = new AuthoredStageLineageResolver(scoped, context.workspaceRoot(), context.workstreamId(), Long.MAX_VALUE)
                        .prepare(context.instance().nodeInstanceId(), runId);
                require(snapshot.nodeId().equals(context.definition().id())
                        && snapshot.nodeId().equals(context.instance().nodeDefinitionId()), "AUTHORED_CONTEXT_NODE_MISMATCH");
                verifyDirectInputs(context.workspaceRoot(), snapshot);
                return Optional.of(snapshot);
            } catch (IllegalArgumentException failure) {
                throw new IOException("AUTHORED_CONTEXT_INVALID", failure);
            }
        });
    }

    public static void verifyManifest(Path root, Path manifestPath, Snapshot expected) throws IOException {
        var ref = root.toAbsolutePath().normalize().relativize(manifestPath.toAbsolutePath().normalize()).toString();
        var path = manifestPath(root, expected.workstreamId(), expected.runId(), ref);
        var document = JSON.readValue(path.toFile(), MAP);
        var actual = snapshot(document);
        require(expected.equals(actual), "AUTHORED_MANIFEST_IDENTITY_MISMATCH");
        require(expected.workstreamId().equals(document.get("workstreamId"))
                && expected.runId().equals(document.get("runId")) && expected.nodeId().equals(document.get("nodeId")),
                "AUTHORED_MANIFEST_RUN_MISMATCH");
        var required = expected.render(root);
        var sections = document.get("sections");
        require(sections instanceof List<?>, "AUTHORED_MANIFEST_SECTIONS_MISSING");
        int count = 0;
        for (var value : (List<?>) sections) {
            if (!(value instanceof Map<?, ?> section) || !SECTION_NAME.equals(section.get("name"))) continue;
            count++;
            require(Boolean.TRUE.equals(section.get("required")) && expected.nodeInstanceId().equals(section.get("sourceRef"))
                    && TaskContractArtifactWriter.sha256(required.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(section.get("sha256")),
                    "AUTHORED_REQUIRED_SECTION_MISMATCH");
        }
        require(count == 1, "AUTHORED_REQUIRED_SECTION_MISSING_OR_DUPLICATED");
        var promptPath = Path.of(String.valueOf(document.get("promptPath"))).toAbsolutePath().normalize();
        require(Files.isRegularFile(promptPath) && Files.isSameFile(promptPath.getParent(), path.getParent()), "AUTHORED_PROMPT_PATH_INVALID");
        var prompt = Files.readString(promptPath);
        require(prompt.contains(required), "AUTHORED_REQUIRED_CONTENT_MISSING");
    }

    static Reference validatePublication(dev.dual.ledger.file.WorkflowFileLedger ledger, Path root, String ws, String nodeInstanceId, String runId,
                                         AgentResultEnvelope.AgentResult result) throws IOException {
        var paths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId(ws));
        return ledger.inspect(paths, (scoped, files) -> validatePublicationSnapshot(scoped, root, ws, nodeInstanceId, runId, result));
    }

    private static Reference validatePublicationSnapshot(dev.dual.ledger.file.WorkflowFileLedger ledger, Path root, String ws,
            String nodeInstanceId, String runId, AgentResultEnvelope.AgentResult result) throws IOException {
        var loaded = runSnapshot(root, ws, nodeInstanceId, runId);
        var expected = new AuthoredStageLineageResolver(ledger, root, ws, Long.MAX_VALUE).prepare(nodeInstanceId, runId);
        // New unrelated ledger events do not change the input identity.
        expected = new Snapshot(expected.workstreamId(), expected.nodeInstanceId(), expected.nodeId(), expected.runId(),
                loaded.snapshot().historyThrough(), expected.contract(), expected.reviewedDesign(), expected.reviewedPlan(),
                expected.parent(), expected.subject(), expected.trigger(), expected.obligations());
        verifyManifest(root, verifiedPath(root, loaded.reference()), expected);
        verifyDirectInputs(root, expected);
        List<AgentResultEnvelope.AuthoredRevisionResponse> responses = switch (result) {
            case AgentResultEnvelope.DesignResult design -> design.revisionResponses();
            case AgentResultEnvelope.PlanResult plan -> plan.revisionResponses();
            default -> null;
        };
        if (result instanceof AgentResultEnvelope.DesignResult || result instanceof AgentResultEnvelope.PlanResult) {
            var errors = AuthoredRevisionValidator.validate(responses, expected.obligations().stream().map(Obligation::id).toList());
            require(errors.isEmpty(), String.join("; ", errors));
        }
        return loaded.reference();
    }

    private static Bound runSnapshot(Path root, String ws, String nodeId, String runId) throws IOException {
        var runPath = canonicalPath(root, ".agent/workstreams/" + ws + "/runs/" + runId + "/run.json");
        var run = JSON.readValue(runPath.toFile(), MAP);
        var declaredPath = String.valueOf(run.get("promptManifestPath"));
        var absolute = Path.of(declaredPath);
        var relative = absolute.isAbsolute() ? root.toAbsolutePath().normalize().relativize(absolute.normalize()).toString() : declaredPath;
        var path = manifestPath(root, ws, runId, relative);
        var snapshot = snapshot(JSON.readValue(path.toFile(), MAP));
        require(snapshot.workstreamId().equals(ws) && snapshot.nodeInstanceId().equals(nodeId)
                && snapshot.runId().equals(runId), "AUTHORED_RUN_SNAPSHOT_MISMATCH");
        return new Bound(reference(root, relative), snapshot);
    }

    private static Snapshot snapshot(Map<String, Object> document) throws IOException {
        require(document.get("authoredContext") instanceof Map<?, ?>, "AUTHORED_MANIFEST_CONTEXT_MISSING");
        try { return JSON.convertValue(document.get("authoredContext"), Snapshot.class); }
        catch (IllegalArgumentException failure) { throw new IOException("AUTHORED_MANIFEST_CONTEXT_INVALID", failure); }
    }

    private static Path manifestPath(Path root, String ws, String run, String ref) throws IOException {
        var path = canonicalPath(root, ref);
        var directory = root.toAbsolutePath().normalize().resolve(".agent/workstreams/" + ws + "/runs/" + run + "/prompts");
        require(directory.equals(path.getParent()), "AUTHORED_MANIFEST_OUTSIDE_RUN");
        return path;
    }

    private static void verifyDirectInputs(Path root, Snapshot snapshot) throws IOException {
        var candidates = new java.util.ArrayList<Candidate>();
        if (snapshot.reviewedDesign() != null) candidates.add(snapshot.reviewedDesign().candidate());
        if (snapshot.reviewedPlan() != null) candidates.add(snapshot.reviewedPlan().candidate());
        if (snapshot.parent() != null) candidates.add(snapshot.parent());
        if (snapshot.subject() != null) candidates.add(snapshot.subject());
        for (var candidate : candidates) {
            var bound = verifiedCandidateSnapshot(root, snapshot.workstreamId(), candidate);
            require(sameAuthorization(root, bound.contract(), snapshot.contract()), "AUTHORED_CONTRACT_EPOCH_MISMATCH");
            if (candidate.artifactType().equals("plan_result"))
                require(Objects.equals(bound.reviewedDesign(), snapshot.reviewedDesign()), "AUTHORED_PLAN_DESIGN_MISMATCH");
        }
    }

    /** Design/plan are authored under GOAL; execution must bind that exact goal, not impersonate its approval. */
    private static boolean sameAuthorization(Path root, ContractIdentity authored, ContractIdentity consumer) throws IOException {
        if (Objects.equals(authored, consumer)) return true;
        if (authored == null || consumer == null) return false;
        var goal = TaskContractArtifactWriter.mapper().readValue(verifiedPath(root, authored.contract()).toFile(),
                dev.dual.core.contract.TaskContract.class);
        var execution = TaskContractArtifactWriter.mapper().readValue(verifiedPath(root, consumer.contract()).toFile(),
                dev.dual.core.contract.TaskContract.class);
        return goal.phase() == dev.dual.core.contract.TaskContract.Phase.GOAL
                && execution.phase() == dev.dual.core.contract.TaskContract.Phase.EXECUTION
                && goal.epoch() == execution.epoch()
                && new dev.dual.core.contract.TaskContract.Reference(authored.contract().ref(), authored.contract().sha256())
                    .equals(execution.goalContract())
                && Objects.equals(goal.source(), execution.source());
    }

    static Snapshot verifiedCandidateSnapshot(Path root, String ws, Candidate candidate) throws IOException {
        var doc = JSON.readValue(verifiedPath(root, candidate.sidecar()).toFile(), MAP);
        require(doc.get("authored_context") instanceof Map<?, ?>, "AUTHORED_CANDIDATE_INPUT_BINDING_MISSING");
        final Reference ref;
        try { ref = JSON.convertValue(doc.get("authored_context"), Reference.class); }
        catch (IllegalArgumentException failure) { throw new IOException("AUTHORED_CANDIDATE_INPUT_BINDING_INVALID", failure); }
        // Publication already verified the prompt. Consumers follow its sealed binding,
        // not the historical run locator or a recursive replay of ancestor prompts.
        var path = manifestPath(root, ws, candidate.runId(), ref.ref());
        verifiedPath(root, ref);
        var manifest = JSON.readValue(path.toFile(), MAP);
        var bound = snapshot(manifest);
        require(ws.equals(bound.workstreamId()) && candidate.nodeInstanceId().equals(bound.nodeInstanceId())
                && candidate.runId().equals(bound.runId()) && candidate.nodeId().equals(bound.nodeId())
                && ws.equals(manifest.get("workstreamId")) && candidate.runId().equals(manifest.get("runId"))
                && candidate.nodeId().equals(manifest.get("nodeId")), "AUTHORED_INPUT_BINDING_MISMATCH");
        return bound;
    }

    private static void require(boolean value, String message) throws IOException {
        AuthoredStageLineageResolver.require(value, message);
    }
    private record Bound(Reference reference, Snapshot snapshot) { }

    public record Reference(String ref, String sha256) {
        public Reference {
            Objects.requireNonNull(ref);
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid authored SHA");
        }
    }

    public record ContractIdentity(String compileNodeInstanceId, String approvalGateRef, String approvalEventId,
                                   Reference contract, Reference resolution) { }

    public record Candidate(Reference sidecar, String nodeInstanceId, String nodeId, String runId, String artifactType) { }

    public record ReviewedCandidate(Reference decision, String eventId, Candidate candidate) { }

    public record Obligation(String id, Reference source, String text) { }

    public record Snapshot(String workstreamId, String nodeInstanceId, String nodeId, String runId,
                           long historyThrough, ContractIdentity contract, ReviewedCandidate reviewedDesign,
                           ReviewedCandidate reviewedPlan, Candidate parent, Candidate subject, Reference trigger,
                           List<Obligation> obligations) {
        public Snapshot { obligations = List.copyOf(obligations); }

        Snapshot withHistoryThrough(long through) {
            return new Snapshot(workstreamId, nodeInstanceId, nodeId, runId, through, contract,
                    reviewedDesign, reviewedPlan, parent, subject, trigger, obligations);
        }

        public String render(Path root) throws IOException {
            var text = new StringBuilder("# Required Authored Context\n\n");
            text.append("The following verified inputs are mandatory. Respond to every active obligation; ")
                    .append("ADDRESSED does not close it. Use NOT_ADDRESSED honestly. Initial authors return [].\n\n");
            append(text, root, "Reviewed design", reviewedDesign == null ? null : reviewedDesign.candidate());
            append(text, root, "Reviewed plan", reviewedPlan == null ? null : reviewedPlan.candidate());
            append(text, root, "Revision parent", parent);
            append(text, root, "Review candidate", subject);
            text.append("## Active obligations\n\n")
                    .append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(obligations)).append('\n');
            return text.toString();
        }

        private static void append(StringBuilder text, Path root, String title, Candidate candidate) throws IOException {
            if (candidate == null) return;
            var path = verifiedPath(root, candidate.sidecar());
            text.append("## ").append(title).append("\n\n")
                    .append(candidate.sidecar().ref()).append("\n\n")
                    .append(Files.readString(path)).append("\n\n");
        }
    }

    static Path verifiedPath(Path root, Reference reference) throws IOException {
        var path = canonicalPath(root, reference.ref());
        if (!TaskContractArtifactWriter.sha256(Files.readAllBytes(path)).equals(reference.sha256()))
            throw new IOException("AUTHORED_SOURCE_HASH_MISMATCH: " + reference.ref());
        return path;
    }

    static Path canonicalPath(Path root, String ref) throws IOException {
        var base = root.toAbsolutePath().normalize();
        if (ref == null || ref.isBlank() || ref.indexOf('\\') >= 0 || Path.of(ref).isAbsolute())
            throw new IOException("AUTHORED_REF_INVALID");
        var path = base.resolve(ref).normalize();
        if (!path.startsWith(base) || !base.relativize(path).toString().equals(ref)
                || !Files.isRegularFile(path) || !path.toRealPath().equals(base.toRealPath().resolve(ref)))
            throw new IOException("AUTHORED_REF_INVALID: " + ref);
        return path;
    }

    static Reference reference(Path root, String ref) throws IOException {
        return new Reference(ref, TaskContractArtifactWriter.sha256(Files.readAllBytes(canonicalPath(root, ref))));
    }
}
