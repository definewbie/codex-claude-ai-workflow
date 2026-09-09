package dev.dual.server;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/workstreams")
public class WorkstreamApiController {
    private final WorkstreamQueryService queryService;
    private final WorkstreamActionService actionService;
    private final WorkstreamStreamService streamService;
    private final DeliveryViewService deliveryViewService;

    public WorkstreamApiController(
            WorkstreamQueryService queryService,
            WorkstreamActionService actionService,
            WorkstreamStreamService streamService,
            DeliveryViewService deliveryViewService
    ) {
        this.queryService = queryService;
        this.actionService = actionService;
        this.streamService = streamService;
        this.deliveryViewService = deliveryViewService;
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

    @GetMapping(path = "/{workstreamId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestParam(defaultValue = "2000") long intervalMs
    ) {
        return streamService.overviewStream(workdirPath(workdir), workstreamId, intervalMs);
    }

    @GetMapping("/{workstreamId}/review/current")
    public Map<String, Object> currentReview(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.currentReview(workdirPath(workdir), workstreamId);
    }

    @GetMapping("/{workstreamId}/review/{nodeInstanceId}")
    public Map<String, Object> review(
            @PathVariable String workstreamId,
            @PathVariable String nodeInstanceId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.review(workdirPath(workdir), workstreamId, nodeInstanceId);
    }

    @GetMapping("/{workstreamId}/recovery/current")
    public Map<String, Object> currentRecovery(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return queryService.currentRecovery(workdirPath(workdir), workstreamId);
    }

    @GetMapping("/{workstreamId}/delivery")
    public Map<String, Object> delivery(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) {
        return deliveryViewService.view(workdirPath(workdir), workstreamId);
    }

    @PostMapping("/{workstreamId}/delivery/merge")
    public Map<String, Object> deliveryMerge(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir
    ) throws Exception {
        return deliveryViewService.merge(workdirPath(workdir), workstreamId);
    }

    @PostMapping("/{workstreamId}/delivery/finalize")
    public Map<String, Object> deliveryFinalize(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestBody DeliveryFinalizeRequest request
    ) throws Exception {
        if (request == null || request.commit() == null || request.commit().isBlank()) {
            throw new IllegalArgumentException("delivery commit is required");
        }
        return deliveryViewService.finalizeCommit(workdirPath(workdir), workstreamId, request.commit());
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

    @PostMapping("/{workstreamId}/recovery/approve-websearch")
    public Map<String, Object> approvePublicWebSearch(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestBody PublicWebSearchApprovalRequest request
    ) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("public WebSearch approval request is required");
        }
        return actionService.approvePublicWebSearch(
                workdirPath(workdir),
                workstreamId,
                request.note(),
                request.publicRepositoryAttested());
    }

    @PostMapping("/{workstreamId}/recovery/network")
    public Map<String, Object> networkAuthority(@PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir, @RequestBody NetworkApprovalRequest request) throws Exception {
        if (request == null) throw new IllegalArgumentException("network authority request is required");
        return actionService.networkAuthority(workdirPath(workdir), workstreamId, request.target(), request.domains(), request.note(), request.cancel());
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
            @RequestParam(defaultValue = "20") int maxSteps,
            @RequestBody(required = false) ReviewApproveRequest request
    ) throws Exception {
        return actionService.approve(
                workdirPath(workdir), workstreamId, request == null ? "" : request.note(), run, maxSteps);
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

    @PostMapping("/{workstreamId}/review/request-scope-revision")
    public Map<String, Object> requestScopeRevision(
            @PathVariable String workstreamId,
            @RequestParam(defaultValue = "") String workdir,
            @RequestParam(defaultValue = "false") boolean run,
            @RequestParam(defaultValue = "20") int maxSteps,
            @RequestBody(required = false) ReviewReviseRequest request
    ) throws Exception {
        return actionService.requestScopeRevision(workdirPath(workdir), workstreamId,
                request == null ? "" : request.feedback(), run, maxSteps);
    }

    private static Path workdirPath(String workdir) {
        if (workdir == null || workdir.isBlank()) {
            return Path.of(System.getProperty("user.dir"));
        }
        return Path.of(workdir);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout() {
        // SSE clients can disappear during browser refresh or application shutdown.
        // The response is already text/event-stream, so returning a JSON error would
        // cause a secondary HttpMessageNotWritableException.
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handle(
            Exception error,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (isStreamRequest(request, response)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "" : error.getMessage()
        ));
    }

    private static boolean isStreamRequest(HttpServletRequest request, HttpServletResponse response) {
        return isStreamRequest(
                request.getRequestURI(),
                request.getHeader("Accept"),
                response.getContentType()
        );
    }

    static boolean isStreamRequest(String requestUri, String accept, String responseContentType) {
        if (responseContentType != null && responseContentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        return requestUri != null && requestUri.endsWith("/stream");
    }

    public record RetryRequest(String note) {
    }

    public record RecoveryCloseRequest(String note) {
    }

    public record RecoveryStaleRequest(String note) {
    }

    public record PublicWebSearchApprovalRequest(String note, boolean publicRepositoryAttested) {
    }

    public record NetworkApprovalRequest(String target, java.util.List<String> domains, String note, boolean cancel) { }

    public record ReviewReviseRequest(String feedback) {
    }

    public record ReviewApproveRequest(String note) {
    }

    public record ReviewCompleteRequest(String note) {
    }

    public record DeliveryFinalizeRequest(String commit) {
    }
}
