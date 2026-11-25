package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Operator-owned readiness, never a tool result, execution approval or test verdict. */
public final class VerificationReadiness {
    public static final String REQUIRED = "VERIFICATION_READINESS_REQUIRED";
    private static final String EVENT = "verification_readiness_recorded";
    private static final String RULE = "verification-readiness-v1";
    // Shared projection of the existing binding contract; never a readiness fact or approval.
    static final String PLAN_BINDING_INSTRUCTION =
            "When binding a current verification readiness declaration, copy its `method` exactly as the decoded JSON string; "
                    + "do not append, paraphrase or normalize whitespace. "
                    + "Put criterion-specific examples, assertions, commands and report/code checks in `plan.validation_plan`, labeled with approved criterion IDs. "
                    + "These details must preserve the declared method, executor, environment, evidence kind, detail_owner and approved acceptance requirements; "
                    + "they are not execution evidence or approval. "
                    + "If no current declaration supports the required method, report the missing readiness without inventing references; "
                    + "a different method needs a new operator declaration and the existing review/approval path. "
                    + "Illustrative method/reference values in the JSON example are placeholders, not registered readiness. ";
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    public enum Action { RECORD, WITHDRAW }
    public enum Lifetime { AUTHORIZATION_EPOCH, EXPIRES_AT }
    public record Dependency(String path, String sha256, boolean absent) {}
    public record Environment(String workspace, String identity, String owner,
                              List<Dependency> dependencies) {
        public Environment { dependencies = List.copyOf(dependencies == null ? List.of() : dependencies); }
    }
    public record Request(int schema, String requestId, String subject,
                          TaskContract.Reference goal, long epoch, List<String> criterionIds,
                          String method, TaskContract.Executor executor, TaskContract.EvidenceKind evidenceKind,
                          Environment environment, String statement, String detailOwner,
                          Lifetime lifetime, Instant expiresAt, Action action,
                          TaskContract.Reference supersedes) {
        public Request { criterionIds = List.copyOf(criterionIds == null ? List.of() : criterionIds); }
    }
    public record Record(Request request, Instant receivedAt, String source, String rule) {}

    private VerificationReadiness() {}

    public static Request readRequest(Path file) throws IOException {
        return JSON.readValue(file.toFile(), Request.class);
    }

    public static TaskContract.Reference record(WorkflowFileLedger ledger, Path root, String workstreamId,
                                                 Request request) throws IOException {
        require(request != null && request.requestId() != null
                && request.requestId().matches("[A-Za-z0-9._-]{1,80}"), "invalid request identity");
        require(nonblank(request.subject()), "subject occurrence is required");
        var paths = new WorkstreamPaths(root.toAbsolutePath().normalize(), new DualId(workstreamId));
        try (var write = ledger.writeSession(paths, "record verification readiness", request.requestId())) {
            var owned = write.ledger();
            var ref = prefix(workstreamId) + request.requestId() + ".json";
            var target = paths.workspaceRoot().resolve(ref);
            if (write.files().exists(target)) {
                var previous = write.files().read(target, Record.class);
                require(previous.request().equals(request), "request id already has different content");
                var identity = new TaskContract.Reference(ref, TaskContractArtifactWriter.sha256(write.files().readBytes(target)));
                registered(owned, paths, identity);
                write.commit();
                return identity;
            }
            var current = owned.loadCurrent(paths);
            require(Set.of(WorkflowStatus.WAITING_FOR_HUMAN, WorkflowStatus.WAITING_FOR_RECOVERY)
                    .contains(current.status()) && current.activeNodeInstanceIds().equals(List.of(request.subject())),
                    "subject must be the current waiting occurrence");
            var node = owned.loadNode(paths, request.subject());
            var definition = owned.loadDefinition(paths).node(node.nodeDefinitionId());
            var gate = node.kind() == NodeKind.GATE && node.status() == NodeStatus.WAITING
                    && Set.of("plan", "contract").contains(definition.config().get("gate_purpose"));
            var failedPreparation = node.status() == NodeStatus.FAILED
                    && (REQUIRED.equals(node.result().get("readiness_reason"))
                    || node.result().get("input_preparation_failure") instanceof Map<?, ?> failure
                    && REQUIRED.equals(failure.get("code")) && Boolean.FALSE.equals(failure.get("providerInvoked")));
            require(gate || failedPreparation, "subject is not a readiness gate or failed preparation");
            var resolution = TaskContractProjectionVerifier.resolve(owned, paths.workspaceRoot(), workstreamId, TaskContract.Phase.GOAL);
            var goal = resolution.qualified().orElseThrow(() -> missing("current approved GOAL required"));
            validateRequest(request, paths.workspaceRoot(), goal, Instant.now());
            var active = activeRecords(owned, paths, goal);
            if (request.supersedes() != null) {
                var prior = active.stream().filter(r -> r.reference().equals(request.supersedes())).findFirst()
                        .orElseThrow(() -> missing("superseded record is not active in this GOAL"));
                require(new HashSet<>(prior.record().request().criterionIds()).equals(new HashSet<>(request.criterionIds())),
                        "replacement must identify the same criteria");
            } else {
                require(request.action() == Action.RECORD, "withdraw requires the original reference");
            }
            if (request.action() == Action.RECORD) {
                require(active.stream().filter(r -> !r.reference().equals(request.supersedes()))
                        .noneMatch(r -> r.record().request().criterionIds().stream().anyMatch(request.criterionIds()::contains)),
                        "conflicting readiness requires an explicit replacement");
            }
            // L04 stores bytes, subject refs and the event in one prepared mutation.
            var bytes = JSON.writeValueAsBytes(new Record(request, Instant.now(), "OPERATOR_ATTESTED", RULE));
            var identity = new TaskContract.Reference(ref, TaskContractArtifactWriter.sha256(bytes));
            owned.recordReadiness(paths, request.subject(), identity, bytes);
            write.commit();
            return identity;
        }
    }

    static void validateBindings(WorkflowFileLedger ledger, Path root, String ws,
                                 TaskContractProjectionVerifier.QualifiedTaskContract goal,
                                 List<TaskContract.VerificationBinding> bindings) throws IOException {
        validateBindings(ledger, root, ws, goal, bindings, null);
    }

    /** Collect operands of these actual checks; declarations remain attestations, not execution proof. */
    static void validateBindings(WorkflowFileLedger ledger, Path root, String ws,
                                 TaskContractProjectionVerifier.QualifiedTaskContract goal,
                                 List<TaskContract.VerificationBinding> bindings,
                                 Map<String, Object> observations) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(ws));
        if (observations != null) {
            observations.put("bindings", bindings);
            observations.put("goal", new TaskContract.Reference(goal.contractRef(), goal.sha256()));
        }
        var active = activeRecords(ledger, paths, goal);
        if (observations != null) observations.put("active_readiness", active.stream().map(Bound::reference)
                .sorted(java.util.Comparator.comparing(TaskContract.Reference::ref)).toList());
        var requests = new java.util.LinkedHashMap<String, Object>();
        var checkTimes = new java.util.LinkedHashMap<String, Object>();
        if (observations != null) { observations.put("requests", requests); observations.put("check_times", checkTimes); }
        var expected = goal.contract().acceptanceCriteria().stream().map(TaskContract.Clause::id).collect(java.util.stream.Collectors.toSet());
        var seen = new HashSet<String>();
        for (var binding : bindings) {
            require(dev.dual.core.contract.TaskContractValidator.verificationEvidenceErrors(binding).isEmpty(),
                    "verification exit evidence requirements must be explicit and consistent");
            require(expected.contains(binding.criterionId()) && seen.add(binding.criterionId()), "unexpected or duplicate criterion");
            require(!binding.environmentRefs().isEmpty() && !binding.feasibilityRefs().isEmpty(), "missing environment or feasibility reference");
            var refs = new HashSet<>(binding.environmentRefs()); refs.addAll(binding.feasibilityRefs());
            for (var ref : refs) {
                var value = active.stream().filter(r -> r.reference().equals(ref)).findFirst()
                        .orElseThrow(() -> missing("unregistered, withdrawn or superseded readiness: " + ref.ref()));
                var request = value.record().request();
                var now = Instant.now();
                var requestInputs = new java.util.LinkedHashMap<String, Object>();
                if (observations != null) {
                    requests.put(ref.ref(), requestInputs); checkTimes.put(ref.ref(), now.toString());
                }
                validateRequest(request, root, goal, now, observations == null ? null : requestInputs);
                require(request.criterionIds().contains(binding.criterionId()) && binding.method().startsWith(request.method())
                        && request.executor() == binding.executor() && request.evidenceKind() == binding.evidenceKind()
                        && request.detailOwner().equals(binding.detailOwner()), "readiness does not support the planned verification");
            }
        }
        require(seen.equals(expected), "every approved criterion needs a verification exit");
        if (observations != null) observations.put("fully_observed", true);
    }

    static boolean sameVerificationBoundary(WorkflowFileLedger ledger, WorkstreamPaths paths,
            List<TaskContract.VerificationBinding> before, List<TaskContract.VerificationBinding> after) throws IOException {
        if (before.size() != after.size()) return false;
        for (var old : before) {
            var matches = after.stream().filter(b -> b.criterionId().equals(old.criterionId())).toList();
            if (matches.size() != 1) return false;
            var next = matches.getFirst();
            if (!old.method().equals(next.method()) || old.executor() != next.executor()
                    || !Objects.equals(old.actualExitRequired(), next.actualExitRequired())
                    || !Objects.equals(old.expectedExitCode(), next.expectedExitCode())
                    || old.evidenceKind() != next.evidenceKind() || !old.detailOwner().equals(next.detailOwner())
                    || !old.authorityRefs().equals(next.authorityRefs())) return false;
            var oldEnvironments = new HashSet<Environment>();
            var nextEnvironments = new HashSet<Environment>();
            for (var ref : old.environmentRefs()) oldEnvironments.add(registered(ledger, paths, ref).request().environment());
            for (var ref : next.environmentRefs()) nextEnvironments.add(registered(ledger, paths, ref).request().environment());
            if (!oldEnvironments.equals(nextEnvironments)) return false;
        }
        return true;
    }

    /** Reference projection for authors/reviewers; never changes readiness or grants authority. */
    static String context(WorkflowFileLedger ledger, Path root, String ws,
                          TaskContractProjectionVerifier.QualifiedTaskContract goal) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(ws));
        return ledger.inspect(paths, (snapshot, files) -> {
            var entries = new ArrayList<Map<String, Object>>();
            for (var bound : activeRecords(snapshot, paths, goal)) {
                String state = "CURRENT_OPERATOR_DECLARATION";
                String detail = "";
                try { validateRequest(bound.record().request(), root, goal, Instant.now()); }
                catch (IOException unavailable) { state = "UNAVAILABLE"; detail = unavailable.getMessage(); }
                entries.add(Map.of("reference", bound.reference(), "declaration", bound.record(),
                        "state", state, "detail", detail));
            }
            return "## Verification readiness declarations\n\n"
                    + "These are operator declarations of prerequisites, not execution results, successful tests or grants. "
                    + "Bind exact refs only where criterion, method, executor and environment match. "
                    + PLAN_BINDING_INSTRUCTION
                    + "If prerequisites are missing, describe the missing items without inventing references.\n\n"
                    + JSON.writerWithDefaultPrettyPrinter().writeValueAsString(entries) + "\n\n";
        });
    }

    private static void validateRequest(Request r, Path root,
                                        TaskContractProjectionVerifier.QualifiedTaskContract goal, Instant now) throws IOException {
        validateRequest(r, root, goal, now, null);
    }

    private static void validateRequest(Request r, Path root,
                                        TaskContractProjectionVerifier.QualifiedTaskContract goal, Instant now,
                                        Map<String, Object> observations) throws IOException {
        if (observations != null) observations.put("declaration", r);
        require(r.schema() == 1 && r.action() != null && r.goal() != null && r.epoch() == goal.contract().epoch()
                && r.goal().equals(new TaskContract.Reference(goal.contractRef(), goal.sha256())), "GOAL identity changed");
        require(!r.criterionIds().isEmpty() && new HashSet<>(r.criterionIds()).size() == r.criterionIds().size()
                && goal.contract().acceptanceCriteria().stream().map(TaskContract.Clause::id).toList().containsAll(r.criterionIds()),
                "readiness must name approved criteria");
        require(r.environment() != null && r.environment().workspace() != null
                && root.toRealPath().toString().equals(r.environment().workspace()), "workspace identity changed");
        require(nonblank(r.method()) && nonblank(r.statement()) && nonblank(r.detailOwner())
                && nonblank(r.environment().identity()) && nonblank(r.environment().owner()), "incomplete declaration");
        require((r.executor() == TaskContract.Executor.AGENT && r.evidenceKind() == TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS)
                || (r.executor() == TaskContract.Executor.OPERATOR && r.evidenceKind() == TaskContract.EvidenceKind.OPERATOR_DECLARATION_WITH_MATERIAL),
                "executor and final evidence kind must agree");
        require(r.lifetime() != null && (r.lifetime() == Lifetime.EXPIRES_AT
                ? r.expiresAt() != null : r.expiresAt() == null), "readiness lifetime unspecified");
        // Withdrawing an expired or changed environment must remain possible.
        if (r.action() == Action.WITHDRAW) return;
        var unexpired = r.lifetime() != Lifetime.EXPIRES_AT || now.isBefore(r.expiresAt());
        if (observations != null) observations.put("unexpired", unexpired);
        require(unexpired, "readiness expired");
        var seen = new HashSet<String>();
        var dependencies = new java.util.LinkedHashMap<String, Object>();
        if (observations != null) observations.put("dependencies", dependencies);
        for (var dependency : r.environment().dependencies()) {
            require(dependency != null && dependency.path() != null && seen.add(dependency.path()), "duplicate environment dependency");
            var path = Path.of(dependency.path());
            require(path.isAbsolute() && path.normalize().equals(path), "environment dependency must be canonical and absolute");
            if (dependency.absent()) {
                var absent = !Files.exists(path, LinkOption.NOFOLLOW_LINKS);
                if (observations != null) dependencies.put(dependency.path(), Map.of("absent", absent));
                require((dependency.sha256() == null || dependency.sha256().isBlank())
                        && absent, "previously absent dependency now exists");
            } else {
                var canonicalFile = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && path.toRealPath().equals(path);
                var actualSha = canonicalFile ? TaskContractArtifactWriter.sha256(Files.readAllBytes(path)) : "";
                if (observations != null) dependencies.put(dependency.path(), Map.of("canonical_file", canonicalFile, "sha256", actualSha));
                require(canonicalFile && actualSha.equals(dependency.sha256()), "environment dependency changed");
            }
        }
    }

    private record Bound(TaskContract.Reference reference, Record record) {}

    private static List<Bound> activeRecords(WorkflowFileLedger ledger, WorkstreamPaths paths,
                                            TaskContractProjectionVerifier.QualifiedTaskContract goal) throws IOException {
        var active = new ArrayList<Bound>();
        for (var line : ledger.files().readLines(paths.eventsJsonl())) {
            var event = JSON.readTree(line);
            if (!EVENT.equals(event.path("event_type").asText())) continue;
            var ref = JSON.treeToValue(event.path("payload").path("reference"), TaskContract.Reference.class);
            var value = registered(ledger, paths, ref);
            var r = value.request();
            if (!Objects.equals(r.goal(), new TaskContract.Reference(goal.contractRef(), goal.sha256()))) continue;
            if (r.supersedes() != null) {
                require(active.removeIf(b -> b.reference().equals(r.supersedes())), "broken readiness replacement chain");
            }
            if (r.action() == Action.RECORD) active.add(new Bound(ref, value));
        }
        return List.copyOf(active);
    }

    private static Record registered(WorkflowFileLedger ledger, WorkstreamPaths paths, TaskContract.Reference ref) throws IOException {
        require(ref.ref().startsWith(prefix(paths.workstreamId().value())), "readiness reference belongs to another workstream");
        var bytes = ledger.files().readBytes(paths.workspaceRoot().resolve(ref.ref()));
        require(TaskContractArtifactWriter.sha256(bytes).equals(ref.sha256()), "readiness bytes changed");
        var record = JSON.readValue(bytes, Record.class);
        require("OPERATOR_ATTESTED".equals(record.source()) && RULE.equals(record.rule()) && record.receivedAt() != null
                && ref.ref().equals(prefix(paths.workstreamId().value()) + record.request().requestId() + ".json"), "invalid readiness producer");
        boolean event = false;
        for (var line : ledger.files().readLines(paths.eventsJsonl())) {
            var e = JSON.readTree(line);
            if (EVENT.equals(e.path("event_type").asText()) && e.path("payload").path("reference").equals(JSON.valueToTree(ref))) {
                require(!event && record.request().subject().equals(e.path("payload").path("subject").asText()), "invalid readiness registration");
                event = true;
            }
        }
        require(event, "readiness was not committed by its owner");
        return record;
    }

    private static String prefix(String ws) { return ".agent/workstreams/" + ws + "/artifacts/verification-readiness/"; }
    private static boolean nonblank(String s) { return s != null && !s.isBlank(); }
    private static IOException missing(String detail) { return new IOException(REQUIRED + ": " + detail); }
    private static void require(boolean condition, String detail) throws IOException { if (!condition) throw missing(detail); }
}
