package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContractArtifactWriterTest {

    private static TaskContract sampleContract() {
        return new TaskContract(
                "tc-test-v1",
                1,
                Instant.parse("2026-05-30T12:00:00Z"),
                "Implement user login feature",
                new TaskContract.Scope(List.of("src/auth/Login.java"), List.of("legacy/")),
                List.of("Login page renders", "Authentication works"),
                List.of(),
                List.of("Max 200 lines per file"),
                List.of("SSO integration"),
                List.of("Token expiry edge case"),
                List.of("Error codes not specified"),
                List.of("Which auth provider?"),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "contract.compile")
        );
    }

    @Test
    void writeJsonArtifact(@TempDir Path tempDir) throws Exception {
        var contract = sampleContract();
        var artifactsDir = tempDir.resolve("artifacts");

        TaskContractArtifactWriter.writeJson(contract, artifactsDir);

        var jsonPath = artifactsDir.resolve("task-contract.json");
        assertTrue(Files.exists(jsonPath), "task-contract.json should exist");

        var json = Files.readString(jsonPath, StandardCharsets.UTF_8);
        assertTrue(json.contains("tc-test-v1"), "JSON should contain contract id");
        assertTrue(json.contains("Implement user login feature"), "JSON should contain objective");
        assertTrue(json.contains("READY_FOR_REVIEW"), "JSON should contain status");
        assertTrue(json.contains("src/auth/Login.java"), "JSON should contain scope included");
        assertTrue(json.contains("2026-05-30T12:00:00Z"), "JSON should contain created_at");

        // Verify round-trip deserialization
        var deserialized = TaskContractArtifactWriter.mapper()
                .readValue(jsonPath.toFile(), TaskContract.class);
        assertEquals(contract.id(), deserialized.id());
        assertEquals(contract.objective(), deserialized.objective());
        assertEquals(contract.status(), deserialized.status());
    }

    @Test
    void writeMarkdownArtifact(@TempDir Path tempDir) throws Exception {
        var contract = sampleContract();
        var artifactsDir = tempDir.resolve("artifacts");

        TaskContractArtifactWriter.writeMarkdown(contract, artifactsDir);

        var mdPath = artifactsDir.resolve("task-contract.md");
        assertTrue(Files.exists(mdPath), "task-contract.md should exist");

        var md = Files.readString(mdPath, StandardCharsets.UTF_8);
        assertTrue(md.contains("# Task Contract"), "Markdown should have title");
        assertTrue(md.contains("## Objective"), "Markdown should have objective section");
        assertTrue(md.contains("Implement user login feature"), "Markdown should contain objective text");
        assertTrue(md.contains("## Scope"), "Markdown should have scope section");
        assertTrue(md.contains("## Acceptance Criteria"), "Markdown should have AC section");
        assertTrue(md.contains("- [ ] Login page renders"), "Markdown should have checkbox AC");
        assertTrue(md.contains("## Constraints"), "Markdown should have constraints section");
        assertTrue(md.contains("Max 200 lines per file"), "Markdown should contain constraint");
        assertTrue(md.contains("## Non-Goals"), "Markdown should have non-goals section");
        assertTrue(md.contains("SSO integration"), "Markdown should contain non-goal");
        assertTrue(md.contains("## Risk Areas"), "Markdown should have risk areas section");
        assertTrue(md.contains("## Ambiguity Notes"), "Markdown should have ambiguity notes section");
        assertTrue(md.contains("## Human Questions"), "Markdown should have human questions section");
        assertTrue(md.contains("## Source"), "Markdown should have source section");
        assertTrue(md.contains("contract.compile"), "Markdown should contain created by");
        assertFalse(md.contains("## Test Requirements"), "Should not include Test Requirements since we removed it from sample");
    }
}
