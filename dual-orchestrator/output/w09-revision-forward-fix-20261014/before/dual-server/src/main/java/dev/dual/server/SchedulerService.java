package dev.dual.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

@Service
public class SchedulerService {
    private final WorkstreamQueryService queryService;
    private final WorkstreamActionService actionService;
    private final SchedulerPolicy schedulerPolicy;

    @Autowired
    public SchedulerService(WorkstreamQueryService queryService, WorkstreamActionService actionService) {
        this(queryService, actionService, new SchedulerPolicy());
    }

    SchedulerService(WorkstreamQueryService queryService, WorkstreamActionService actionService, SchedulerPolicy schedulerPolicy) {
        this.queryService = queryService;
        this.actionService = actionService;
        this.schedulerPolicy = schedulerPolicy;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> tick(Path workspaceRoot, int maxWorkstreams, int maxStepsPerWorkstream) throws Exception {
        return tick(workspaceRoot, maxWorkstreams, maxStepsPerWorkstream, false);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> tick(
            Path workspaceRoot,
            int maxWorkstreams,
            int maxStepsPerWorkstream,
            boolean autoRecover
    ) throws Exception {
        validatePositive(maxWorkstreams, "maxWorkstreams");
        validatePositive(maxStepsPerWorkstream, "maxStepsPerWorkstream");

        var summaries = (java.util.List<Map<String, Object>>) queryService.list(workspaceRoot).get("workstreams");
        var items = new ArrayList<SchedulerWorkstreamResult>();
        for (var summary : summaries) {
            if (items.size() >= maxWorkstreams) {
                break;
            }
            items.add(tickWorkstream(workspaceRoot, summary, maxStepsPerWorkstream, autoRecover));
        }
        return new SchedulerTickResult(workspaceRoot, maxWorkstreams, maxStepsPerWorkstream, autoRecover, items).toMap();
    }

    private SchedulerWorkstreamResult tickWorkstream(
            Path workspaceRoot,
            Map<String, Object> summary,
            int maxStepsPerWorkstream,
            boolean autoRecover
    ) throws Exception {
        var workstreamId = text(summary.get("workstreamId"));
        var statusSource = text(summary.get("statusSource"));
        if (!"workflow_current".equals(statusSource)) {
            return SchedulerWorkstreamResult.skipped(workstreamId, "canonical workflow current is unavailable");
        }
        var status = text(summary.get("status"));
        var assessment = recoveryAssessment(workspaceRoot, workstreamId, status, autoRecover);
        var decision = schedulerPolicy.decide(status, autoRecover, assessment);
        return switch (decision.action()) {
            case RESUME -> resume(workspaceRoot, workstreamId, maxStepsPerWorkstream);
            case AUTO_RECOVER -> autoRecover(workspaceRoot, workstreamId, maxStepsPerWorkstream, decision);
            case SKIP -> SchedulerWorkstreamResult.skipped(workstreamId, decision.reason());
        };
    }

    private Map<String, Object> recoveryAssessment(
            Path workspaceRoot,
            String workstreamId,
            String status,
            boolean autoRecover
    ) {
        if (!autoRecover || !"WAITING_FOR_RECOVERY".equals(status)) {
            return Map.of();
        }
        return queryService.recoveryAssessment(workspaceRoot, workstreamId);
    }

    private SchedulerWorkstreamResult resume(Path workspaceRoot, String workstreamId, int maxStepsPerWorkstream) throws Exception {
        var runResult = actionService.resume(workspaceRoot, workstreamId, maxStepsPerWorkstream);
        return SchedulerWorkstreamResult.resumed(workstreamId, runResult);
    }

    private SchedulerWorkstreamResult autoRecover(
            Path workspaceRoot,
            String workstreamId,
            int maxStepsPerWorkstream,
            SchedulerDecision decision
    ) throws Exception {
        var retryResult = actionService.retry(
                workspaceRoot,
                workstreamId,
                "auto recovery retry from scheduler"
        );
        var runResult = actionService.resume(workspaceRoot, workstreamId, maxStepsPerWorkstream);
        return SchedulerWorkstreamResult.autoRecovered(workstreamId, decision, retryResult, runResult);
    }

    private static void validatePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
