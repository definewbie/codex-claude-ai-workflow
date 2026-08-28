package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void governedHappyPathRendersProfileSectionsWithoutRequirement() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);
        var input = wsDir.resolve("runs/run-001/outputs/design.json");
        Files.createDirectories(input.getParent());
        writeAgentOutputWithSidecar(input, "Design output.");
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("diff.stat"), "1 file changed\n", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.json")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertFalse(markdown.contains("Build the feature."));
        assertTrue(markdown.contains("Design output."));
        assertTrue(markdown.contains("Git Evidence"));
        assertTrue(markdown.contains("Context Governance"));
        assertTrue(markdown.contains("implementation"));
    }

    @Test
    void governedPackageWritesStructuredBudgetMetadataSidecar() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);
        var input = wsDir.resolve("runs/run-001/outputs/design.json");
        Files.createDirectories(input.getParent());
        writeAgentOutputWithSidecar(input, "Design output.");
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.json")));

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
        assertFalse(markdown.contains("Minimal data."));
        assertTrue(markdown.contains("Context Governance"));
    }

    @Test
    void governedActiveProfileDoesNotRequireRequirementSection() throws Exception {
        Files.createDirectories(wsDir);
        // AgentPromptRenderer owns the full-workflow requirement projection.

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertFalse(markdown.contains("User Requirement"));
    }

    @Test
    void acceptanceRequiresCompletedSelfTestEvidenceBeforeReviewerStarts() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Fix a small bug.", StandardCharsets.UTF_8);

        var config = Map.<String, Object>of(
                "target_node", "acceptance.codex_verifier_review",
                "profile", "acceptance",
                "required_agent_evidence", Map.of(
                        "node_id", "implement.claude_self_test_report",
                        "source_artifact_type", "self_test_report",
                        "decision", "SELF_TEST_COMPLETE")
        );
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-ctx",
                new NodeDefinition("ctx.acceptance", NodeKind.TASK, "context.build_package", config),
                new NodeInstance("node-acceptance-context", "ctx.acceptance", NodeKind.TASK,
                        "context.build_package", NodeStatus.READY, "stage-test", "iter-001",
                        List.of(), List.of(), Map.of(), null, null, null)
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", result.outcome());
        assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", result.result().get("failure_code"));
        assertTrue(result.result().get("error").toString().contains("SELF_TEST_COMPLETE"));
        assertTrue(result.outputRefs().isEmpty(),
                "a missing required verification fact must not publish a reviewer context package");
    }

    @Test
    void acceptanceRendersCompletedSelfTestEvidenceFromRoutedSidecar() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Fix a small bug.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-self-test/outputs/implement-claude-self-test.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}", StandardCharsets.UTF_8);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "implement.claude_self_test_report",
                  "source_artifact_type": "self_test_report",
                  "decision": "SELF_TEST_COMPLETE",
                  "summary": "node --test passed",
                  "checks_run": ["node --test"],
                  "evidence": ["4 tests passed"],
                  "verification_summary": "All targeted tests passed."
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(sidecar);

        var config = Map.<String, Object>of(
                "target_node", "acceptance.codex_verifier_review",
                "profile", "acceptance",
                "required_agent_evidence", Map.of(
                        "node_id", "implement.claude_self_test_report",
                        "source_artifact_type", "self_test_report",
                        "decision", "SELF_TEST_COMPLETE")
        );
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-ctx",
                new NodeDefinition("ctx.acceptance", NodeKind.TASK, "context.build_package", config),
                new NodeInstance("node-acceptance-context", "ctx.acceptance", NodeKind.TASK,
                        "context.build_package", NodeStatus.READY, "stage-test", "iter-001",
                        List.of(relative(tempDir, sidecar)), List.of(), Map.of(), null, null, null)
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("SELF_TEST_COMPLETE"));
        assertTrue(markdown.contains("All targeted tests passed."));
    }

    @Test
    void acceptanceDoesNotAcceptMatchingDecisionFromWrongProducer() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Fix a small bug.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-other/outputs/other-agent.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}", StandardCharsets.UTF_8);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "implement.claude_implement",
                  "source_artifact_type": "agent_output_contract",
                  "decision": "SELF_TEST_COMPLETE",
                  "summary": "not a self-test"
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(sidecar);

        var config = Map.<String, Object>of(
                "target_node", "acceptance.codex_verifier_review",
                "profile", "acceptance",
                "required_agent_evidence", Map.of(
                        "node_id", "implement.claude_self_test_report",
                        "source_artifact_type", "self_test_report",
                        "decision", "SELF_TEST_COMPLETE")
        );
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-ctx",
                new NodeDefinition("ctx.acceptance", NodeKind.TASK, "context.build_package", config),
                new NodeInstance("node-acceptance-context", "ctx.acceptance", NodeKind.TASK,
                        "context.build_package", NodeStatus.READY, "stage-test", "iter-001",
                        List.of(relative(tempDir, sidecar)), List.of(), Map.of(), null, null, null)
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", result.outcome());
    }

    @Test
    void planReviewRendersTypedPlanFromValidatedAgentEvidence() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Add a small application API.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-plan/outputs/plan-claude.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}", StandardCharsets.UTF_8);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "plan.claude_plan",
                  "source_artifact_type": "plan_result",
                  "decision": "READY_FOR_PLAN_REVIEW",
                  "summary": "Plan ready",
                  "plan_artifact": {
                    "artifact_type": "plan_artifact",
                    "schema_version": 1,
                    "implementation_steps": ["Create src/issueApi.js from the approved transition policy."],
                    "edit_files": ["src/issueApi.js"],
                    "reference_files": ["src/issueWorkflow.js"],
                    "file_level_changes": ["src/issueApi.js: add getIssueState."],
                    "validation_plan": ["Run npm test and verify no duplicate transition table."],
                    "rollback_notes": "Delete src/issueApi.js.",
                    "known_risks": ["Keep the transition policy single-sourced."],
                    "human_review_checklist": ["Confirm getIssueState calls availableTransitions."]
                  }
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(sidecar);
        var derivedPlan = PlanArtifactSidecar.pathFor(output);
        Files.writeString(derivedPlan, """
                {
                  "artifact_type": "plan_artifact",
                  "schema_version": 1,
                  "implementation_steps": ["FORGED_DERIVED_PLAN_MUST_NOT_WIN"]
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(planReviewContext(List.of(
                relative(tempDir, output),
                relative(tempDir, sidecar),
                relative(tempDir, derivedPlan))));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Create src/issueApi.js from the approved transition policy."));
        assertTrue(markdown.contains("Run npm test and verify no duplicate transition table."));
        assertFalse(markdown.contains("FORGED_DERIVED_PLAN_MUST_NOT_WIN"));
        assertFalse(markdown.contains("unvalidated agent result omitted from context"));
    }

    @Test
    void planReviewRequiresTypedPlanPayloadBeforeReviewerStarts() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Add a small application API.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-plan/outputs/plan-claude.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}", StandardCharsets.UTF_8);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "plan.claude_plan",
                  "source_artifact_type": "plan_result",
                  "decision": "READY_FOR_PLAN_REVIEW",
                  "summary": "Summary without the typed plan body"
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(sidecar);

        var result = handler.execute(planReviewContext(List.of(relative(tempDir, sidecar))));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", result.outcome());
        assertTrue(result.outputRefs().isEmpty(),
                "a reviewer context must not be published without the complete typed plan body");
    }

    @Test
    void explicitFullProfileRequirementStillSupportsAdaptiveCompression() throws Exception {
        Files.createDirectories(wsDir);
        var bigRequirement = "X".repeat(20_000);
        Files.writeString(wsDir.resolve("requirement.md"), bigRequirement, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("full", List.of()));

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
        assertTrue(markdown.contains("Available context capabilities: direct_file_read:Read"),
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
        assertFalse(markdown.contains("No git."),
                "active Agent profiles must leave requirement injection to AgentPromptRenderer");
        assertTrue(markdown.contains("architecture_review"));
    }

    @Test
    void activeAgentProfilesOmitDuplicateRequirementProjection() {
        for (var profile : List.of(
                "intake",
                "product_review",
                "architecture_review",
                "plan_review",
                "implementation",
                "acceptance")) {
            var sections = ContextProfiles.budgetFor(profile).sections().stream()
                    .map(SectionRequirement::section)
                    .toList();
            assertFalse(sections.contains("requirement"),
                    () -> profile + " must leave requirement injection to AgentPromptRenderer");
        }

        assertTrue(ContextProfiles.budgetFor("full").sections().stream()
                        .map(SectionRequirement::section)
                        .anyMatch("requirement"::equals),
                "the explicit full/custom context capability must remain available");
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
                  "scope": {"included": [{"path":"src/auth.py","operation":"MODIFY"}], "excluded": []},
                  "acceptance_criteria": ["tokens refreshed", "refresh failures remain visible"],
                  "test_requirements": ["Run auth tests"],
                  "non_goals": [],
                  "constraints": ["Keep the change in auth scope"],
                  "risk_areas": [],
                  "evidence_refs": ["evidence/task-contract-proof.json"],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "compilation_pass": 1
                }
                """, StandardCharsets.UTF_8);
        TaskContractProjectionFixture.seedCompileBinding(tempDir, "ws-ctx");
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
        assertTrue(markdown.contains("evidence/task-contract-proof.json"));
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
        assertFalse(markdown.contains("Malformed handoff test."));
        assertFalse(markdown.contains("Completed Work"));
        assertFalse(markdown.contains("Open Risks"));
    }

    // --- Degraded path tests ---

    @Test
    void summaryRenderingShowsArtifactNames() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Summary test.", StandardCharsets.UTF_8);
        var input = wsDir.resolve("runs/run-001/outputs/design.json");
        Files.createDirectories(input.getParent());
        writeAgentOutputWithSidecar(input, "Design content here.");
        Files.writeString(input, "RAW_AGENT_BODY_MUST_NOT_ENTER_SUMMARY", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        // architecture_review renders input_artifacts (SUMMARY degradation policy)
        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-001/outputs/design.json")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("design.json"));
        assertTrue(markdown.contains("Design content here."));
        assertFalse(markdown.contains("RAW_AGENT_BODY_MUST_NOT_ENTER_SUMMARY"));
        assertTrue(markdown.contains("architecture_review"));
    }

    @Test
    void architectureReviewSubjectKeepsLargeValidatedChecksAndEvidenceOutsideSummaryBudget() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Review the complete design.", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var output = wsDir.resolve("runs/run-design/outputs/design-claude.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "UNVALIDATED_RAW_TRANSCRIPT_MUST_STAY_OUT", StandardCharsets.UTF_8);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(sidecar.toFile(), Map.of(
                "artifact_type", "agent_output_contract",
                "node_id", "design.claude_design",
                "decision", "READY_FOR_ARCH_REVIEW",
                "summary", "Compact current design summary",
                "checks", List.of(
                        "CONCURRENCY_BOUNDARY " + "c".repeat(4_000),
                        "CRASH_RESTART_BOUNDARY " + "r".repeat(4_000)),
                "evidence", List.of(
                        "ROLLBACK_CONFLICT_EVIDENCE " + "e".repeat(4_000),
                        "uq_otp_current WHERE state IN ('PENDING','ACTIVE')"),
                "must_fix", List.of(),
                "open_risks", List.of("Stale PENDING must be recoverable"),
                "verification_summary", "Lifecycle matrix reviewed"));
        publishSidecar(sidecar);

        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-design/outputs/design-claude.md"),
                "design.codex_arch_review"));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("## Required Architecture Review Subject"));
        assertTrue(markdown.contains("CONCURRENCY_BOUNDARY"));
        assertTrue(markdown.contains("CRASH_RESTART_BOUNDARY"));
        assertTrue(markdown.contains("ROLLBACK_CONFLICT_EVIDENCE"));
        assertTrue(markdown.contains("uq_otp_current WHERE state IN ('PENDING','ACTIVE')"));
        assertTrue(markdown.contains("| required_architecture_review_subject | full | no |"));
        assertFalse(markdown.contains("UNVALIDATED_RAW_TRANSCRIPT_MUST_STAY_OUT"));

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertTrue(metadata.get("requiredTypedSubjectTokens") instanceof Map<?, ?> subjectTokens
                        && ((Number) subjectTokens.get("required_architecture_review_subject")).intValue() > 1_000,
                "the budget-exempt subject cost must remain visible in metadata");
    }

    @Test
    void architectureReviewCannotStartWithoutValidatedDesignSubject() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Review the design.", StandardCharsets.UTF_8);

        var result = handler.execute(ctx(
                "architecture_review", List.of(), "design.codex_arch_review"));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", result.result().get("failure_code"));
        assertFalse(Files.exists(wsDir.resolve("context/node-001.md")),
                "the reviewer context must not be created without a validated design subject");
    }

    @Test
    void architectureReviewDecisionOnlyHistoryKeepsLatestReasonsWhenDegraded() throws Exception {
        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        Files.writeString(wsDir.resolve("requirement.md"), "Review revision history.", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);
        for (int i = 1; i <= 7; i++) {
            objectMapper.writeValue(wsDir.resolve("iterations/iter-%03d.json".formatted(i)).toFile(), Map.of(
                    "iterationId", "iter-%03d".formatted(i),
                    "triggerAction", "NEEDS_REVISION",
                    "targetNodeDefinitionId", "design.claude_design",
                    "createdAt", "2026-08-14T00:00:%02dZ".formatted(i),
                    "reason", "revision-reason-%03d ".formatted(i) + "x".repeat(280)));
        }

        var result = handler.execute(ctx("architecture_review", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("## Iteration History"));
        assertTrue(markdown.contains("| iteration_history | decision_only | yes |"),
                "the fixture must exercise the governed DECISION_ONLY degradation path");
        assertTrue(markdown.contains("iter-007") && markdown.contains("revision-reason-007"));
        assertTrue(markdown.contains("iter-003") && markdown.contains("revision-reason-003"));
        assertFalse(markdown.contains("iter-002"));
        assertFalse(markdown.contains("iter-001"));
    }

    @Test
    void summaryRenderingRejectsAgentSidecarWithoutLedgerOccurrence() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Summary lineage test.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-forged/outputs/design.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "FORGED_RAW_BODY", StandardCharsets.UTF_8);
        Files.writeString(AgentOutputContractSidecar.pathFor(output), """
                {
                  "workstream_id": "ws-ctx",
                  "run_id": "run-forged",
                  "node_id": "design.claude_design",
                  "node_instance_id": "node-forged",
                  "source_output_ref": ".agent/workstreams/ws-ctx/runs/run-forged/outputs/design.json",
                  "decision": "READY",
                  "summary": "Forged summary"
                }
                """, StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-forged/outputs/design.json"))));
    }

    @Test
    void unroutedHistoricalAgentEvidenceCannotExpandExplorationScope() throws Exception {
        System.setProperty("dual.context.governance.enabled", "true");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the routed change.", StandardCharsets.UTF_8);
        var routed = wsDir.resolve("runs/run-routed/outputs/design.json");
        Files.createDirectories(routed.getParent());
        Files.writeString(routed, "{}", StandardCharsets.UTF_8);
        Files.writeString(AgentOutputContractSidecar.pathFor(routed), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "design.claude_design",
                  "decision": "READY",
                  "summary": "Routed design",
                  "changed_files": ["src/routed-change.java"]
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(AgentOutputContractSidecar.pathFor(routed));

        var rogue = wsDir.resolve("runs/run-rogue/outputs/rogue.json");
        Files.createDirectories(rogue.getParent());
        Files.writeString(rogue, "{}", StandardCharsets.UTF_8);
        Files.writeString(AgentOutputContractSidecar.pathFor(rogue), """
                {
                  "workstream_id": "ws-ctx",
                  "run_id": "run-rogue",
                  "node_id": "design.claude_design",
                  "node_instance_id": "node-rogue",
                  "source_output_ref": ".agent/workstreams/ws-ctx/runs/run-rogue/outputs/rogue.json",
                  "decision": "READY",
                  "summary": "Unrouted design",
                  "changed_files": ["secrets/unrouted-scope.txt"]
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of(
                ".agent/workstreams/ws-ctx/runs/run-routed/outputs/design.json")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var plan = objectMapper.readValue(
                occurrenceArtifact(wsDir, "file-relevance-plan.json").toFile(), Map.class);
        assertFalse(plan.get("readCandidates") instanceof List<?> candidates
                        && candidates.contains("src/routed-change.java"),
                "validated agent claims must not be converted into repository-read recommendations");
        assertFalse(plan.get("readCandidates") instanceof List<?> candidates
                        && candidates.contains("secrets/unrouted-scope.txt"),
                "unrouted historical sidecars must not contribute to any scope");
        var scopeCandidates = plan.get("contractScopeCandidates");
        assertTrue(scopeCandidates instanceof List<?> scopeList
                        && !scopeList.contains("secrets/unrouted-scope.txt"),
                "unrouted evidence must not appear in contract scope candidates");
    }

    @Test
    void recentAgentOutputsRenderContractSidecarSummary() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Sidecar summary test.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-001/outputs/design-codex-review.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{\"untrusted\":\"Long review body\"}", StandardCharsets.UTF_8);
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
        publishSidecar(output.getParent().resolve("design-codex-review.contract.json"));
        var rogueDir = wsDir.resolve("runs/run-rogue/outputs");
        Files.createDirectories(rogueDir);
        Files.writeString(rogueDir.resolve("rogue.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(rogueDir.resolve("rogue.contract.json"), """
                {
                  "node_id": "design.codex_arch_review",
                  "decision": "BLOCKED",
                  "summary": "UNROUTED_ROGUE_HISTORY_MUST_NOT_APPEAR"
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
        assertFalse(markdown.contains("UNROUTED_ROGUE_HISTORY_MUST_NOT_APPEAR"));
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
        publishSidecar(output.getParent().resolve("self-test.contract.json"));
        Files.createDirectories(wsDir.resolve("artifacts/git-evidence"));
        Files.writeString(wsDir.resolve("artifacts/git-evidence/git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "tc-1",
                  "version": 1,
                  "created_at": "2026-06-18T00:00:00Z",
                  "objective": "Verify context package raw-output hygiene.",
                  "status": "READY_FOR_REVIEW",
                  "scope": {"included": [{"path":"miniprogram/src/pages/record/index.vue","operation":"MODIFY"}], "excluded": []},
                  "acceptance_criteria": ["validated evidence is projected", "raw output is omitted"],
                  "test_requirements": ["Run context projection tests"],
                  "non_goals": ["Do not change unrelated pages"],
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
        TaskContractProjectionFixture.seedCompileBinding(tempDir, "ws-ctx");

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
    void inputArtifactAgentOutputOnlySurfacesValidatedProjection() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Need a canonical task contract proposal.", StandardCharsets.UTF_8);
        var output = wsDir.resolve("runs/run-004/outputs/intake-claude-brainstorm.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{\"objective\":\"unvalidated objective must stay hidden\"}", StandardCharsets.UTF_8);
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
        publishSidecar(output.getParent().resolve("intake-claude-brainstorm.contract.json"));

        var result = handler.execute(ctx("product_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-004/outputs/intake-claude-brainstorm.json")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Candidate task contract proposal is ready."));
        assertFalse(markdown.contains("unvalidated objective must stay hidden"));
        assertTrue(markdown.contains("- Raw Output: "));
    }

    @Test
    void productReviewContextProjectsCompleteProposalFromValidatedReference() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Review the proposal.", StandardCharsets.UTF_8);
        var sidecar = wsDir.resolve("runs/run-proposal/outputs/intake-claude-brainstorm.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "intake.claude_brainstorm",
                  "source_artifact_type": "task_contract_proposal",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "summary": "Proposal ready",
                  "task_contract_proposal": {
                    "artifact_type": "task_contract_proposal",
                    "schema_version": 3,
                    "objective": "Review it",
                    "scope": {
                      "included": [{"path": "README.md", "operation": "MODIFY"}],
                      "excluded": []
                    },
                    "acceptance_criteria": [
                      {"id": "AC-1", "text": "Reviewed"},
                      {"id": "AC-2", "text": "Approved"}
                    ],
                    "test_requirements": [{"id": "TEST-1", "text": "Inspect"}],
                    "constraints": [],
                    "non_goals": [],
                    "risk_areas": [],
                    "evidence_refs": ["README.md"],
                    "open_questions": [],
                    "revision_responses": []
                  }
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(sidecar);
        var document = AgentOutputContractDocument.read(sidecar, objectMapper).orElseThrow();
        var published = TaskContractProposalReference.publishInitial(
                tempDir, "ws-ctx", sidecar, document, objectMapper);

        var result = handler.execute(ctx("product_review", List.of(
                relative(tempDir, sidecar), published.referenceRef()), "intake.codex_product_review"));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertTrue(result.outputRefs().contains(published.referenceRef()));
        assertFalse(result.outputRefs().contains(relative(tempDir, sidecar)));
        assertEquals(2, result.outputRefs().size(), "only context package and typed proposal ref may cross");
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("## Required Review Subject"));
        assertTrue(markdown.contains("<!-- dual:typed-payload:start -->"));
        assertTrue(markdown.contains("<!-- dual:typed-payload:end -->"));
        assertTrue(markdown.contains("\"objective\" : \"Review it\""));
        assertTrue(markdown.contains("\"path\" : \"README.md\""));
        assertTrue(markdown.contains("\"operation\" : \"MODIFY\""));
        assertTrue(markdown.contains("\"id\" : \"AC-1\""));
        assertTrue(markdown.contains("\"text\" : \"Reviewed\""));
        assertTrue(markdown.contains("\"evidence_refs\" : [ \"README.md\" ]"));
        assertTrue(markdown.contains(published.reference().sha256()));
    }

    @Test
    void productReviewContextRejectsTamperedProposalBeforeReviewerCanStart() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Review the proposal.", StandardCharsets.UTF_8);
        var sidecar = wsDir.resolve("runs/run-proposal/outputs/intake-claude-brainstorm.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "intake.claude_brainstorm",
                  "source_artifact_type": "task_contract_proposal",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "summary": "Proposal ready",
                  "task_contract_proposal": {
                    "artifact_type": "task_contract_proposal",
                    "schema_version": 3,
                    "objective": "Original objective",
                    "scope": {
                      "included": [{"path": "README.md", "operation": "MODIFY"}],
                      "excluded": []
                    },
                    "acceptance_criteria": [
                      {"id": "AC-1", "text": "Reviewed"},
                      {"id": "AC-2", "text": "Approved"}
                    ],
                    "test_requirements": [{"id": "TEST-1", "text": "Inspect"}],
                    "constraints": [],
                    "non_goals": [{"id": "NON_GOAL-1", "text": "No source changes"}],
                    "risk_areas": [],
                    "evidence_refs": ["README.md"],
                    "open_questions": [],
                    "revision_responses": []
                  }
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(sidecar);
        var document = AgentOutputContractDocument.read(sidecar, objectMapper).orElseThrow();
        var published = TaskContractProposalReference.publishInitial(
                tempDir, "ws-ctx", sidecar, document, objectMapper);
        Files.writeString(sidecar, Files.readString(sidecar).replace("Original objective", "Tampered objective"));

        var result = handler.execute(ctx(
                "product_review",
                List.of(relative(tempDir, sidecar), published.referenceRef()),
                "intake.codex_product_review"));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals(
                "verified task-contract proposal reference is required for product review",
                result.result().get("error"));
        assertFalse(Files.exists(wsDir.resolve("context/node-001.md")));
    }

    @Test
    void productReviewContextRejectsVerifiedReferenceToUnsupportedProposalVersion() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Review the proposal.", StandardCharsets.UTF_8);
        var sidecar = wsDir.resolve("runs/run-v2/outputs/intake-claude-brainstorm.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "intake.claude_brainstorm",
                  "source_artifact_type": "task_contract_proposal",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "summary": "Legacy proposal",
                  "task_contract_proposal": {
                    "artifact_type": "task_contract_proposal",
                    "schema_version": 2,
                    "objective": "Legacy objective",
                    "scope": {"included": [{"path": "README.md", "operation": "MODIFY"}], "excluded": []},
                    "acceptance_criteria": [
                      {"id": "AC-1", "text": "Reviewed"},
                      {"id": "AC-2", "text": "Approved"}
                    ],
                    "test_requirements": [{"id": "TEST-1", "text": "Inspect"}],
                    "constraints": [], "non_goals": [], "risk_areas": [],
                    "evidence_refs": [], "open_questions": [], "revision_responses": []
                  }
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(sidecar);
        var document = AgentOutputContractDocument.read(sidecar, objectMapper).orElseThrow();
        var published = TaskContractProposalReference.publishInitial(
                tempDir, "ws-ctx", sidecar, document, objectMapper);

        var result = handler.execute(ctx(
                "product_review", List.of(published.referenceRef()), "intake.codex_product_review"));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("verified task-contract proposal reference is required for product review",
                result.result().get("error"));
        assertFalse(Files.exists(wsDir.resolve("context/node-001.md")));
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

                untrusted terminal-looking prose
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("architecture_review", List.of(
                ".agent/workstreams/ws-ctx/runs/run-003/outputs/intake-claude-brainstorm.transcript.log")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("execution transcript omitted from context"));
        assertFalse(markdown.contains("[tool] Read huge-file.md"));
        assertFalse(markdown.contains("massive tool output that must not enter context"));
        assertFalse(markdown.contains("untrusted terminal-looking prose"));
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

        var result = handler.execute(ctx("full", List.of(
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
    void activeProfileKeepsRecentAgentOutputsWithoutDuplicateRequirementPressure() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Short req.", StandardCharsets.UTF_8);

        // Keep the same populated implementation profile that previously overflowed
        // after requirement was projected a second time by the context builder.
        var input = wsDir.resolve("runs/run-001/outputs/design.md");
        Files.createDirectories(input.getParent());
        writeAgentOutputWithSidecar(input, "Design output.");

        Files.createDirectories(wsDir.resolve("runs/run-002/outputs"));
        Files.createDirectories(wsDir.resolve("runs/run-003/outputs"));
        writeAgentOutputWithSidecar(
                wsDir.resolve("runs/run-002/outputs/agent.md"), "Output from run 2.");
        writeAgentOutputWithSidecar(
                wsDir.resolve("runs/run-003/outputs/agent.md"), "Output from run 3.");

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
        assertTrue(markdown.contains("recent_agent_outputs"),
                "recent_agent_outputs should appear in governance table, got: " + markdown);
        assertTrue(markdown.contains("| recent_agent_outputs | full | no |"),
                "duplicate requirement removal should preserve full recent outputs, got: " + markdown);
        assertFalse(markdown.contains("Short req."),
                "active profile must not project the requirement into the context package");
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
                    "included": [{"path": "src/issueWorkflow.js", "operation": "MODIFY"}],
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
        TaskContractProjectionFixture.seedCompileBinding(tempDir, "ws-ctx");
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
        publishSidecar(wsDir.resolve("runs/run-001/outputs/acceptance.contract.json"));
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
        publishSidecar(wsDir.resolve("runs/run-004/outputs/acceptance-codex-review.contract.json"));

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
        publishSidecar(wsDir.resolve("runs/run-routed/outputs/acceptance-codex-review.contract.json"));
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
        assertFalse(markdown.contains("miniprogram/src/pages/record/index.vue"),
                "unverified handoff change claims must not be projected as repository facts");

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
        publishSidecar(wsDir.resolve("runs/run-001/outputs/design-claude.contract.json"));
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "task-ws",
                  "version": 1,
                  "created_at": "2026-06-21T00:00:00Z",
                  "objective": "Keep review evidence scoped.",
                  "status": "READY_FOR_REVIEW",
                  "scope": {
                    "included": [{"path": "docs/design.md", "operation": "MODIFY"}],
                    "excluded": []
                  },
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
        publishSidecar(wsDir.resolve("runs/run-001/outputs/design-claude.contract.json"));

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
                    "included": [
                      {
                        "path": "miniprogram/src/pages/treatment-record/index.vue",
                        "operation": "MODIFY"
                      },
                      {
                        "path": "miniprogram/src/api/treatmentRecord.ts",
                        "operation": "CREATE"
                      }
                    ],
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
        assertTrue(markdown.contains("agent-capability-catalog.json"));
        assertFalse(markdown.contains("exploration-plan-node-001.json"));
        assertTrue(markdown.contains("miniprogram/src/pages/treatment-record/index.vue"));
        assertTrue(markdown.contains("Already inspected files"));
        assertTrue(markdown.contains("Read reuse rule"));
        assertTrue(markdown.contains("their recorded sha is still trusted"));
        assertTrue(markdown.contains("Previously read source file"));

        var metadataPath = tempDir.resolve((String) result.result().get("context_package_metadata"));
        var metadata = objectMapper.readValue(metadataPath.toFile(), Map.class);
        assertEquals(true, metadata.get("contextGovernanceEnabled"));
        var occurrenceRef = ".agent/workstreams/ws-ctx/artifacts/context-governance/occurrences/node-001/";
        assertEquals(occurrenceRef + "repo-map.json", metadata.get("repoMap"));
        assertEquals(occurrenceRef + "file-relevance-plan.json", metadata.get("fileRelevancePlan"));
        assertEquals(occurrenceRef + "stage-scope-policy.json", metadata.get("stageScopePolicy"));
        assertEquals(occurrenceRef + "planned-report.json", metadata.get("contextGovernanceReport"));
        assertEquals(".agent/workstreams/ws-ctx/memory/workstream-memory.json", metadata.get("workstreamMemory"));
        assertEquals(occurrenceRef + "working-set.json", metadata.get("workingSet"));
        assertEquals(occurrenceRef + "file-summaries.json", metadata.get("fileSummaries"));
        assertEquals(occurrenceRef + "read-reuse-contract.json", metadata.get("readReuseContract"));
        assertEquals(occurrenceRef + "agent-capability-catalog.json", metadata.get("agentCapabilityCatalog"));
        assertEquals(occurrenceRef + "binding.json", metadata.get("repositoryFactBinding"));
        assertEquals(64, String.valueOf(metadata.get("repositoryFactBindingSha256")).length());
        assertFalse(metadata.containsKey("agentExplorationPlan"));

        var plan = objectMapper.readValue(occurrenceArtifact(wsDir, "file-relevance-plan.json").toFile(), Map.class);
        assertEquals("ctx.test", plan.get("builderNodeDefinitionId"));
        assertEquals("next.node", plan.get("targetNodeDefinitionId"));
        assertTrue(plan.get("readCandidates") instanceof List<?> candidates
                        && candidates.contains("miniprogram/src/pages/treatment-record/index.vue")
                        && candidates.contains("miniprogram/src/api/treatmentRecord.ts"),
                "file relevance plan should preserve task-contract file scope");
        assertTrue(plan.get("previouslyReadFiles") instanceof List<?> previousReads
                        && previousReads.contains("miniprogram/src/pages/treatment-record/index.vue"),
                "file relevance plan should surface prior reads so the next agent can avoid repeated exploration");
        assertTrue(plan.get("repeatedReadAvoidance") instanceof List<?> avoidance
                        && !avoidance.isEmpty(),
                "file relevance plan should include typed repeated-read avoidance hints");

        var policy = objectMapper.readValue(occurrenceArtifact(wsDir, "stage-scope-policy.json").toFile(), Map.class);
        assertEquals("implement", policy.get("stageId"));
        assertFalse(policy.containsKey("scopeExceedPolicy"),
                "stage scope guidance must not own a change-boundary failure mode");
        assertFalse(policy.containsKey("readScope"));
        assertFalse(policy.containsKey("explorationBudget"));
        assertEquals("ctx.test", policy.get("builderNodeDefinitionId"));
        assertEquals("next.node", policy.get("targetNodeDefinitionId"));
        assertTrue(policy.get("writeScope") instanceof List<?> writeScope
                        && writeScope.contains("miniprogram/src/pages/treatment-record/index.vue")
                        && writeScope.contains("miniprogram/src/api/treatmentRecord.ts"),
                "implementation policy should expose scoped writable files");
        assertTrue(policy.get("readReusePolicy") instanceof Map<?, ?> reusePolicy
                        && Boolean.TRUE.equals(reusePolicy.get("preferExistingEvidence")),
                "stage scope policy should carry read reuse rules as typed data, not only prompt text");

        var workingSet = objectMapper.readValue(occurrenceArtifact(wsDir, "working-set.json").toFile(), Map.class);
        assertEquals("working_set", workingSet.get("artifactType"));
        assertTrue(workingSet.get("coreFiles") instanceof List<?> coreFiles
                        && !coreFiles.isEmpty(),
                "working set should expose bounded core files for the next agent");

        var fileSummaries = objectMapper.readValue(occurrenceArtifact(wsDir, "file-summaries.json").toFile(), Map.class);
        assertEquals("file_summaries", fileSummaries.get("artifactType"));
        assertTrue(fileSummaries.get("files") instanceof List<?> summaries
                        && !summaries.isEmpty(),
                "file summaries should give agents reusable memory before repeated reads");

        var readReuse = objectMapper.readValue(occurrenceArtifact(wsDir, "read-reuse-contract.json").toFile(), Map.class);
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

        assertTrue(policy.get("writeScope") instanceof List<?> edits
                        && edits.contains("miniprogram/src/pages/treatment-record/index.vue"),
                "stage guidance should project the contract change intent without read authority");
        var workstreamMemory = objectMapper.readValue(wsDir.resolve("memory/workstream-memory.json").toFile(), Map.class);
        assertEquals("workstream_memory", workstreamMemory.get("artifactType"));
        assertEquals("latest_projection_refs", workstreamMemory.get("refsSemantics"));
        assertTrue(workstreamMemory.get("readFiles") instanceof List<?> remembered
                        && !remembered.isEmpty(),
                "context governance must preserve typed read memory instead of overwriting it with refs only");
        assertTrue(workstreamMemory.get("refs") instanceof Map<?, ?> refs
                        && refs.containsKey("readReuseContract"),
                "workstream memory should index the reusable memory sidecars");

        var report = objectMapper.readValue(occurrenceArtifact(wsDir, "planned-report.json").toFile(), Map.class);
        assertEquals("planned", report.get("reportPhase"));
        assertEquals("ctx.test", report.get("builderNodeDefinitionId"));
        assertEquals("next.node", report.get("targetNodeDefinitionId"));
        assertTrue(report.get("plannedRepeatedReadAvoidance") instanceof List<?> plannedAvoidance
                        && !plannedAvoidance.isEmpty(),
                "planned governance report should record repeated-read risks before execution");
        assertEquals(true, report.get("telemetryPending"));
    }

    @Test
    void governedStageGuidanceContainsNoReadAuthority() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Keep stage governance explicit.", StandardCharsets.UTF_8);

        for (var stage : List.of("product_review", "architecture_review", "plan_review", "implementation", "acceptance")) {
            var result = handler.execute(ctx(stage, List.of()));

            assertEquals(NodeStatus.COMPLETED, result.status(), stage);
        var policy = objectMapper.readValue(occurrenceArtifact(wsDir, "stage-scope-policy.json").toFile(), Map.class);
            assertFalse(policy.containsKey("scopeExceedPolicy"), stage);
            assertFalse(policy.containsKey("readScope"), stage);
            assertFalse(policy.containsKey("explorationBudget"), stage);
            assertFalse(Files.exists(wsDir.resolve("memory/exploration-plan-node-001.json")), stage);
        }
    }

    @Test
    void unroutedWorkspaceFilesCannotEnterContextThroughRepoHeuristics() throws Exception {
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
        assertFalse(markdown.contains("Seed Evidence Digests"));
        assertFalse(markdown.contains("backend/app/api/treatment_events.py"));
        assertFalse(markdown.contains("miniprogram/src/pages/follow-up-summary/index.vue"));
        assertFalse(markdown.contains("Treatment Event Chain API Usage"));

        var plan = objectMapper.readValue(occurrenceArtifact(wsDir, "file-relevance-plan.json").toFile(), Map.class);
        assertTrue(plan.get("readCandidates") instanceof List<?> candidates
                        && candidates.isEmpty(),
                "unrouted workspace files must not become context candidates");
        assertTrue(plan.get("contractScopeCandidates") instanceof List<?> scopeCandidates
                        && scopeCandidates.isEmpty(),
                "context discovery must not invent canonical contract scope");
        var policy = objectMapper.readValue(
                occurrenceArtifact(wsDir, "stage-scope-policy.json").toFile(), Map.class);
        assertTrue(policy.get("writeScope") instanceof List<?> writeScope && writeScope.isEmpty(),
                "unrouted workspace files must not become change intent");
        assertFalse(plan.containsKey("seedEvidenceDigests"),
                "context plans must not carry workspace-derived seed content");
    }

    @Test
    void unknownRoutedFileIsPointerOnlyAndCannotInjectContextContent() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Use only typed routed evidence.", StandardCharsets.UTF_8);
        var unknown = wsDir.resolve("artifacts/unknown-note.md");
        Files.createDirectories(unknown.getParent());
        Files.writeString(unknown, "UNTRUSTED_CONTEXT_PAYLOAD", StandardCharsets.UTF_8);
        var ref = tempDir.relativize(unknown).toString();

        var result = handler.execute(ctx("implementation", List.of(ref)));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains(ref));
        assertFalse(markdown.contains("UNTRUSTED_CONTEXT_PAYLOAD"));
        var plan = objectMapper.readValue(occurrenceArtifact(wsDir, "file-relevance-plan.json").toFile(), Map.class);
        assertFalse(plan.get("readCandidates") instanceof List<?> candidates && candidates.contains(ref),
                "an unknown routed ref must not become a repository-read candidate");
    }

    @Test
    void observedAndHeuristicFilesCannotExpandCanonicalChangeBoundary() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts/git-evidence"));
        Files.writeString(wsDir.resolve("requirement.md"),
                "Inspect src/recommended.rs and src/observed.go.", StandardCharsets.UTF_8);
        writeFile("src/allowed.java", "class Allowed {}\n");
        writeFile("src/recommended.rs", "pub fn recommended() {}\n");
        writeFile("src/observed.go", "package observed\n");
        Files.writeString(wsDir.resolve("artifacts/git-evidence/changed-files.txt"),
                "src/observed.go\n", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("artifacts/task-contract.json"), """
                {
                  "id": "task-scope",
                  "version": 1,
                  "created_at": "2026-07-23T00:00:00Z",
                  "objective": "Keep change intent contract-owned.",
                  "status": "READY_FOR_REVIEW",
                  "scope": {
                    "included": [
                      {"path": "src/allowed.java", "operation": "MODIFY"},
                      {"path": "docs/API contract.md", "operation": "CREATE"}
                    ],
                    "excluded": []
                  },
                  "acceptance_criteria": ["Allowed file remains in change boundary", "Observed files do not expand change intent"],
                  "test_requirements": ["Run scope tests"],
                  "non_goals": [],
                  "constraints": ["Do not widen scope"],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "compilation_pass": 1,
                  "source": {"raw_request_ref": "requirement.md", "created_by": "test"}
                }
                """, StandardCharsets.UTF_8);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var relevancePlan = objectMapper.readValue(
                occurrenceArtifact(wsDir, "file-relevance-plan.json").toFile(), Map.class);
        assertFalse(relevancePlan.get("readCandidates") instanceof List<?> candidates
                        && candidates.contains("src/recommended.rs"),
                "filename and requirement heuristics must not enter prepared context");
        assertTrue(relevancePlan.get("readCandidates") instanceof List<?> candidates
                        && candidates.contains("src/observed.go"),
                "typed Git evidence remains an authoritative observation");
        assertEquals(
                List.of("src/allowed.java", "docs/API contract.md"),
                relevancePlan.get("contractScopeCandidates"));

        var policy = objectMapper.readValue(
                occurrenceArtifact(wsDir, "stage-scope-policy.json").toFile(), Map.class);
        assertEquals(
                List.of("src/allowed.java", "docs/API contract.md"),
                policy.get("writeScope"),
                "only canonical TaskContract.scope may define write candidates");
        assertFalse(policy.containsKey("readScope"));
    }

    @Test
    void canonicalScopeIsNotTruncatedByRecommendationLimit() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("requirement.md"), "Use the declared scope.", StandardCharsets.UTF_8);
        var included = new java.util.ArrayList<Map<String, Object>>();
        for (var index = 0; index < 30; index++) {
            included.add(Map.of("path", "src/file-" + index + ".go", "operation", "CREATE"));
        }
        var contract = new LinkedHashMap<String, Object>();
        contract.put("id", "task-many-paths");
        contract.put("version", 1);
        contract.put("created_at", "2026-07-23T00:00:00Z");
        contract.put("objective", "Keep every declared path.");
        contract.put("status", "READY_FOR_REVIEW");
        contract.put("scope", Map.of("included", included, "excluded", List.of()));
        contract.put("acceptance_criteria", List.of("All paths remain in change boundary", "No recommendation truncation"));
        contract.put("test_requirements", List.of("Run scope tests"));
        contract.put("non_goals", List.of());
        contract.put("constraints", List.of("Do not truncate canonical scope"));
        contract.put("risk_areas", List.of());
        contract.put("ambiguity_notes", List.of());
        contract.put("human_questions", List.of());
        contract.put("compilation_pass", 1);
        contract.put("source", Map.of("raw_request_ref", "requirement.md", "created_by", "test"));
        objectMapper.writeValue(wsDir.resolve("artifacts/task-contract.json").toFile(), contract);

        var result = handler.execute(ctx("implementation", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var relevancePlan = objectMapper.readValue(
                occurrenceArtifact(wsDir, "file-relevance-plan.json").toFile(), Map.class);
        assertTrue(relevancePlan.get("contractScopeCandidates") instanceof List<?> scope
                        && scope.size() == 30
                        && scope.contains("src/file-29.go"),
                "canonical contract scope must not inherit the 24-file recommendation limit");
        var policy = objectMapper.readValue(
                occurrenceArtifact(wsDir, "stage-scope-policy.json").toFile(), Map.class);
        assertTrue(policy.get("writeScope") instanceof List<?> writeScope
                        && writeScope.size() == 30,
                "writeScope must carry all contract scope entries");
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

        var proposalRef = publishProposalReference("run-review-allowance", "Review source evidence");
        var result = handler.execute(ctx(
                "product_review", List.of(proposalRef), "intake.codex_product_review"));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var readReuse = objectMapper.readValue(occurrenceArtifact(wsDir, "read-reuse-contract.json").toFile(), Map.class);
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
        var readReuse = objectMapper.readValue(occurrenceArtifact(wsDir, "read-reuse-contract.json").toFile(), Map.class);
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
        assertFalse(Files.exists(wsDir.resolve("artifacts/context-governance/occurrences/node-001")));
    }

    @Test
    void acceptancePreservesQualifiedContractBeyondSoftBudgetAndRecordsWarning() throws Exception {
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("requirement.md"), "Review the complete contract.");
        var objective = "complete-contract-fact-".repeat(700);
        var contract = new TaskContract(
                "tc-large", 1, Instant.parse("2026-08-26T00:00:00Z"), objective,
                new TaskContract.Scope(
                        List.of(new TaskContract.ScopeEntry("src/Auth.java", TaskContract.ScopeOperation.MODIFY)),
                        List.of("unrelated files")),
                List.of("registration works", "login works"),
                List.of("Run authentication tests"),
                List.of("Keep the existing API"),
                List.of("Do not add SSO"),
                List.of(), List.of(), List.of("src/Auth.java"),
                ContractStatus.READY_FOR_REVIEW, 1,
                new TaskContract.Source("requirement.md", "contract_compiler"));
        TaskContractArtifactWriter.writeJson(contract, wsDir.resolve("artifacts"));
        TaskContractProjectionFixture.seedCompileBinding(tempDir, "ws-ctx");

        var result = handler.execute(ctx("acceptance", List.of()));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains(objective), "the complete qualified contract must survive the soft ceiling");
        assertEquals(1, occurrences(markdown, TypedContextPayloadBoundary.START),
                "typed marker index=" + markdown.indexOf("dual:typed")
                        + ", objective index=" + markdown.indexOf(objective));
        var metadata = objectMapper.readTree(
                tempDir.resolve((String) result.result().get("context_package_metadata")).toFile());
        assertTrue(metadata.path("budgetWarnings").toString().contains("task_contract"));
        assertTrue(metadata.path("sections").toString().contains("softBudgetExceeded"));
    }

    @Test
    void acceptanceSectionOverrideFailsBeforePublishingContextArtifacts() throws Exception {
        Files.createDirectories(wsDir);
        var definition = new NodeDefinition("ctx.acceptance", NodeKind.TASK, "context.build_package", Map.of(
                "target_node", "acceptance.codex_verifier_review",
                "profile", "acceptance",
                "include_sections", List.of("task_contract")));
        var context = new TaskHandler.TaskContext(
                tempDir, "ws-ctx", definition,
                new NodeInstance("node-acceptance", definition.id(), NodeKind.TASK, definition.handlerType(),
                        NodeStatus.READY, "acceptance", "iter-001", List.of(), List.of(), Map.of(),
                        null, null, null));

        var result = handler.execute(context);

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("MANDATORY_CONTEXT_SECTION_OVERRIDE_REJECTED", result.outcome());
        assertTrue(result.outputRefs().isEmpty());
        assertFalse(Files.exists(wsDir.resolve("context")));
    }

    // --- Helper ---

    private void writeFile(String relativePath, String content) throws Exception {
        var path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private void publishSidecar(Path sidecar) throws Exception {
        AgentOutputLineageFixture.completeAndPublish(tempDir, "ws-ctx", sidecar);
    }

    private String publishProposalReference(String runId, String objective) throws Exception {
        var sidecar = wsDir.resolve("runs").resolve(runId)
                .resolve("outputs/intake-claude-brainstorm.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "intake.claude_brainstorm",
                  "source_artifact_type": "task_contract_proposal",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "summary": "Proposal ready",
                  "task_contract_proposal": {
                    "artifact_type": "task_contract_proposal",
                    "schema_version": 3,
                    "objective": "%s",
                    "scope": {
                      "included": [{"path": "README.md", "operation": "MODIFY"}],
                      "excluded": []
                    },
                    "acceptance_criteria": [
                      {"id": "AC-1", "text": "Review the proposal"},
                      {"id": "AC-2", "text": "Record a decision"}
                    ],
                    "test_requirements": [{"id": "TEST-1", "text": "Inspect README.md"}],
                    "constraints": [],
                    "non_goals": [{"id": "NON_GOAL-1", "text": "No source changes"}],
                    "risk_areas": [],
                    "evidence_refs": ["README.md"],
                    "open_questions": [],
                    "revision_responses": []
                  }
                }
                """.formatted(objective), StandardCharsets.UTF_8);
        publishSidecar(sidecar);
        var document = AgentOutputContractDocument.read(sidecar, objectMapper).orElseThrow();
        return TaskContractProposalReference.publishInitial(
                tempDir, "ws-ctx", sidecar, document, objectMapper).referenceRef();
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static Path occurrenceArtifact(Path workstreamDir, String fileName) {
        return workstreamDir.resolve("artifacts/context-governance/occurrences/node-001").resolve(fileName);
    }

    private static int occurrences(String content, String value) {
        var count = 0;
        var offset = 0;
        while ((offset = content.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private TaskHandler.TaskContext ctx(String profile, List<String> inputRefs) {
        return ctx(profile, inputRefs, "next.node");
    }

    private TaskHandler.TaskContext ctx(String profile, List<String> inputRefs, String targetNode) {
        var config = new LinkedHashMap<String, Object>();
        config.put("target_node", targetNode);
        config.put("profile", profile);
        if ("intake.codex_product_review".equals(targetNode)) {
            config.put("required_input_artifact", "task_contract_proposal");
        }
        return new TaskHandler.TaskContext(
                tempDir, "ws-ctx",
                new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.copyOf(config)),
                new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", inputRefs, List.of(), Map.of(), null, null, null)
        );
    }

    private TaskHandler.TaskContext planReviewContext(List<String> inputRefs) {
        var config = Map.<String, Object>of(
                "target_node", "plan.codex_plan_review",
                "profile", "plan_review",
                "required_agent_evidence", Map.of(
                        "node_id", "plan.claude_plan",
                        "source_artifact_type", "plan_result",
                        "decision", "READY_FOR_PLAN_REVIEW")
        );
        return new TaskHandler.TaskContext(
                tempDir,
                "ws-ctx",
                new NodeDefinition("ctx.plan-review", NodeKind.TASK, "context.build_package", config),
                new NodeInstance("node-plan-review-context", "ctx.plan-review", NodeKind.TASK,
                        "context.build_package", NodeStatus.READY, "stage-plan", "iter-001",
                        inputRefs, List.of(), Map.of(), null, null, null)
        );
    }

    private void writeAgentOutputWithSidecar(Path output, String summary) throws Exception {
        Files.writeString(output, "{}");
        Files.writeString(AgentOutputContractSidecar.pathFor(output), """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "fixture.agent",
                  "decision": "READY",
                  "summary": "%s",
                  "must_fix": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "fixture verified"
                }
                """.formatted(summary));
        AgentOutputLineageFixture.completeAndPublish(
                tempDir, "ws-ctx", AgentOutputContractSidecar.pathFor(output));
    }
}
