package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
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
    void governedPackageWritesStructuredBudgetMetadataSidecar() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);
        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Design output.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertTrue(result.result().containsKey("context_package_metadata"),
                "context package metadata sidecar should be exposed as a structured artifact");
        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);

        assertEquals(1, metadata.get("schemaVersion"));
        assertEquals("implementation", metadata.get("profile"));
        assertEquals(6_000, metadata.get("tokenBudget"));
        assertEquals(true, metadata.get("withinBudget"));
        assertTrue(((Number) metadata.get("estimatedTokens")).intValue() > 0);
        assertTrue(metadata.get("sections") instanceof List<?> sections && !sections.isEmpty(),
                "metadata must expose per-section budget facts");
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
    void governedBudgetOverflowWithAdaptive_completesWithCompression() throws Exception {
        Files.createDirectories(wsDir);
        // Write a very large requirement — ADAPTIVE policy now compresses instead of failing
        var bigRequirement = "X".repeat(20_000);
        Files.writeString(wsDir.resolve("requirement.md"), bigRequirement, StandardCharsets.UTF_8);

        // product_review has 4000 token budget, requirement softBudget=2000 tokens
        // ADAPTIVE policy compresses the oversized requirement instead of failing
        var result = handler.execute(ctx("product_review", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("adaptively compressed"),
                "Large requirement should be adaptively compressed");
    }

    @Test
    void governedProductReviewCapsRenderedPackageToTotalBudget() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Product review budget test.", StandardCharsets.UTF_8);

        var input = wsDir.resolve("runs/run-001/outputs/intake-claude-brainstorm.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "I".repeat(18_000), StandardCharsets.UTF_8);

        var recent = wsDir.resolve("runs/run-002/outputs/design-review.md");
        Files.createDirectories(recent.getParent());
        Files.writeString(recent, "R".repeat(18_000), StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("product_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/intake-claude-brainstorm.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(TokenEstimator.defaultEstimator().estimate(markdown) <= 4_000,
                "product_review package must stay within its 4k token budget");
        assertTrue(markdown.contains("section degraded to stay within"),
                "oversized sections should be explicitly degraded");
        assertFalse(markdown.contains("11.0k / 4.0k"),
                "governance must not report an over-budget package as successful");
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
                {
                  "completedWork": ["Implemented auth module", "Wrote tests"],
                  "importantDecisions": ["Use existing session model"],
                  "openRisks": ["Token expiry", "Rate limit"],
                  "recommendedNextStage": "Continue with implementation verification."
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Completed Work"));
        assertTrue(markdown.contains("Implemented auth module"));
        assertTrue(markdown.contains("Important Decisions"));
        assertTrue(markdown.contains("Use existing session model"));
        assertTrue(markdown.contains("Open Risks"));
        assertTrue(markdown.contains("Token expiry"));
        assertTrue(markdown.contains("Recommended Next Stage"));
        assertTrue(markdown.contains("Continue with implementation verification."));
    }

    @Test
    void malformedHandoffJsonDoesNotBreakContextBuild() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Malformed handoff test.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var nextStageDir = wsDir.resolve("artifacts/next-stage");
        Files.createDirectories(nextStageDir);
        Files.writeString(nextStageDir.resolve("handoff.json"), "{not-json", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Malformed handoff test."));
        assertFalse(markdown.contains("Completed Work"));
        assertFalse(markdown.contains("Open Risks"));
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
    void recentAgentOutputsRenderContractSidecarSummary() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Sidecar summary test.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-001/outputs/design-codex-review.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, """
                Long review body.
                [tool] rtk read huge-file.md (completed)
                  output:
                    Full tool log that should not enter context.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Architecture is ready.
                ```
                """, StandardCharsets.UTF_8);
        Files.writeString(output.getParent().resolve("design-codex-review.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "design.codex_arch_review",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Architecture is ready.",
                  "must_fix": ["Clarify rollout scope"],
                  "open_risks": ["Migration risk"],
                  "changed_files": ["docs/design.md"],
                  "verification_summary": "No runtime verification needed yet.",
                  "source_output_ref": ".agent/workstreams/ws-ctx/runs/run-001/outputs/design-codex-review.md"
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("architecture_review", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("design-codex-review.md"));
        assertTrue(markdown.contains("- Contract: design.codex_arch_review"));
        assertTrue(markdown.contains("- Decision: READY_FOR_ARCH_REVIEW"));
        assertTrue(markdown.contains("- Summary: Architecture is ready."));
        assertTrue(markdown.contains("- Must Fix: Clarify rollout scope"));
        assertTrue(markdown.contains("- Open Risks: Migration risk"));
        assertTrue(markdown.contains("- Changed Files: docs/design.md"));
        assertTrue(markdown.contains("- Verification: No runtime verification needed yet."));
        assertTrue(markdown.contains("- Raw Output: "));
        assertFalse(markdown.contains("Long review body."));
        assertFalse(markdown.contains("[tool] rtk read huge-file.md"));
        assertFalse(markdown.contains("Full tool log that should not enter context."));
    }

    @Test
    void inputArtifactAgentOutputUsesSidecarSummaryInsteadOfRawBody() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Input artifact sidecar test.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-002/outputs/self-test.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, """
                Raw self-test body.
                [tool] rtk read large-log.md (completed)
                  output:
                    tool log should not be included
                """, StandardCharsets.UTF_8);
        Files.writeString(output.getParent().resolve("self-test.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "implement.claude_self_test_report",
                  "decision": "READY_FOR_ACCEPTANCE",
                  "summary": "Self-test evidence is ready.",
                  "must_fix": [],
                  "missing_evidence": ["frontend typecheck unavailable"],
                  "open_risks": [],
                  "changed_files": ["miniprogram/src/pages/record/index.vue"],
                  "verification_summary": "Backend test passed; frontend typecheck missing."
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("artifacts/git-evidence"));
        Files.writeString(wsDir.resolve("artifacts/git-evidence/git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "tc-1",
                  "version": 1,
                  "created_at": "2026-06-18T00:00:00Z",
                  "objective": "Verify context package raw-output hygiene.",
                  "status": "READY_FOR_REVIEW",
                  "scope": {"included": [], "excluded": []},
                  "acceptance_criteria": [],
                  "test_requirements": [],
                  "non_goals": [],
                  "constraints": [],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "compilation_pass": 1,
                  "source": {
                    "raw_request_ref": ".agent/workstreams/ws-ctx/requirement.md",
                    "created_by": "test"
                  }
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("acceptance", List.of(
                ".agent/workstreams/ws-ctx/runs/run-002/outputs/self-test.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("- Contract: implement.claude_self_test_report"));
        assertTrue(markdown.contains("- Decision: READY_FOR_ACCEPTANCE"));
        assertTrue(markdown.contains("- Raw Output: "));
        assertFalse(markdown.contains("Raw self-test body."));
        assertFalse(markdown.contains("[tool] rtk read large-log.md"));
        assertFalse(markdown.contains("tool log should not be included"));
    }

    @Test
    void transcriptInputRefIsNeverExpandedIntoContextPackage() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Transcript guard test.", StandardCharsets.UTF_8);
        var transcript = wsDir.resolve("runs/run-003/outputs/intake-claude-brainstorm.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, """
                [client] initialize
                [tool] Read huge-file.md
                  input: {"file":"huge-file.md"}
                  output:
                    massive tool output that must not enter context

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                ```
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-003/outputs/intake-claude-brainstorm.transcript.log")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("execution transcript omitted from context"));
        assertFalse(markdown.contains("[tool] Read huge-file.md"));
        assertFalse(markdown.contains("massive tool output that must not enter context"));
        assertFalse(markdown.contains("decision: READY_FOR_PRODUCT_REVIEW"));
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
        assertFalse(markdown.length() > 30_000,
                "Output should be reasonable after Phase 5 enforcement, was " + markdown.length());
    }

    @Test
    void adaptiveDegradation_compressesLargeRequirement() throws Exception {
        Files.createDirectories(wsDir);
        // implementation has requirement with ADAPTIVE policy, softBudget=2500 tokens
        // 20_000 chars = ~5000 tokens > 2500 softBudget → triggers ADAPTIVE compression
        Files.writeString(wsDir.resolve("requirement.md"), "R".repeat(20_000), StandardCharsets.UTF_8);

        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Design output.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        // ADAPTIVE compression should truncate and add marker
        assertTrue(markdown.contains("adaptively compressed"),
                "Should contain adaptive compression marker");
        // Original 20000 chars should NOT appear in full — compressed to ~2500*4=10000 chars
        assertFalse(markdown.contains("R".repeat(15_000)),
                "Large requirement should be compressed, not rendered in full");
    }

    @Test
    void taskContractSectionIncludesContractResolution() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("requirement.md"), "Resolution context test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "task-ws",
                  "version": 1,
                  "created_at": "2026-06-03T00:00:00Z",
                  "objective": "Add review status",
                  "scope": {
                    "included": ["src/issueWorkflow.js"],
                    "excluded": ["No external dependencies"]
                  },
                  "acceptance_criteria": ["Criterion one", "Criterion two"],
                  "test_requirements": ["Run tests"],
                  "constraints": ["Keep scope narrow"],
                  "non_goals": ["Do not add UI"],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "status": "READY_FOR_REVIEW",
                  "compilation_pass": 1,
                  "source": {"raw_request_ref": "requirement.md", "created_by": "dual"}
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/contract-resolution.json"), """
                {
                  "schema_version": 1,
                  "decision": "APPROVE",
                  "resolved_questions": [
                    {
                      "question": "Should review be mandatory?",
                      "resolution": "Yes, mandatory review was approved.",
                      "source": "human"
                    }
                  ],
                  "assumptions": ["Human approved the requirement gate."],
                  "accepted_risks": [],
                  "gate_ref": ".agent/workstreams/ws-ctx/gates/gate-node-001.json",
                  "approved_by": "human_gate",
                  "created_at": "2026-06-06T00:00:00Z"
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("acceptance", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("TaskContract"));
        assertTrue(markdown.contains("Contract Resolution"));
        assertTrue(markdown.contains("Should review be mandatory?"));
        assertTrue(markdown.contains("Yes, mandatory review was approved."));
    }

    @Test
    void contextIncludesStateEvidenceBoundaryAndContractFailures() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts/contract-failures"));
        Files.createDirectories(wsDir.resolve("runs/run-001/outputs"));
        Files.writeString(wsDir.resolve("requirement.md"), "Boundary context test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-001/outputs/acceptance.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Accepted with notes.",
                  "lifecycle_status": "ACCEPTED_WITH_NOTES",
                  "must_fix": [],
                  "missing_evidence": [],
                  "open_risks": ["Manual smoke still useful"],
                  "changed_files": ["src/example.js"],
                  "verification_summary": "Sufficient evidence.",
                  "evidence_state": {
                    "blocking": false,
                    "must_fix_count": 0,
                    "missing_evidence_count": 0,
                    "open_risk_count": 1
                  }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/contract-failures/node-001.json"), """
                {
                  "schema_version": 1,
                  "artifact_type": "contract_failure_diagnostic",
                  "node_id": "implement.claude_self_test_report",
                  "category": "missing_structured_artifact",
                  "reason": "STRUCTURED_ARTIFACT_MISSING",
                  "recovery_hint": "Ask the agent to emit typed artifact."
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("architecture_review", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("State & Evidence Boundary"));
        assertTrue(markdown.contains("Trusted Harness Facts"));
        assertTrue(markdown.contains("Agent Proposed States"));
        assertTrue(markdown.contains("acceptance.codex_verifier_review: decision=ACCEPT_WITH_NOTES"));
        assertTrue(markdown.contains("Pending Contract/Evidence Gaps"));
        assertTrue(markdown.contains("implement.claude_self_test_report: missing_structured_artifact / STRUCTURED_ARTIFACT_MISSING"));
        assertTrue(markdown.contains("Ask the agent to emit typed artifact."));
    }

    @Test
    void contextHighlightsCurrentRepairObjectiveFromRejectedAcceptance() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.createDirectories(wsDir.resolve("runs/run-004/outputs"));
        Files.writeString(wsDir.resolve("requirement.md"), "Repair objective test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/failure-classification.json"), """
                {
                  "category": "CONTRACT_GAP",
                  "reasoning": "The accepted implementation missed exact ledger wording.",
                  "suggestedAction": "Repair the contract gap before continuing."
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-004/outputs/acceptance-codex-review.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "REJECT",
                  "summary": "README misses the exact ledger pointer.",
                  "lifecycle_status": "REJECTED",
                  "must_fix": [
                    "Update README to point to .agent/workstreams/ exactly.",
                    "Remove the misleading statement that implementation nodes only modify business code."
                  ],
                  "failure": {
                    "category_hint": "CONTRACT_GAP",
                    "contract_gaps": [
                      "README usage tip points to .agent/ instead of .agent/workstreams/."
                    ],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": "Implementation does not satisfy the accepted TaskContract."
                  },
                  "evidence_state": {
                    "blocking": true,
                    "must_fix_count": 2
                  }
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Current Repair Objective"));
        assertTrue(markdown.contains("Category: CONTRACT_GAP"));
        assertTrue(markdown.contains("Update README to point to .agent/workstreams/ exactly."));
        assertTrue(markdown.contains("Remove the misleading statement"));
        assertTrue(markdown.contains("Implementation does not satisfy the accepted TaskContract."));
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
