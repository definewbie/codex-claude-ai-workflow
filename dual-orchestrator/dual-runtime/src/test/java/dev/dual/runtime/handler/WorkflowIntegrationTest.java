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

    // --- Extended integration tests ---

    @Test
    void chain5_needsRevisionLoopWithContextCarryover() throws Exception {
        fixture.writeRequirement("Design caching layer.\n");
        fixture.writeAgentOutput("design-claude.md", """
                # Design

                Proposed Redis-based caching.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Caching layer design complete
                ```
                """);

        // Codex review rejects → NEEDS_REVISION
        fixture.writeAgentOutput("codex-arch-review-reject.md", """
                # Architecture Review

                Missing cache invalidation strategy.

                ```dual-control
                decision: NEEDS_REVISION
                summary: Must address cache invalidation
                ```
                """);

        var reviewRef = ".agent/workstreams/ws-int/runs/run-001/outputs/codex-arch-review-reject.md";
        var decisionHandler = new ReviewDecisionHandler();
        var rejectResult = decisionHandler.evaluate(decisionContext(
                "design.decision",
                List.of(reviewRef)
        ));
        assertEquals(NodeStatus.COMPLETED, rejectResult.status());
        assertEquals("NEEDS_REVISION", rejectResult.outcome());

        // Write iteration history simulating the rejection
        var iterationsDir = tempDir.resolve(".agent/workstreams/ws-int/iterations");
        Files.createDirectories(iterationsDir);
        Files.writeString(iterationsDir.resolve("iter-001.json"), """
                {
                  "iterationId": "iter-001",
                  "parentIterationId": null,
                  "number": 1,
                  "stageId": "stage-design",
                  "status": "active",
                  "triggerNodeInstanceId": "node-design-decision-001",
                  "triggerAction": "START",
                  "targetNodeDefinitionId": "design.claude_design",
                  "reason": "",
                  "createdAt": "2026-05-28T08:00:00Z"
                }
                """);
        Files.writeString(iterationsDir.resolve("iter-002.json"), """
                {
                  "iterationId": "iter-002",
                  "parentIterationId": "iter-001",
                  "number": 2,
                  "stageId": "stage-design",
                  "status": "active",
                  "triggerNodeInstanceId": "node-design-decision-001",
                  "triggerAction": "NEEDS_REVISION",
                  "targetNodeDefinitionId": "design.claude_design",
                  "reason": "Missing cache invalidation strategy",
                  "createdAt": "2026-05-28T08:05:00Z"
                }
                """);

        // Revised design output
        fixture.writeAgentOutput("design-claude-v2.md", """
                # Design v2

                Added cache invalidation via TTL + event bus.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Revised design with invalidation strategy
                ```
                """);

        var revisedRef = ".agent/workstreams/ws-int/runs/run-001/outputs/design-claude-v2.md";

        // Build context for the revised iteration — should include iteration history
        var ctxHandler = new ContextBuildPackageHandler();
        var ctxResult = ctxHandler.execute(taskContext(
                "context.design_claude",
                "context.build_package",
                List.of(revisedRef)
        ));
        assertEquals(NodeStatus.COMPLETED, ctxResult.status());

        var packagePath = (String) ctxResult.result().get("context_package");
        var markdown = Files.readString(tempDir.resolve(packagePath), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("Design caching layer"), "Requirement must be present");
        assertTrue(markdown.contains("iter-002"), "Iteration history must include the rejection loop");
        assertTrue(markdown.contains("NEEDS_REVISION"), "Must show the trigger action");
        assertTrue(markdown.contains("Missing cache invalidation"), "Must include rejection reason");

        // Round 2: architecture review approves
        fixture.writeAgentOutput("codex-arch-review-approve.md", """
                # Architecture Review v2

                LGTM.

                ```dual-control
                decision: READY_FOR_ARCH_REVIEW
                summary: Invalidation strategy addresses concern
                ```
                """);
        var approveReviewRef = ".agent/workstreams/ws-int/runs/run-001/outputs/codex-arch-review-approve.md";
        var approveResult = decisionHandler.evaluate(decisionContext(
                "design.decision",
                List.of(approveReviewRef)
        ));
        assertEquals("READY_FOR_ARCH_REVIEW", approveResult.outcome());
    }

    @Test
    void chain6_blockedDecisionPath() throws Exception {
        fixture.writeAgentOutput("codex-review-blocked.md", """
                # Review

                Critical blocker found: API key is hardcoded.

                ```dual-control
                decision: BLOCKED
                summary: Hardcoded API key is a security violation
                ```
                """);

        var reviewRef = ".agent/workstreams/ws-int/runs/run-001/outputs/codex-review-blocked.md";
        var handler = new ReviewDecisionHandler();

        var result = handler.evaluate(decisionContext(
                "acceptance.decision",
                List.of(reviewRef)
        ));

        assertEquals(NodeStatus.BLOCKED, result.status());
        assertEquals("BLOCKED", result.outcome());
        assertEquals("BLOCKED", result.result().get("decision"));
        assertEquals("Hardcoded API key is a security violation", result.result().get("summary"));
        assertEquals("dual-control", result.result().get("source"));
    }

    @Test
    void chain7_multiProfileGovernance() throws Exception {
        fixture.writeRequirement("Full feature.\n");
        fixture.writeAgentOutput("agent-1.md", "## Output 1\n\nSome agent content.\n");
        fixture.writeGitEvidence("M src/A.java\nM src/B.java", "2 files changed, 100 insertions(+)", null);

        // Write human feedback
        var nodesDir = tempDir.resolve(".agent/workstreams/ws-int/nodes");
        Files.createDirectories(nodesDir);
        Files.writeString(nodesDir.resolve("node-gate-human-approval-001.json"), """
                {
                  "nodeInstanceId": "node-gate-human-approval-001",
                  "nodeDefinitionId": "gate.human_design_approval",
                  "finishedAt": "2026-05-28T09:00:00Z",
                  "result": {
                    "human_action": "revise",
                    "note": "Please add error handling for edge cases"
                  }
                }
                """);

        // product_review: should NOT have human_feedback, git_evidence
        var productResult = buildContextPackage("product_review");
        var productMd = readPackageMarkdown(productResult);
        assertTrue(productMd.contains("## Context Governance"), "product_review should have governance");
        assertTrue(productMd.contains("User Requirement"));
        assertTrue(productMd.contains("Recent Agent Outputs"));
        assertFalse(productMd.contains("## Human Feedback"), "product_review should NOT have human_feedback");
        assertFalse(productMd.contains("## Git Evidence"), "product_review should NOT have git_evidence");
        assertFalse(productMd.contains("## Iteration History"), "product_review should NOT have iteration_history");

        // acceptance: should have EVERYTHING including git_evidence
        var acceptanceResult = buildContextPackage("acceptance");
        var acceptanceMd = readPackageMarkdown(acceptanceResult);
        assertTrue(acceptanceMd.contains("User Requirement"));
        assertTrue(acceptanceMd.contains("## Git Evidence"), "acceptance should have git_evidence");
        assertTrue(acceptanceMd.contains("## Human Feedback"), "acceptance should have human_feedback");

        // full: should have everything, requirement is PLACEHOLDER-friendly
        var fullResult = buildContextPackage("full");
        var fullMd = readPackageMarkdown(fullResult);
        assertTrue(fullMd.contains("## Context Governance"));
        assertTrue(fullMd.contains("User Requirement"));

        // Verify profile-specific budget enforcement
        var productBudget = ContextProfiles.budgetFor("product_review");
        assertTrue(productMd.length() <= productBudget.totalBudget(),
                "product_review (" + productMd.length() + ") must fit " + productBudget.totalBudget() + " budget");

        var acceptanceBudget = ContextProfiles.budgetFor("acceptance");
        assertTrue(acceptanceMd.length() <= acceptanceBudget.totalBudget(),
                "acceptance (" + acceptanceMd.length() + ") must fit " + acceptanceBudget.totalBudget() + " budget");
    }

    @Test
    void chain8_handoffJsonFromEmptyHandoff() throws Exception {
        var handoffRef = ".agent/workstreams/ws-int/runs/run-010/outputs/stage-claude-handoff.md";
        fixture.writeHandoff("""
                # Minimal Handoff

                Done with this batch.

                ```dual-control
                decision: HANDOFF_READY
                summary: Minimal completion
                ```
                """);

        var handler = new PromptGenerateNextStageHandler();
        var result = handler.execute(taskContext(
                "stage.generate_next_prompt",
                "prompt.generate_next_stage",
                List.of(handoffRef)
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());

        var handoffJsonRef = ".agent/workstreams/ws-int/artifacts/next-stage/handoff.json";
        assertTrue(Files.exists(tempDir.resolve(handoffJsonRef)));

        var mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(
                tempDir.resolve(handoffJsonRef).toFile(), Map.class);

        assertEquals(1, json.get("schemaVersion"));
        assertEquals("ws-int", json.get("workstreamId"));
        assertEquals("stage-test", json.get("stageId"));
        assertEquals("iter-001", json.get("iterationId"));

        @SuppressWarnings("unchecked")
        var completedWork = (List<String>) json.get("completedWork");
        assertTrue(completedWork.isEmpty(), "No Completed Work section → empty array");

        @SuppressWarnings("unchecked")
        var changedFiles = (List<String>) json.get("changedFiles");
        assertTrue(changedFiles.isEmpty(), "No Changed Files section → empty array");

        @SuppressWarnings("unchecked")
        var openRisks = (List<String>) json.get("openRisks");
        assertTrue(openRisks.isEmpty(), "No Open Risks section → empty array");

        var verificationState = (String) json.get("verificationState");
        assertTrue(verificationState.isEmpty(), "No Verification State section → empty string");

        assertTrue(((String) json.get("generatedAt")).startsWith("202"));
    }

    // --- Helpers ---

    private NodeResult buildContextPackage(String profile) throws Exception {
        var handler = new ContextBuildPackageHandler();
        return handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws-int",
                new NodeDefinition("context.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.agent",
                        "profile", profile
                )),
                node("context.test", NodeKind.TASK, "context.build_package", List.of())
        ));
    }

    private String readPackageMarkdown(NodeResult result) throws Exception {
        var packagePath = (String) result.result().get("context_package");
        return Files.readString(tempDir.resolve(packagePath), StandardCharsets.UTF_8);
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
