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
        var context = new TaskHandler.TaskContext(root, "ws-cold", definition, new NodeInstance(
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
                        null), dev.dual.runtime.RuntimeTestLedger.ledger());

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
        assertTrue(guidance.toString().contains(
                "repoMap=.agent/workstreams/ws-cold/artifacts/context-governance/occurrences/node-intake/repo-map.json"));
        assertTrue(guidance.toString().contains("Tracked inventory: count=33"));
        var binding = RepositoryFactBinding.verify(
                root,
                "ws-cold",
                documents.repositoryFactBinding().ref(),
                documents.repositoryFactBinding().sha256(),
                "node-intake",
                "",
                new ObjectMapper().registerModule(new JavaTimeModule()));
        assertTrue(binding.valid(), binding.errors().toString());
    }

    @Test
    void occurrenceBindingsSurviveSiblingRunsAndRejectGlobalFallbackOrTampering() throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/Main.java"), "class Main {}\n");
        git("init");
        git("config", "user.email", "dual@example.test");
        git("config", "user.name", "Dual Test");
        git("add", ".");
        git("commit", "-m", "baseline");

        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var workstreamDir = root.resolve(".agent/workstreams/ws-cold");
        Files.createDirectories(workstreamDir);
        var definition = new NodeDefinition(
                "intake.claude_brainstorm",
                NodeKind.TASK,
                "acpx.agent_run",
                Map.of("permission_profile", "contract_discovery"));
        var context = new TaskHandler.TaskContext(root, "ws-cold", definition, new NodeInstance(
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
                        null), dev.dual.runtime.RuntimeTestLedger.ledger());

        var first = RepoScopeGovernance.build(root, workstreamDir, context, mapper, java.util.Optional.empty(), "run-1");
        var firstBindingBytes = Files.readAllBytes(first.repositoryFactBinding().path());
        var firstRepoMap = root.resolve(first.repositoryFactBinding().document().artifacts().get("repoMap").ref());
        var firstRepoMapBytes = Files.readAllBytes(firstRepoMap);

        var second = RepoScopeGovernance.build(root, workstreamDir, context, mapper, java.util.Optional.empty(), "run-2");

        assertFalse(first.repositoryFactBinding().ref().equals(second.repositoryFactBinding().ref()));
        assertEquals(List.of("run-1", "run-2"), List.of(
                first.repositoryFactBinding().document().runId(),
                second.repositoryFactBinding().document().runId()));
        assertTrue(java.util.Arrays.equals(firstBindingBytes, Files.readAllBytes(first.repositoryFactBinding().path())));
        assertTrue(java.util.Arrays.equals(firstRepoMapBytes, Files.readAllBytes(firstRepoMap)));
        assertTrue(RepositoryFactBinding.verify(
                root, "ws-cold", first.repositoryFactBinding().ref(), first.repositoryFactBinding().sha256(),
                "node-intake", "run-1", mapper).valid());
        assertTrue(RepositoryFactBinding.verify(
                root, "ws-cold", second.repositoryFactBinding().ref(), second.repositoryFactBinding().sha256(),
                "node-intake", "run-2", mapper).valid());

        var globalRepoMap = workstreamDir.resolve("memory/repo-map.json");
        Files.createDirectories(globalRepoMap.getParent());
        Files.writeString(globalRepoMap, "{\"files\":[]}");
        Files.delete(first.repositoryFactBinding().path());
        assertFalse(RepositoryFactBinding.verify(
                root, "ws-cold", first.repositoryFactBinding().ref(), first.repositoryFactBinding().sha256(),
                "node-intake", "run-1", mapper).valid(),
                "a mutable global projection must not replace a missing occurrence binding");

        Files.write(first.repositoryFactBinding().path(), firstBindingBytes);
        Files.writeString(firstRepoMap, "{\"tampered\":true}");
        var tampered = RepositoryFactBinding.verify(
                root, "ws-cold", first.repositoryFactBinding().ref(), first.repositoryFactBinding().sha256(),
                "node-intake", "run-1", mapper);
        assertFalse(tampered.valid());
        assertTrue(tampered.errors().stream().anyMatch(error -> error.contains("artifact sha256 does not match")),
                tampered.errors().toString());
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
