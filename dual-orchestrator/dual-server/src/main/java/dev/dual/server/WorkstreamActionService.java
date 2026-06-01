package dev.dual.server;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.WorkflowCommand;
import dev.dual.runtime.WorkflowRuntimeResult;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    public WorkstreamActionService(ServerWorkflowRuntimeFactory runtimeFactory) {
        this(new WorkflowFileLedger(), runtimeFactory::create);
    }

    WorkstreamActionService(WorkflowFileLedger ledger, Supplier<WorkflowResumer> resumerFactory) {
        this.ledger = ledger;
        this.resumerFactory = resumerFactory;
    }

    public Map<String, Object> resume(Path workspaceRoot, String workstreamId, int maxSteps) throws Exception {
        var root = normalize(workspaceRoot);
        validateMaxSteps(maxSteps);
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api resume"))) {
            return runLoop(root, workstreamId, maxSteps);
        }
    }

    public Map<String, Object> retry(Path workspaceRoot, String workstreamId, String note) throws Exception {
        var root = normalize(workspaceRoot);
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api retry"))) {
            ledger.retryActiveNode(paths, note);
        }
        return actionResponse("retry", workstreamId, "active node marked ready for retry");
    }

    public Map<String, Object> approve(Path workspaceRoot, String workstreamId, boolean runImmediately, int maxSteps) throws Exception {
        var root = normalize(workspaceRoot);
        resolveGate(root, workstreamId, "approved", "approved from API");
        if (runImmediately) {
            var response = resume(root, workstreamId, maxSteps);
            response.put("action", "review.approve.run");
            return response;
        }
        return actionResponse("review.approve", workstreamId, "human gate approved");
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
        return actionResponse("review.revise", workstreamId, "human gate revision requested");
    }

    private void resolveGate(Path root, String workstreamId, String action, String note) throws Exception {
        var paths = paths(root, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api review " + action))) {
            ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), action, note);
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

    private static WorkstreamPaths paths(Path root, String workstreamId) {
        return new WorkstreamPaths(root, new DualId(workstreamId));
    }

    private static Path normalize(Path workspaceRoot) {
        return workspaceRoot.toAbsolutePath().normalize();
    }
}
