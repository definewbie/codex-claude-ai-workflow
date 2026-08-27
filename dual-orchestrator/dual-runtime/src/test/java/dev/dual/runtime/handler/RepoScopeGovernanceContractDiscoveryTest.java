package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoScopeGovernanceContractDiscoveryTest {
    @TempDir
    Path root;

    @Test
    void coldStartProjectsTrackedInventoryWithoutSelectingFinalScope() throws Exception {
        Files.createDirectories(root.resolve("src/main/java/example"));
        Files.createDirectories(root.resolve("src/test/java/example"));
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        Files.writeString(root.resolve("src/main/java/example/MailService.java"), "class MailService {}\n");
        Files.writeString(root.resolve("src/test/java/example/MailServiceTest.java"), "class MailServiceTest {}\n");
        Files.createDirectories(root.resolve("docs"));
        for (var index = 0; index < 30; index++) {
            Files.writeString(root.resolve("docs/fact-%02d.md".formatted(index)), "fact\n");
        }
        git("init");
        git("config", "user.email", "dual@example.test");
        git("config", "user.name", "Dual Test");
        git("add", ".");
        git("commit", "-m", "baseline");
        Files.writeString(root.resolve("untracked.tmp"), "not authoritative\n");

        var workstreamDir = root.resolve(".agent/workstreams/ws-cold");
        Files.createDirectories(workstreamDir);
        var definition = new NodeDefinition(
                "intake.claude_brainstorm",
                NodeKind.TASK,
                "acpx.agent_run",
                Map.of("permission_profile", "contract_discovery"));
        var context = new TaskHandler.TaskContext(
                root,
                "ws-cold",
                definition,
                new NodeInstance(
                        "node-intake",
                        definition.id(),
                        definition.kind(),
                        definition.handlerType(),
                        NodeStatus.RUNNING,
                        "intake",
                        "iter-001",
                        List.of(),
                        List.of(),
                        Map.of(),
                        null,
                        null,
                        null));

        var documents = RepoScopeGovernance.build(
                root,
                workstreamDir,
                context,
                new ObjectMapper().registerModule(new JavaTimeModule()));

        assertEquals(33, documents.repoMap().get("inventoryPathCount"));
        assertEquals(33, documents.repoMap().get("fileCount"));
        assertFalse(String.valueOf(documents.repoMap().get("inventoryHead")).isBlank());
        assertEquals(64, String.valueOf(documents.repoMap().get("inventorySha256")).length());
        assertTrue(documents.repoMap().toString().contains("src/main/java/example/MailService.java"));
        assertFalse(documents.repoMap().toString().contains("untracked.tmp"));
        assertEquals(24, ((List<?>) documents.plan().get("readCandidates")).size());
        assertEquals(List.of(), documents.plan().get("contractScopeCandidates"));
        assertTrue(documents.agentCapabilityCatalog().toString().contains("direct_file_read"));

        var guidance = new StringBuilder();
        RepoScopeGovernance.appendMarkdownGuidance(guidance, documents, root, true);
        assertTrue(guidance.toString().contains("repoMap=.agent/workstreams/ws-cold/memory/repo-map.json"));
        assertTrue(guidance.toString().contains("Tracked inventory: count=33"));
    }

    private void git(String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }
}
