package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void renderWithSectionsProducesContentAndProvenance() throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.");

        var renderer = new AgentPromptRenderer();
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

        var prompt = new AgentPromptRenderer()
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
        assertTrue(prompt.contains("Do not start an open-ended repository survey"));
        assertTrue(prompt.contains("still emit the required strict `task_contract_proposal` result"));
        assertTrue(prompt.contains("This intake stage is seed-only contract drafting"));
        assertTrue(prompt.contains("do not call tools"));
        assertTrue(prompt.contains("do not ask to list directories"));
        assertTrue(prompt.contains("A prose-only response is a contract failure"));
        assertTrue(prompt.contains("Explicitly address each Must Fix"));
        assertTrue(prompt.contains("Use the orchestrator-provided seed, memory, contract scope, and Exploration Plan"));
        assertTrue(prompt.contains("account for every explicit user objective"));
        assertTrue(prompt.contains("A requirement mentioned only in `risk_areas`, `open_questions`, or `evidence_refs` is not preserved"));
        assertTrue(prompt.contains("Do not broaden or narrow behavior"));
        assertTrue(prompt.contains("preserve the literal user requirement"));
        assertTrue(prompt.contains("Do not copy the current node's `AgentResultEnvelope`"));
        assertTrue(prompt.contains("enforced independently by the orchestrator's Output Contract"));
        assertFalse(prompt.contains("Read the repository"));
        assertFalse(prompt.contains("bounded discovery tools"));
        assertFalse(prompt.contains("dual-control"));
        assertFalse(prompt.contains("<dual-artifact"));
        assertTrue(prompt.contains("\"artifact_type\" : \"task_contract_proposal\""));
    }

    @Test
    void nonIntakePromptsKeepShellWorkspaceGuard() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of());

        assertTrue(prompt.contains("Use English for human-readable string values inside the final JSON result."));
        assertTrue(prompt.contains("Keep JSON keys, enum values"));
    }

    @Test
    void missingSettingsDefaultsPromptLanguageFromRequirement() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm", List.of());

        assertTrue(prompt.contains("Use English for human-readable string values inside the final JSON result."));
    }

    @Test
    void includesContextPackageWhenProvidedByInputRefs() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, "# Context Package\n\nImportant prior review.\n");

        var prompt = new AgentPromptRenderer()
                .render(
                        tempDir,
                        "ws-test",
                        "design.codex_arch_review",
                        "design-codex-review",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        assertTrue(prompt.contains("## Context Package"));
        assertTrue(prompt.contains(".agent/workstreams/ws-test/context/node-context.md"));
        assertTrue(prompt.contains("Important prior review."));
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

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "intake.codex_product_review", "intake-codex-product-review", List.of());

        assertTrue(prompt.contains("READY_FOR_PRODUCT_REVIEW"));
        assertTrue(prompt.contains("task_contract_proposal"));
        assertTrue(prompt.contains("Intent fidelity"));
        assertTrue(prompt.contains("Explicit-requirement coverage"));
        assertTrue(prompt.contains("Semantic neutrality"));
        assertTrue(prompt.contains("Protocol ownership"));
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

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "intake.codex_product_review", "intake-codex-product-review", List.of());

        assertTrue(prompt.contains("Project principle: never introduce medical assurance wording."));
    }

    @Test
    void promptsExplainBlockingEvidenceFieldSemantics() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
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

        var rendered = new AgentPromptRenderer()
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

        var rendered = new AgentPromptRenderer()
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
    void exposesExplorationPlanAsPromptSectionWhenContextContainsIt() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        var contextPath = workstreamDir.resolve("context").resolve("node-context.md");
        Files.createDirectories(contextPath.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");
        Files.writeString(contextPath, """
                # Context Package

                ## Exploration Plan

                - Intent: contract_discovery
                - Preferred capabilities:
                  - symbol_index
                  - token_filtered_shell
                - Direct read budget: 3
                - Agent capability catalog: .agent/workstreams/ws-test/memory/agent-capability-catalog.json
                - Exploration plan: .agent/workstreams/ws-test/memory/exploration-plan-node-001.json

                ## User Requirement

                Do the task.
                """);

        var rendered = new AgentPromptRenderer()
                .renderWithSections(
                        tempDir,
                        "ws-test",
                        "intake.claude_brainstorm",
                        "intake-claude-brainstorm",
                        List.of(".agent/workstreams/ws-test/context/node-context.md")
                );

        var section = rendered.sections().stream()
                .filter(item -> "exploration_plan".equals(item.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("exploration_plan", section.sourceType());
        assertTrue(section.content().contains("contract_discovery"));
        assertTrue(section.content().contains("symbol_index"));
        assertFalse(section.content().contains("## User Requirement"));
    }

    @Test
    void designCodexPromptUsesDecisionValuesAcceptedByDesignDecisionNode() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "design.codex_arch_review", "design-codex-review", List.of());

        assertTrue(prompt.contains("READY_FOR_ARCH_REVIEW"));
        assertTrue(prompt.contains("READY_FOR_ARCH_REVIEW"));
        assertTrue(prompt.contains("NEEDS_REVISION"));
        assertTrue(prompt.contains("BLOCKED"));
        assertTrue(!prompt.contains("READY_FOR_ARCH_REVIEW|NEEDS_REVISION|BLOCKED"));
    }

    @Test
    void acceptancePromptInjectsDecisionValuesFromRuntimeContract() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "acceptance.codex_verifier_review", "acceptance-codex-review", List.of());

        assertTrue(prompt.contains("Allowed `result.decision` values:"));
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

        var implementPrompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "implement.claude_implement", "implement-claude", List.of());
        var selfTestPrompt = new AgentPromptRenderer()
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

        var prompt = new AgentPromptRenderer()
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

        var rendered = new AgentPromptRenderer().renderWithSections(
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
    void failsClearlyWhenTemplateIsMissing() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                new AgentPromptRenderer().render(tempDir, "ws-test", "bad.node", "missing-template", List.of()));

        assertTrue(error.getMessage().contains("missing prompt template resource"));
    }
}
