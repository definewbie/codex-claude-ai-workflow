package dev.dual.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceRegistryController {
    private final WorkspaceRegistryService registry;

    public WorkspaceRegistryController(WorkspaceRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<String, Object> list() throws Exception {
        return registry.list();
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterWorkspaceRequest request) throws Exception {
        if (request == null || request.repoRoot() == null || request.repoRoot().isBlank()) {
            throw new IllegalArgumentException("repoRoot is required");
        }
        return registry.register(Path.of(request.repoRoot()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handle(Exception error) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "" : error.getMessage()
        ));
    }

    public record RegisterWorkspaceRequest(String repoRoot) {
    }
}
