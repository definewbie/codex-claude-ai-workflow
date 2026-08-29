package dev.dual.runtime.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.adapter.git.GitPatchMergeService;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.DeliveryCandidateWriter;
import dev.dual.ledger.file.DeliveryMergePlan;
import dev.dual.ledger.file.DeliveryMergePlanWriter;
import dev.dual.ledger.file.DeliveryMergeStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamDocument;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Sole application boundary for an accepted candidate and its target repository side effects. */
public final class DeliveryApplicationService {
    private final ObjectMapper objectMapper;
    private final WorkflowFileLedger ledger;
    private final AcceptedDeliveryCandidateResolver resolver;
    private final GitPatchMergeService git;
    private final DeliveryMergePlanWriter planWriter;

    public DeliveryApplicationService() {
        this(
                new ObjectMapper().findAndRegisterModules(),
                new WorkflowFileLedger(),
                new AcceptedDeliveryCandidateResolver(),
                new GitPatchMergeService(),
                new DeliveryMergePlanWriter()
        );
    }

    DeliveryApplicationService(
            ObjectMapper objectMapper,
            WorkflowFileLedger ledger,
            AcceptedDeliveryCandidateResolver resolver,
            GitPatchMergeService git,
            DeliveryMergePlanWriter planWriter
    ) {
        this.objectMapper = objectMapper;
        this.ledger = ledger;
        this.resolver = resolver;
        this.git = git;
        this.planWriter = planWriter;
    }

    public View view(Path sourceWorktree, String workstreamId, Path requestedTarget) {
        var paths = paths(sourceWorktree, workstreamId);
        Path target = requestedTarget == null ? null : requestedTarget.toAbsolutePath().normalize();
        try {
            target = targetRepo(paths, requestedTarget);
            var existing = readPlan(paths);
            requireCompleted(paths);
            if (existing != null) {
                if (!existing.candidateRef().isBlank()) {
                    requirePlanCandidate(existing, accepted(paths));
                }
                return fromPlan(existing);
            }
            var accepted = accepted(paths);
            return new View(
                    "READY", "", "", text(accepted.gate().result().get("human_action")),
                    accepted.candidateRef(), accepted.candidateSha256(),
                    accepted.candidate().acceptedFiles(), accepted.candidate().excludedFiles(), target.toString(), "",
                    true, false
            );
        } catch (Exception failure) {
            return new View(
                    "BLOCKED", reasonCode(failure), message(failure), "", "", "", List.of(), List.of(),
                    target == null ? "" : target.toString(), "", false, false
            );
        }
    }

    public View apply(Path sourceWorktree, String workstreamId, Path requestedTarget) throws Exception {
        var paths = paths(sourceWorktree, workstreamId);
        try (var ignored = WorkstreamLock.acquire(
                paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "delivery candidate apply")
        )) {
            AcceptedDeliveryCandidateResolver.Accepted accepted = null;
            Path target = null;
            try {
                requireCompleted(paths);
                var existing = readPlan(paths);
                if (existing != null && (existing.status() == DeliveryMergeStatus.APPLIED
                        || existing.status() == DeliveryMergeStatus.COMMITTED)) {
                    throw new IOException("delivery candidate was already applied");
                }
                accepted = accepted(paths);
                target = targetRepo(paths, requestedTarget);
                verifySourceCapture(sourceWorktree.toAbsolutePath().normalize(), accepted);
                verifyTargetBase(target, accepted);
                var candidate = accepted.candidate();
                var ready = DeliveryMergePlan.create(
                        workstreamId,
                        sourceWorktree.toAbsolutePath().normalize().toString(),
                        target.toString(),
                        text(accepted.gate().result().get("human_action")),
                        accepted.candidateRef(),
                        accepted.candidateSha256(),
                        candidate.baseCommit(),
                        candidate.baseTree(),
                        candidate.expectedResultTree(),
                        candidate.acceptedFiles(),
                        candidate.excludedFiles(),
                        DeliveryMergeStatus.READY,
                        "",
                        ""
                );
                writeCompatibilityArtifacts(paths, accepted);
                planWriter.write(paths, ready);
                var patch = Files.readString(resolveRef(paths, candidate.patch().ref()), StandardCharsets.UTF_8);
                var result = git.apply(target, patch);
                Files.writeString(paths.deliveryApplyStdoutLog(), result.stdout(), StandardCharsets.UTF_8);
                Files.writeString(paths.deliveryApplyStderrLog(), result.stderr(), StandardCharsets.UTF_8);
                if (!result.succeeded()) {
                    var failed = ready.withStatus(
                            DeliveryMergeStatus.FAILED, "DELIVERY_APPLY_FAILED", result.stderr().stripTrailing());
                    planWriter.write(paths, failed);
                    return fromPlan(failed);
                }
                var stagedTree = git.stagedTree(target);
                if (!candidate.expectedResultTree().equals(stagedTree)) {
                    var failed = ready.withStatus(DeliveryMergeStatus.FAILED, "TARGET_TREE_MISMATCH",
                            "target staged tree does not match accepted candidate");
                    planWriter.write(paths, failed);
                    return fromPlan(failed);
                }
                var applied = ready.withStatus(DeliveryMergeStatus.APPLIED, "", "");
                planWriter.write(paths, applied);
                return fromPlan(applied, "");
            } catch (Exception failure) {
                var previous = readPlan(paths);
                if (previous == null || (previous.status() != DeliveryMergeStatus.APPLIED
                        && previous.status() != DeliveryMergeStatus.COMMITTED)) {
                    var failed = failedPlan(paths, sourceWorktree, target, accepted, failure);
                    planWriter.write(paths, failed);
                    return fromPlan(failed, reasonCode(failure));
                }
                return fromPlan(previous, reasonCode(failure), message(failure));
            }
        }
    }

    public View finalizeCommit(Path sourceWorktree, String workstreamId, String commitSha) throws Exception {
        var paths = paths(sourceWorktree, workstreamId);
        try (var ignored = WorkstreamLock.acquire(
                paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "delivery commit finalize")
        )) {
            var plan = readPlan(paths);
            if (plan == null || plan.status() != DeliveryMergeStatus.APPLIED) {
                return plan == null
                        ? blocked("DELIVERY_NOT_APPLIED", "delivery candidate has not been applied")
                        : fromPlan(plan, "DELIVERY_NOT_APPLIED", "delivery plan is not APPLIED");
            }
            try {
                var accepted = accepted(paths);
                requirePlanCandidate(plan, accepted);
                var target = Path.of(plan.targetRepo()).toAbsolutePath().normalize();
                var repository = git.repositoryState(target);
                var commit = git.commitState(target, commitSha);
                if (!repository.clean()) throw boundary("TARGET_NOT_ELIGIBLE", "target repository is not clean after commit");
                if (!repository.headCommit().equals(commit.commit())) throw boundary("TARGET_NOT_ELIGIBLE", "target HEAD does not equal finalized commit");
                if (!commit.parent().equals(plan.baseCommit())) throw boundary("TARGET_NOT_ELIGIBLE", "delivery commit parent does not match candidate base");
                if (!commit.tree().equals(plan.expectedResultTree())) throw boundary("TARGET_NOT_ELIGIBLE", "delivery commit tree does not match candidate tree");
                var committed = plan.withCommitted(commit.commit());
                planWriter.write(paths, committed);
                return fromPlan(committed, "");
            } catch (Exception failure) {
                var rejected = plan.withFailure(reasonCode(failure), message(failure));
                planWriter.write(paths, rejected);
                return fromPlan(rejected);
            }
        }
    }

    private void requireCompleted(WorkstreamPaths paths) throws IOException {
        var current = ledger.loadCurrent(paths);
        if (current.status() != WorkflowStatus.COMPLETED) {
            throw boundary("WORKFLOW_NOT_DELIVERABLE", "workstream is not completed: " + current.status());
        }
        if (!Files.isRegularFile(paths.completionAuditJson())) throw boundary("WORKFLOW_NOT_DELIVERABLE", "completion audit is missing");
        var audit = objectMapper.readTree(paths.completionAuditJson().toFile());
        if (!"ready".equalsIgnoreCase(audit.path("status").asText())) {
            throw boundary("WORKFLOW_NOT_DELIVERABLE", "completion audit is not ready: " + audit.path("status").asText());
        }
    }

    private void verifySourceCapture(Path source, AcceptedDeliveryCandidateResolver.Accepted accepted) throws Exception {
        var candidate = accepted.candidate();
        if (!source.toString().equals(candidate.sourceEffectiveWorkdir())) {
            throw boundary("SOURCE_CANDIDATE_DRIFT", "source worktree does not match accepted candidate workspace");
        }
        var current = git.capture(source);
        var patchSha = DeliveryCandidateWriter.sha256(current.patch().getBytes(StandardCharsets.UTF_8));
        if (!current.baseCommit().equals(candidate.baseCommit())
                || !current.baseTree().equals(candidate.baseTree())
                || !current.acceptedFiles().equals(candidate.acceptedFiles())
                || !current.excludedFiles().equals(candidate.excludedFiles())
                || !patchSha.equals(candidate.patch().sha256())
                || !current.expectedResultTree().equals(candidate.expectedResultTree())) {
            throw boundary("SOURCE_CANDIDATE_DRIFT", "source worktree drifted from the accepted delivery candidate");
        }
    }

    private void verifyTargetBase(Path target, AcceptedDeliveryCandidateResolver.Accepted accepted) throws Exception {
        var state = git.repositoryState(target);
        if (!state.clean()) throw boundary("TARGET_NOT_ELIGIBLE", "target repo is not clean");
        if (!state.headCommit().equals(accepted.candidate().baseCommit())
                || !state.headTree().equals(accepted.candidate().baseTree())) {
            throw boundary("TARGET_NOT_ELIGIBLE", "target repository base does not match accepted candidate");
        }
    }

    private Path targetRepo(WorkstreamPaths paths, Path requestedTarget) throws IOException {
        var workstream = objectMapper.readValue(paths.workstreamJson().toFile(), WorkstreamDocument.class);
        var recorded = Path.of(workstream.repository().originalWorkdir()).toAbsolutePath().normalize();
        if (requestedTarget != null && !recorded.equals(requestedTarget.toAbsolutePath().normalize())) {
            throw boundary("TARGET_NOT_ELIGIBLE", "requested target does not match workstream repository identity");
        }
        return recorded;
    }

    private void requirePlanCandidate(DeliveryMergePlan plan, AcceptedDeliveryCandidateResolver.Accepted accepted)
            throws IOException {
        if (!plan.candidateRef().equals(accepted.candidateRef())
                || !plan.candidateSha256().equals(accepted.candidateSha256())
                || !plan.expectedResultTree().equals(accepted.candidate().expectedResultTree())) {
            throw boundary("ACCEPTED_CANDIDATE_INVALID", "delivery plan does not match the accepted candidate");
        }
    }

    private void writeCompatibilityArtifacts(WorkstreamPaths paths, AcceptedDeliveryCandidateResolver.Accepted accepted)
            throws IOException {
        Files.createDirectories(paths.deliveryDir());
        Files.copy(resolveRef(paths, accepted.candidate().patch().ref()), paths.deliveryMergePatch(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(paths.deliveryChangedFilesTxt(), String.join(System.lineSeparator(), accepted.candidate().acceptedFiles())
                + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(paths.deliveryExcludedFilesTxt(), String.join(System.lineSeparator(), accepted.candidate().excludedFiles())
                + (accepted.candidate().excludedFiles().isEmpty() ? "" : System.lineSeparator()), StandardCharsets.UTF_8);
    }

    private DeliveryMergePlan failedPlan(
            WorkstreamPaths paths,
            Path source,
            Path target,
            AcceptedDeliveryCandidateResolver.Accepted accepted,
            Exception failure
    ) {
        var candidate = accepted == null ? null : accepted.candidate();
        return DeliveryMergePlan.create(
                paths.workstreamId().value(), source.toAbsolutePath().normalize().toString(),
                target == null ? "" : target.toString(),
                accepted == null ? "" : text(accepted.gate().result().get("human_action")),
                accepted == null ? "" : accepted.candidateRef(), accepted == null ? "" : accepted.candidateSha256(),
                candidate == null ? "" : candidate.baseCommit(), candidate == null ? "" : candidate.baseTree(),
                candidate == null ? "" : candidate.expectedResultTree(),
                candidate == null ? List.of() : candidate.acceptedFiles(),
                candidate == null ? List.of() : candidate.excludedFiles(),
                DeliveryMergeStatus.FAILED, reasonCode(failure), message(failure)
        );
    }

    private DeliveryMergePlan readPlan(WorkstreamPaths paths) throws IOException {
        return Files.isRegularFile(paths.deliveryMergePlanJson())
                ? objectMapper.readValue(paths.deliveryMergePlanJson().toFile(), DeliveryMergePlan.class)
                : null;
    }

    private static Path resolveRef(WorkstreamPaths paths, String ref) throws IOException {
        var root = paths.workspaceRoot().toAbsolutePath().normalize();
        var path = root.resolve(ref).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IOException("delivery artifact ref is invalid: " + ref);
        return path;
    }

    private static WorkstreamPaths paths(Path source, String workstreamId) {
        return new WorkstreamPaths(source.toAbsolutePath().normalize(), new DualId(workstreamId));
    }

    private static View fromPlan(DeliveryMergePlan plan, String reasonCode) {
        return fromPlan(plan, reasonCode.isBlank() ? plan.failureCode() : reasonCode, plan.failureReason());
    }

    private static View fromPlan(DeliveryMergePlan plan) {
        return fromPlan(plan, plan.failureCode(), plan.failureReason());
    }

    private static View fromPlan(DeliveryMergePlan plan, String reasonCode, String reason) {
        return new View(
                plan.status().name(), reasonCode, reason, plan.acceptanceDecision(), plan.candidateRef(), plan.candidateSha256(),
                plan.changedFiles(), plan.excludedFiles(), plan.targetRepo(), plan.commit(),
                plan.status() == DeliveryMergeStatus.READY,
                plan.status() == DeliveryMergeStatus.APPLIED
        );
    }

    private static View blocked(String code, String reason) {
        return new View("BLOCKED", code, reason, "", "", "", List.of(), List.of(), "", "", false, false);
    }

    private static String reasonCode(Exception failure) {
        if (failure instanceof DeliveryBoundaryException boundary) return boundary.code();
        var message = message(failure);
        if (message.contains("source worktree")) return "SOURCE_CANDIDATE_DRIFT";
        if (message.contains("commit tree") || message.contains("target repository") || message.contains("target staged tree")) {
            return "TARGET_NOT_ELIGIBLE";
        }
        if (message.contains("candidate") || message.contains("acceptance")) return "LEGACY_NOT_BOUND";
        if (message.contains("completed") || message.contains("completion audit")) return "WORKFLOW_NOT_DELIVERABLE";
        if (message.contains("target")) return "TARGET_NOT_ELIGIBLE";
        return "DELIVERY_BOUNDARY_REJECTED";
    }

    private AcceptedDeliveryCandidateResolver.Accepted accepted(WorkstreamPaths paths) throws IOException {
        try {
            return resolver.resolve(paths);
        } catch (AcceptedDeliveryCandidateResolver.MissingBindingException missing) {
            throw boundary("LEGACY_NOT_BOUND", missing.getMessage());
        } catch (IOException invalid) {
            throw boundary("ACCEPTED_CANDIDATE_INVALID", invalid.getMessage());
        }
    }

    private static DeliveryBoundaryException boundary(String code, String message) {
        return new DeliveryBoundaryException(code, message);
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record View(
            String status,
            String reasonCode,
            String reason,
            String acceptanceDecision,
            String candidateRef,
            String candidateSha256,
            List<String> changedFiles,
            List<String> excludedFiles,
            String targetRepo,
            String commit,
            boolean canMerge,
            boolean canFinalize
    ) {
        public View {
            changedFiles = List.copyOf(changedFiles);
            excludedFiles = List.copyOf(excludedFiles);
        }
    }

    private static final class DeliveryBoundaryException extends IOException {
        private final String code;

        private DeliveryBoundaryException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }
}
