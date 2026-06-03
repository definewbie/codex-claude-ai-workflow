package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptRendererTest {
    @TempDir
    Path tempDir;

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
        assertTrue(prompt.contains("decision: READY_FOR_PRODUCT_REVIEW"));
        assertTrue(prompt.contains("Do not create, edit, or delete files under .agent/"));
        assertTrue(prompt.contains("Only the orchestrator writes run ledger artifacts"));
        assertTrue(prompt.contains("Explicitly address each Must Fix"));
        assertTrue(prompt.contains("<dual-artifact type=\"task_contract_proposal\" version=\"1\">"));
        assertTrue(prompt.contains("\"artifact_type\": \"task_contract_proposal\""));
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
                git -C /Users/kriswang/work/projects/ai/cancer-clinic status --short
                Edit /Users/kriswang/work/projects/ai/cancer-clinic/miniprogram/src/pages/record/index.vue
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
        assertTrue(!prompt.contains("/Users/kriswang/work/projects/ai/cancer-clinic"));
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
        assertTrue(prompt.contains("decision: READY_FOR_PRODUCT_REVIEW|NEEDS_REVISION|BLOCKED"));
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
    void designCodexPromptUsesDecisionValuesAcceptedByDesignDecisionNode() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Add one small function.\n");

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "design.codex_arch_review", "design-codex-review", List.of());

        assertTrue(prompt.contains("READY_FOR_ARCH_REVIEW"));
        assertTrue(prompt.contains("decision: READY_FOR_ARCH_REVIEW|NEEDS_REVISION|BLOCKED"));
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
        assertTrue(selfTestPrompt.contains("Repository Hygiene"));
        assertTrue(selfTestPrompt.contains("generated byproducts"));
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
    void failsClearlyWhenTemplateIsMissing() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                new AgentPromptRenderer().render(tempDir, "ws-test", "bad.node", "missing-template", List.of()));

        assertTrue(error.getMessage().contains("missing prompt template resource"));
    }
}
