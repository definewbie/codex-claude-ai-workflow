package dev.dual.server;

import dev.dual.ledger.file.OperatorWorkspaceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceRegistryServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void registersProjectAndReturnsLiveWorkstreamProjection() throws Exception {
        var repo = tempDir.resolve("sample-repo");
        Files.createDirectories(repo.resolve(".git"));
        var registry = new OperatorWorkspaceRegistry(tempDir.resolve("operator-workspaces.json"));
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var service = new WorkspaceRegistryService(registry, new WorkstreamQueryService(objectMapper, new DeliveryViewService(objectMapper)));

        var response = service.register(repo);
        var projects = projects(response);
        var project = projects.get(0);

        assertEquals("sample-repo", project.get("name"));
        assertEquals(repo.toAbsolutePath().normalize().toString(), project.get("repoRoot"));
        assertTrue(String.valueOf(project.get("agentRunsDir")).endsWith("sample-repo-agent-runs"));
        assertEquals(true, project.get("available"));
        assertEquals(0, ((Number) project.get("workstreamCount")).intValue());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> projects(Map<String, Object> response) {
        return (List<Map<String, Object>>) response.get("projects");
    }
}
