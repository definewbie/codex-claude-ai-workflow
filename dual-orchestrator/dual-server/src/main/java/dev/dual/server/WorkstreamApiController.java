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
@RequestMapping("/api/workstreams/{workstreamId}")
public class WorkstreamApiController {
    private final WorkstreamQueryService queryService;
    private final WorkstreamActionService actionService;

    public WorkstreamApiController(WorkstreamQueryService queryService, WorkstreamActionService actionService) {
        this.queryService = queryService;
        this.actionService = actionService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = ".") Path workdir
    ) throws Exception {
        return queryService.status(workdir, workstreamId);
    }

    @GetMapping("/timeline")
    public Map<String, Object> timeline(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = ".") Path workdir
    ) throws Exception {
        return queryService.timeline(workdir, workstreamId);
    }

    @PostMapping("/resume")
    public Map<String, Object> resume(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = ".") Path workdir,
            @RequestParam(defaultValue = "1") int maxSteps
    ) throws Exception {
        return actionService.resume(workdir, workstreamId, maxSteps);
    }

    @PostMapping("/retry")
    public Map<String, Object> retry(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = ".") Path workdir,
            @RequestBody(required = false) RetryRequest request
    ) throws Exception {
        return actionService.retry(workdir, workstreamId, request == null ? "" : request.note());
    }

    @PostMapping("/review/approve")
    public Map<String, Object> approve(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = ".") Path workdir,
            @RequestParam(defaultValue = "false") boolean run,
            @RequestParam(defaultValue = "20") int maxSteps
    ) throws Exception {
        return actionService.approve(workdir, workstreamId, run, maxSteps);
    }

    @PostMapping("/review/revise")
    public Map<String, Object> revise(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = ".") Path workdir,
            @RequestParam(defaultValue = "false") boolean run,
            @RequestParam(defaultValue = "20") int maxSteps,
            @RequestBody(required = false) ReviewReviseRequest request
    ) throws Exception {
        return actionService.revise(workdir, workstreamId, request == null ? "" : request.feedback(), run, maxSteps);
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

    public record ReviewReviseRequest(String feedback) {
    }
}
