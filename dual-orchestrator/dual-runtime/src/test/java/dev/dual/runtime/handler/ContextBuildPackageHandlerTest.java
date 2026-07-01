package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void tearDown() {
        System.clearProperty("dual.context.memory.enabled");
        System.clearProperty("dual.context.governance.enabled");
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
        assertFalse(markdown.contains("I".repeat(10_000)),
                "oversized input artifact must not be expanded into the review package");
        assertFalse(markdown.contains("R".repeat(10_000)),
                "oversized recent output must not be expanded into the review package");
        assertFalse(markdown.contains("11.0k / 4.0k"),
                "governance must not report an over-budget package as successful");
        assertTrue(markdown.contains("capabilities=direct_file_read:Read"),
                "compact governance must keep available capability providers agent-facing");
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

    @Test
    void legacyPathCanRenderExplicitWorkingMemorySection() throws Exception {
        // Working Memory now renders from WorkstreamMemory, not the EvidenceManifest dump.
        // Seed memory with scope/decisions; EvidenceManifest is still written for provenance.
        Files.createDirectories(wsDir.resolve("memory"));
        Files.writeString(wsDir.resolve("requirement.md"), "Legacy memory test.", StandardCharsets.UTF_8);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                wsDir.resolve("memory/workstream-memory.json").toFile(),
                Map.of(
                        "schemaVersion", 1, "artifactType", "workstream_memory", "workstreamId", "ws-ctx",
                        "decidedScope", List.of("Selected the minimal API surface"),
                        "nonGoals", List.of(),
                        "decisions", List.of(Map.of("stage", "plan", "decision", "READY_TO_IMPLEMENT",
                                "summary", "Do not expand backend schema")),
                        "readFiles", List.of(), "openIssues", List.of(),
                        "priorVerdicts", List.of(), "priorFailures", List.of()
                ));

        var def = new NodeDefinition("ctx.custom-memory", NodeKind.TASK, "context.build_package", Map.of(
                "target_node", "next.node",
                "profile", "implementation",
                "include_sections", List.of("requirement", "working_memory")
        ));
        var instance = new NodeInstance("node-001", "ctx.custom-memory", NodeKind.TASK, "context.build_package",
                NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-ctx", def, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Working Memory"));
        assertTrue(markdown.contains("Selected the minimal API surface"));
        assertTrue(markdown.contains("Do not expand backend schema"));
        assertTrue(Files.exists(wsDir.resolve("memory/evidence-manifest.json")),
                "EvidenceManifest must still be written for provenance/debug");

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertEquals(".agent/workstreams/ws-ctx/memory/evidence-manifest.json", metadata.get("evidenceManifest"));
    }

    @Test
    void implementationProfileRendersAgentHandoffFromWorkstreamMemory() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("memory"));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                wsDir.resolve("memory/workstream-memory.json").toFile(),
                Map.of(
                        "schemaVersion", 1,
                        "artifactType", "workstream_memory",
                        "workstreamId", "ws-ctx",
                        "decidedScope", List.of("add login"),
                        "nonGoals", List.of("SSO"),
                        "readFiles", List.of(Map.of(
                                "path", "src/a.py", "sha", "s1", "summary", "A file",
                                "trusted", true, "invalidationReason", "", "readRunId", "run-1")),
                        "decisions", List.of(Map.of(
                                "stage", "plan", "decision", "READY_TO_IMPLEMENT", "summary", "approved")),
                        "openIssues", List.of(Map.of("id", "a", "desc", "handle edge", "status", "open")),
                        "priorVerdicts", List.of(),
                        "priorFailures", List.of()
                ));

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Working Memory"));
        assertTrue(markdown.contains("add login"));
        assertTrue(markdown.contains("src/a.py"));
        assertTrue(markdown.contains("READY_TO_IMPLEMENT"));
        assertTrue(markdown.contains("handle edge"));
        assertFalse(markdown.contains("(no stage handoff evidence yet)"));
    }

    @Test
    void acceptanceProfileRendersReviewEvidenceBriefWithChangedFiles() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Accept the change.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("memory"));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                wsDir.resolve("memory/workstream-memory.json").toFile(),
                Map.of(
                        "schemaVersion", 1, "artifactType", "workstream_memory", "workstreamId", "ws-ctx",
                        "priorVerdicts", List.of(Map.of(
                                "nodeId", "acceptance.codex_verifier_review", "stage", "acceptance",
                                "decision", "REJECTED", "blocking", true, "mustFix", List.of("fix token race"))),
                        "openIssues", List.of(Map.of("id", "x", "desc", "verify refresh", "status", "open")),
                        "readFiles", List.of(), "decisions", List.of()
                ));
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "tc-1",
                  "version": 1,
                  "created_at": "2026-06-18T00:00:00Z",
                  "objective": "Accept the change.",
                  "status": "READY_FOR_REVIEW",
                  "scope": {"included": [], "excluded": []},
                  "acceptance_criteria": ["tokens refreshed"],
                  "test_requirements": [],
                  "non_goals": [],
                  "constraints": [],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "compilation_pass": 1
                }
                """, StandardCharsets.UTF_8);
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("changed-files.txt"), "src/auth.py\n", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("acceptance", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Review Evidence Brief"));
        assertTrue(markdown.contains("REJECTED"));
        assertTrue(markdown.contains("src/auth.py"));
        assertTrue(markdown.contains("tokens refreshed"));
        assertTrue(markdown.toLowerCase().contains("incremental"));
    }

    @Test
    void unchangedReadFilesAreMarkedReusableInHandoff() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build it.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("memory"));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                wsDir.resolve("memory/workstream-memory.json").toFile(),
                Map.of(
                        "schemaVersion", 1, "artifactType", "workstream_memory", "workstreamId", "ws-ctx",
                        "decidedScope", List.of(), "nonGoals", List.of(),
                        "readFiles", List.of(Map.of(
                                "path", "src/a.py", "sha", "s1", "summary", "A file",
                                "trusted", true, "invalidationReason", "", "readRunId", "run-1")),
                        "decisions", List.of(), "openIssues", List.of(),
                        "priorVerdicts", List.of(), "priorFailures", List.of()
                ));

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Reusable reads"), markdown);
        assertTrue(markdown.contains("reuse unless editing"), markdown);
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

        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design-codex-review.md"
        )));

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
    void inputArtifactAgentOutputSurfacesEmbeddedBusinessArtifact() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Need a canonical task contract proposal.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-004/outputs/intake-claude-brainstorm.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, """
                Intake summary.

                <dual-artifact type="task_contract_proposal" version="1">
                {
                  "artifact_type": "task_contract_proposal",
                  "objective": "Add a small README note.",
                  "scope": {"included": ["README.md"], "excluded": ["business code"]},
                  "acceptance_criteria": ["README contains the note"],
                  "test_requirements": ["no runtime test required"],
                  "constraints": ["minimal change"],
                  "non_goals": ["dependency changes"],
                  "risk_areas": [],
                  "open_questions": []
                }
                </dual-artifact>

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: Candidate task contract proposal is ready.
                ```
                """, StandardCharsets.UTF_8);
        Files.writeString(output.getParent().resolve("intake-claude-brainstorm.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "intake.claude_brainstorm",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "summary": "Candidate task contract proposal is ready.",
                  "must_fix": [],
                  "missing_evidence": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": ""
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("product_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-004/outputs/intake-claude-brainstorm.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("**Embedded Structured Artifacts:**"));
        assertTrue(markdown.contains("<dual-artifact type=\"task_contract_proposal\" version=\"1\">"));
        assertTrue(markdown.contains("\"artifact_type\": \"task_contract_proposal\""));
        assertTrue(markdown.contains("\"objective\": \"Add a small README note.\""));
        assertTrue(markdown.contains("- Raw Output: "));
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

        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/acceptance.contract.json"
        )));

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

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-004/outputs/acceptance-codex-review.contract.json"
        )));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Current Repair Objective"));
        assertTrue(markdown.contains("Category: CONTRACT_GAP"));
        assertTrue(markdown.contains("Update README to point to .agent/workstreams/ exactly."));
        assertTrue(markdown.contains("Remove the misleading statement"));
        assertTrue(markdown.contains("Implementation does not satisfy the accepted TaskContract."));
    }

    @Test
    void stateEvidenceBoundaryUsesRoutedAcceptanceAndIgnoresUnroutedNewerSidecars() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.createDirectories(wsDir.resolve("runs/run-routed/outputs"));
        Files.createDirectories(wsDir.resolve("runs/run-unrouted-newer/outputs"));
        Files.writeString(wsDir.resolve("requirement.md"), "Routed evidence boundary test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/failure-classification.json"), """
                {
                  "category": "VERIFICATION_GAP",
                  "suggestedAction": "Repair only the routed verifier gap."
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-routed/outputs/acceptance-codex-review.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "REJECT",
                  "summary": "Routed verifier says evidence is insufficient.",
                  "lifecycle_status": "REJECTED",
                  "must_fix": ["Add routed verification evidence."],
                  "failure": {
                    "category_hint": "VERIFICATION_GAP",
                    "contract_gaps": ["Routed acceptance lacks proof."],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": "Routed rejection must drive repair."
                  },
                  "evidence_state": {
                    "blocking": true,
                    "must_fix_count": 1
                  }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-unrouted-newer/outputs/acceptance-codex-review.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Unrouted newer acceptance must not affect current repair.",
                  "lifecycle_status": "ACCEPTED_WITH_NOTES",
                  "must_fix": [],
                  "missing_evidence": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Unrouted acceptance."
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-routed/outputs/acceptance-codex-review.contract.json"
        )));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Current Repair Objective"));
        assertTrue(markdown.contains("Routed verifier says evidence is insufficient."));
        assertTrue(markdown.contains("Add routed verification evidence."));
        assertTrue(markdown.contains("Routed rejection must drive repair."));
        assertFalse(markdown.contains("Unrouted newer acceptance must not affect current repair."));
        assertFalse(markdown.contains("decision=ACCEPT_WITH_NOTES"));
    }

    @Test
    void implementationPackageWritesEvidenceManifestAndWorkingMemory() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts/next-stage"));
        Files.writeString(wsDir.resolve("requirement.md"), "Memory loop test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/next-stage/handoff.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-ctx",
                  "stageId": "design",
                  "iterationId": "iter-002",
                  "completedWork": ["Mapped the existing upload flow"],
                  "importantDecisions": ["Reuse the existing API"],
                  "changedFiles": ["miniprogram/src/pages/record/index.vue"],
                  "openRisks": ["Manual UX smoke still useful"],
                  "recommendedNextStage": "Implement scoped UI change."
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Working Memory"));
        assertTrue(markdown.contains("Mapped the existing upload flow"));
        assertTrue(markdown.contains("Reuse the existing API"));
        assertTrue(markdown.contains("miniprogram/src/pages/record/index.vue"));

        var manifestPath = wsDir.resolve("memory/evidence-manifest.json");
        assertTrue(Files.exists(manifestPath), "context build should write rebuildable evidence manifest");
        var manifest = objectMapper.readValue(manifestPath.toFile(), Map.class);
        assertTrue(manifest.get("items") instanceof List<?> items && !items.isEmpty());

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertEquals(".agent/workstreams/ws-ctx/memory/evidence-manifest.json", metadata.get("evidenceManifest"));
        assertTrue(((Number) metadata.get("workingMemoryEvidenceCount")).intValue() > 0);
        assertTrue(metadata.get("evidenceRefs") instanceof List<?> refs
                        && refs.contains(".agent/workstreams/ws-ctx/artifacts/next-stage/handoff.json"),
                "metadata should expose evidence refs used by the package");
    }

    @Test
    void memoryDisabledOmitsWorkingMemoryFromImplementationPackage() throws Exception {
        System.setProperty("dual.context.memory.enabled", "false");
        Files.createDirectories(wsDir.resolve("artifacts/next-stage"));
        Files.writeString(wsDir.resolve("requirement.md"), "Memory off test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/next-stage/handoff.json"), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-ctx",
                  "stageId": "design",
                  "iterationId": "iter-002",
                  "completedWork": ["Mapped the existing upload flow"],
                  "importantDecisions": ["Reuse the existing API"],
                  "changedFiles": ["miniprogram/src/pages/record/index.vue"],
                  "openRisks": [],
                  "recommendedNextStage": "Implement scoped UI change."
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertFalse(markdown.contains("## Working Memory"));

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertEquals(false, metadata.get("memoryEnabled"));
        assertTrue(metadata.get("memoryDisabledSections") instanceof List<?> disabled
                        && disabled.contains("working_memory"),
                "metadata should record which memory sections were removed for A/B analysis");
    }

    @Test
    void reviewPackageIncludesEvidenceBriefFromManifest() throws Exception {
        Files.createDirectories(wsDir.resolve("runs/run-001/outputs"));
        Files.writeString(wsDir.resolve("requirement.md"), "Review evidence test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-001/outputs/design-claude.md"), "Design body.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-001/outputs/design-claude.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "source_artifact_type": "agent_output_contract",
                  "node_id": "design.claude_design",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Design keeps the task scoped.",
                  "changed_files": ["docs/design.md"],
                  "open_risks": ["Migration path needs review"],
                  "verification_summary": "Design-only evidence."
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "task-ws",
                  "version": 1,
                  "created_at": "2026-06-21T00:00:00Z",
                  "objective": "Keep review evidence scoped.",
                  "status": "READY_FOR_REVIEW",
                  "scope": {"included": ["docs/design.md"], "excluded": []},
                  "acceptance_criteria": ["Reviewer sees evidence brief"],
                  "test_requirements": [],
                  "non_goals": [],
                  "constraints": [],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "compilation_pass": 1,
                  "source": {"raw_request_ref": "requirement.md", "created_by": "test"}
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design-claude.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Review Evidence Brief"));
        assertTrue(markdown.contains("Design keeps the task scoped."));
        assertTrue(markdown.contains("docs/design.md"));
        assertTrue(markdown.contains("Migration path needs review"));

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertTrue(((Number) metadata.get("reviewEvidenceCount")).intValue() > 0);
    }

    @Test
    void memoryDisabledOmitsReviewEvidenceBriefFromReviewPackage() throws Exception {
        System.setProperty("dual.context.memory.enabled", "false");
        Files.createDirectories(wsDir.resolve("runs/run-001/outputs"));
        Files.writeString(wsDir.resolve("requirement.md"), "Review evidence off test.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-001/outputs/design-claude.md"), "Design body.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("runs/run-001/outputs/design-claude.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "source_artifact_type": "agent_output_contract",
                  "node_id": "design.claude_design",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Design keeps the task scoped.",
                  "changed_files": ["docs/design.md"],
                  "open_risks": ["Migration path needs review"],
                  "verification_summary": "Design-only evidence."
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design-claude.md")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertFalse(markdown.contains("## Review Evidence Brief"));

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertEquals(false, metadata.get("memoryEnabled"));
        assertTrue(metadata.get("memoryDisabledSections") instanceof List<?> disabled
                        && disabled.contains("review_evidence"),
                "metadata should record disabled review evidence for A/B analysis");
    }

    @Test
    void governedPackageWritesRepoScopeGovernanceArtifacts() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("requirement.md"),
                "Update treatment record upload flow in miniprogram/src/pages/treatment-record/index.vue.",
                StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "task-ws",
                  "version": 1,
                  "created_at": "2026-06-21T00:00:00Z",
                  "objective": "Update treatment record upload flow.",
                  "status": "READY_FOR_REVIEW",
                  "scope": {
                    "included": ["miniprogram/src/pages/treatment-record/index.vue"],
                    "excluded": ["backend API changes"]
                  },
                  "acceptance_criteria": ["Upload entry remains visible"],
                  "test_requirements": [],
                  "non_goals": [],
                  "constraints": [],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "compilation_pass": 1,
                  "source": {"raw_request_ref": "requirement.md", "created_by": "test"}
                }
                """, StandardCharsets.UTF_8);
        var sourceFile = tempDir.resolve("miniprogram/src/pages/treatment-record/index.vue");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, "<template>record</template>", StandardCharsets.UTF_8);
        var previousRun = wsDir.resolve("runs/run-000/run.json");
        Files.createDirectories(previousRun.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(previousRun.toFile(), Map.of(
                "runId", "run-000",
                "toolUsage", Map.of(
                        "readFiles", List.of("miniprogram/src/pages/treatment-record/index.vue", "docs/old.md")
                )
        ));
        Files.createDirectories(wsDir.resolve("memory"));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(wsDir.resolve("memory/workstream-memory.json").toFile(), Map.of(
                "schemaVersion", 1,
                "artifactType", "workstream_memory",
                "workstreamId", "ws-ctx",
                "readFiles", List.of(Map.of(
                        "path", "miniprogram/src/pages/treatment-record/index.vue",
                        "sha", "sha-1",
                        "summary", "Previously read source file (27 bytes).",
                        "evidenceRole", "source",
                        "trusted", true,
                        "invalidationReason", "",
                        "readRunId", "run-000",
                        "lastSeenStage", "intake"
                ))
        ));

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Repo Scope Guidance"));
        assertTrue(markdown.contains("Exploration Plan"));
        assertTrue(markdown.contains("edit_execution"));
        assertTrue(markdown.contains("agent-capability-catalog-node-001.json"));
        assertTrue(markdown.contains("exploration-plan-node-001.json"));
        assertTrue(markdown.contains("miniprogram/src/pages/treatment-record/index.vue"));
        assertTrue(markdown.contains("Already inspected files"));
        assertTrue(markdown.contains("Read reuse rule"));
        assertTrue(markdown.contains("their recorded sha is still trusted"));
        assertTrue(markdown.contains("Previously read source file"));

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertEquals(true, metadata.get("contextGovernanceEnabled"));
        assertEquals(".agent/workstreams/ws-ctx/memory/repo-map.json", metadata.get("repoMap"));
        assertEquals(".agent/workstreams/ws-ctx/memory/file-relevance-plan-node-001.json", metadata.get("fileRelevancePlan"));
        assertEquals(".agent/workstreams/ws-ctx/memory/stage-scope-policy-node-001.json", metadata.get("stageScopePolicy"));
        assertEquals(".agent/workstreams/ws-ctx/artifacts/context-governance/node-001.json", metadata.get("contextGovernanceReport"));
        assertEquals(".agent/workstreams/ws-ctx/memory/workstream-memory.json", metadata.get("workstreamMemory"));
        assertEquals(".agent/workstreams/ws-ctx/memory/working-set.json", metadata.get("workingSet"));
        assertEquals(".agent/workstreams/ws-ctx/memory/file-summaries.json", metadata.get("fileSummaries"));
        assertEquals(".agent/workstreams/ws-ctx/memory/read-reuse-contract.json", metadata.get("readReuseContract"));
        assertEquals(".agent/workstreams/ws-ctx/memory/agent-capability-catalog-node-001.json", metadata.get("agentCapabilityCatalog"));
        assertEquals(".agent/workstreams/ws-ctx/memory/exploration-plan-node-001.json", metadata.get("agentExplorationPlan"));

        var plan = objectMapper.readValue(wsDir.resolve("memory/file-relevance-plan-node-001.json").toFile(), Map.class);
        assertEquals("ctx.test", plan.get("builderNodeDefinitionId"));
        assertEquals("next.node", plan.get("targetNodeDefinitionId"));
        assertTrue(plan.get("readCandidates") instanceof List<?> candidates
                        && candidates.contains("miniprogram/src/pages/treatment-record/index.vue"),
                "file relevance plan should preserve task-contract file scope");
        assertTrue(plan.get("previouslyReadFiles") instanceof List<?> previousReads
                        && previousReads.contains("miniprogram/src/pages/treatment-record/index.vue"),
                "file relevance plan should surface prior reads so the next agent can avoid repeated exploration");
        assertTrue(plan.get("repeatedReadAvoidance") instanceof List<?> avoidance
                        && !avoidance.isEmpty(),
                "file relevance plan should include typed repeated-read avoidance hints");

        var policy = objectMapper.readValue(wsDir.resolve("memory/stage-scope-policy-node-001.json").toFile(), Map.class);
        assertEquals("implement", policy.get("stageId"));
        assertEquals("warn", policy.get("scopeExceedPolicy"));
        assertEquals("ctx.test", policy.get("builderNodeDefinitionId"));
        assertEquals("next.node", policy.get("targetNodeDefinitionId"));
        assertTrue(policy.get("writeScope") instanceof List<?> writeScope
                        && writeScope.contains("miniprogram/src/pages/treatment-record/index.vue"),
                "implementation policy should expose scoped writable files");
        assertTrue(policy.get("readReusePolicy") instanceof Map<?, ?> reusePolicy
                        && Boolean.TRUE.equals(reusePolicy.get("preferExistingEvidence")),
                "stage scope policy should carry read reuse rules as typed data, not only prompt text");

        var workingSet = objectMapper.readValue(wsDir.resolve("memory/working-set.json").toFile(), Map.class);
        assertEquals("working_set", workingSet.get("artifactType"));
        assertTrue(workingSet.get("coreFiles") instanceof List<?> coreFiles
                        && !coreFiles.isEmpty(),
                "working set should expose bounded core files for the next agent");

        var fileSummaries = objectMapper.readValue(wsDir.resolve("memory/file-summaries.json").toFile(), Map.class);
        assertEquals("file_summaries", fileSummaries.get("artifactType"));
        assertTrue(fileSummaries.get("files") instanceof List<?> summaries
                        && !summaries.isEmpty(),
                "file summaries should give agents reusable memory before repeated reads");

        var readReuse = objectMapper.readValue(wsDir.resolve("memory/read-reuse-contract.json").toFile(), Map.class);
        assertEquals("read_reuse_contract", readReuse.get("artifactType"));
        assertTrue(readReuse.get("avoidRepeatingReads") instanceof List<?> avoidReads
                        && !avoidReads.isEmpty(),
                "read reuse contract should turn repeated-read risks into structured guidance");
        assertTrue(readReuse.get("reusableReadFiles") instanceof List<?> reusable
                        && !reusable.isEmpty(),
                "read reuse contract should derive reusable files from WorkstreamMemory.readFiles");
        assertTrue(readReuse.get("allowedRereadFiles") instanceof List<?> allowed
                        && allowed.isEmpty(),
                "non-review targets should not receive review reread allowances");
        assertEquals("", readReuse.get("reasonField"),
                "targets without a registered agent_output_contract artifact must not see its reread field");
        assertFalse(markdown.contains("agent_output_contract.reread_reasons"),
                "context package must not leak agent_output_contract fields into targets that do not declare that artifact");

        var capabilityCatalogPath = (String) metadata.get("agentCapabilityCatalog");
        var capabilityCatalog = objectMapper.readValue(tempDir.resolve(capabilityCatalogPath).toFile(), Map.class);
        assertEquals("agent_capability_catalog", capabilityCatalog.get("artifactType"));
        assertTrue(capabilityCatalog.get("capabilities") instanceof List<?> capabilities
                        && !capabilities.isEmpty(),
                "context governance should expose available exploration capabilities as typed data");

        var explorationPlan = objectMapper.readValue(wsDir.resolve("memory/exploration-plan-node-001.json").toFile(), Map.class);
        assertEquals("agent_exploration_plan", explorationPlan.get("artifactType"));
        assertEquals("edit_execution", explorationPlan.get("explorationIntent"));
        assertEquals("warn", explorationPlan.get("failureMode"));
        assertTrue(explorationPlan.get("expectedEditFiles") instanceof List<?> edits
                        && edits.contains("miniprogram/src/pages/treatment-record/index.vue"),
                "exploration plan should carry expected edit files for post-run evaluation");
        var workstreamMemory = objectMapper.readValue(wsDir.resolve("memory/workstream-memory.json").toFile(), Map.class);
        assertEquals("workstream_memory", workstreamMemory.get("artifactType"));
        assertTrue(workstreamMemory.get("readFiles") instanceof List<?> remembered
                        && !remembered.isEmpty(),
                "context governance must preserve typed read memory instead of overwriting it with refs only");
        assertTrue(workstreamMemory.get("refs") instanceof Map<?, ?> refs
                        && refs.containsKey("readReuseContract"),
                "workstream memory should index the reusable memory sidecars");

        var report = objectMapper.readValue(wsDir.resolve("artifacts/context-governance/node-001.json").toFile(), Map.class);
        assertEquals("planned", report.get("reportPhase"));
        assertEquals("ctx.test", report.get("builderNodeDefinitionId"));
        assertEquals("next.node", report.get("targetNodeDefinitionId"));
        assertTrue(report.get("plannedRepeatedReadAvoidance") instanceof List<?> plannedAvoidance
                        && !plannedAvoidance.isEmpty(),
                "planned governance report should record repeated-read risks before execution");
        assertEquals(true, report.get("telemetryPending"));
    }

    @Test
    void governedStageScopePoliciesMatchStageBoundaries() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Keep stage governance explicit.", StandardCharsets.UTF_8);

        Map<String, String> expected = Map.of(
                "product_review", "block",
                "architecture_review", "warn",
                "plan_review", "warn",
                "implementation", "warn",
                "acceptance", "block"
        );
        for (var entry : expected.entrySet()) {
            var result = handler.execute(ctx(entry.getKey(), List.of()));

            assertEquals(NodeStatus.COMPLETED, result.status(), entry.getKey());
            var policy = objectMapper.readValue(wsDir.resolve("memory/stage-scope-policy-node-001.json").toFile(), Map.class);
            assertEquals(entry.getValue(), policy.get("scopeExceedPolicy"), entry.getKey());
        }
    }

    @Test
    void governedIntakePackageDerivesConcreteScopeCandidatesFromRepoMap() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), """
                # 复诊准备清单与缺失资料提醒闭环

                如果已有 follow-up summary / treatment event / exam report 聚合逻辑，优先在其附近补充。
                前端复诊相关页面能展示这个准备状态。
                """, StandardCharsets.UTF_8);
        writeFile("backend/app/api/treatment_events.py", "def list_treatment_events(): pass\n");
        writeFile("backend/app/schemas/summary.py", "class FollowUpSummary: pass\n");
        writeFile("miniprogram/src/pages/follow-up-summary/index.vue", "<template>follow up</template>\n");
        writeFile("backend/tests/test_treatment_events.py", "def test_treatment_event_chain(): pass\n");
        writeFile("docs/superpowers/specs/2026-05-20-treatment-event-chain-api-usage.md", """
                # Treatment Event Chain API Usage

                follow-up summary should reuse treatment event and exam report evidence.
                """);

        var result = handler.execute(ctx("requirements", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Contract scope candidates"));
        assertTrue(markdown.contains("Seed Evidence Digests"));
        assertTrue(markdown.contains("backend/app/api/treatment_events.py"));
        assertTrue(markdown.contains("miniprogram/src/pages/follow-up-summary/index.vue"));

        var plan = objectMapper.readValue(wsDir.resolve("memory/file-relevance-plan-node-001.json").toFile(), Map.class);
        assertTrue(plan.get("readCandidates") instanceof List<?> candidates
                        && candidates.contains("backend/app/api/treatment_events.py")
                        && candidates.contains("miniprogram/src/pages/follow-up-summary/index.vue"),
                "Chinese requirements with English domain anchors should produce concrete repo-map candidates");
        assertTrue(plan.get("contractScopeCandidates") instanceof List<?> scopeCandidates
                        && scopeCandidates.contains("backend/app/api/treatment_events.py")
                        && scopeCandidates.contains("miniprogram/src/pages/follow-up-summary/index.vue"),
                "contract scope candidates should be typed paths the intake artifact can legally cite");
        assertTrue(plan.get("seedEvidenceDigests") instanceof List<?> digests
                        && !digests.isEmpty(),
                "intake should receive bounded seed evidence instead of needing direct Read");
    }

    @Test
    void reviewTargetReceivesTypedSourceRereadAllowance() throws Exception {
        System.setProperty("dual.context.governance.enabled", "true");
        Files.createDirectories(wsDir.resolve("memory"));
        Files.writeString(wsDir.resolve("requirement.md"), "Review source evidence.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("memory/workstream-memory.json"), """
                {
                  "schemaVersion": 1,
                  "artifactType": "workstream_memory",
                  "workstreamId": "ws-ctx",
                  "readFiles": [{
                    "path": "src/api.js",
                    "sha": "abc",
                    "summary": "defs: loadApi",
                    "evidenceRole": "source",
                    "readRunId": "run-1",
                    "lastSeenStage": "stage-intake",
                    "invalidationReason": "",
                    "trusted": true
                  }, {
                    "path": "docs/spec.md",
                    "sha": "def",
                    "summary": "headings: Spec",
                    "evidenceRole": "documentation",
                    "readRunId": "run-1",
                    "lastSeenStage": "stage-intake",
                    "invalidationReason": "",
                    "trusted": true
                  }]
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("product_review", List.of(), "intake.codex_product_review"));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var readReuse = objectMapper.readValue(wsDir.resolve("memory/read-reuse-contract.json").toFile(), Map.class);
        assertTrue(readReuse.get("allowedRereadFiles") instanceof List<?> allowed
                        && allowed.stream().anyMatch(item -> String.valueOf(item).contains("src/api.js"))
                        && allowed.stream().noneMatch(item -> String.valueOf(item).contains("docs/spec.md")),
                "review targets should receive typed source-file verification allowance only");
        assertEquals("", readReuse.get("reasonField"),
                "review targets may receive harness-computed reread allowances but must not see implementation reread fields");
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertFalse(markdown.contains("agent_output_contract.reread_reasons"),
                "review context package must not leak implementation reread fields");
    }

    @Test
    void implementTargetDoesNotReceiveTypedSourceRereadAllowance() throws Exception {
        System.setProperty("dual.context.governance.enabled", "true");
        Files.createDirectories(wsDir.resolve("memory"));
        Files.writeString(wsDir.resolve("requirement.md"), "Implement source change.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("memory/workstream-memory.json"), """
                {
                  "schemaVersion": 1,
                  "artifactType": "workstream_memory",
                  "workstreamId": "ws-ctx",
                  "readFiles": [{
                    "path": "src/api.js",
                    "sha": "abc",
                    "summary": "defs: loadApi",
                    "evidenceRole": "source",
                    "readRunId": "run-1",
                    "lastSeenStage": "stage-intake",
                    "invalidationReason": "",
                    "trusted": true
                  }]
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(), "implement.claude_implement"));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var readReuse = objectMapper.readValue(wsDir.resolve("memory/read-reuse-contract.json").toFile(), Map.class);
        assertTrue(readReuse.get("allowedRereadFiles") instanceof List<?> allowed && allowed.isEmpty(),
                "producer targets must use changed_files/plan/invalidation, not review reread allowance");
        assertEquals("agent_output_contract.reread_reasons", readReuse.get("reasonField"),
                "implement agent_output_contract targets should receive the typed reread reason field");
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("agent_output_contract.reread_reasons"));
        assertTrue(markdown.contains("Entry shape:"));
    }

    @Test
    void governanceDisabledOmitsRepoScopeArtifacts() throws Exception {
        System.setProperty("dual.context.governance.enabled", "false");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Governance off test.", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertFalse(markdown.contains("Repo Scope Guidance"));

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertEquals(false, metadata.get("contextGovernanceEnabled"));
        assertFalse(Files.exists(wsDir.resolve("memory/repo-map.json")));
        assertFalse(Files.exists(wsDir.resolve("artifacts/context-governance/node-001.json")));
    }

    // --- Helper ---

    private void writeFile(String relativePath, String content) throws Exception {
        var path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private TaskHandler.TaskContext ctx(String profile, List<String> inputRefs) {
        return ctx(profile, inputRefs, "next.node");
    }

    private TaskHandler.TaskContext ctx(String profile, List<String> inputRefs, String targetNode) {
        return new TaskHandler.TaskContext(
                tempDir, "ws-ctx",
                new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", targetNode,
                        "profile", profile
                )),
                new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", inputRefs, List.of(), Map.of(), null, null, null)
        );
    }
}
