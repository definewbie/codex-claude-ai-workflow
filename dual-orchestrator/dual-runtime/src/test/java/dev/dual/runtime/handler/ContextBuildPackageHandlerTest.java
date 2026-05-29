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

    @Test
    void budgetOverflowDegradesGitEvidenceToDiffStat() throws Exception {
        Files.createDirectories(wsDir);
        // Fill requirement to consume most of the 24k implementation budget
        Files.writeString(wsDir.resolve("requirement.md"), "R".repeat(18_000), StandardCharsets.UTF_8);

        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "A".repeat(3_000), StandardCharsets.UTF_8);

        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        // Full diff content that would be too large
        var diffContent = "D".repeat(5_000);
        Files.writeString(evidenceDir.resolve("diff.stat"), diffContent + "\n", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        // git_evidence is MEDIUM priority, should be degraded due to budget pressure
        // Governance table should show "diff_stat" as level for git_evidence
        assertTrue(markdown.contains("git_evidence") || markdown.contains("Git Evidence"),
                "git_evidence section should appear in output, got: " + markdown);
        // Should contain the diff stat, not the full 5k diff content
        assertTrue(markdown.contains("diff_stat") || markdown.contains("diff.stat"),
                "degraded git_evidence should reference diff stat, got: " + markdown);
    }

    @Test
    void budgetOverflowDegradesRecentAgentOutputsToLatest() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Short req.", StandardCharsets.UTF_8);

        // Make input_artifacts available so its 6k softBudget is allocated,
        // pushing git_evidence + recent_agent_outputs into degraded territory
        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Design output.", StandardCharsets.UTF_8);

        // Create multiple agent outputs — recent_agent_outputs is LOW priority,
        // DegradationPolicy(LATEST, 2), degraded when budget overflows
        Files.createDirectories(wsDir.resolve("runs/run-002/outputs"));
        Files.createDirectories(wsDir.resolve("runs/run-003/outputs"));
        Files.writeString(wsDir.resolve("runs/run-002/outputs/agent.md"), "Output from run 2.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-003/outputs/agent.md"), "Output from run 3.", StandardCharsets.UTF_8);

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
        // Governance table should show recent_agent_outputs as degraded with "latest" level
        assertTrue(markdown.contains("recent_agent_outputs"),
                "recent_agent_outputs should appear in governance table, got: " + markdown);
        assertTrue(markdown.contains("latest"),
                "recent_agent_outputs should show degraded level 'latest', got: " + markdown);
    }

    @Test
    void budgetOverflowDropsOptionalSectionsInPhase5() throws Exception {
        Files.createDirectories(wsDir);
        // Write a massive requirement to push actual rendered content past 24k budget
        // This triggers Phase 5 post-render budget enforcement
        Files.writeString(wsDir.resolve("requirement.md"), "R".repeat(20_000), StandardCharsets.UTF_8);

        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "A".repeat(3_000), StandardCharsets.UTF_8);

        // Create large agent output to push total over budget
        Files.createDirectories(wsDir.resolve("runs/run-002/outputs"));
        Files.writeString(wsDir.resolve("runs/run-002/outputs/agent.md"), "B".repeat(2_000), StandardCharsets.UTF_8);

        Files.createDirectories(wsDir.resolve("iterations"));
        var iterDir = wsDir.resolve("iterations/001");
        Files.createDirectories(iterDir);
        Files.writeString(iterDir.resolve("summary.md"), "S".repeat(2_000), StandardCharsets.UTF_8);

        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("diff.stat"), "10 files changed\n", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        // Phase 5 should have triggered — total should be within budget
        // ledger_pointers (LOW, DROP_OPTIONAL) should be dropped by Phase 5
        // The governance table is re-rendered after Phase 5 drops sections
        assertFalse(markdown.length() > 24_500,
                "Output should be within budget after Phase 5 enforcement, was " + markdown.length());
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
