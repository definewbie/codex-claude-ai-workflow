package dev.dual.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/workstreams")
public class WorkstreamApiController {
    private final WorkstreamQueryService queryService;
    private final WorkstreamActionService actionService;

    public WorkstreamApiController(WorkstreamQueryService queryService, WorkstreamActionService actionService) {
        this.queryService = queryService;
        this.actionService = actionService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.list(workdirPath(workdir));
    }

    public Map<String, Object> status(
            String workstreamId,
            Path workdir
    ) throws Exception {
        return queryService.status(workdir, workstreamId);
    }

    @GetMapping("/{workstreamId}/status")
    public Map<String, Object> statusByPath(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.status(workdirPath(workdir), workstreamId);
    }

    @GetMapping("/{workstreamId}/timeline")
    public Map<String, Object> timeline(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.timeline(workdirPath(workdir), workstreamId);
    }

    @GetMapping("/{workstreamId}/overview")
    public Map<String, Object> overview(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.overview(workdirPath(workdir), workstreamId);
    }

    @GetMapping("/{workstreamId}/review/current")
    public Map<String, Object> currentReview(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.currentReview(workdirPath(workdir), workstreamId);
    }

    @GetMapping("/{workstreamId}/recovery/current")
    public Map<String, Object> currentRecovery(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.currentRecovery(workdirPath(workdir), workstreamId);
    }

    @PostMapping("/{workstreamId}/resume")
    public Map<String, Object> resume(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestParam(defaultValue = "1") int maxSteps
    ) throws Exception {
        return actionService.resume(workdirPath(workdir), workstreamId, maxSteps);
    }

    @PostMapping("/{workstreamId}/retry")
    public Map<String, Object> retry(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestBody(required = false) RetryRequest request
    ) throws Exception {
        return actionService.retry(workdirPath(workdir), workstreamId, request == null ? "" : request.note());
    }

    @PostMapping("/{workstreamId}/recovery/close-mistaken-iteration")
    public Map<String, Object> closeMistakenIteration(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestBody(required = false) RecoveryCloseRequest request
    ) throws Exception {
        return actionService.closeMistakenIteration(workdirPath(workdir), workstreamId, request == null ? "" : request.note());
    }

    @PostMapping("/{workstreamId}/recovery/stale")
    public Map<String, Object> recoverStaleRunning(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestBody(required = false) RecoveryStaleRequest request
    ) throws Exception {
        return actionService.recoverStaleRunning(workdirPath(workdir), workstreamId, request == null ? "" : request.note());
    }

    @PostMapping("/{workstreamId}/review/approve")
    public Map<String, Object> approve(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestParam(defaultValue = "false") boolean run,
            @RequestParam(defaultValue = "20") int maxSteps
    ) throws Exception {
        return actionService.approve(workdirPath(workdir), workstreamId, run, maxSteps);
    }

    @PostMapping("/{workstreamId}/review/complete")
    public Map<String, Object> complete(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestBody(required = false) ReviewCompleteRequest request
    ) throws Exception {
        return actionService.complete(workdirPath(workdir), workstreamId, request == null ? "" : request.note());
    }

    @PostMapping("/{workstreamId}/review/revise")
    public Map<String, Object> revise(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestParam(defaultValue = "false") boolean run,
            @RequestParam(defaultValue = "20") int maxSteps,
            @RequestBody(required = false) ReviewReviseRequest request
    ) throws Exception {
        return actionService.revise(workdirPath(workdir), workstreamId, request == null ? "" : request.feedback(), run, maxSteps);
    }

    private static Path workdirPath(String workdir) {
        if (workdir == null || workdir.isBlank()) {
            return Path.of(System.getProperty("user.dir"));
        }
        return Path.of(workdir);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception error) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "" : error.getMessage()
        ));
    }

    public record RetryRequest(String note) {
    }

    public record RecoveryCloseRequest(String note) {
    }

    public record RecoveryStaleRequest(String note) {
    }

    public record ReviewReviseRequest(String feedback) {
    }

    public record ReviewCompleteRequest(String note) {
    }
}
