package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.runtime.handler.TypedContextPayloadBoundary;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeExecutionDocument;
import dev.dual.ledger.file.NodeExecutionStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.runtime.handler.AgentOutputContractSidecar;
import dev.dual.runtime.handler.TaskContractProposalReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void qualifiedGoalPromptsPermitFileProposalsWithoutPretendingExactScopeWasApproved() throws Exception {
        for (var topology : dev.dual.runtime.handler.AuthoredStageSequenceTest.PlanTopology.values()) {
            var ledger = AcpxTestLedger.ledger();
            var root = tempDir.resolve(topology.name());
            var fixture = new dev.dual.runtime.handler.AuthoredStageSequenceTest.Fixture(
                    root, "src/new_product.py", topology, "ws-phase", null, ledger);
            for (var target : List.of(
                    Map.entry("design.claude_design", "design-claude"),
                    Map.entry("design.codex_arch_review", "design-codex-review"),
                    Map.entry("plan.claude_plan", "plan-claude"),
                    Map.entry("plan.codex_plan_review", "plan-codex-review"))) {
                var context = fixture.before(target.getKey());
                var prepared = dev.dual.runtime.handler.AgentRunGovernanceContext.ensureQualified(context, "render-proof");
                assertEquals(dev.dual.core.contract.TaskContract.Phase.GOAL,
                        prepared.qualifiedTaskContract().orElseThrow().phase());
                assertTrue(prepared.qualifiedTaskContract().orElseThrow().scope().included().isEmpty());
                var refs = new java.util.ArrayList<>(context.instance().inputRefs());
                refs.add(prepared.contextRef());
                var prompt = new AgentPromptRenderer(ledger).renderWithSections(
                        root, "ws-phase", "render-proof", target.getKey(), target.getValue(), refs, null,
                        prepared.trustedTaskContractPayload().orElseThrow()).content();
                assertFalse(prompt.contains("Copy exact canonical paths and operations from the approved TaskContract scope"), target.toString());
                assertFalse(prompt.contains("exact paths and operations must be authorized by the approved TaskContract"), target.toString());
                assertTrue(prompt.contains("GOAL"), target.toString());
                assertTrue(prompt.contains("proposals, not authorization"), target.toString());
                assertTrue(prompt.contains("EXECUTION"), target.toString());
                assertFalse(Files.exists(root.resolve("src/new_product.py")), "a proposed path is not an implementation");
            }
        }
    }

    @Test
    void qualifiedExecutionPromptRetainsExactPathAndOperationApprovalBeforeImplementation() throws Exception {
        var ledger = AcpxTestLedger.ledger();
        var root = tempDir.resolve("execution");
        var fixture = new dev.dual.runtime.handler.AuthoredStageSequenceTest.Fixture(
                root, "src/new_product.py",
                dev.dual.runtime.handler.AuthoredStageSequenceTest.PlanTopology.AUTOMATIC,
                "ws-phase", null, ledger);
        var context = fixture.before("implement.claude_implement");
        var prepared = dev.dual.runtime.handler.AgentRunGovernanceContext.ensureQualified(context, "render-proof");
        assertEquals(dev.dual.core.contract.TaskContract.Phase.EXECUTION,
                prepared.qualifiedTaskContract().orElseThrow().phase());
        assertTrue(prepared.qualifiedTaskContract().orElseThrow().scope().included().stream()
                .anyMatch(entry -> entry.path().equals("src/new_product.py")));
        var refs = new java.util.ArrayList<>(context.instance().inputRefs());
        refs.add(prepared.contextRef());
        var prompt = new AgentPromptRenderer(ledger).renderWithSections(
                root, "ws-phase", "render-proof", "implement.claude_implement", "implement-claude", refs, null,
                prepared.trustedTaskContractPayload().orElseThrow()).content();
        assertTrue(prompt.contains("Copy exact canonical paths and operations from the approved EXECUTION TaskContract scope"));
        assertTrue(prompt.contains("Neither a reviewed design nor a reviewed plan authorizes an additional path or operation"));
        assertTrue(prompt.contains("src/new_product.py"));
        assertFalse(Files.exists(root.resolve("src/new_product.py")), "rendering cannot execute the approved implementation");
    }

    @Test
    void renderWithSectionsProducesContentAndProvenance() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.");

        var renderer = new AgentPromptRenderer(AcpxTestLedger.ledger());
        var rendered = renderer.renderWithSections(tempDir, "ws-test", "intake.claude_brainstorm",
                "intake-claude-brainstorm", List.of());

        // content must be byte-identical to render() — provenance must not change the prompt
        assertEquals(
                renderer.render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of()),
                rendered.content());
        var names = rendered.sections().stream().map(AgentPromptRenderer.PromptSection::name).toList();
        assertTrue(names.contains("workspace_guard"));
        assertTrue(names.contains("requirement"));
        assertTrue(names.contains("context_package"));
        assertTrue(names.contains("prompt_contract"));
        // section bytes sum is reasonable (non-zero, ≤ 2x content — joins/placeholders)
        var sectionBytes = rendered.sections().stream()
                .mapToLong(s -> s.content().getBytes(StandardCharsets.UTF_8).length).sum();
        var contentBytes = rendered.content().getBytes(StandardCharsets.UTF_8).length;
        assertTrue(sectionBytes > 0);
        assertTrue(sectionBytes <= contentBytes * 2L,
                "sectionBytes=" + sectionBytes + " contentBytes=" + contentBytes);
    }

    @Test
    void rendersTemplateWithWorkstreamAndRequirement() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of());

        assertTrue(prompt.contains("Workstream: ws-test"));
        assertTrue(prompt.contains("Node: intake.claude_brainstorm"));
        assertTrue(prompt.contains("Build a safer agent workflow."));
        assertTrue(prompt.contains("\"decision\" : \"READY_FOR_PRODUCT_REVIEW\""));
        assertTrue(prompt.contains("This intake run uses restricted contract discovery"));
        assertTrue(prompt.contains("Do not output shell commands or Bash code fences"));
        assertFalse(prompt.contains("Run all shell commands from the effective workspace above."));
        assertTrue(prompt.contains("Do not create, edit, or delete files under .agent/"));
        assertTrue(prompt.contains("Only the orchestrator writes run ledger artifacts"));
        assertTrue(prompt.contains("do not start an open-ended repository survey"));
        assertTrue(prompt.contains("permits targeted Read/Grep/Glob discovery"));
        assertTrue(prompt.contains("Start from the projected repo map"));
        assertTrue(prompt.contains("Terminal, Bash, execute, write, edit, and file creation remain prohibited"));
        assertTrue(prompt.contains("A prose-only response is a contract failure"));
        assertTrue(prompt.contains("Explicitly address each Must Fix"));
        assertTrue(prompt.contains("Use the orchestrator-provided seed, tracked-path inventory, memory"));
        assertTrue(prompt.contains("account for every explicit user objective"));
        assertTrue(prompt.contains("A requirement mentioned only in `risk_areas`, `open_questions`, or `evidence_refs` is not preserved"));
        assertTrue(prompt.contains("Do not broaden or narrow behavior"));
        assertTrue(prompt.contains("preserve the literal user requirement"));
        assertTrue(prompt.contains("Do not copy the current node's `AgentResultEnvelope`"));
        assertTrue(prompt.contains("enforced independently by the orchestrator's Output Contract"));
        assertTrue(prompt.contains("`evidence_refs` must include at least one existing repository file"));
        assertFalse(prompt.contains("dual-control"));
        assertFalse(prompt.contains("<dual-artifact"));
        assertTrue(prompt.contains("\"artifact_type\" : \"task_contract_proposal\""));
    }

    @Test
    void intakeRenderingDistinguishesProductEvidenceFromControlBindings() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-evidence");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Create a small product in an empty repository.");
        var renderer = new AgentPromptRenderer(AcpxTestLedger.ledger());
        for (var pair : List.of(new String[]{"intake.claude_brainstorm", "intake-claude-brainstorm"},
                new String[]{"intake.codex_product_review", "intake-codex-product-review"})) {
            var prompt = renderer.render(tempDir, "ws-evidence", pair[0], pair[1], List.of());
            assertTrue(prompt.contains("evidence_refs are canonical product-tree file paths"));
            assertTrue(prompt.contains("empty tracked-path inventory permits evidence_refs=[]"));
            assertTrue(prompt.contains("Never copy .agent/ or .git/ references into evidence_refs"));
            assertFalse(prompt.contains("additional durable evidence references remain allowed"));
        }
    }

    @Test
    void nonIntakePromptsKeepShellWorkspaceGuard() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "implement.claude_implement", "implement-claude", List.of());

        assertTrue(prompt.contains("Run all shell commands from the effective workspace above."));
        assertFalse(prompt.contains("This intake run uses restricted contract discovery"));
    }

    @Test
    void autoPromptLanguageUsesChineseForChineseRequirement() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("settings.json"), """
                {
                  "schemaVersion": 1,
                  "promptLanguage": "auto"
                }
                """);
        Files.writeString(workstreamDir.resolve("requirement.md"), "给 README 增加一段项目说明。\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of());

        assertTrue(prompt.contains("Use Simplified Chinese for human-readable string values inside the final JSON result."));
        assertTrue(prompt.contains("Keep JSON keys, enum values"));
        assertTrue(prompt.contains("\"decision\" : \"READY_FOR_PRODUCT_REVIEW\""));
    }

    @Test
    void explicitPromptLanguageOverridesRequirementLanguage() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("settings.json"), """
                {
                  "schemaVersion": 1,
                  "promptLanguage": "en"
                }
                """);
        Files.writeString(workstreamDir.resolve("requirement.md"), "给 README 增加一段项目说明。\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of());

        assertTrue(prompt.contains("Use English for human-readable string values inside the final JSON result."));
        assertTrue(prompt.contains("Keep JSON keys, enum values"));
    }

    @Test
    void missingSettingsDefaultsPromptLanguageFromRequirement() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of());

        assertTrue(prompt.contains("Use English for human-readable string values inside the final JSON result."));
    }

    @Test
    void activeContextTargetsInjectRequirementExactlyOnce() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, "# Context Package\n\nImportant prior review.\n");

        var targets = Map.of(
                "intake.claude_brainstorm", "intake-claude-brainstorm",
                "intake.codex_product_review", "intake-codex-product-review",
                "design.codex_arch_review", "design-codex-review",
                "plan.codex_plan_review", "plan-codex-review",
                "implement.claude_implement", "implement-claude",
                "acceptance.codex_verifier_review", "acceptance-codex-review"
        );

        for (var target : targets.entrySet()) {
            var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                    tempDir,
                    "ws-test",
                    target.getKey(),
                    target.getValue(),
                    List.of(".agent/workstreams/ws-test/context/node-context.md")
            );

            assertTrue(prompt.contains("## Context Package"));
            assertTrue(prompt.contains(".agent/workstreams/ws-test/context/node-context.md"));
            assertTrue(prompt.contains("Important prior review."));
            assertTrue(prompt.contains("Build a safer agent workflow."));
            assertEquals(
                    prompt.indexOf("Build a safer agent workflow."),
                    prompt.lastIndexOf("Build a safer agent workflow."),
                    target.getKey() + " must inject requirement.md exactly once"
            );
        }
    }

    @Test
    void stripsAbsolutePathsOutsideWorkspaceFromContextPackagePreview() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, """
                # Context Package

                Previous output suggested:
                git -C /private/projects/internal-product status --short
                Edit /private/projects/internal-product/src/main.py
                """);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(
                        tempDir,
                        "ws-test",
                        "implement.claude_implement",
                        "implement-claude",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        assertTrue(prompt.contains("Execution Workspace Guard"));
        assertTrue(prompt.contains(tempDir.toAbsolutePath().normalize().toString()));
        assertTrue(prompt.contains("[outside-workspace path removed]"));
        assertTrue(!prompt.contains("/private/projects/internal-product"));
    }

    @Test
    void preservesRepoRelativePathsWhenSanitizingContextPreview() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, """
                - Contract scope candidates:
                  - miniprogram/src/pages/follow-up-summary/index.vue
                  - backend/app/models/treatment_event.py
                External file: /private/projects/internal-product/src/main.py
                """);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(
                        tempDir,
                        "ws-test",
                        "intake.claude_brainstorm",
                        "intake-claude-brainstorm",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        assertTrue(prompt.contains("miniprogram/src/pages/follow-up-summary/index.vue"));
        assertTrue(prompt.contains("backend/app/models/treatment_event.py"));
        assertTrue(prompt.contains("[outside-workspace path removed]"));
        assertTrue(!prompt.contains("/private/projects/internal-product"));
    }

    @Test
    void preservesChineseSlashPhrasesWhileSanitizingAbsolutePaths() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, """
                - 缺失/非对象输入必须被拒绝。
                - 仅允许修改/新增三个明确文件。
                External file: /private/projects/internal-product/src/main.py
                """);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(
                        tempDir,
                        "ws-test",
                        "intake.codex_product_review",
                        "intake-codex-product-review",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        assertTrue(prompt.contains("缺失/非对象输入必须被拒绝"));
        assertTrue(prompt.contains("仅允许修改/新增三个明确文件"));
        assertTrue(prompt.contains("[outside-workspace path removed]"));
        assertFalse(prompt.contains("/private/projects/internal-product"));
    }

    @Test
    void keepsArtifactEnvelopeClosingTagsWhenSanitizingContextPreview() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, """
                <dual-artifact type="agent_output_contract" version="1">
                {"decision":"NEEDS_REVISION"}
                </dual-artifact>

                External file: /private/projects/internal-product/src/main.py
                """);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(
                        tempDir,
                        "ws-test",
                        "implement.claude_implement",
                        "implement-claude",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        assertTrue(prompt.contains("</dual-artifact>"));
        assertTrue(prompt.contains("[outside-workspace path removed]"));
        assertTrue(!prompt.contains("/private/projects/internal-product"));
    }

    @Test
    void keepsAbsolutePathsInsideWorkspaceForLedgerPointers() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        var insidePath = tempDir.resolve(".agent/workstreams/ws-test/context/node-context.md").toAbsolutePath().normalize();
        Files.writeString(contextPath, "Read full artifact at " + insidePath + "\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(
                        tempDir,
                        "ws-test",
                        "implement.claude_implement",
                        "implement-claude",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        assertTrue(prompt.contains(insidePath.toString()));
    }

    @Test
    void intakeCodexPromptUsesDecisionValuesAcceptedByIntakeDecisionNode() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "intake.codex_product_review", "intake-codex-product-review", List.of());

        assertTrue(prompt.contains("READY_FOR_PRODUCT_REVIEW"));
        assertTrue(prompt.contains("task_contract_proposal"));
        assertTrue(prompt.contains("Intent fidelity"));
        assertTrue(prompt.contains("Explicit-requirement coverage"));
        assertTrue(prompt.contains("Semantic neutrality"));
        assertTrue(prompt.contains("Protocol ownership"));
        assertTrue(prompt.contains(
                "`scope.included` is the exhaustive authorization set for permitted repository changes"));
        assertTrue(prompt.contains(
                "Acceptance criteria, constraints, risks, and other prose cannot authorize an undeclared path"));
        assertTrue(prompt.contains("Never request an illustrative or non-exhaustive EXECUTION scope"));
        assertTrue(prompt.contains("intake file entries are proposals, not authorization"));
        assertTrue(prompt.contains("GOAL approval fixes the objective, acceptance and exclusions, not an implementation file set"));
        assertTrue(prompt.contains("A scope gap must request concrete typed path-and-operation changes"));
        assertTrue(prompt.contains("must not broaden or narrow accepted input"));
        assertTrue(prompt.contains("Do not require the task proposal to duplicate those harness protocols"));
        assertTrue(prompt.contains("do not return `NEEDS_REVISION` solely because they are absent"));
        assertTrue(prompt.contains("\"decision\" : \"<one allowed decision>\""));
        assertTrue(prompt.contains("READY_FOR_PRODUCT_REVIEW"));
        assertTrue(prompt.contains("NEEDS_REVISION"));
        assertTrue(prompt.contains("BLOCKED"));
        assertTrue(!prompt.contains("READY_FOR_PRODUCT_REVIEW|NEEDS_REVISION|BLOCKED"));
    }

    @Test
    void intakeClaudePromptProjectsTypedScopeAuthorityForProducer() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of());

        assertTrue(prompt.contains(
                "Treat intake file entries as proposals, not authorization"));
        assertTrue(prompt.contains("GOAL approval confirms the objective, acceptance and exclusions"));
        assertTrue(prompt.contains("approved EXECUTION `scope.included` is the exhaustive authorization set"));
        assertTrue(prompt.contains(
                "Do not use acceptance criteria, constraints, risks, or other prose to authorize undeclared paths"));
        assertFalse(prompt.contains("Treat the typed scope projected by the Output Contract as the exhaustive authorization set"));
        assertTrue(prompt.contains(
                "When a revision obligation identifies a scope gap, use only the exact typed scope-addition field"));
    }

    @Test
    void intakeCodexPromptIncludesProjectContractReviewPrinciples() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var templatesDir = tempDir.resolve(".agent").resolve("templates");
        Files.createDirectories(workstreamDir);
        Files.createDirectories(templatesDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");
        Files.writeString(
                templatesDir.resolve("contract-review-principles.md"),
                "- Project principle: never introduce medical assurance wording.\n"
        );

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "intake.codex_product_review", "intake-codex-product-review", List.of());

        assertTrue(prompt.contains("Project principle: never introduce medical assurance wording."));
    }

    @Test
    void promptsExplainBlockingEvidenceFieldSemantics() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "plan.claude_plan", "plan-claude", List.of());

        assertTrue(prompt.contains("Structured Artifact Field Semantics"));
        assertTrue(prompt.contains("If the decision is positive, ready, or accepted, keep blocking fields empty"));
        assertTrue(prompt.contains("Put resolved review comments in `checks`, `evidence`, or `acceptance_notes`"));
    }

    @Test
    void promptsDoNotInjectReadReuseReasonFieldsWithoutContextContract() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "plan.claude_plan", "plan-claude", List.of());

        assertFalse(prompt.contains("Read Reuse Contract Semantics"), prompt);
        assertFalse(prompt.contains("reread_reasons"), prompt);
        assertFalse(prompt.contains("agent_output_contract.reread_reasons"), prompt);
        assertTrue(prompt.contains("If the Context Package contains a Read Reuse Contract, follow that contract exactly."));
    }

    @Test
    void intakePromptDoesNotInjectAgentOutputContractRereadFieldsIntoTaskContractProposal() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var rendered = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .renderWithSections(tempDir, "ws-test", "intake.claude_brainstorm",
                        "intake-claude-brainstorm", List.of());

        assertTrue(rendered.content().contains("\"artifact_type\" : \"task_contract_proposal\""));
        assertFalse(rendered.content().contains("<dual-artifact"));
        assertFalse(rendered.content().contains("Structured Artifact Field Semantics"), rendered.content());
        assertFalse(rendered.content().contains("`must_fix`, `missing_evidence`, `open_risks`"), rendered.content());
        assertFalse(rendered.content().contains("Read Reuse Contract Semantics"), rendered.content());
        assertFalse(rendered.content().contains("reread_reasons"), rendered.content());
        assertTrue(rendered.sections().stream().noneMatch(
                section -> "read_reuse_contract_semantics".equals(section.name())));
    }

    @Test
    void exposesReadReuseContractAsPromptSectionWhenContextContainsIt() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, """
                # Context Package

                ## Repo Scope Guidance

                - Read reuse contract: the following files were already inspected and their recorded sha is still trusted.
                  - src/app.js: Previously read source file.
                - Read reuse rule: use existing evidence before re-reading files already inspected in this workstream.

                ## User Requirement

                Do the task.
                """);

        var rendered = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .renderWithSections(
                        tempDir,
                        "ws-test",
                        "plan.claude_plan",
                        "plan-claude",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        var section = rendered.sections().stream()
                .filter(item -> "read_reuse_contract".equals(item.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("read_reuse_contract", section.sourceType());
        assertTrue(section.content().contains("src/app.js"));
        assertTrue(!section.content().contains("## User Requirement"));
    }

    @Test
    void designCodexPromptUsesDecisionValuesAcceptedByDesignDecisionNode() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "design.codex_arch_review", "design-codex-review", List.of());

        assertTrue(prompt.contains("READY_FOR_ARCH_REVIEW"));
        assertTrue(prompt.contains("READY_FOR_ARCH_REVIEW"));
        assertTrue(prompt.contains("NEEDS_REVISION"));
        assertTrue(prompt.contains("BLOCKED"));
        assertTrue(!prompt.contains("READY_FOR_ARCH_REVIEW|NEEDS_REVISION|BLOCKED"));
    }

    @Test
    void capturedDesignReviewReplayKeepsTypedMechanismEvidenceAndRevisionSequence() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-test");
        var contextPath = workstreamDir.resolve("context/design-review.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Design captcha authentication.\n");
        var capturedScaleEvidence = "CAPTURED_SCALE_EVIDENCE_" + "x".repeat(12_000);
        Files.writeString(contextPath, """
                # Context Package

                ## Iteration History

                - iter-003: trigger=NEEDS_REVISION, target=design.claude_design, reason=atomic OTP consumption
                - iter-004: trigger=NEEDS_REVISION, target=design.claude_design, reason=resend state consistency
                - iter-005: trigger=NEEDS_REVISION, target=design.claude_design, reason=concurrent resend guard

                ## Required Architecture Review Subject

                **Checks:**
                - crash/restart leaves stale PENDING recoverable

                **Evidence:**
                - uq_otp_current WHERE state IN ('PENDING','ACTIVE')
                - rollback/conflict behavior is explicit
                - %s
                """.formatted(capturedScaleEvidence));

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "design.codex_arch_review",
                "design-codex-review",
                List.of(".agent/workstreams/ws-test/context/design-review.md"));

        assertTrue(prompt.contains("uq_otp_current WHERE state IN ('PENDING','ACTIVE')"));
        assertTrue(prompt.contains(capturedScaleEvidence));
        assertTrue(prompt.contains("iter-003") && prompt.contains("iter-004") && prompt.contains("iter-005"));
        assertTrue(prompt.contains("interruption/crash"));
        assertTrue(prompt.contains("restart/recovery"));
        assertTrue(prompt.contains("rollback/conflict"));
    }

    @Test
    void acceptancePromptInjectsDecisionValuesFromRuntimeContract() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "acceptance.codex_verifier_review", "acceptance-codex-review", List.of());

        assertTrue(prompt.contains("Allowed `result.decision` values:"));
        assertTrue(prompt.contains("Allowed `result.failure.verification_remediation` values for this node: "
                + "[EVIDENCE_COLLECTION, NONE, TEST_CODE_CHANGE]"));
        assertTrue(prompt.contains("ACCEPT"));
        assertTrue(prompt.contains("ACCEPT_WITH_NOTES"));
        assertTrue(prompt.contains("REJECT"));
        assertTrue(prompt.contains("\"decision\""));
        assertTrue(prompt.contains("\"summary\""));
        assertTrue(!prompt.contains("ACCEPT|ACCEPT_WITH_NOTES|REJECT"));
    }

    @Test
    void implementationPromptsRequireSelfTestArtifactCleanup() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var implementPrompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "implement.claude_implement", "implement-claude", List.of());
        var selfTestPrompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "implement.claude_self_test_report", "implement-claude-self-test", List.of());

        assertTrue(implementPrompt.contains("Clean up self-test byproducts"));
        assertTrue(implementPrompt.contains("__pycache__"));
        assertTrue(implementPrompt.contains("reread_reasons"),
                "implement result schema must always expose its typed optional evidence field");
        assertTrue(selfTestPrompt.contains("check repository hygiene"));
        assertTrue(selfTestPrompt.contains("generated byproducts"));
        assertFalse(selfTestPrompt.contains("reread_reasons"),
                "self_test_report prompts must not expose agent_output_contract-only fields");
    }

    @Test
    void includesDirectInputArtifactsWhenProvidedByInputRefs() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var nextPromptPath = workstreamDir.resolve("artifacts").resolve("next-stage").resolve("next-stage-prompt.md");
        Files.createDirectories(nextPromptPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(nextPromptPath, "# Next Stage Prompt\n\nImplement the approved follow-up.\n");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(
                        tempDir,
                        "ws-test",
                        "intake.claude_brainstorm",
                        "intake-claude-brainstorm",
                        List.of(".agent/workstreams/ws-test/artifacts/next-stage/next-stage-prompt.md")
                );

        assertTrue(prompt.contains("### Direct Input Artifacts"));
        assertTrue(prompt.contains(".agent/workstreams/ws-test/artifacts/next-stage/next-stage-prompt.md"));
        assertTrue(prompt.contains("Implement the approved follow-up."));
    }

    @Test
    void acceptancePromptUsesQualifiedSnapshotWithoutRereadingOrLeakingMachineMetadata() throws Exception {
        var fixture = AcceptanceContractProjectionFixture.create(
                tempDir, "ws-test", "acceptance.codex_verifier_review");
        var trustedPayload = TypedContextPayloadBoundary.wrap(
                "```json\n" + fixture.rawContract() + "\n```");
        Files.writeString(
                tempDir.resolve(".agent/workstreams/ws-test/artifacts/task-contract.json"),
                "{\"tampered\":true}");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).renderWithSections(
                tempDir,
                "ws-test",
                "run-acceptance",
                "acceptance.codex_verifier_review",
                "acceptance-codex-review",
                fixture.inputRefs(),
                null,
                trustedPayload).content();

        assertTrue(prompt.contains("Project the complete authentication contract"));
        assertTrue(prompt.contains("src/Auth.java"));
        assertFalse(prompt.contains("tampered"));
        assertFalse(prompt.contains(fixture.metadataRef()));
        assertFalse(prompt.contains("taskContractSha256"));
        assertFalse(prompt.contains("dual:typed-payload"));
    }

    @Test
    void directInputRefsDoNotExpandExecutionNoiseRawCandidatesOrUnknownFiles() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var outputs = workstreamDir.resolve("runs/run-001/outputs");
        Files.createDirectories(outputs);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Review only validated evidence.\n");
        Files.writeString(outputs.resolve("design.stderr.log"), "STDERR_INJECTION");
        Files.writeString(outputs.resolve("design.transcript.log"), "TRANSCRIPT_INJECTION");
        Files.writeString(outputs.resolve("design.json"), "{\"summary\":\"RAW_CANDIDATE_INJECTION\"}");
        var unknown = workstreamDir.resolve("artifacts/unknown-note.md");
        Files.createDirectories(unknown.getParent());
        Files.writeString(unknown, "UNKNOWN_REF_INJECTION");
        var refs = List.of(
                tempDir.relativize(outputs.resolve("design.stderr.log")).toString(),
                tempDir.relativize(outputs.resolve("design.transcript.log")).toString(),
                tempDir.relativize(outputs.resolve("design.json")).toString(),
                tempDir.relativize(unknown).toString()
        );

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger())
                .render(tempDir, "ws-test", "plan.claude_plan", "plan-claude", refs);

        assertFalse(prompt.contains("STDERR_INJECTION"));
        assertFalse(prompt.contains("TRANSCRIPT_INJECTION"));
        assertFalse(prompt.contains("RAW_CANDIDATE_INJECTION"));
        assertFalse(prompt.contains("UNKNOWN_REF_INJECTION"));
    }

    @Test
    void forgedContractSidecarCannotEnterPromptByFilenameAlone() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var sidecar = workstreamDir.resolve("runs/run-forged/outputs/design.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Review only validated evidence.\n");
        Files.writeString(sidecar, "{\"summary\":\"FORGED_CONTRACT_INJECTION\"}");

        assertThrows(IllegalStateException.class, () -> new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "plan.claude_plan",
                "plan-claude",
                List.of(tempDir.relativize(sidecar).toString())
        ));
    }

    @Test
    void validatedSidecarProjectsTypedFieldsWithoutUnknownRawContent() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var output = workstreamDir.resolve("runs/run-typed/outputs/design.json");
        var sidecar = workstreamDir.resolve("runs/run-typed/outputs/design.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Review only validated evidence.\n");
        Files.writeString(output, "{}");
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "workstream_id": "ws-test",
                  "run_id": "run-typed",
                  "node_id": "design.claude_design",
                  "node_instance_id": "node-design-typed",
                  "source_output_ref": ".agent/workstreams/ws-test/runs/run-typed/outputs/design.json",
                  "decision": "DESIGN_READY",
                  "summary": "VALIDATED_TYPED_SUMMARY",
                  "source_artifact_type": "design_result",
                  "blocking": false,
                  "untyped_payload": "PROMPT_INJECTION"
                }
                """);
        publishAgentEvidenceOccurrence(sidecar, output);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "plan.claude_plan",
                "plan-claude",
                List.of(tempDir.relativize(sidecar).toString())
        );

        assertTrue(prompt.contains("VALIDATED_TYPED_SUMMARY"));
        assertFalse(prompt.contains("PROMPT_INJECTION"));
        assertFalse(prompt.contains("untyped_payload"));
    }

    @Test
    void knownArtifactLookalikeCannotEnterPromptBySuffixAlone() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var lookalike = workstreamDir.resolve("runs/run-001/artifacts/task-contract.json");
        Files.createDirectories(lookalike.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Use the canonical task contract.\n");
        Files.writeString(lookalike, "KNOWN_ARTIFACT_LOOKALIKE_INJECTION");

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "plan.claude_plan",
                "plan-claude",
                List.of(tempDir.relativize(lookalike).toString())
        );

        assertFalse(prompt.contains("KNOWN_ARTIFACT_LOOKALIKE_INJECTION"));
    }

    @Test
    void canonicalContextSymlinkCannotInjectTargetContent() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextDir = workstreamDir.resolve("context");
        var target = tempDir.resolve("outside-context.md");
        var link = contextDir.resolve("node-context.md");
        Files.createDirectories(contextDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Use only canonical context.\n");
        Files.writeString(target, "SYMLINK_CONTEXT_INJECTION");
        Files.createSymbolicLink(link, target);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "plan.claude_plan",
                "plan-claude",
                List.of(tempDir.relativize(link).toString())
        );

        assertFalse(prompt.contains("SYMLINK_CONTEXT_INJECTION"));
    }

    @Test
    void contextLookalikesOutsideCurrentCanonicalDirectoryCannotInjectContent() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Use only the current context package.\n");
        var otherWorkstreamContext = tempDir.resolve(
                ".agent/workstreams/ws-other/context/lookalike.md");
        var nestedContext = workstreamDir.resolve("context/nested/lookalike.md");
        Files.createDirectories(otherWorkstreamContext.getParent());
        Files.createDirectories(nestedContext.getParent());
        Files.writeString(otherWorkstreamContext, """
                OTHER_WORKSTREAM_INJECTION
                - Read reuse contract: OTHER_REUSE_INJECTION
                """);
        Files.writeString(nestedContext, """
                NESTED_CONTEXT_INJECTION
                - Read reuse contract: NESTED_REUSE_INJECTION
                """);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "plan.claude_plan",
                "plan-claude",
                List.of(
                        tempDir.relativize(otherWorkstreamContext).toString(),
                        tempDir.relativize(nestedContext).toString()
                )
        );

        assertFalse(prompt.contains("OTHER_WORKSTREAM_INJECTION"));
        assertFalse(prompt.contains("NESTED_CONTEXT_INJECTION"));
        assertFalse(prompt.contains("OTHER_REUSE_INJECTION"));
        assertFalse(prompt.contains("NESTED_REUSE_INJECTION"));
    }

    @Test
    void completeContextPackageKeepsRequiredReviewSubjectBeyondLegacyPreviewLimit() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Review the typed proposal.\n");
        Files.writeString(contextPath, """
                # Context Package

                ## Large Optional Context

                %s

                ## Required Review Subject

                ```json
                {
                  "artifact_type": "task_contract_proposal",
                  "objective": "KEEP_THIS_COMPLETE_REQUIRED_OBJECTIVE",
                  "scope": {
                    "included": [{"path": "README.md", "operation": "MODIFY"}],
                    "excluded": []
                  }
                }
                ```
                """.formatted("x".repeat(7_000)));

        var rendered = new AgentPromptRenderer(AcpxTestLedger.ledger()).renderWithSections(
                tempDir,
                "ws-test",
                "run-review",
                "intake.codex_product_review",
                "intake-codex-product-review",
                List.of(".agent/workstreams/ws-test/context/node-context.md"));

        assertFalse(rendered.content().contains("[truncated; read the full context package at"));
        assertTrue(rendered.content().contains("KEEP_THIS_COMPLETE_REQUIRED_OBJECTIVE"));
        var required = rendered.sections().stream()
                .filter(section -> "context_package".equals(section.name()))
                .findFirst()
                .orElseThrow();
        assertTrue(required.required());
        assertEquals("context_package", required.sourceType());
        assertTrue(required.content().contains(
                "\"included\": [{\"path\": \"README.md\", \"operation\": \"MODIFY\"}]"));
    }

    @Test
    void verifiedTypedReviewSubjectBypassesOnlyFreeTextPathRedaction() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Review the typed proposal.\n");
        var output = workstreamDir.resolve("runs/run-typed/outputs/intake-claude-brainstorm.json");
        var sidecar = workstreamDir.resolve("runs/run-typed/outputs/intake-claude-brainstorm.contract.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}\n");
        Files.writeString(sidecar, """
                {
                  "workstream_id": "ws-test",
                  "run_id": "run-typed",
                  "node_id": "intake.claude_brainstorm",
                  "node_instance_id": "node-intake-typed",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "source_artifact_type": "task_contract_proposal",
                  "source_output_ref": "%s",
                  "task_contract_proposal": {
                    "artifact_type": "task_contract_proposal",
                    "schema_version": 3,
                    "objective": "Keep /pages/treatment-record/index",
                    "scope": {"included": [{"path": "README.md", "operation": "MODIFY"}], "excluded": []},
                    "acceptance_criteria": [
                      {"id": "AC-1", "text": "Route preserved"},
                      {"id": "AC-2", "text": "Review remains actionable"}
                    ],
                    "test_requirements": [{"id": "TEST-1", "text": "Review typed proposal"}],
                    "constraints": [], "non_goals": [], "risk_areas": [], "evidence_refs": [],
                    "open_questions": [], "revision_responses": []
                  }
                }
                """.formatted(tempDir.relativize(output).toString().replace('\\', '/')));
        publishAgentEvidenceOccurrence(
                sidecar, output, "intake.claude_brainstorm", "node-intake-typed", "READY_FOR_PRODUCT_REVIEW");
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var reference = workstreamDir.resolve("artifacts/task-contract-proposals/intake.ref.json");
        Files.createDirectories(reference.getParent());
        mapper.writeValue(reference.toFile(), Map.of(
                "schema_version", 1,
                "proposal_id", "node-intake-typed:test",
                "workstream_id", "ws-test",
                "producer_node_id", "intake.claude_brainstorm",
                "producer_node_instance_id", "node-intake-typed",
                "artifact_ref", tempDir.relativize(sidecar).toString().replace('\\', '/'),
                "sha256", AgentOutputContractSidecar.sha256(sidecar)));
        var referenceRef = tempDir.relativize(reference).toString().replace('\\', '/');
        var proposal = TaskContractProposalReference.resolveProposalFromRefs(
                tempDir, "ws-test", List.of(referenceRef), mapper).orElseThrow().proposal();
        var typedProposal = "```json\n"
                + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proposal)
                + "\n```";
        Files.writeString(contextPath, """
                # Context Package

                ## Free Text

                Inspect /private/tmp/untrusted-context.txt.

                ## Required Review Subject

                %s
                """.formatted(TypedContextPayloadBoundary.wrap(typedProposal)));

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "intake.codex_product_review",
                "intake-codex-product-review",
                List.of(
                        ".agent/workstreams/ws-test/context/node-context.md",
                        referenceRef));

        assertTrue(prompt.contains(typedProposal));
        assertEquals(1, occurrences(prompt, "\"artifact_type\" : \"task_contract_proposal\""));
        assertFalse(prompt.contains("/private/tmp/untrusted-context.txt"));
        assertTrue(prompt.contains("[outside-workspace path removed]"));
    }

    @Test
    void intakeRevisionPromptProjectsExactVerifiedPreviousProposal() throws Exception {
        var fixture = intakeRevisionEvidence();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var proposal = TaskContractProposalReference.resolveProposalFromRefs(
                tempDir, "ws-test", List.of(fixture.referenceRef()), mapper).orElseThrow().proposal();
        var expectedBaseline = TypedContextPayloadBoundary.wrapJson(proposal);

        var prompt = new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "intake.claude_brainstorm",
                "intake-claude-brainstorm",
                fixture.reviewRefs());

        assertTrue(prompt.contains("### Verified Previous Task Contract Proposal"));
        assertTrue(prompt.contains(expectedBaseline));
        assertTrue(prompt.contains("BASELINE_CLAUSE_MUST_REMAIN_BYTE_STABLE"));
        assertTrue(prompt.contains("/pages/captcha/index"));
        assertEquals(1, occurrences(prompt, "BASELINE_CLAUSE_MUST_REMAIN_BYTE_STABLE"));
    }

    @Test
    void intakeRevisionPromptRejectsTamperedPreviousProposalBeforeDispatch() throws Exception {
        var fixture = intakeRevisionEvidence();
        Files.writeString(
                fixture.proposalSidecar(),
                Files.readString(fixture.proposalSidecar())
                        .replace("BASELINE_CLAUSE_MUST_REMAIN_BYTE_STABLE", "TAMPERED_BASELINE"));

        var error = assertThrows(IllegalArgumentException.class, () -> new AgentPromptRenderer(AcpxTestLedger.ledger()).render(
                tempDir,
                "ws-test",
                "intake.claude_brainstorm",
                "intake-claude-brainstorm",
                fixture.reviewRefs()));

        assertTrue(error.getMessage().contains(
                "verified previous task-contract proposal is required for intake revision"));
    }


    @Test
    void failsClearlyWhenTemplateIsMissing() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                new AgentPromptRenderer(AcpxTestLedger.ledger()).render(tempDir, "ws-test", "bad.node", "missing-template", List.of()));

        assertTrue(error.getMessage().contains("missing prompt template resource"));
    }

    private void publishAgentEvidenceOccurrence(Path sidecar, Path output) throws Exception {
        publishAgentEvidenceOccurrence(sidecar, output, "design.claude_design", "node-design-typed", "DESIGN_READY");
    }

    private void publishAgentEvidenceOccurrence(
            Path sidecar,
            Path output,
            String nodeId,
            String nodeInstanceId,
            String decision
    ) throws Exception {
        var paths = new dev.dual.ledger.file.WorkstreamPaths(tempDir, new dev.dual.core.id.DualId("ws-test"));
        if (!Files.exists(paths.workflowCurrentJson())) {
            var definition = new dev.dual.core.workflow.WorkflowDefinition("prompt-evidence", 1, nodeId,
                    List.of(new dev.dual.core.workflow.NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of())), List.of());
            AcpxTestLedger.initialize(AcpxTestLedger.ledger(), paths, "ws-test", definition);
        }
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var sidecarRef = tempDir.relativize(sidecar).toString();
        var outputRef = tempDir.relativize(output).toString();
        var outputs = List.of(outputRef, sidecarRef);
        var result = Map.<String, Object>of(
                "run_id", "run-typed",
                "decision", decision,
                "contract_sidecar_sha256", AgentOutputContractSidecar.sha256(sidecar));
        var now = Instant.parse("2026-07-26T00:00:00Z");
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-test");
        var executionId = "nxe-" + nodeInstanceId;
        Files.createDirectories(workstreamDir.resolve("nodes"));
        Files.createDirectories(workstreamDir.resolve("executions/nodes"));
        objectMapper.writeValue(
                workstreamDir.resolve("nodes/" + nodeInstanceId + ".json").toFile(),
                new NodeInstanceDocument(
                        1, nodeInstanceId, nodeId, NodeKind.TASK,
                        "acpx.agent_run", NodeStatus.COMPLETED, "design", "iteration-1",
                        List.of(), Map.of(), outputs, result, "run-typed", executionId,
                        now, now));
        objectMapper.writeValue(
                workstreamDir.resolve("executions/nodes/" + executionId + ".json").toFile(),
                new NodeExecutionDocument(
                        1, executionId, "agent-task-design", "workflow-execution-1",
                        nodeInstanceId, nodeId, "acpx.agent_run",
                        NodeExecutionStatus.SUCCEEDED, 1L, "completed", NodeStatus.COMPLETED,
                        outputs, result, "", "run-typed", "", "claude", "session-1",
                        0, now, now, 1L));
    }

    private RevisionEvidenceFixture intakeRevisionEvidence() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-test");
        var outputDir = workstreamDir.resolve("runs/run-typed/outputs");
        Files.createDirectories(outputDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Revise the verified proposal.\n");

        var proposalOutput = outputDir.resolve("intake-claude-brainstorm.json");
        var proposalSidecar = outputDir.resolve("intake-claude-brainstorm.contract.json");
        Files.writeString(proposalOutput, "{}\n");
        Files.writeString(proposalSidecar, """
                {
                  "workstream_id": "ws-test",
                  "run_id": "run-typed",
                  "node_id": "intake.claude_brainstorm",
                  "node_instance_id": "node-intake-original",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "source_artifact_type": "task_contract_proposal",
                  "source_output_ref": "%s",
                  "task_contract_proposal": {
                    "artifact_type": "task_contract_proposal",
                    "schema_version": 3,
                    "objective": "Preserve the verified route /pages/captcha/index",
                    "scope": {
                      "included": [{"path": "src/main/resources/templates/captcha/index.html", "operation": "CREATE"}],
                      "excluded": []
                    },
                    "acceptance_criteria": [
                      {"id": "AC-1", "text": "BASELINE_CLAUSE_MUST_REMAIN_BYTE_STABLE"},
                      {"id": "AC-2", "text": "The review obligation is resolved"}
                    ],
                    "test_requirements": [{"id": "TR-1", "text": "Verify the baseline"}],
                    "constraints": [],
                    "non_goals": [],
                    "risk_areas": [],
                    "evidence_refs": [],
                    "open_questions": [],
                    "revision_responses": []
                  }
                }
                """.formatted(tempDir.relativize(proposalOutput).toString().replace('\\', '/')));
        publishAgentEvidenceOccurrence(
                proposalSidecar,
                proposalOutput,
                "intake.claude_brainstorm",
                "node-intake-original",
                "READY_FOR_PRODUCT_REVIEW");

        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var referencePath = workstreamDir.resolve("artifacts/task-contract-proposals/intake.ref.json");
        Files.createDirectories(referencePath.getParent());
        mapper.writeValue(referencePath.toFile(), Map.of(
                "schema_version", 1,
                "proposal_id", "node-intake-original:test",
                "workstream_id", "ws-test",
                "producer_node_id", "intake.claude_brainstorm",
                "producer_node_instance_id", "node-intake-original",
                "artifact_ref", tempDir.relativize(proposalSidecar).toString().replace('\\', '/'),
                "sha256", AgentOutputContractSidecar.sha256(proposalSidecar)));
        var referenceRef = tempDir.relativize(referencePath).toString().replace('\\', '/');

        var reviewOutput = outputDir.resolve("intake-codex-product-review.json");
        var reviewSidecar = outputDir.resolve("intake-codex-product-review.contract.json");
        Files.writeString(reviewOutput, "{}\n");
        Files.writeString(reviewSidecar, """
                {
                  "workstream_id": "ws-test",
                  "run_id": "run-typed",
                  "node_id": "intake.codex_product_review",
                  "node_instance_id": "node-intake-review",
                  "decision": "NEEDS_REVISION",
                  "source_artifact_type": "agent_output_contract",
                  "source_output_ref": "%s",
                  "contract_obligations": [
                    {"id": "CO-1", "text": "Clarify the accepted input"}
                  ],
                  "review_subject": %s
                }
                """.formatted(
                tempDir.relativize(reviewOutput).toString().replace('\\', '/'),
                mapper.writeValueAsString(mapper.readTree(referencePath.toFile()))));
        publishAgentEvidenceOccurrence(
                reviewSidecar,
                reviewOutput,
                "intake.codex_product_review",
                "node-intake-review",
                "NEEDS_REVISION");

        return new RevisionEvidenceFixture(
                List.of(
                        tempDir.relativize(reviewOutput).toString().replace('\\', '/'),
                        tempDir.relativize(reviewSidecar).toString().replace('\\', '/')),
                proposalSidecar,
                referenceRef);
    }

    private record RevisionEvidenceFixture(
            List<String> reviewRefs,
            Path proposalSidecar,
            String referenceRef
    ) {
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = value.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }
}
