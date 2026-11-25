package dev.dual.server;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceBrowseController {
    @GetMapping("/browse")
    public Map<String, Object> browse(@RequestParam(defaultValue = "") String path) throws IOException {
        var current = currentPath(path);
        if (!Files.isDirectory(current)) {
            throw new IOException("not a directory: " + current);
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("path", current.toString());
        response.put("parent", current.getParent() == null ? "" : current.getParent().toString());
        try (var stream = Files.list(current)) {
            response.put("directories", stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(pathValue -> pathValue.getFileName().toString().toLowerCase()))
                    .limit(200)
                    .map(this::directoryItem)
                    .toList());
        }
        return response;
    }

    private Path currentPath(String path) {
        if (path == null || path.isBlank()) {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }
        return Path.of(path).toAbsolutePath().normalize();
    }

    private Map<String, Object> directoryItem(Path path) {
        return Map.of(
                "name", path.getFileName().toString(),
                "path", path.toAbsolutePath().normalize().toString()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception error) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", error.getClass().getSimpleName(),
                "message", error.getMessage() == null ? "" : error.getMessage()
        ));
    }
}
