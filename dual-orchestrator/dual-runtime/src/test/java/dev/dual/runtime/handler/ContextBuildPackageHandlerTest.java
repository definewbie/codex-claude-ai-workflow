package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.BeforeEach;
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

class ContextBuildPackageHandlerTest {
    @TempDir
    Path tempDir;

    private Path wsDir;
    private final ContextBuildPackageHandler handler = new ContextBuildPackageHandler();

    @BeforeEach
    void setup() {
        wsDir = tempDir.resolve(".agent/workstreams/ws-ctx");
    }

    // --- Governed pipeline ---

    @Test
    void governedHappyPathRendersAllSections() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);
        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Design output.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("diff.stat"), "1 file changed\n", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Build the feature."));
        assertTrue(markdown.contains("Design output."));
        assertTrue(markdown.contains("Git Evidence"));
        assertTrue(markdown.contains("Context Governance"));
        assertTrue(markdown.contains("implementation"));
    }

    @Test
    void governedMissingOptionalSectionSkipsGracefully() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Minimal data.", StandardCharsets.UTF_8);
        // No runs dir → recent_agent_outputs is SKIP

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Minimal data."));
        assertTrue(markdown.contains("Context Governance"));
    }

    @Test
    void governedMissingRequiredSectionFails() throws Exception {
        Files.createDirectories(wsDir);
        // No requirement.md → requirement section has MissingPolicy.FAIL in implementation profile

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.FAILED, result.status());
        assertTrue(result.result().get("error").toString().contains("requirement"));
    }

    @Test
    void governedBudgetOverflowDegradesLowPriority() throws Exception {
        Files.createDirectories(wsDir);
        // Write a very large requirement to push budget
        var bigRequirement = "X".repeat(20_000);
        Files.writeString(wsDir.resolve("requirement.md"), bigRequirement, StandardCharsets.UTF_8);

        // product_review has 16k budget, requirement FAIL policy so it can't be dropped
        // This should trigger budget enforcement
        var result = handler.execute(ctx("product_review", List.of()));

        assertEquals(NodeStatus.FAILED, result.status());
        assertTrue(result.result().get("error").toString().contains("budget"));
    }

    @Test
    void governedProfileWithoutGitEvidenceSkipsGitSection() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "No git.", StandardCharsets.UTF_8);

        // architecture_review profile has no git_evidence section
        var result = handler.execute(ctx("architecture_review", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("No git."));
        assertTrue(markdown.contains("architecture_review"));
    }

    // --- Legacy path ---

    @Test
    void legacyPathWithIncludeSectionsOverride() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Legacy test.", StandardCharsets.UTF_8);
        var eventsFile = wsDir.resolve("events.jsonl");
        Files.createDirectories(eventsFile.getParent());
        Files.writeString(eventsFile, "", StandardCharsets.UTF_8);

        var def = new NodeDefinition("ctx.custom", NodeKind.TASK, "context.build_package", Map.of(
                "target_node", "next.node",
                "profile", "implementation",
                "include_sections", List.of("requirement", "ledger_pointers")
        ));
        var instance = new NodeInstance("node-001", "ctx.custom", NodeKind.TASK, "context.build_package",
                NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-ctx", def, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Legacy test."));
        assertTrue(markdown.contains("Context Contract"));
        assertTrue(markdown.contains("requirement, ledger_pointers"));
    }

    // --- Handoff.json consumption ---

    @Test
    void handoffJsonConsumedWhenPresent() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Handoff test.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var nextStageDir = wsDir.resolve("artifacts/next-stage");
        Files.createDirectories(nextStageDir);
        Files.writeString(nextStageDir.resolve("handoff.json"), """
                {"completedWork": ["Implemented auth module", "Wrote tests"], "openRisks": ["Token expiry", "Rate limit"]}
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Completed Work"));
        assertTrue(markdown.contains("Implemented auth module"));
        assertTrue(markdown.contains("Open Risks"));
        assertTrue(markdown.contains("Token expiry"));
    }

    // --- Degraded path tests ---

    @Test
    void summaryRenderingShowsArtifactNames() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Summary test.", StandardCharsets.UTF_8);
        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Design content here.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        // architecture_review renders input_artifacts (SUMMARY degradation policy)
        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("design.md"));
        assertTrue(markdown.contains("architecture_review"));
    }

    @Test
    void gitEvidenceSectionRendersWhenPresent() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Git evidence test.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.createDirectories(wsDir.resolve("runs"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("diff.stat"), "3 files changed, 50 insertions(+), 10 deletions(-)\n", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Git Evidence"));
        assertTrue(markdown.contains("diff.stat"));
    }

    @Test
    void handoffOpenRisksAndCompletedWorkRendered() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Handoff content test.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var nextStageDir = wsDir.resolve("artifacts/next-stage");
        Files.createDirectories(nextStageDir);
        Files.writeString(nextStageDir.resolve("handoff.json"), """
                {"completedWork": ["Built API", "Added tests"], "openRisks": ["Performance", "Security"]}
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Completed Work"));
        assertTrue(markdown.contains("Built API"));
        assertTrue(markdown.contains("Open Risks"));
        assertTrue(markdown.contains("Performance"));
    }

    // --- Helper ---

    private TaskHandler.TaskContext ctx(String profile, List<String> inputRefs) {
        return new TaskHandler.TaskContext(
                tempDir, "ws-ctx",
                new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.node",
                        "profile", profile
                )),
                new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", inputRefs, List.of(), Map.of(), null, null, null)
        );
    }
}
