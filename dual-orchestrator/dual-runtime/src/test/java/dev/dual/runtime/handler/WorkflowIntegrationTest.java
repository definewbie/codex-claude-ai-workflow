package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        var decisionSidecar = fixture.writeAgentOutput("intake-claude-brainstorm.contract.json", """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "intake.codex_product_review",
                  "decision": "READY_FOR_PRODUCT_REVIEW",
                  "summary": "Auth module brainstorm complete"
                }
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
        assertFalse(markdown.contains("Build user auth module"), "active profile leaves the single requirement injection to the prompt renderer");
        assertTrue(markdown.contains("READY_FOR_PRODUCT_REVIEW"));

        var decisionHandler = new ReviewDecisionHandler();
        var decisionResult = decisionHandler.evaluate(decisionContext(
                "intake.decision",
                List.of(contextRef, toWorkspaceRef(decisionSidecar))
        ));
        assertEquals(NodeStatus.COMPLETED, decisionResult.status());
        assertEquals("READY_FOR_PRODUCT_REVIEW", decisionResult.outcome());
        assertEquals("READY_FOR_PRODUCT_REVIEW", decisionResult.result().get("decision"));
    }

    @Test
    void gateDocumentAndReviewPacketExposeTheSameActions() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-actions"));
        Files.createDirectories(paths.workstreamDir());
        var gateDefinition = new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of(
                "allowed_actions", List.of("approve", "revise")));
        var definition = new WorkflowDefinition(
                "action-visibility",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        gateDefinition,
                        new NodeDefinition("next", NodeKind.TASK, "noop.task", Map.of())),
                List.of(
                        new EdgeDefinition("first", "READY", "gate.review"),
                        new EdgeDefinition("gate.review", "approved", "next"),
                        new EdgeDefinition("gate.review", "revise", "first"))
        );
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-actions", definition);
        var first = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "READY", List.of(), Map.of());
        var current = ledger.loadCurrent(paths);
        var gateNode = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        var gate = ledger.loadOpenGateForNode(paths, gateNode.nodeInstanceId());
        var instance = new NodeInstance(
                gateNode.nodeInstanceId(),
                gateNode.nodeDefinitionId(),
                gateNode.kind(),
                gateNode.handlerType(),
                gateNode.status(),
                gateNode.stageId(),
                gateNode.iterationId(),
                gateNode.inputRefs(),
                gateNode.outputRefs(),
                gateNode.result(),
                gateNode.runRef(),
                gateNode.startedAt(),
                gateNode.finishedAt()
        );

        var result = new HumanReviewGateHandler().openOrInspect(new GateHandler.GateContext(tempDir,
                "ws-actions",
                gateDefinition,
                instance,
                "READY",
                dev.dual.runtime.RuntimeTestLedger.ledger()));
        @SuppressWarnings("unchecked")
        var packet = (Map<String, Object>) new ObjectMapper().readValue(
                tempDir.resolve(String.valueOf(result.result().get("review_packet_json"))).toFile(), Map.class);

        assertEquals(gate.allowedActions(), result.result().get("allowed_actions"));
        assertEquals(gate.allowedActions(), packet.get("allowedActions"));
    }

    @Test
    void chain2_handoffToNextStageAndJson() throws Exception {
        var handoffRef = ".agent/workstreams/ws-int/runs/run-010/outputs/stage-claude-handoff.json";
        fixture.writeHandoffContract(
                List.of("Designed the API schema", "Created component architecture", "Wrote unit tests for auth module"),
                List.of("src/main/java/dev/dual/auth/AuthService.java",
                        "src/main/java/dev/dual/auth/AuthController.java",
                        "src/test/java/dev/dual/auth/AuthServiceTest.java"),
                List.of("Rate limit behavior under load", "Token refresh edge case on midnight UTC"),
                "All self-tests passed. 47/47 green.",
                "Continue with integration testing for auth endpoints.");

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
    void chain2b_handoffJsonPreservesTypedMultilingualFields() throws Exception {
        var handoffRef = ".agent/workstreams/ws-int/runs/run-010/outputs/stage-claude-handoff.json";
        fixture.writeHandoffContract(
                List.of("B4 新用户引导卡片已实现", "hasNoRecords 计算属性已添加"),
                List.of("miniprogram/src/pages/record/index.vue"),
                List.of("未执行构建检查", "未进行运行时 UI 测试"),
                "grep B4 文案通过；npm run build 跳过，缺少 node_modules",
                "没有特定的下一个提示。");

        var handler = new PromptGenerateNextStageHandler();
        var result = handler.execute(taskContext(
                "stage.generate_next_prompt",
                "prompt.generate_next_stage",
                List.of(handoffRef)
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());

        var handoffJsonRef = ".agent/workstreams/ws-int/artifacts/next-stage/handoff.json";
        var mapper = new ObjectMapper();
        @SuppressWarnings("unchecked")
        Map<String, Object> json = mapper.readValue(
                tempDir.resolve(handoffJsonRef).toFile(), Map.class);

        @SuppressWarnings("unchecked")
        var completedWork = (List<String>) json.get("completedWork");
        assertFalse(completedWork.isEmpty());
        assertTrue(completedWork.stream().anyMatch(line -> line.contains("B4 新用户引导卡片")));
        assertTrue(completedWork.stream().anyMatch(line -> line.contains("hasNoRecords")));

        @SuppressWarnings("unchecked")
        var changedFiles = (List<String>) json.get("changedFiles");
        assertEquals(List.of("miniprogram/src/pages/record/index.vue"), changedFiles);

        @SuppressWarnings("unchecked")
        var openRisks = (List<String>) json.get("openRisks");
        assertEquals(List.of("未执行构建检查", "未进行运行时 UI 测试"), openRisks);

        var verificationState = (String) json.get("verificationState");
        assertTrue(verificationState.contains("grep B4 文案"));
        assertTrue(verificationState.contains("缺少 node_modules"));
    }

    @Test
    void chain3_activeProfileBudgetExcludesDuplicateRequirement() throws Exception {
        var authorized = AuthoredInputFixture.before(tempDir, "ws-int", "implement.claude_implement", "run-implement");
        var largeRequirement = "A".repeat(8_000) + "\n";
        fixture.writeRequirement(largeRequirement);
        fixture.writeGitEvidence("M src/Foo.java\nM src/Bar.java", "2 files changed, 400 insertions(+)", null);

        for (int i = 0; i < 6; i++) {
            var content = "## Agent Run " + i + "\n\n" + "X".repeat(5_000) + "\n";
            fixture.writeAgentOutput("agent-output-" + i + ".md", content);
        }

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-int", new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "required_contract_phase", "EXECUTION", "target_node", "implement.claude_implement",
                        "profile", "implementation"
                )), authorized.instance(), dev.dual.runtime.RuntimeTestLedger.ledger()));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var packagePath = (String) result.result().get("context_package");
        var markdown = Files.readString(tempDir.resolve(packagePath), StandardCharsets.UTF_8);

        assertTrue(markdown.contains("## Context Governance"), "Should have governance summary");
        assertFalse(markdown.contains("User Requirement"),
                "active context profiles must leave requirement injection to AgentPromptRenderer");
        assertFalse(markdown.contains(largeRequirement.stripTrailing()),
                "duplicate requirement content must not consume context-package budget");

        var budget = ContextProfiles.implementation();
        // Budget is now token-based; verify estimated tokens within budget
        var estimatedTokens = (markdown.length() + 3) / 4;
        assertTrue(estimatedTokens <= budget.totalBudget(),
                "Estimated tokens (" + estimatedTokens + ") must be <= totalBudget (" + budget.totalBudget() + ")");

        assertTrue(markdown.contains("## Context Governance"));
        var govSection = markdown.substring(markdown.indexOf("## Context Governance"));
        assertTrue(govSection.contains("full") || govSection.contains("summary") || govSection.contains("latest") || govSection.contains("diff_stat"),
                "Governance table should show degradation levels");
    }

    @Test
    void chain4_explicitFullProfileCompressesOversizedRequirement() throws Exception {
        var authorized = AuthoredInputFixture.before(tempDir, "ws-int", "implement.claude_implement", "run-implement");
        var oversizedRequirement = "R".repeat(25_000) + "\n";
        fixture.writeRequirement(oversizedRequirement);

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-int", new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "required_contract_phase", "EXECUTION", "target_node", "implement.claude_implement",
                        "profile", "full"
                )), authorized.instance(), dev.dual.runtime.RuntimeTestLedger.ledger()));

        // ADAPTIVE policy now compresses oversized requirement instead of hard-failing
        assertEquals(NodeStatus.COMPLETED, result.status());
        var packagePath = (String) result.result().get("context_package");
        var markdown = Files.readString(tempDir.resolve(packagePath), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("adaptively compressed"),
                "Oversized requirement should be adaptively compressed");
    }

    // --- Extended integration tests ---

    @Test
    void chain5_needsRevisionLoopWithContextCarryover() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(tempDir, "src/cache.java", AuthoredStageSequenceTest.PlanTopology.REVIEWED_HUMAN, "ws-int");
        f.advanceToGate(); f.approve();
        assertEquals("gate.human_plan_approval", f.currentNode());
        f.revise("Include cache invalidation in the plan");
        assertEquals("gate.human_plan_approval", f.currentNode());
        var revised = f.inputs.get("plan.claude_plan");
        assertEquals(List.of("plan issue 1", "Include cache invalidation in the plan"),
                revised.obligations().stream().map(AuthoredExecutionContext.Obligation::text).toList());
        assertNotNull(revised.parent());
        assertEquals(2, java.util.Collections.frequency(f.calls, "plan.codex_plan_review"));
        f.approve();
        assertEquals(dev.dual.core.workflow.WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
        assertNotNull(f.inputs.get("implement.claude_implement").reviewedPlan());
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
        fixture.writeAgentOutput("codex-review-blocked.contract.json", """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "acceptance.codex_review",
                  "decision": "BLOCKED",
                  "summary": "Hardcoded API key is a security violation"
                }
                """);

        var reviewRef = ".agent/workstreams/ws-int/runs/run-001/outputs/codex-review-blocked.contract.json";
        var handler = new ReviewDecisionHandler();

        var result = handler.evaluate(decisionContext(
                "acceptance.decision",
                List.of(reviewRef)
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("BLOCKED", result.outcome());
        assertEquals("BLOCKED", result.result().get("decision"));
        assertEquals("Hardcoded API key is a security violation", result.result().get("summary"));
        assertEquals("contract-sidecar", result.result().get("source"));
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
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-gate-human-approval-001",
                  "nodeDefinitionId": "gate.human_design_approval",
                  "kind": "GATE",
                  "handlerType": "human.review_gate",
                  "status": "COMPLETED",
                  "stageId": "design",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "inputTransition": {},
                  "outputRefs": [],
                  "runRef": null,
                  "latestExecutionId": "",
                  "startedAt": "2026-05-28T08:59:00Z",
                  "finishedAt": "2026-05-28T09:00:00Z",
                  "result": {
                    "human_action": "revise",
                    "note": "Please add error handling for edge cases"
                  }
                }
                """);

        // Write TaskContract artifact (required by acceptance profile's task_contract section)
        var artifactsDir = tempDir.resolve(".agent/workstreams/ws-int/artifacts");
        Files.createDirectories(artifactsDir);
        Files.writeString(artifactsDir.resolve("task-contract.json"), """
                {
                  "id": "tc-test",
                  "version": 1,
                  "created_at": "2026-05-28T09:00:00Z",
                  "objective": "Test objective",
                  "scope": {
                    "included": [{"path": "src/App.java", "operation": "MODIFY"}],
                    "excluded": []
                  },
                  "acceptance_criteria": ["AC1", "AC2"],
                  "test_requirements": ["Run tests"],
                  "constraints": ["Keep scope narrow"],
                  "non_goals": ["No unrelated changes"],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "status": "READY_FOR_REVIEW",
                  "compilation_pass": 1,
                  "source": { "raw_request_ref": "requirement.md", "created_by": "contract_compiler" }
                }
                """);
        TaskContractProjectionFixture.seedCompileBinding(tempDir, "ws-int");

        // product_review: should NOT have human_feedback, git_evidence
        var productResult = buildContextPackage("product_review");
        var productMd = readPackageMarkdown(productResult);
        assertTrue(productMd.contains("## Context Governance"), "product_review should have governance");
        assertFalse(productMd.contains("User Requirement"));
        assertTrue(productMd.contains("Recent Agent Outputs"));
        assertFalse(productMd.contains("## Human Feedback"), "product_review should NOT have human_feedback");
        assertFalse(productMd.contains("## Git Evidence"), "product_review should NOT have git_evidence");
        assertFalse(productMd.contains("## Iteration History"), "product_review should NOT have iteration_history");

        // acceptance: should have EVERYTHING including git_evidence
        var acceptanceResult = buildContextPackage("acceptance");
        assertEquals(NodeStatus.COMPLETED, acceptanceResult.status(), acceptanceResult.result().toString());
        var acceptanceMd = readPackageMarkdown(acceptanceResult);
        assertFalse(acceptanceMd.contains("User Requirement"));
        assertTrue(acceptanceMd.contains("## Git Evidence"), "acceptance should have git_evidence");
        assertTrue(acceptanceMd.contains("## Human Feedback"), "acceptance should have human_feedback");

        // full: should have everything, requirement is PLACEHOLDER-friendly
        var fullResult = buildContextPackage("full");
        var fullMd = readPackageMarkdown(fullResult);
        assertTrue(fullMd.contains("## Context Governance"));
        assertTrue(fullMd.contains("User Requirement"));
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
        var handoffRef = ".agent/workstreams/ws-int/runs/run-010/outputs/stage-claude-handoff.json";
        fixture.writeGitEvidence("M miniprogram/src/pages/record/index.vue", "1 file changed, 5 insertions(+)", null);
        fixture.writeHandoffContract(List.of(), List.of(), List.of(), "", "");

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
        assertEquals(List.of("miniprogram/src/pages/record/index.vue"), changedFiles);

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
        return handler.execute(new TaskHandler.TaskContext(tempDir, "ws-int", new NodeDefinition("context.test", NodeKind.TASK, "context.build_package", Map.of(
                        "required_contract_phase", "EXECUTION", "target_node", "next.agent",
                        "profile", profile
                )), node("context.test", NodeKind.TASK, "context.build_package", List.of()), dev.dual.runtime.RuntimeTestLedger.ledger()));
    }

    private String readPackageMarkdown(NodeResult result) throws Exception {
        var packagePath = (String) result.result().get("context_package");
        return Files.readString(tempDir.resolve(packagePath), StandardCharsets.UTF_8);
    }

    private TaskHandler.TaskContext taskContext(String nodeId, String handlerType, List<String> inputRefs) {
        return new TaskHandler.TaskContext(tempDir, "ws-int", new NodeDefinition(nodeId, NodeKind.TASK, handlerType, Map.of()), node(nodeId, NodeKind.TASK, handlerType, inputRefs), dev.dual.runtime.RuntimeTestLedger.ledger());
    }

    private DecisionHandler.DecisionContext decisionContext(String nodeId, List<String> inputRefs) {
        return new DecisionHandler.DecisionContext(tempDir, "ws-int", new NodeDefinition(nodeId, NodeKind.DECISION, "review.decision", Map.of()), node(nodeId, NodeKind.DECISION, "review.decision", inputRefs), dev.dual.runtime.RuntimeTestLedger.ledger());
    }

    private String toWorkspaceRef(Path path) {
        return tempDir.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
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
