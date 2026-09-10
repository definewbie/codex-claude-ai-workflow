package dev.dual.server;

import dev.dual.adapter.acpx.AcpxSessionLifecycle;
import dev.dual.adapter.acpx.WorkstreamSessionCloser;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.WorkflowCommand;
import dev.dual.runtime.WorkflowRuntimeResult;
import dev.dual.runtime.StaleRunningRecovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Service
public class WorkstreamActionService {
    private static final Set<WorkflowStatus> TERMINAL_OR_PAUSED = Set.of(
            WorkflowStatus.WAITING_FOR_HUMAN,
            WorkflowStatus.WAITING_FOR_RECOVERY,
            WorkflowStatus.BLOCKED,
            WorkflowStatus.COMPLETED,
            WorkflowStatus.FAILED
    );

    private final WorkflowFileLedger ledger;
    private final Supplier<WorkflowResumer> resumerFactory;
    private final WorkstreamSessionCloser sessionCloser;
    private final StaleRunningRecovery staleRunningRecovery;

    @Autowired
    public WorkstreamActionService(ServerWorkflowRuntimeFactory runtimeFactory) {
        this(ServerLedgerFactory.create(), runtimeFactory::create, new AcpxSessionLifecycle());
    }

    WorkstreamActionService(WorkflowFileLedger ledger, Supplier<WorkflowResumer> resumerFactory) {
        this(ledger, resumerFactory, WorkstreamSessionCloser.noop());
    }

    WorkstreamActionService(WorkflowFileLedger ledger, Supplier<WorkflowResumer> resumerFactory,
                            WorkstreamSessionCloser sessionCloser) {
        this.ledger = ledger;
        this.resumerFactory = resumerFactory;
        this.sessionCloser = sessionCloser == null ? WorkstreamSessionCloser.noop() : sessionCloser;
        this.staleRunningRecovery = new StaleRunningRecovery(ledger);
    }

    /**
     * P0: 当 workstream 处于终态（COMPLETED / FAILED）时，best-effort 关闭其 acpx session。
     * 永不影响 workflow 主状态：任何异常都被吞掉（closer 自身也不抛）。
     */
    private void maybeCloseTerminalSessions(Path root, String workstreamId) {
        try {
            var status = ledger.loadCurrent(paths(root, workstreamId)).status();
            if (status == WorkflowStatus.COMPLETED || status == WorkflowStatus.FAILED) {
                sessionCloser.closeWorkstreamSessions(root, workstreamId);
            }
        } catch (Exception ignored) {
            // session cleanup must never affect the workflow's terminal status or the action result
        }
    }

    public Map<String, Object> resume(Path workspaceRoot, String workstreamId, int maxSteps) throws Exception {
        var root = normalize(workspaceRoot);
        validateMaxSteps(maxSteps);
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api resume"))) {
            assertResumable(paths, "resume");
            var response = runLoop(root, workstreamId, maxSteps);
            maybeCloseTerminalSessions(root, workstreamId);
            return response;
        }
    }

    public Map<String, Object> retry(Path workspaceRoot, String workstreamId, String note) throws Exception {
        var root = normalize(workspaceRoot);
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api retry"))) {
            assertNotCompleted(paths, "retry");
            ledger.retryActiveNode(paths, note);
        }
        maybeCloseTerminalSessions(root, workstreamId);
        return actionResponse("retry", workstreamId, "active node marked ready for retry");
    }

    public Map<String, Object> approvePublicWebSearch(
            Path workspaceRoot,
            String workstreamId,
            String note,
            boolean publicRepositoryAttested
    ) throws Exception {
        var root = normalize(workspaceRoot);
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(
                paths.workstreamDir(),
                LockOwner.currentProcess(workstreamId, "api approve public WebSearch"))) {
            assertNotCompleted(paths, "approve public WebSearch");
            ledger.approvePublicWebSearch(paths, note, publicRepositoryAttested);
        }
        return actionResponse(
                "recovery.approve_public_websearch",
                workstreamId,
                "public WebSearch approved for one retry");
    }

    public Map<String, Object> networkAuthority(Path workspaceRoot, String workstreamId,
                                                String target, java.util.List<String> domains, String note, boolean cancel) throws Exception {
        var root = normalize(workspaceRoot).toRealPath();
        var paths = paths(root, workstreamId);
        var store = new dev.dual.ledger.file.ExecutionNetworkGrantStore();
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api network authority"))) {
            if (cancel) {
                if (target != null || domains != null || note != null) throw new IllegalArgumentException("cancel cannot include approval fields");
                store.cancel(paths);
            } else {
                store.approve(paths, target, domains, note);
            }
            return store.view(paths);
        }
    }

    public Map<String, Object> recoverStaleRunning(Path workspaceRoot, String workstreamId, String note) throws Exception {
        var root = normalize(workspaceRoot);
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api recovery stale"))) {
            staleRunningRecovery.recover(paths, note);
        }
        maybeCloseTerminalSessions(root, workstreamId);
        return actionResponse("recovery.stale", workstreamId, "stale running node moved to recovery");
    }

    public Map<String, Object> closeMistakenIteration(Path workspaceRoot, String workstreamId, String note) throws Exception {
        var root = normalize(workspaceRoot);
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api recovery close mistaken iteration"))) {
            ledger.closeMistakenIteration(paths, note);
        }
        maybeCloseTerminalSessions(root, workstreamId);
        return actionResponse("recovery.close_mistaken_iteration", workstreamId, "mistaken iteration closed");
    }

    public Map<String, Object> approve(
            Path workspaceRoot, String workstreamId, String note, boolean runImmediately, int maxSteps
    ) throws Exception {
        var root = normalize(workspaceRoot);
        resolveGate(root, workstreamId, "approve", note);
        if (runImmediately) {
            var current = ledger.loadCurrent(paths(root, workstreamId));
            if (current.status() == WorkflowStatus.COMPLETED) {
                maybeCloseTerminalSessions(root, workstreamId);
                var response = actionResponse("review.approve.run", workstreamId, "human gate approved; workflow completed");
                response.put("status", WorkflowStatus.COMPLETED.name());
                response.put("activeNode", "");
                response.put("steps", List.of());
                response.put("maxSteps", maxSteps);
                response.put("stoppedBecause", "boundary");
                return response;
            }
            var response = resume(root, workstreamId, maxSteps);
            response.put("action", "review.approve.run");
            return response;
        }
        maybeCloseTerminalSessions(root, workstreamId);
        return actionResponse("review.approve", workstreamId, "human gate approved");
    }

    public Map<String, Object> complete(Path workspaceRoot, String workstreamId, String note) throws Exception {
        var root = normalize(workspaceRoot);
        resolveGate(root, workstreamId, "complete", note == null || note.isBlank() ? "completed from API" : note);
        maybeCloseTerminalSessions(root, workstreamId);
        return actionResponse("review.complete", workstreamId, "human gate completed");
    }

    public Map<String, Object> revise(Path workspaceRoot, String workstreamId, String feedback,
                                      boolean runImmediately, int maxSteps) throws Exception {
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("feedback is required");
        }
        var root = normalize(workspaceRoot);
        resolveGate(root, workstreamId, "revise", feedback);
        if (runImmediately) {
            var response = resume(root, workstreamId, maxSteps);
            response.put("action", "review.revise.run");
            return response;
        }
        maybeCloseTerminalSessions(root, workstreamId);
        return actionResponse("review.revise", workstreamId, "human gate revision requested");
    }

    public Map<String, Object> requestScopeRevision(Path workspaceRoot, String workstreamId, String note,
                                                    boolean runImmediately, int maxSteps) throws Exception {
        var root = normalize(workspaceRoot);
        resolveGate(root, workstreamId, "request_scope_revision", note);
        if (runImmediately) {
            var response = resume(root, workstreamId, maxSteps);
            response.put("action", "review.request_scope_revision.run");
            return response;
        }
        return actionResponse("review.request_scope_revision", workstreamId,
                "scope revision requested; the existing contract remains authoritative until approval");
    }

    public Map<String, Object> requestAction(Path workspaceRoot, String workstreamId,
                                            dev.dual.ledger.file.HumanActionRequest request) throws Exception {
        var record = ledger.requestAction(paths(normalize(workspaceRoot), workstreamId), request);
        return Map.of("action", "review.request_action", "request", record,
                "message", "Action need recorded; gate remains waiting and no execution is authorized");
    }

    public Map<String, Object> collectEvidence(Path workspaceRoot, String workstreamId, String note,
                                               boolean runImmediately, int maxSteps) throws Exception {
        var root = normalize(workspaceRoot);
        resolveGate(root, workstreamId, "collect_evidence", note);
        if (runImmediately) return resume(root, workstreamId, maxSteps);
        return actionResponse("review.collect_evidence", workstreamId, "Evidence collection approved against source baseline");
    }

    private void resolveGate(Path root, String workstreamId, String action, String note) throws Exception {
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api review " + action))) {
            assertNotCompleted(paths, "review " + action);
            dev.dual.runtime.handler.EvidenceCollection.resolveGate(ledger, paths, ledger.loadDefinition(paths), action, note);
        }
    }

    private Map<String, Object> runLoop(Path root, String workstreamId, int maxSteps) throws Exception {
        var resumer = resumerFactory.get();
        var command = new WorkflowCommand(root, workstreamId);
        var steps = new ArrayList<Map<String, Object>>();
        WorkflowRuntimeResult last = null;
        for (int step = 1; step <= maxSteps; step++) {
            last = resumer.resume(command);
            steps.add(stepResult(step, last));
            if (TERMINAL_OR_PAUSED.contains(last.status())) {
                break;
            }
        }
        if (last == null) {
            throw new IllegalStateException("workflow did not run");
        }
        var response = runtimeResponse("resume", last);
        response.put("steps", steps);
        response.put("maxSteps", maxSteps);
        response.put("stoppedBecause", TERMINAL_OR_PAUSED.contains(last.status()) ? "boundary" : "max_steps");
        return response;
    }

    private static Map<String, Object> stepResult(int step, WorkflowRuntimeResult result) {
        var item = new LinkedHashMap<String, Object>();
        item.put("step", step);
        item.put("status", result.status().name());
        item.put("activeNode", result.activeNode());
        item.put("message", result.message());
        return item;
    }

    private static Map<String, Object> runtimeResponse(String action, WorkflowRuntimeResult result) {
        var response = actionResponse(action, result.workstreamId(), result.message());
        response.put("status", result.status().name());
        response.put("activeNode", result.activeNode());
        return response;
    }

    private static Map<String, Object> actionResponse(String action, String workstreamId, String message) {
        var response = new LinkedHashMap<String, Object>();
        response.put("action", action);
        response.put("workstreamId", workstreamId);
        response.put("message", message);
        return response;
    }

    private static void validateMaxSteps(int maxSteps) {
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
    }

    private void assertResumable(WorkstreamPaths paths, String action) throws Exception {
        var status = ledger.loadCurrent(paths).status();
        if (status == WorkflowStatus.COMPLETED) {
            throw new IllegalStateException("workflow is already completed; " + action + " is not allowed");
        }
        if (status == WorkflowStatus.FAILED || status == WorkflowStatus.BLOCKED) {
            throw new IllegalStateException("workflow is " + status + "; inspect recovery before " + action);
        }
        if (status == WorkflowStatus.WAITING_FOR_RECOVERY) {
            throw new IllegalStateException("workflow is waiting for recovery; run retry only after the cause is fixed");
        }
        if (status == WorkflowStatus.WAITING_FOR_HUMAN) {
            throw new IllegalStateException("workflow is waiting for human review; approve, revise, or complete the gate first");
        }
    }

    private void assertNotCompleted(WorkstreamPaths paths, String action) throws Exception {
        var status = ledger.loadCurrent(paths).status();
        if (status == WorkflowStatus.COMPLETED) {
            throw new IllegalStateException("workflow is already completed; " + action + " is not allowed");
        }
    }

    private static WorkstreamPaths paths(Path root, String workstreamId) {
        return new WorkstreamPaths(root, new DualId(workstreamId));
    }

    private static Path normalize(Path workspaceRoot) {
        return workspaceRoot.toAbsolutePath().normalize();
    }
}
