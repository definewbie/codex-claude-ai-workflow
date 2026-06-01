package dev.dual.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/workstreams/{workstreamId}")
public class WorkstreamApiController {
    private final WorkstreamQueryService queryService;

    public WorkstreamApiController(WorkstreamQueryService queryService) {
        this.queryService = queryService;
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception error) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "" : error.getMessage()
        ));
    }
}
