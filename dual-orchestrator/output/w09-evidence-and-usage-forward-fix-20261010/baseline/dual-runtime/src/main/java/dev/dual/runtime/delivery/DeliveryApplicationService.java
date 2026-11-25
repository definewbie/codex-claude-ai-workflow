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
import java.util.Map;
import java.util.LinkedHashMap;

/** Sole application boundary for an accepted candidate and its target repository side effects. */
public final class DeliveryApplicationService {
    private final ObjectMapper objectMapper;
    private final WorkflowFileLedger ledger;
    private final AcceptedDeliveryCandidateResolver resolver;
    private final GitPatchMergeService git;

    public DeliveryApplicationService(WorkflowFileLedger ledger) {
        this(
                new ObjectMapper().findAndRegisterModules(),
                ledger,
                new AcceptedDeliveryCandidateResolver(ledger),
                new GitPatchMergeService()
        );
    }

    DeliveryApplicationService(
            ObjectMapper objectMapper,
            WorkflowFileLedger ledger,
            AcceptedDeliveryCandidateResolver resolver,
            GitPatchMergeService git
    ) {
        this.objectMapper = objectMapper;
        this.ledger = ledger;
        this.resolver = resolver;
        this.git = git;
    }

    public View view(Path sourceWorktree, String workstreamId, Path requestedTarget) {
        var paths = paths(sourceWorktree, workstreamId);
        try {
            var target = ledger.inspect(paths, (scoped, files) -> {
                var service = scopedService(scoped);
                service.requireCompleted(paths);
                service.accepted(paths);
                return service.targetRepo(paths, requestedTarget);
            });
            try (var ignored = targetLock(target, workstreamId, "delivery observation")) {
                return ledger.inspect(paths, (scoped, files) -> scopedService(scoped).viewSnapshot(paths, target));
            }
        } catch (dev.dual.ledger.file.WorkstreamLockedException busy) {
            return unavailable("BUSY", "DELIVERY_BUSY", busy.getMessage());
        } catch (dev.dual.ledger.file.LedgerSession.PendingCommitException pending) {
            return unavailable("PENDING", "LEDGER_PENDING", pending.getMessage());
        } catch (DeliveryBoundaryException failure) {
            return blocked(failure.code(), failure.getMessage());
        } catch (IOException invalid) {
            return unavailable("INVALID", "LEDGER_INVALID", message(invalid));
        } catch (Exception failure) {
            return blocked("DELIVERY_IO", message(failure));
        }
    }

    /** Called in the existing L snapshot; never takes the target lock or launches Git here. */
    public static View inSnapshot(View sampled, WorkflowFileLedger scoped, WorkstreamPaths paths) throws IOException {
        if (sampled.candidateRef().isBlank()) return sampled.withoutActions();
        if (sampled.ledgerCommitSequence() != scoped.loadCurrent(paths).commitSequence()) return sampled.stale();
        var planSha = scoped.files().exists(paths.deliveryMergePlanJson())
                ? DeliveryCandidateWriter.sha256(scoped.files().readBytes(paths.deliveryMergePlanJson())) : "";
        if (!sampled.planSha256().equals(planSha)) return sampled.stale();
        try {
            var candidate = new AcceptedDeliveryCandidateResolver(scoped).resolve(paths);
            if (!sampled.candidateRef().equals(candidate.candidateRef())
                    || !sampled.candidateSha256().equals(candidate.candidateSha256())) return sampled.stale();
        } catch (IOException changed) { return sampled.stale(); }
        return sampled;
    }

    private View viewSnapshot(WorkstreamPaths paths, Path target) throws IOException {
        try {
        requireCompleted(paths);
        var accepted = accepted(paths);
        var plan = readPlan(paths);
        if (!target.equals(targetRepo(paths, target))) throw boundary("DELIVERY_INVALID", "target binding changed");
        if (plan == null) verifySourceCapture(paths.workspaceRoot().toRealPath(), accepted);
        else requirePlanCandidate(paths, plan, accepted);
        Map<String, Object> observation;
        try { observation = git.applicationObservation(target); }
        catch (Exception unavailable) {
            if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
            throw boundary("DELIVERY_IO", message(unavailable));
        }
        var actions = new java.util.ArrayList<String>();
        String stop = plan == null ? "UNKNOWN" : stopBasis(paths, plan);
        String reason = "";
        if (plan == null) {
            if (baseMatches(accepted.candidate().baseCommit(), accepted.candidate().baseTree(), observation)) actions.add("MERGE");
            else reason = "Target no longer matches the approved base; preserve it for inspection.";
        } else if ("ACTIVE".equals(stop)) {
            reason = "The old Git invocation is still active; no application or finalization is allowed.";
        } else if (plan.status() == DeliveryMergeStatus.COMMITTED) {
            reason = plan.commit().equals(observation.get("head")) && Boolean.TRUE.equals(observation.get("clean"))
                    ? "The recorded approved commit is current." : "The recorded approved commit is historical; target has since changed.";
            actions.add("INSPECT");
        } else {
            boolean closed = Boolean.TRUE.equals(plan.operation().get("application_closed"));
            if (!"UNKNOWN".equals(stop) && applicationMatches(plan, observation)) {
                actions.add("RECONCILE"); actions.add("COMMIT_EXTERNALLY");
            } else if (!"UNKNOWN".equals(stop) && commitMatches(plan, target, observation)) {
                actions.add("FINALIZE");
            } else if (!closed && baseMatches(plan.baseCommit(), plan.baseTree(), observation)) {
                actions.add("RESOLVE_AUTHORIZE_NEW_APPLY");
            }
            actions.add("INSPECT");
            if (!closed || "UNKNOWN".equals(stop)) actions.add("RESOLVE_CLOSE_OR_CONFIRM_STOP");
            if ("UNKNOWN".equals(stop)) reason = "Old application outcome/stop is unconfirmed; inspect it and record an explicit resolution.";
            else if (closed) reason = "Further application is closed; only an existing exact result may be confirmed.";
            else if (actions.contains("COMMIT_EXTERNALLY")) reason = "Approved changes are staged; review and commit them manually.";
            else if (actions.contains("FINALIZE")) reason = "An exact clean commit exists; confirm it without another application.";
            else reason = "Inspect the target; a new application requires a new explicit resolution and the exact base.";
        }
        String status = plan == null ? (actions.contains("MERGE") ? "READY" : "BLOCKED") : plan.status().name();
        String code = plan == null && !actions.contains("MERGE") ? "TARGET_NOT_ELIGIBLE"
                : "ACTIVE".equals(stop) ? "DELIVERY_ACTIVE" : plan != null && "UNKNOWN".equals(stop) ? "DELIVERY_RESULT_UNKNOWN" : "";
        var template = plan == null || plan.status() == DeliveryMergeStatus.COMMITTED || "ACTIVE".equals(stop)
                ? Map.<String, Object>of() : resolutionTemplate(paths, plan, observation);
        var next = actions.contains("MERGE") ? "Apply the approved candidate."
                : actions.contains("FINALIZE") ? "Confirm the existing exact commit."
                : actions.contains("COMMIT_EXTERNALLY") ? "Review the staged changes, commit manually, then refresh and finalize."
                : reason.isBlank() ? "Inspect the recorded delivery facts." : reason;
        var commands = new java.util.ArrayList<String>();
        var source = shellQuote(paths.workspaceRoot().toString());
        if (actions.contains("MERGE")) commands.add("dualctl delivery merge --workdir " + source + " " + paths.workstreamId());
        if (actions.contains("COMMIT_EXTERNALLY")) {
            commands.add("git -C " + shellQuote(target.toString()) + " diff --cached");
            commands.add("git -C " + shellQuote(target.toString()) + " commit -m '<reviewed message>'");
        }
        if (actions.contains("FINALIZE")) commands.add("dualctl delivery finalize --workdir " + source + " --commit "
                + observation.get("head") + " " + paths.workstreamId());
        if (!template.isEmpty()) commands.add("dualctl delivery resolve --workdir " + source + " --decision <decision.json> " + paths.workstreamId());
        return new View(status, code, reason, "approved", accepted.candidateRef(), accepted.candidateSha256(),
                accepted.candidate().acceptedFiles(), accepted.candidate().excludedFiles(), target.toString(),
                plan == null ? "" : plan.commit(), actions.contains("MERGE"), actions.contains("FINALIZE"),
                plan == null ? "" : plan.status().name(), observation, plan == null ? "" : text(plan.operation().get("id")),
                planHash(paths), ledger.loadCurrent(paths).commitSequence(), stop, actions, next, commands, template);
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("delivery boundary observation failed", failure);
        }
    }

    public View apply(Path sourceWorktree, String workstreamId, Path requestedTarget) throws Exception {
        try { return applyBound(sourceWorktree, workstreamId, requestedTarget); }
        catch (DeliveryBoundaryException refusal) { return blocked(refusal.code(), refusal.getMessage()); }
    }

    private View applyBound(Path sourceWorktree, String workstreamId, Path requestedTarget) throws Exception {
        var paths = paths(sourceWorktree, workstreamId);
        var target = ledger.inspect(paths, (scoped, files) -> scopedService(scoped).targetRepo(paths, requestedTarget));
        try (var ignored = targetLock(target, workstreamId, "delivery apply")) {
            DeliveryMergePlan intent;
            String patch;
            try (var write = ledger.writeSession(paths, "delivery apply intent", java.util.UUID.randomUUID().toString())) {
                var service = scopedService(write.ledger());
                service.requireCompleted(paths);
                var accepted = service.accepted(paths);
                var previous = service.readPlan(paths);
                var before = git.applicationObservation(target);
                if (previous != null) {
                    service.requirePlanCandidate(paths, previous, accepted);
                    if ("ACTIVE".equals(service.stopBasis(paths, previous))) throw boundary("DELIVERY_ACTIVE", "old application is active");
                    if (previous.status() != DeliveryMergeStatus.COMMITTED
                            && !"UNKNOWN".equals(service.stopBasis(paths, previous)) && applicationMatches(previous, before)
                            && previous.status() != DeliveryMergeStatus.APPLIED) {
                        var recovered = service.withObservedTermination(previous).withStatus(DeliveryMergeStatus.APPLIED, "", "");
                        recovered = observed(recovered, before);
                        service.savePlan(paths, recovered, "delivery_application_observed"); write.commit();
                    }
                    // Re-observation is never a fresh external intent, even when the target is still the base.
                    return service.viewSnapshot(paths, target);
                }
                service.verifySourceCapture(paths.workspaceRoot().toRealPath(), accepted);
                requireBase(accepted.candidate().baseCommit(), accepted.candidate().baseTree(), before);
                intent = service.newIntent(paths, target, accepted, before, null, null);
                patch = service.patch(paths, accepted);
                service.writeCompatibilityArtifacts(paths, accepted);
                service.savePlan(paths, intent, "delivery_apply_intent");
                write.commit();
            }
            // This live stack alone has fresh commit completion. Nothing on disk restores start authority.
            return executeApply(paths, target, intent, patch);
        }
    }

    public View resolve(Path sourceWorktree, String workstreamId, byte[] decision) throws Exception {
        try { return resolveBound(sourceWorktree, workstreamId, decision); }
        catch (DeliveryBoundaryException refusal) { return blocked(refusal.code(), refusal.getMessage()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException malformed) { return blocked("DELIVERY_INVALID", "invalid decision JSON"); }
    }

    private View resolveBound(Path sourceWorktree, String workstreamId, byte[] decision) throws Exception {
        var request = readDecision(decision);
        var paths = paths(sourceWorktree, workstreamId);
        if (!workstreamId.equals(requiredText(request, "workstream_id"))) throw boundary("DELIVERY_INVALID", "wrong workstream");
        var id = requiredText(request, "request_id");
        if (!id.matches("[A-Za-z0-9._-]{1,80}")) throw boundary("DELIVERY_INVALID", "invalid request id");
        String requestHash = canonicalSha256(request);
        var target = ledger.inspect(paths, (scoped, files) -> scopedService(scoped).targetRepo(paths, null));
        try (var ignored = targetLock(target, workstreamId, "delivery resolution")) {
            DeliveryMergePlan intent;
            String patch;
            try (var write = ledger.writeSession(paths, "delivery resolve", id)) {
                var service = scopedService(write.ledger());
                var oldReceipt = service.findEvent(paths, "delivery-resolution-" + id);
                if (oldReceipt != null) {
                    var payload = oldReceipt.document().path("payload");
                    if (!"delivery_resolution".equals(oldReceipt.document().path("event_type").asText())
                            || !requestHash.equals(payload.path("request_sha256").asText())
                            || !request.equals(payload.path("request"))) throw boundary("DELIVERY_INVALID", "request id was used for different bytes");
                    // Do not require an obsolete plan SHA, current sequence, or reread mutable original materials here.
                    return service.viewSnapshot(paths, target);
                }
                service.requireCompleted(paths);
                var accepted = service.accepted(paths);
                var previous = service.readPlan(paths);
                if (previous == null) throw boundary("DELIVERY_INVALID", "no old operation to resolve");
                service.requirePlanCandidate(paths, previous, accepted);
                var before = git.applicationObservation(target);
                service.requireRequestBinding(paths, previous, request, before);
                String disposition = requiredText(request, "disposition");
                var stop = service.validateStopRequest(paths, previous, request.path("stop_basis"),
                        "AUTHORIZE_NEW_APPLY".equals(disposition));
                if ("ACTIVE".equals(service.stopBasis(paths, previous))) throw boundary("DELIVERY_ACTIVE", "old application remains active");
                var payload = new LinkedHashMap<String, Object>();
                payload.put("request", request); payload.put("request_sha256", requestHash);
                payload.put("old_operation", previous); payload.put("stop_basis", stop);
                payload.put("runtime_identity", ledger.frozenRuntimeIdentity());
                service.ledger.appendEvent(paths, "delivery-resolution-" + id, "delivery_resolution", payload);
                var receipt = service.findEvent(paths, "delivery-resolution-" + id);
                var ref = receipt.reference();
                if ("CLOSE_APPLICATION".equals(disposition)) {
                    var facts = new LinkedHashMap<>(previous.operation()); facts.put("application_closed", true);
                    if (stop != null) facts.put("operator_stop", ref);
                    service.savePlan(paths, previous.withOperation(facts), "delivery_application_closed");
                    write.commit();
                    return service.viewSnapshot(paths, target);
                }
                if (!"AUTHORIZE_NEW_APPLY".equals(disposition)) throw boundary("DELIVERY_INVALID", "unsupported resolution disposition");
                if (Boolean.TRUE.equals(previous.operation().get("application_closed")) || previous.status() == DeliveryMergeStatus.COMMITTED)
                    throw boundary("DELIVERY_CLOSED", "this candidate cannot be applied again");
                requireBase(previous.baseCommit(), previous.baseTree(), before);
                service.verifySourceCapture(paths.workspaceRoot().toRealPath(), accepted);
                intent = service.newIntent(paths, target, accepted, before, ref, Map.of("request_id", id, "request_sha256", requestHash));
                patch = service.patch(paths, accepted);
                service.writeCompatibilityArtifacts(paths, accepted);
                service.savePlan(paths, intent, "delivery_apply_intent");
                write.commit();
            }
            return executeApply(paths, target, intent, patch);
        }
    }

    private View executeApply(WorkstreamPaths paths, Path target, DeliveryMergePlan intent, String patch) throws Exception {
        var result = git.apply(target, patch, map(intent.operation().get("before")), new GitPatchMergeService.ApplyLifecycle() {
            @Override public void started(Process process) throws IOException {
                recordProcess(paths, intent, process, null);
            }
            @Override public void exited(Process process, int exit) throws IOException {
                recordProcess(paths, intent, process, exit);
            }
        });
        var after = git.applicationObservation(target);
        try (var write = ledger.writeSession(paths, "delivery application observation", text(intent.operation().get("id")))) {
            var service = scopedService(write.ledger());
            var actual = service.requireSameOperation(paths, intent);
            service.requirePlanCandidate(paths, actual, service.accepted(paths));
            if ("UNKNOWN".equals(service.stopBasis(paths, actual))) throw boundary("DELIVERY_RESULT_UNKNOWN", "Git end receipt is missing");
            var facts = new LinkedHashMap<>(actual.operation()); facts.put("observation", after);
            facts.put("output", Map.of("stdout", result.stdout(), "stderr", result.stderr(), "failure", result.outputFailure()));
            var outcome = actual.withOperation(facts).withStatus(applicationMatches(actual, after) ? DeliveryMergeStatus.APPLIED : DeliveryMergeStatus.UNKNOWN,
                    applicationMatches(actual, after) ? "" : "DELIVERY_RESULT_UNKNOWN", applicationMatches(actual, after) ? "" : "Preserve target differences for adjudication.");
            service.savePlan(paths, outcome, "delivery_application_observed"); write.commit();
            return service.viewSnapshot(paths, target);
        }
    }

    private void recordProcess(WorkstreamPaths paths, DeliveryMergePlan intent, Process process, Integer exit) throws IOException {
        try (var write = ledger.writeSession(paths, exit == null ? "delivery process started" : "delivery process exited", text(intent.operation().get("id")))) {
            var service = scopedService(write.ledger());
            var actual = service.requireSameOperation(paths, intent);
            service.requirePlanCandidate(paths, actual, service.accepted(paths));
            if (Boolean.TRUE.equals(actual.operation().get("application_closed"))) throw boundary("DELIVERY_CLOSED", "application closed before patch input");
            var identity = exit == null ? Map.<String, Object>of("pid", process.pid(), "started_at",
                    process.info().startInstant().orElseThrow(() -> new IOException("Git process start identity missing")).toString(), "command_kind", "GIT_APPLY_INDEX")
                    : map(actual.operation().get("process"));
            var facts = new LinkedHashMap<>(actual.operation());
            if (exit == null) {
                if (actual.operation().get("process") != null) throw boundary("DELIVERY_INVALID", "process already registered");
                facts.put("process", identity);
            } else {
                if (!(identity.get("pid") instanceof Number pid) || pid.longValue() != process.pid()) throw boundary("DELIVERY_INVALID", "different Git process");
                facts.put("process_exit", Map.of("pid", process.pid(), "started_at", identity.get("started_at"),
                        "termination_observed", true, "observation_source", "PROCESS_WAIT_FOR", "actual_exit_code", exit,
                        "observed_at", java.time.Instant.now().toString()));
            }
            service.savePlan(paths, actual.withOperation(facts), exit == null ? "delivery_process_started" : "delivery_process_exited");
            write.commit();
        }
    }

    public View finalizeCommit(Path sourceWorktree, String workstreamId, String commitSha) throws Exception {
        try { return finalizeBound(sourceWorktree, workstreamId, commitSha); }
        catch (DeliveryBoundaryException refusal) { return blocked(refusal.code(), refusal.getMessage()); }
    }

    private View finalizeBound(Path sourceWorktree, String workstreamId, String commitSha) throws Exception {
        var paths = paths(sourceWorktree, workstreamId);
        var target = ledger.inspect(paths, (scoped, files) -> scopedService(scoped).targetRepo(paths, null));
        try (var ignored = targetLock(target, workstreamId, "delivery finalize")) {
            try (var write = ledger.writeSession(paths, "delivery finalize", commitSha)) {
                var service = scopedService(write.ledger()); service.requireCompleted(paths);
                var actual = service.readPlan(paths);
                if (actual == null) throw boundary("DELIVERY_NOT_APPLIED", "delivery candidate has not been applied");
                service.requirePlanCandidate(paths, actual, service.accepted(paths));
                String stop = service.stopBasis(paths, actual);
                if ("ACTIVE".equals(stop) || "UNKNOWN".equals(stop)) throw boundary("DELIVERY_RESULT_UNKNOWN", "confirm the old application stopped before finalizing");
                var repository = git.repositoryState(target); var commit = git.commitState(target, commitSha);
                if (!repository.clean() || !repository.headCommit().equals(commit.commit())
                        || !commit.parent().equals(actual.baseCommit()) || !commit.tree().equals(actual.expectedResultTree()))
                    throw boundary("TARGET_NOT_ELIGIBLE", "actual clean HEAD, parent and tree must match the accepted candidate");
                if (actual.status() == DeliveryMergeStatus.COMMITTED) {
                    if (!actual.commit().equals(commit.commit())) throw boundary("DELIVERY_INVALID", "different commit was already finalized");
                    return service.viewSnapshot(paths, target);
                }
                service.savePlan(paths, service.withObservedTermination(actual).withCommitted(commit.commit()), "delivery_committed");
                write.commit(); return service.viewSnapshot(paths, target);
            }
        }
    }

    private DeliveryApplicationService scopedService(WorkflowFileLedger scoped) {
        return new DeliveryApplicationService(objectMapper, scoped, new AcceptedDeliveryCandidateResolver(scoped), git);
    }

    private WorkstreamLock targetLock(Path target, String workstream, String label) throws Exception {
        return WorkstreamLock.acquire(git.commonDirectory(target).resolve("dual-delivery"), LockOwner.currentProcess(workstream, label));
    }

    private String patch(WorkstreamPaths paths, AcceptedDeliveryCandidateResolver.Accepted accepted) throws IOException {
        var bytes = ledger.files().readBytes(resolveRef(paths, accepted.candidate().patch().ref()));
        if (!DeliveryCandidateWriter.sha256(bytes).equals(accepted.candidate().patch().sha256())) throw boundary("ACCEPTED_CANDIDATE_INVALID", "patch bytes drifted");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private DeliveryMergePlan newIntent(WorkstreamPaths paths, Path target, AcceptedDeliveryCandidateResolver.Accepted accepted,
            Map<String, Object> before, Map<String, Object> predecessor, Map<String, Object> authorization) throws Exception {
        var candidate = accepted.candidate();
        var facts = new LinkedHashMap<String, Object>();
        facts.put("schema", "dual.delivery-application.v1"); facts.put("id", java.util.UUID.randomUUID().toString());
        facts.put("apply_intent", true); facts.put("before", before);
        facts.put("executor_pid", ProcessHandle.current().pid()); facts.put("executor_started_at", ProcessHandle.current().info().startInstant().orElseThrow().toString());
        facts.put("approval_node", accepted.gateNode().nodeInstanceId()); facts.put("approval_sha256", gateSha(paths, accepted));
        facts.put("binding", expectedBinding(paths, target, accepted));
        facts.put("process", null); facts.put("process_exit", null); facts.put("operator_stop", null);
        facts.put("output", Map.of()); facts.put("observation", before); facts.put("application_closed", false);
        facts.put("predecessor", predecessor); facts.put("authorizing_request", authorization);
        return DeliveryMergePlan.create(paths.workstreamId().value(), paths.workspaceRoot().toRealPath().toString(), target.toString(), "approved",
                accepted.candidateRef(), accepted.candidateSha256(), candidate.baseCommit(), candidate.baseTree(), candidate.expectedResultTree(),
                candidate.acceptedFiles(), candidate.excludedFiles(), DeliveryMergeStatus.UNKNOWN, "DELIVERY_RESULT_UNKNOWN", "Durable intent; outcome not recorded.").withOperation(facts);
    }

    private Map<String, Object> expectedBinding(WorkstreamPaths paths, Path target, AcceptedDeliveryCandidateResolver.Accepted accepted) throws Exception {
        var c = accepted.candidate(); var b = new LinkedHashMap<String, Object>();
        b.put("workstream", paths.workstreamId().value()); b.put("source", paths.workspaceRoot().toRealPath().toString());
        b.put("target", target.toRealPath().toString()); b.put("common_dir", git.commonDirectory(target).toString());
        b.put("candidate_ref", accepted.candidateRef()); b.put("candidate_sha256", accepted.candidateSha256());
        b.put("patch_ref", c.patch().ref()); b.put("patch_sha256", c.patch().sha256());
        b.put("approval_node", accepted.gateNode().nodeInstanceId()); b.put("approval_ref", relative(paths, paths.gateJson(accepted.gate().gateId())));
        b.put("approval_sha256", gateSha(paths, accepted)); b.put("approval_node_sha256", DeliveryCandidateWriter.sha256(ledger.files().readBytes(paths.nodeJson(accepted.gateNode().nodeInstanceId()))));
        b.put("base_commit", c.baseCommit()); b.put("base_tree", c.baseTree()); b.put("expected_tree", c.expectedResultTree());
        b.put("accepted_files", c.acceptedFiles()); b.put("excluded_files", c.excludedFiles());
        b.put("runtime_identity", ledger.frozenRuntimeIdentity()); return b;
    }

    private void savePlan(WorkstreamPaths paths, DeliveryMergePlan plan, String event) throws IOException {
        new DeliveryMergePlanWriter(ledger.files()).write(paths, plan);
        ledger.appendEvent(paths, "delivery-" + plan.operation().get("id") + "-" + event + "-" + (ledger.loadCurrent(paths).commitSequence() + 1), event,
                Map.of("operation", plan.operation(), "candidate_ref", plan.candidateRef(), "candidate_sha256", plan.candidateSha256(),
                        "status", plan.status().name(), "commit", plan.commit()));
    }

    private DeliveryMergePlan requireSameOperation(WorkstreamPaths paths, DeliveryMergePlan expected) throws IOException {
        var current = readPlan(paths);
        if (current == null || !java.util.Objects.equals(current.operation().get("id"), expected.operation().get("id"))
                || !current.candidateSha256().equals(expected.candidateSha256())) throw boundary("DELIVERY_INVALID", "delivery operation changed");
        return current;
    }

    private static DeliveryMergePlan observed(DeliveryMergePlan plan, Map<String, Object> observation) {
        var facts = new LinkedHashMap<>(plan.operation()); facts.put("observation", observation); return plan.withOperation(facts);
    }

    private static boolean applicationMatches(DeliveryMergePlan plan, Map<String, Object> observation) {
        return plan.baseCommit().equals(observation.get("head")) && plan.expectedResultTree().equals(observation.get("index_tree"))
                && Boolean.TRUE.equals(observation.get("worktree_matches_index")) && Boolean.TRUE.equals(observation.get("no_untracked"));
    }

    private static boolean baseMatches(String commit, String tree, Map<String, Object> observation) {
        return commit.equals(observation.get("head")) && tree.equals(observation.get("head_tree")) && tree.equals(observation.get("index_tree"))
                && Boolean.TRUE.equals(observation.get("clean")) && Boolean.TRUE.equals(observation.get("worktree_matches_index"))
                && Boolean.TRUE.equals(observation.get("no_untracked"));
    }

    private static void requireBase(String commit, String tree, Map<String, Object> observation) throws IOException {
        if (!baseMatches(commit, tree, observation)) throw boundary("TARGET_NOT_ELIGIBLE", "target repository base does not match accepted candidate");
    }

    private boolean commitMatches(DeliveryMergePlan plan, Path target, Map<String, Object> observation) throws Exception {
        if (!Boolean.TRUE.equals(observation.get("clean")) || plan.baseCommit().equals(observation.get("head"))) return false;
        var commit = git.commitState(target, text(observation.get("head")));
        return commit.parent().equals(plan.baseCommit()) && commit.tree().equals(plan.expectedResultTree());
    }

    private void requireCompleted(WorkstreamPaths paths) throws IOException {
        var current = ledger.loadCurrent(paths);
        if (current.status() != WorkflowStatus.COMPLETED) {
            throw boundary("WORKFLOW_NOT_DELIVERABLE", "workstream is not completed: " + current.status());
        }
        if (!Files.isRegularFile(paths.completionAuditJson())) throw boundary("WORKFLOW_NOT_DELIVERABLE", "completion audit is missing");
        var audit = ledger.files().readTree(paths.completionAuditJson());
        if (!"ready".equalsIgnoreCase(audit.path("status").asText())) {
            throw boundary("WORKFLOW_NOT_DELIVERABLE", "completion audit is not ready: " + audit.path("status").asText());
        }
    }

    private void verifySourceCapture(Path source, AcceptedDeliveryCandidateResolver.Accepted accepted) throws Exception {
        var candidate = accepted.candidate();
        if (!source.toRealPath().equals(Path.of(candidate.sourceEffectiveWorkdir()).toRealPath())) {
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

    private Path targetRepo(WorkstreamPaths paths, Path requestedTarget) throws IOException {
        var workstream = ledger.files().read(paths.workstreamJson(), WorkstreamDocument.class);
        var recorded = Path.of(workstream.repository().originalWorkdir()).toRealPath();
        if (requestedTarget != null && !recorded.equals(requestedTarget.toRealPath()))
            throw boundary("TARGET_NOT_ELIGIBLE", "requested target does not match workstream repository identity");
        return recorded;
    }

    private void requirePlanCandidate(WorkstreamPaths paths, DeliveryMergePlan plan, AcceptedDeliveryCandidateResolver.Accepted accepted) throws IOException {
        try {
        if (plan.candidateRef().isBlank()) throw boundary("ACCEPTED_CANDIDATE_INVALID", "delivery plan is missing its accepted candidate binding");
        var target = targetRepo(paths, null); var c = accepted.candidate();
        if (plan.schemaVersion() != 3 || !plan.workstreamId().equals(paths.workstreamId().value())
                || !plan.sourceWorktree().equals(paths.workspaceRoot().toRealPath().toString()) || !plan.targetRepo().equals(target.toString())
                || !"approved".equals(plan.acceptanceDecision()) || !plan.candidateRef().equals(accepted.candidateRef())
                || !plan.candidateSha256().equals(accepted.candidateSha256()) || !plan.baseCommit().equals(c.baseCommit())
                || !plan.baseTree().equals(c.baseTree()) || !plan.expectedResultTree().equals(c.expectedResultTree())
                || !plan.changedFiles().equals(c.acceptedFiles()) || !plan.excludedFiles().equals(c.excludedFiles())
                || !"dual.delivery-application.v1".equals(plan.operation().get("schema"))
                || !Boolean.TRUE.equals(plan.operation().get("apply_intent")) || text(plan.operation().get("id")).isBlank()
                || !accepted.gateNode().nodeInstanceId().equals(plan.operation().get("approval_node"))
                || !gateSha(paths, accepted).equals(plan.operation().get("approval_sha256"))
                || !objectMapper.valueToTree(expectedBinding(paths, target, accepted)).equals(objectMapper.valueToTree(plan.operation().get("binding"))))
            throw boundary("ACCEPTED_CANDIDATE_INVALID", "delivery plan does not match the accepted candidate and operation binding");
        var allowed = java.util.Set.of("schema", "id", "apply_intent", "before", "executor_pid", "executor_started_at", "approval_node", "approval_sha256",
                "binding", "process", "process_exit", "operator_stop", "output", "observation", "application_closed", "predecessor", "authorizing_request");
        if (!allowed.equals(plan.operation().keySet()) || !(plan.operation().get("application_closed") instanceof Boolean))
            throw boundary("DELIVERY_INVALID", "unsupported delivery operation fields");
        for (var key : List.of("process", "process_exit", "operator_stop", "predecessor", "authorizing_request"))
            if (plan.operation().get(key) != null && !(plan.operation().get(key) instanceof Map<?, ?>))
                throw boundary("DELIVERY_INVALID", "invalid delivery fact: " + key);
        } catch (IOException failure) { throw failure; }
        catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("delivery boundary observation failed", failure);
        }
    }

    private void writeCompatibilityArtifacts(WorkstreamPaths paths, AcceptedDeliveryCandidateResolver.Accepted accepted)
            throws IOException {
        ledger.files().writeBytes(paths.deliveryMergePatch(), ledger.files().readBytes(resolveRef(paths, accepted.candidate().patch().ref())));
        ledger.files().writeBytes(paths.deliveryChangedFilesTxt(), (String.join(System.lineSeparator(), accepted.candidate().acceptedFiles())
                + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        ledger.files().writeBytes(paths.deliveryExcludedFilesTxt(), (String.join(System.lineSeparator(), accepted.candidate().excludedFiles())
                + (accepted.candidate().excludedFiles().isEmpty() ? "" : System.lineSeparator())).getBytes(StandardCharsets.UTF_8));
    }

    private DeliveryMergePlan readPlan(WorkstreamPaths paths) throws IOException {
        return ledger.files().exists(paths.deliveryMergePlanJson())
                ? ledger.files().read(paths.deliveryMergePlanJson(), DeliveryMergePlan.class)
                : null;
    }

    private String gateSha(WorkstreamPaths paths, AcceptedDeliveryCandidateResolver.Accepted accepted) throws IOException {
        return DeliveryCandidateWriter.sha256(ledger.files().readBytes(paths.gateJson(accepted.gate().gateId())));
    }

    private String planHash(WorkstreamPaths paths) throws IOException {
        return ledger.files().exists(paths.deliveryMergePlanJson()) ? DeliveryCandidateWriter.sha256(ledger.files().readBytes(paths.deliveryMergePlanJson())) : "";
    }

    private String stopBasis(WorkstreamPaths paths, DeliveryMergePlan plan) throws IOException {
        var process = map(plan.operation().get("process"));
        var exit = map(plan.operation().get("process_exit"));
        if (!process.isEmpty()) {
            fields(objectMapper.valueToTree(process), java.util.Set.of("pid", "started_at", "command_kind"));
            if (!(process.get("pid") instanceof Number pid) || pid.longValue() <= 0
                    || !"GIT_APPLY_INDEX".equals(process.get("command_kind"))) throw boundary("DELIVERY_INVALID", "invalid Git process identity");
            java.time.Instant start;
            try { start = java.time.Instant.parse(text(process.get("started_at"))); }
            catch (RuntimeException bad) { throw boundary("DELIVERY_INVALID", "invalid process start time"); }
            boolean started = false;
            for (var line : ledger.files().readLines(paths.eventsJsonl())) {
                var event = objectMapper.readTree(line);
                if ("delivery_process_started".equals(event.path("event_type").asText())
                        && text(plan.operation().get("id")).equals(event.at("/payload/operation/id").asText())
                        && canonicalSha256(objectMapper.valueToTree(process)).equals(canonicalSha256(event.at("/payload/operation/process")))
                        && objectMapper.valueToTree(plan.operation().get("binding")).equals(event.at("/payload/operation/binding"))) started = true;
            }
            if (!started) throw boundary("DELIVERY_INVALID", "process identity has no matching durable start event");
            var handle = ProcessHandle.of(pid.longValue());
            if (handle.isPresent() && handle.get().isAlive()) {
                var actualStart = handle.get().info().startInstant();
                if (actualStart.isEmpty()) return "UNKNOWN";
                if (start.equals(actualStart.get())) return "ACTIVE";
            }
            if (!exit.isEmpty()) {
                fields(objectMapper.valueToTree(exit), java.util.Set.of("pid", "started_at", "termination_observed", "observation_source", "actual_exit_code", "observed_at"));
                if (!(exit.get("pid") instanceof Number endedPid) || endedPid.longValue() != pid.longValue()
                        || !start.toString().equals(exit.get("started_at")) || !Boolean.TRUE.equals(exit.get("termination_observed")))
                    throw boundary("DELIVERY_INVALID", "process termination is not bound to its registered identity");
                String source = text(exit.get("observation_source"));
                if (!("PROCESS_WAIT_FOR".equals(source) && exit.get("actual_exit_code") instanceof Number)
                        && !("PROGRAM_PROCESS_ABSENT".equals(source) && exit.get("actual_exit_code") == null))
                    throw boundary("DELIVERY_INVALID", "unmeasured process exit must not be invented");
                boolean recorded = false;
                for (var line : ledger.files().readLines(paths.eventsJsonl())) {
                    var e = objectMapper.readTree(line);
                    if (java.util.Set.of("delivery_process_exited", "delivery_committed", "delivery_application_observed").contains(e.path("event_type").asText())
                            && text(plan.operation().get("id")).equals(e.at("/payload/operation/id").asText())
                            && canonicalSha256(objectMapper.valueToTree(exit)).equals(canonicalSha256(e.at("/payload/operation/process_exit")))) recorded = true;
                }
                if (!recorded) throw boundary("DELIVERY_INVALID", "termination fact has no matching durable delivery event");
                return "PROGRAM_OBSERVED_TERMINATION";
            }
            // Identity is complete and the registered main process is absent/replaced. Never invent an exit code.
            if (handle.isEmpty() || handle.get().info().startInstant().filter(start::equals).isEmpty()) return "PROGRAM_OBSERVED_TERMINATION";
        }
        var operator = map(plan.operation().get("operator_stop"));
        if (!operator.isEmpty()) {
            var entry = checkedEvent(paths, operator);
            var payload = entry.document().path("payload");
            var request = payload.path("request");
            if (!"delivery_resolution".equals(entry.document().path("event_type").asText())
                    || !text(plan.operation().get("id")).equals(request.path("operation_id").asText())
                    || !canonicalSha256(request).equals(payload.path("request_sha256").asText())
                    || !objectMapper.valueToTree(ledger.frozenRuntimeIdentity()).equals(payload.path("runtime_identity"))
                    || !objectMapper.valueToTree(plan.operation().get("binding")).equals(payload.at("/old_operation/operation/binding"))
                    || !request.path("stop_basis").equals(payload.path("stop_basis")))
                throw boundary("DELIVERY_INVALID", "operator stop declaration has wrong origin");
            if (!java.util.Set.of("OPERATOR_CONFIRMED_STOPPED", "PROGRAM_OBSERVED_TERMINATION").contains(request.at("/stop_basis/type").asText()))
                throw boundary("DELIVERY_INVALID", "unsupported persisted stop evidence");
            return "OPERATOR_CONFIRMED_STOPPED".equals(request.at("/stop_basis/type").asText())
                    ? "OPERATOR_CONFIRMED_STOPPED" : "PROGRAM_OBSERVED_TERMINATION";
        }
        return "UNKNOWN";
    }

    private DeliveryMergePlan withObservedTermination(DeliveryMergePlan plan) {
        if (plan.operation().get("process_exit") != null || plan.operation().get("process") == null) return plan;
        var process = map(plan.operation().get("process")); var exit = new LinkedHashMap<String, Object>();
        exit.put("pid", process.get("pid")); exit.put("started_at", process.get("started_at"));
        exit.put("termination_observed", true); exit.put("observation_source", "PROGRAM_PROCESS_ABSENT");
        exit.put("actual_exit_code", null); exit.put("observed_at", java.time.Instant.now().toString());
        var facts = new LinkedHashMap<>(plan.operation()); facts.put("process_exit", exit); return plan.withOperation(facts);
    }

    private Map<String, Object> resolutionTemplate(WorkstreamPaths paths, DeliveryMergePlan plan, Map<String, Object> observation) throws IOException {
        var request = new LinkedHashMap<String, Object>();
        request.put("schema", "dual.delivery-resolution.v1"); request.put("request_id", java.util.UUID.randomUUID().toString());
        request.put("workstream_id", paths.workstreamId().value()); request.put("frozen_runtime_identity", ledger.frozenRuntimeIdentity());
        request.put("operation_id", plan.operation().get("id")); request.put("plan_sha256", planHash(paths));
        request.put("ledger_commit_sequence", ledger.loadCurrent(paths).commitSequence());
        request.put("candidate", Map.of("ref", plan.candidateRef(), "sha256", plan.candidateSha256()));
        var binding = map(plan.operation().get("binding"));
        request.put("approval", Map.of("node", binding.get("approval_node"), "ref", binding.get("approval_ref"), "sha256", binding.get("approval_sha256")));
        request.put("target", Map.of("repo", plan.targetRepo(), "common_dir", binding.get("common_dir")));
        request.put("target_observation_sha256", observationSha256(observation));
        request.put("disposition", ""); request.put("operator_note", ""); request.put("stop_basis", null);
        return java.util.Collections.unmodifiableMap(request);
    }

    private void requireRequestBinding(WorkstreamPaths paths, DeliveryMergePlan plan, com.fasterxml.jackson.databind.JsonNode request,
            Map<String, Object> observation) throws IOException {
        var binding = map(plan.operation().get("binding"));
        if (!objectMapper.valueToTree(ledger.frozenRuntimeIdentity()).equals(request.path("frozen_runtime_identity"))
                || !text(plan.operation().get("id")).equals(requiredText(request, "operation_id"))
                || !planHash(paths).equals(requiredText(request, "plan_sha256"))
                || !request.path("ledger_commit_sequence").isIntegralNumber() || !request.path("ledger_commit_sequence").canConvertToLong()
                || ledger.loadCurrent(paths).commitSequence() != request.path("ledger_commit_sequence").longValue()
                || !plan.candidateRef().equals(requiredText(request.path("candidate"), "ref"))
                || !plan.candidateSha256().equals(requiredText(request.path("candidate"), "sha256"))
                || !text(binding.get("approval_node")).equals(requiredText(request.path("approval"), "node"))
                || !text(binding.get("approval_ref")).equals(requiredText(request.path("approval"), "ref"))
                || !text(binding.get("approval_sha256")).equals(requiredText(request.path("approval"), "sha256"))
                || !plan.targetRepo().equals(requiredText(request.path("target"), "repo"))
                || !text(binding.get("common_dir")).equals(requiredText(request.path("target"), "common_dir"))
                || !observationSha256(observation).equals(requiredText(request, "target_observation_sha256")))
            throw boundary("DELIVERY_INVALID", "resolution no longer matches the current operation, approval or target");
        if (requiredText(request, "operator_note").isBlank()) throw boundary("DELIVERY_INVALID", "operator note is required");
    }

    private com.fasterxml.jackson.databind.JsonNode validateStopRequest(WorkstreamPaths paths, DeliveryMergePlan plan,
            com.fasterxml.jackson.databind.JsonNode stop, boolean required) throws IOException {
        if (stop.isNull()) {
            if (required) throw boundary("DELIVERY_RESULT_UNKNOWN", "new application requires confirmed old-operation stop");
            return null;
        }
        String kind = requiredText(stop, "type");
        if ("PROGRAM_OBSERVED_TERMINATION".equals(kind)) {
            fields(stop, java.util.Set.of("type", "event_ref", "event_id", "entry_sha256"));
            var ref = Map.<String, Object>of("ref", requiredText(stop, "event_ref"), "event_id", requiredText(stop, "event_id"), "entry_sha256", requiredText(stop, "entry_sha256"));
            var receipt = checkedEvent(paths, ref);
            if (!"PROGRAM_OBSERVED_TERMINATION".equals(stopBasis(paths, plan))
                    || !java.util.Set.of("delivery_process_exited", "delivery_application_observed").contains(receipt.document().path("event_type").asText())
                    || !text(plan.operation().get("id")).equals(receipt.document().at("/payload/operation/id").asText())
                    || !canonicalSha256(objectMapper.valueToTree(plan.operation().get("process_exit"))).equals(canonicalSha256(receipt.document().at("/payload/operation/process_exit"))))
                throw boundary("DELIVERY_INVALID", "stop receipt does not prove this operation ended");
        } else if ("OPERATOR_CONFIRMED_STOPPED".equals(kind)) {
            fields(stop, java.util.Set.of("type", "checked_at", "checks_performed", "old_operation_stopped", "accept_unknown_result_and_repeat_risk", "material_refs"));
            try { java.time.Instant.parse(requiredText(stop, "checked_at")); } catch (RuntimeException bad) { throw boundary("DELIVERY_INVALID", "stop check time is invalid"); }
            var checks = stop.path("checks_performed");
            if (!checks.isArray() || checks.isEmpty() || !stop.path("old_operation_stopped").isBoolean()
                    || !stop.path("old_operation_stopped").booleanValue() || !stop.path("accept_unknown_result_and_repeat_risk").isBoolean()
                    || !stop.path("accept_unknown_result_and_repeat_risk").booleanValue()) throw boundary("DELIVERY_INVALID", "explicit human check and risk confirmation required");
            for (var check : checks) if (!check.isTextual() || check.textValue().isBlank()) throw boundary("DELIVERY_INVALID", "human checks cannot be empty");
            if (!stop.path("material_refs").isArray()) throw boundary("DELIVERY_INVALID", "material refs must be a list");
            long size = 0;
            for (var material : stop.path("material_refs")) {
                fields(material, java.util.Set.of("ref", "sha256"));
                var source = resolveRef(paths, requiredText(material, "ref")).toRealPath();
                var artifacts = paths.workstreamDir().resolve("artifacts").toRealPath();
                if (!source.startsWith(artifacts) || !(source.toString().endsWith(".json") || source.toString().endsWith(".md")))
                    throw boundary("DELIVERY_INVALID", "stop material is outside this workstream's data artifacts");
                long length = Files.size(source); size += length;
                if (size > 1024 * 1024) throw boundary("DELIVERY_INVALID", "stop materials exceed 1 MiB");
                var bytes = ledger.files().readBytes(source);
                if (bytes.length != length || !DeliveryCandidateWriter.sha256(bytes).equals(requiredText(material, "sha256")))
                    throw boundary("DELIVERY_INVALID", "stop material content changed");
                var content = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                var snapshot = Map.of("type", "OPERATOR_DECLARATION", "ref", requiredText(material, "ref"), "sha256", requiredText(material, "sha256"), "utf8", content);
                var fixed = canonicalBytes(objectMapper.valueToTree(snapshot));
                var dest = paths.workstreamDir().resolve("artifacts/delivery-stop-" + DeliveryCandidateWriter.sha256(fixed) + ".json");
                if (ledger.files().exists(dest) && !java.util.Arrays.equals(ledger.files().readBytes(dest), fixed)) throw boundary("DELIVERY_INVALID", "immutable stop material differs");
                ledger.files().writeBytes(dest, fixed);
            }
        } else throw boundary("DELIVERY_INVALID", "unsupported stop evidence type");
        if ("ACTIVE".equals(stopBasis(paths, plan))) throw boundary("DELIVERY_ACTIVE", "human confirmation cannot override a live registered process");
        return stop;
    }

    public static com.fasterxml.jackson.databind.JsonNode readDecision(byte[] bytes) throws IOException {
        if (bytes.length > 64 * 1024) throw boundary("DELIVERY_INVALID", "decision exceeds 64 KiB");
        var mapper = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        var r = mapper.readTree(bytes);
        if (r == null) throw boundary("DELIVERY_INVALID", "decision is empty");
        fields(r, java.util.Set.of("schema", "request_id", "workstream_id", "frozen_runtime_identity", "operation_id", "plan_sha256",
                "ledger_commit_sequence", "candidate", "approval", "target", "target_observation_sha256", "disposition", "operator_note", "stop_basis"));
        if (!"dual.delivery-resolution.v1".equals(requiredText(r, "schema"))) throw boundary("DELIVERY_INVALID", "unsupported resolution schema");
        fields(r.path("candidate"), java.util.Set.of("ref", "sha256")); fields(r.path("approval"), java.util.Set.of("node", "ref", "sha256"));
        fields(r.path("target"), java.util.Set.of("repo", "common_dir"));
        return r;
    }

    private static void fields(com.fasterxml.jackson.databind.JsonNode value, java.util.Set<String> expected) throws IOException {
        var actual = new java.util.HashSet<String>(); value.fieldNames().forEachRemaining(actual::add);
        if (!value.isObject() || !actual.equals(expected)) throw boundary("DELIVERY_INVALID", "required/unknown object fields: " + expected);
    }

    private static String requiredText(com.fasterxml.jackson.databind.JsonNode value, String field) throws IOException {
        var v = value.path(field);
        if (!v.isTextual() || v.textValue().isBlank()) throw boundary("DELIVERY_INVALID", field + " is required");
        return v.textValue();
    }

    /** One canonical function for all entrypoints; only the observation's top-level time is excluded. */
    public static String observationSha256(Map<String, Object> observation) throws IOException {
        var identity = new LinkedHashMap<String, Object>();
        for (var key : List.of("repo", "common_dir", "head", "head_tree", "index_tree", "worktree_matches_index", "clean", "status_entries", "untracked_entries")) {
            if (!observation.containsKey(key)) throw boundary("DELIVERY_INVALID", "observation lacks " + key);
            identity.put(key, observation.get(key));
        }
        return canonicalSha256(new ObjectMapper().valueToTree(identity));
    }

    public static String canonicalSha256(com.fasterxml.jackson.databind.JsonNode value) throws IOException {
        return DeliveryCandidateWriter.sha256(canonicalBytes(value));
    }

    private static byte[] canonicalBytes(com.fasterxml.jackson.databind.JsonNode value) throws IOException {
        var mapper = new ObjectMapper(); return mapper.writeValueAsBytes(sorted(value, mapper));
    }

    private static com.fasterxml.jackson.databind.JsonNode sorted(com.fasterxml.jackson.databind.JsonNode value, ObjectMapper mapper) {
        if (value.isObject()) {
            var result = mapper.createObjectNode(); var keys = new java.util.TreeSet<String>(); value.fieldNames().forEachRemaining(keys::add);
            for (var key : keys) result.set(key, sorted(value.get(key), mapper)); return result;
        }
        if (value.isArray()) { var result = mapper.createArrayNode(); for (var item : value) result.add(sorted(item, mapper)); return result; }
        return value;
    }

    private EventEntry findEvent(WorkstreamPaths paths, String id) throws IOException {
        EventEntry found = null;
        for (var line : ledger.files().readLines(paths.eventsJsonl())) {
            var event = objectMapper.readTree(line);
            if (id.equals(event.path("event_id").asText())) {
                if (found != null) throw boundary("DELIVERY_INVALID", "duplicate resolution event identity");
                found = new EventEntry(event, Map.of("ref", relative(paths, paths.eventsJsonl()), "event_id", id,
                        "entry_sha256", DeliveryCandidateWriter.sha256(line.getBytes(StandardCharsets.UTF_8))));
            }
        }
        return found;
    }

    private EventEntry checkedEvent(WorkstreamPaths paths, Map<String, Object> ref) throws IOException {
        if (!relative(paths, paths.eventsJsonl()).equals(ref.get("ref"))) throw boundary("DELIVERY_INVALID", "wrong event file");
        var event = findEvent(paths, text(ref.get("event_id")));
        if (event == null || !event.reference().equals(ref)) throw boundary("DELIVERY_INVALID", "event entry identity differs");
        return event;
    }

    private record EventEntry(com.fasterxml.jackson.databind.JsonNode document, Map<String, Object> reference) {}

    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static String relative(WorkstreamPaths paths, Path file) { return paths.workspaceRoot().toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString(); }
    private static String shellQuote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }

    private static Path resolveRef(WorkstreamPaths paths, String ref) throws IOException {
        var root = paths.workspaceRoot().toAbsolutePath().normalize();
        var path = root.resolve(ref).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) throw new IOException("delivery artifact ref is invalid: " + ref);
        return path;
    }

    private static WorkstreamPaths paths(Path source, String workstreamId) {
        return new WorkstreamPaths(source.toAbsolutePath().normalize(), new DualId(workstreamId));
    }

    private static View unavailable(String status, String code, String reason) {
        return new View(status, code, reason, "", "", "", List.of(), List.of(), "", "", false, false);
    }

    private static View blocked(String code, String reason) { return unavailable("BLOCKED", code, reason); }

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

    public record View(String status, String reasonCode, String reason, String acceptanceDecision, String candidateRef,
            String candidateSha256, List<String> changedFiles, List<String> excludedFiles, String targetRepo, String commit,
            boolean canMerge, boolean canFinalize, String historicalStatus, Map<String, Object> targetObservation,
            String operationId, String planSha256, long ledgerCommitSequence, String stopBasis, List<String> currentActions,
            String nextAction, List<String> nextCommands, Map<String, Object> resolutionTemplate) {
        public View {
            changedFiles = List.copyOf(changedFiles); excludedFiles = List.copyOf(excludedFiles);
            targetObservation = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(targetObservation));
            currentActions = List.copyOf(currentActions); nextCommands = List.copyOf(nextCommands);
            resolutionTemplate = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(resolutionTemplate));
        }
        public View(String status, String reasonCode, String reason, String acceptanceDecision, String candidateRef,
                String candidateSha256, List<String> changedFiles, List<String> excludedFiles, String targetRepo, String commit,
                boolean canMerge, boolean canFinalize) {
            this(status, reasonCode, reason, acceptanceDecision, candidateRef, candidateSha256, changedFiles, excludedFiles,
                    targetRepo, commit, canMerge, canFinalize, status, Map.of(), "", "", 0, "UNKNOWN", List.of(), reason, List.of(), Map.of());
        }
        public View withoutActions() {
            return new View(status, reasonCode, reason, acceptanceDecision, candidateRef, candidateSha256, changedFiles, excludedFiles,
                    targetRepo, commit, false, false, historicalStatus, targetObservation, operationId, planSha256, ledgerCommitSequence,
                    stopBasis, List.of(), nextAction, List.of(), Map.of());
        }
        public View stale() {
            return new View(status, "DELIVERY_OBSERVATION_STALE", "Delivery observation is stale; refresh before acting.", acceptanceDecision,
                    candidateRef, candidateSha256, changedFiles, excludedFiles, targetRepo, commit, false, false, historicalStatus,
                    targetObservation, operationId, planSha256, ledgerCommitSequence, stopBasis, List.of(),
                    "Refresh the delivery observation.", List.of(), Map.of());
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
