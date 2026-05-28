package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
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

class WorkflowIntegrationTest {
    @TempDir
    Path tempDir;
    private TestLedgerFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new TestLedgerFixture(tempDir, "ws-int");
        fixture.init();
    }

    @Test
    void chain1_contextBuildToDecision() throws Exception {
        fixture.writeRequirement("Build user auth module.\n");
        var agentOutput = fixture.writeAgentOutput("intake-claude-brainstorm.md", """
                # Brainstorm

                Analyzed requirements for auth module.

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: Auth module brainstorm complete
                ```
                """);

        var contextRef = agentOutput.toString()
                .replace(tempDir.toString() + "/", "");

        var contextHandler = new ContextBuildPackageHandler();
        var contextResult = contextHandler.execute(taskContext(
                "context.intake_claude",
                "context.build_package",
                List.of(contextRef)
        ));
        assertEquals(NodeStatus.COMPLETED, contextResult.status());

        var packagePath = (String) contextResult.result().get("context_package");
        assertTrue(Files.exists(tempDir.resolve(packagePath)));
        var markdown = Files.readString(tempDir.resolve(packagePath), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("Build user auth module"));
        assertTrue(markdown.contains("READY_FOR_PRODUCT_REVIEW"));

        var decisionHandler = new ReviewDecisionHandler();
        var decisionResult = decisionHandler.evaluate(decisionContext(
                "intake.decision",
                List.of(contextRef)
        ));
        assertEquals(NodeStatus.COMPLETED, decisionResult.status());
        assertEquals("READY_FOR_PRODUCT_REVIEW", decisionResult.outcome());
        assertEquals("READY_FOR_PRODUCT_REVIEW", decisionResult.result().get("decision"));
    }

    @Test
    void chain2_handoffToNextStageAndJson() throws Exception {
        var handoffRef = ".agent/workstreams/ws-int/runs/run-010/outputs/stage-claude-handoff.md";
        fixture.writeHandoff("""
                # Claude Stage Handoff

                ## Completed Work

                - Designed the API schema
                - Created component architecture
                - Wrote unit tests for auth module

                ## Changed Files

                - src/main/java/dev/dual/auth/AuthService.java
                - src/main/java/dev/dual/auth/AuthController.java
                - src/test/java/dev/dual/auth/AuthServiceTest.java

                ## Open Risks

                - Rate limit behavior under load
                - Token refresh edge case on midnight UTC

                ## Verification State

                All self-tests passed. 47/47 green.

                ## Suggested Next Prompt

                Continue with integration testing for auth endpoints.

                ```dual-control
                decision: HANDOFF_READY
                summary: Auth module implementation complete
                ```
                """);

        var handler = new PromptGenerateNextStageHandler();
        var result = handler.execute(taskContext(
                "stage.generate_next_prompt",
                "prompt.generate_next_stage",
                List.of(handoffRef)
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var promptRef = ".agent/workstreams/ws-int/artifacts/next-stage/next-stage-prompt.md";
        assertTrue(result.outputRefs().contains(promptRef));
        assertTrue(Files.exists(tempDir.resolve(promptRef)));

        var promptMarkdown = Files.readString(tempDir.resolve(promptRef), StandardCharsets.UTF_8);
        assertTrue(promptMarkdown.contains("Continue with integration testing"));
        assertTrue(promptMarkdown.contains("## Handoff Context"));

        var handoffJsonRef = ".agent/workstreams/ws-int/artifacts/next-stage/handoff.json";
        assertTrue(result.outputRefs().contains(handoffJsonRef));
        assertTrue(Files.exists(tempDir.resolve(handoffJsonRef)));

        var mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(
                tempDir.resolve(handoffJsonRef).toFile(), Map.class);

        assertEquals(1, json.get("schemaVersion"));
        assertEquals("ws-int", json.get("workstreamId"));

        @SuppressWarnings("unchecked")
        var completedWork = (List<String>) json.get("completedWork");
        assertEquals(3, completedWork.size());
        assertTrue(completedWork.contains("Designed the API schema"));
        assertTrue(completedWork.contains("Created component architecture"));
        assertTrue(completedWork.contains("Wrote unit tests for auth module"));

        @SuppressWarnings("unchecked")
        var changedFiles = (List<String>) json.get("changedFiles");
        assertEquals(3, changedFiles.size());
        assertTrue(changedFiles.contains("src/main/java/dev/dual/auth/AuthService.java"));

        @SuppressWarnings("unchecked")
        var openRisks = (List<String>) json.get("openRisks");
        assertEquals(2, openRisks.size());
        assertTrue(openRisks.contains("Rate limit behavior under load"));

        var verificationState = (String) json.get("verificationState");
        assertTrue(verificationState.contains("All self-tests passed"));
        assertFalse(verificationState.isBlank());
    }

    @Test
    void chain3_budgetGovernanceUnderOversizedInputs() throws Exception {
        var largeRequirement = "A".repeat(8_000) + "\n";
        fixture.writeRequirement(largeRequirement);
        fixture.writeGitEvidence("M src/Foo.java\nM src/Bar.java", "2 files changed, 400 insertions(+)", null);

        for (int i = 0; i < 6; i++) {
            var content = "## Agent Run " + i + "\n\n" + "X".repeat(5_000) + "\n";
            fixture.writeAgentOutput("agent-output-" + i + ".md", content);
        }

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-int",
                new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "implement.claude_implement",
                        "profile", "implementation"
                )),
                node("context.implement_claude", NodeKind.TASK, "context.build_package", List.of())
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var packagePath = (String) result.result().get("context_package");
        var markdown = Files.readString(tempDir.resolve(packagePath), StandardCharsets.UTF_8);

        assertTrue(markdown.contains("## Context Governance"), "Should have governance summary");
        assertTrue(markdown.contains("User Requirement"), "Requirement section should be present");

        assertTrue(markdown.contains(largeRequirement.stripTrailing()),
                "Requirement content must be preserved in full");

        var budget = ContextProfiles.implementation();
        assertTrue(markdown.length() <= budget.totalBudget(),
                "Total markdown (" + markdown.length() + ") must be <= totalBudget (" + budget.totalBudget() + ")");

        assertTrue(markdown.contains("## Context Governance"));
        var govSection = markdown.substring(markdown.indexOf("## Context Governance"));
        assertTrue(govSection.contains("full") || govSection.contains("summary") || govSection.contains("latest") || govSection.contains("diff_stat"),
                "Governance table should show degradation levels");
    }

    @Test
    void chain4_requirementOverHardLimitFails() throws Exception {
        var oversizedRequirement = "R".repeat(25_000) + "\n";
        fixture.writeRequirement(oversizedRequirement);

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-int",
                new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "implement.claude_implement",
                        "profile", "implementation"
                )),
                node("context.implement_claude", NodeKind.TASK, "context.build_package", List.of())
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        var errorMsg = (String) result.result().get("error");
        assertTrue(errorMsg.contains("budget exceeded"),
                "Should fail due to budget: " + errorMsg);
    }

    private TaskHandler.TaskContext taskContext(String nodeId, String handlerType, List<String> inputRefs) {
        return new TaskHandler.TaskContext(
                tempDir,
                "ws-int",
                new NodeDefinition(nodeId, NodeKind.TASK, handlerType, Map.of()),
                node(nodeId, NodeKind.TASK, handlerType, inputRefs)
        );
    }

    private DecisionHandler.DecisionContext decisionContext(String nodeId, List<String> inputRefs) {
        return new DecisionHandler.DecisionContext(
                tempDir,
                "ws-int",
                new NodeDefinition(nodeId, NodeKind.DECISION, "review.decision", Map.of()),
                node(nodeId, NodeKind.DECISION, "review.decision", inputRefs)
        );
    }

    private static NodeInstance node(String id, NodeKind kind, String handlerType, List<String> inputRefs) {
        return new NodeInstance(
                "node-" + id.replace('.', '-'),
                id,
                kind,
                handlerType,
                NodeStatus.READY,
                "stage-test",
                "iter-001",
                inputRefs,
                List.of(),
                Map.of(),
                null,
                null,
                null
        );
    }
}
