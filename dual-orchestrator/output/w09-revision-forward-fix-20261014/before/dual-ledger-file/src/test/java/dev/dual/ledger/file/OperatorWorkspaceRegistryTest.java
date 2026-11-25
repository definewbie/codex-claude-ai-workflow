package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorWorkspaceRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void registersMultipleProjectsAndWorkstreams() throws Exception {
        var repoA = gitRepo("alpha");
        var repoB = gitRepo("beta");
        var registry = new OperatorWorkspaceRegistry(tempDir.resolve("registry.json"));

        registry.registerProject(repoA);
        registry.registerProject(repoB);
        registry.registerWorkstream(
                repoA,
                tempDir.resolve("alpha-agent-runs/ws-1"),
                "ws-1",
                "Alpha task",
                "running",
                Instant.parse("2026-06-19T00:00:00Z")
        );

        var document = registry.read();

        assertEquals(2, document.projects().size());
        assertEquals("beta", document.projects().get(0).name());
        var alpha = document.projects().stream()
                .filter(project -> project.name().equals("alpha"))
                .findFirst()
                .orElseThrow();
        assertEquals(OperatorWorkspaceRegistry.defaultAgentRunsDir(repoA).toString(), alpha.agentRunsDir());
        assertEquals(1, alpha.workstreams().size());
        assertEquals("ws-1", alpha.workstreams().get(0).workstreamId());
    }

    @Test
    void rejectsNonGitDirectory() throws Exception {
        var registry = new OperatorWorkspaceRegistry(tempDir.resolve("registry.json"));
        var plainDir = tempDir.resolve("plain");
        Files.createDirectories(plainDir);

        var error = assertThrows(IllegalArgumentException.class, () -> registry.registerProject(plainDir));

        assertTrue(error.getMessage().contains("git repository"));
    }

    private Path gitRepo(String name) throws Exception {
        var repo = tempDir.resolve(name);
        Files.createDirectories(repo.resolve(".git"));
        return repo;
    }
}
