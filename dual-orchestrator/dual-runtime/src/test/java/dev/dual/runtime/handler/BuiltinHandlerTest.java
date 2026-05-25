package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

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

class BuiltinHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void humanGateReturnsWaitingResult() throws Exception {
        var handler = new HumanReviewGateHandler();
        var result = handler.openOrInspect(new GateHandler.GateContext(
                tempDir,
                "ws",
                gateDefinition(),
                node("gate.human_design_approval", NodeKind.GATE, "human.review")
        ));

        assertEquals(NodeStatus.WAITING, result.status());
        assertEquals("open", result.outcome());
        assertEquals(List.of("approve", "revise"), result.result().get("allowed_actions"));
    }

    @Test
    void reviewDecisionReadsDualControlOutcomeFromInputRef() throws Exception {
        var output = writeOutput("codex-review.md", """
                # Review

                ```dual-control
                decision: READY_TO_IMPLEMENT
                summary: plan is ready
                ```
                """);
        var handler = new ReviewDecisionHandler();
        var plan = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("plan.decision"),
                node("plan.decision", NodeKind.DECISION, "review.decision", List.of(output))
        ));

        assertEquals("READY_TO_IMPLEMENT", plan.outcome());
        assertEquals(NodeStatus.COMPLETED, plan.status());
        assertEquals("dual-control", plan.result().get("source"));
    }

    @Test
    void reviewDecisionFailsWhenDualControlIsMissing() throws Exception {
        var output = writeOutput("codex-review.md", "# Review\nNo control block.\n");
        var handler = new ReviewDecisionHandler();
        var result = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("intake.decision"),
                node("intake.decision", NodeKind.DECISION, "review.decision", List.of(output))
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("MISSING_DUAL_CONTROL", result.outcome());
    }

    @Test
    void reviewDecisionFailsStructurallyWhenDualControlMissesDecision() throws Exception {
        var output = writeOutput("codex-review.md", """
                ```dual-control
                summary: forgot the decision
                ```
                """);
        var handler = new ReviewDecisionHandler();
        var result = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("design.decision"),
                node("design.decision", NodeKind.DECISION, "review.decision", List.of(output))
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("INVALID_DUAL_CONTROL", result.outcome());
        assertTrue(((List<?>) result.result().get("allowed_decisions")).contains("PASS"));
        assertEquals("forgot the decision", ((Map<?, ?>) result.result().get("control_values")).get("summary"));
    }

    @Test
    void reviewDecisionFailsWithAllowedDecisionsWhenDecisionIsUnsupported() throws Exception {
        var output = writeOutput("codex-review.md", """
                ```dual-control
                decision: ACCEPT
                summary: wrong decision family
                ```
                """);
        var handler = new ReviewDecisionHandler();
        var result = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("design.decision"),
                node("design.decision", NodeKind.DECISION, "review.decision", List.of(output))
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("INVALID_DECISION", result.outcome());
        assertTrue(((List<?>) result.result().get("allowed_decisions")).contains("NEEDS_REVISION"));
        assertEquals("ACCEPT", ((Map<?, ?>) result.result().get("control_values")).get("decision"));
    }

    @Test
    void reviewDecisionFailsWhenDualControlHasMalformedLine() throws Exception {
        var output = writeOutput("codex-review.md", """
                ```dual-control
                decision PASS
                summary: malformed contract
                ```
                """);
        var handler = new ReviewDecisionHandler();
        var result = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("design.decision"),
                node("design.decision", NodeKind.DECISION, "review.decision", List.of(output))
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("INVALID_DUAL_CONTROL", result.outcome());
        var parseError = (Map<?, ?>) result.result().get("parse_error");
        assertEquals("MALFORMED_DUAL_CONTROL_LINE", parseError.get("code"));
    }

    @Test
    void reviewDecisionFailsWhenOutputContainsMultipleDualControlBlocks() throws Exception {
        var output = writeOutput("codex-review.md", """
                ```dual-control
                decision: PASS
                ```

                ```dual-control
                decision: NEEDS_REVISION
                ```
                """);
        var handler = new ReviewDecisionHandler();
        var result = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("design.decision"),
                node("design.decision", NodeKind.DECISION, "review.decision", List.of(output))
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("INVALID_DUAL_CONTROL", result.outcome());
        var parseError = (Map<?, ?>) result.result().get("parse_error");
        assertEquals("MULTIPLE_DUAL_CONTROL_BLOCKS", parseError.get("code"));
    }

    @Test
    void reviewDecisionDoesNotFallBackToOlderOutputWhenLatestControlBlockIsInvalid() throws Exception {
        var older = writeOutput("older-codex-review.md", """
                ```dual-control
                decision: PASS
                summary: stale pass
                ```
                """);
        var latest = writeOutput("latest-codex-review.md", """
                ```dual-control
                decision PASS
                summary: latest output is malformed
                ```
                """);
        var handler = new ReviewDecisionHandler();
        var result = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("design.decision"),
                node("design.decision", NodeKind.DECISION, "review.decision", List.of(older, latest))
        ));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("INVALID_DUAL_CONTROL", result.outcome());
        var parseError = (Map<?, ?>) result.result().get("parse_error");
        assertEquals("MALFORMED_DUAL_CONTROL_LINE", parseError.get("code"));
    }

    @Test
    void reviewDecisionBlocksOnBlockedDecision() throws Exception {
        var output = writeOutput("codex-review.md", """
                ```dual-control
                decision: BLOCKED
                summary: requirement conflicts with repository constraints
                ```
                """);
        var handler = new ReviewDecisionHandler();
        var result = handler.evaluate(new DecisionHandler.DecisionContext(
                tempDir,
                "ws",
                decisionDefinition("design.decision"),
                node("design.decision", NodeKind.DECISION, "review.decision", List.of(output))
        ));

        assertEquals(NodeStatus.BLOCKED, result.status());
        assertEquals("BLOCKED", result.outcome());
    }

    @Test
    void artifactValidationCollectsGitEvidence() throws Exception {
        git(tempDir, "init");
        git(tempDir, "config", "user.email", "dual@example.test");
        git(tempDir, "config", "user.name", "Dual Test");
        Files.writeString(tempDir.resolve("README.md"), "before\n");
        git(tempDir, "add", "README.md");
        git(tempDir, "commit", "-m", "init");
        Files.writeString(tempDir.resolve("README.md"), "after\n");
        Files.createDirectories(tempDir.resolve(".agent"));
        Files.writeString(tempDir.resolve(".agent/noise.log"), "ledger noise\n");

        var handler = new ArtifactValidateHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                taskDefinition("implement.artifact_validation", "artifact.validate"),
                node("implement.artifact_validation", NodeKind.TASK, "artifact.validate")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(true, result.result().get("git_available"));
        assertEquals(true, result.result().get("has_changes"));
        assertEquals(1, result.result().get("changed_file_count"));
        assertTrue(result.outputRefs().contains(".agent/workstreams/ws/artifacts/git-evidence/git-evidence.json"));
        assertTrue(result.outputRefs().contains(".agent/workstreams/ws/artifacts/git-evidence/diff.patch"));

        var evidenceDir = tempDir.resolve(".agent/workstreams/ws/artifacts/git-evidence");
        assertTrue(Files.readString(evidenceDir.resolve("git-evidence.json")).contains("\"available\" : true"));
        assertTrue(Files.readString(evidenceDir.resolve("git-status-short.txt")).contains("README.md"));
        assertFalse(Files.readString(evidenceDir.resolve("git-status-short.txt")).contains(".agent"));
        assertTrue(Files.readString(evidenceDir.resolve("diff.patch")).contains("-before"));
        assertTrue(Files.readString(evidenceDir.resolve("changed-files.txt")).contains("README.md"));
    }

    @Test
    void artifactValidationMarksGitUnavailableWithoutFailingWorkflow() throws Exception {
        var handler = new ArtifactValidateHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                taskDefinition("implement.artifact_validation", "artifact.validate"),
                node("implement.artifact_validation", NodeKind.TASK, "artifact.validate")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(false, result.result().get("git_available"));
        assertEquals(false, result.result().get("has_changes"));
        assertEquals(0, result.result().get("changed_file_count"));
        assertTrue(result.outputRefs().contains(".agent/workstreams/ws/artifacts/git-evidence/git-evidence.json"));
        assertFalse(Files.exists(tempDir.resolve(".agent/workstreams/ws/artifacts/git-evidence/diff.patch")));
        assertTrue(Files.readString(tempDir.resolve(".agent/workstreams/ws/artifacts/git-evidence/git-evidence.json"))
                .contains("\"available\" : false"));
    }

    @Test
    void contextPackageIncludesRequirementInputArtifactAndGitEvidencePointers() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws");
        var input = workstreamDir.resolve("runs/run-001/outputs/design.md");
        var evidence = workstreamDir.resolve("artifacts/git-evidence/git-evidence.json");
        Files.createDirectories(input.getParent());
        Files.createDirectories(evidence.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build context packages.\n");
        Files.writeString(input, "Claude design output.\n");
        Files.writeString(evidence, "{\"available\":true}\n");

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                new NodeDefinition("context.design_codex_arch_review", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "design.codex_arch_review",
                        "purpose", "Package design for Codex."
                )),
                node(
                        "context.design_codex_arch_review",
                        NodeKind.TASK,
                        "context.build_package",
                        List.of(".agent/workstreams/ws/runs/run-001/outputs/design.md")
                )
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals("design.codex_arch_review", result.result().get("target_node"));
        var packageRef = (String) result.result().get("context_package");
        assertTrue(result.outputRefs().contains(packageRef));

        var markdown = Files.readString(tempDir.resolve(packageRef));
        assertTrue(markdown.contains("Target Node: design.codex_arch_review"));
        assertTrue(markdown.contains("Build context packages."));
        assertTrue(markdown.contains("Claude design output."));
        assertTrue(markdown.contains("## Recent Agent Outputs"));
        assertTrue(markdown.contains("git-evidence.json"));
        assertTrue(markdown.contains("\"available\":true"));
    }

    @Test
    void contextPackageIncludesRecentAgentOutputsAfterHumanGateWithNoInputRefs() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws");
        var planOutput = workstreamDir.resolve("runs/run-002/outputs/plan-codex-review.md");
        Files.createDirectories(planOutput.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement approved plan.\n");
        Files.writeString(planOutput, "READY_TO_IMPLEMENT with one note.\n");

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "implement.claude_implement"
                )),
                node("context.implement_claude", NodeKind.TASK, "context.build_package", List.of())
        ));

        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Implement approved plan."));
        assertTrue(markdown.contains("READY_TO_IMPLEMENT with one note."));
        assertTrue(markdown.contains("plan-codex-review.md"));
    }

    @Test
    void contextPackageUsesProfileToKeepImplementationContextFocused() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws");
        var planOutput = workstreamDir.resolve("runs/run-002/outputs/plan-codex-review.md");
        var evidence = workstreamDir.resolve("artifacts/git-evidence/git-evidence.json");
        Files.createDirectories(planOutput.getParent());
        Files.createDirectories(evidence.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Implement approved plan.\n");
        Files.writeString(planOutput, "READY_TO_IMPLEMENT with one note.\n");
        Files.writeString(evidence, "{\"available\":true}\n");

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "implement.claude_implement",
                        "profile", "implementation"
                )),
                node("context.implement_claude", NodeKind.TASK, "context.build_package", List.of())
        ));

        assertEquals("implementation", result.result().get("profile"));
        assertTrue(((List<?>) result.result().get("sections")).contains("recent_agent_outputs"));
        assertFalse(((List<?>) result.result().get("sections")).contains("git_evidence"));

        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Profile: implementation"));
        assertTrue(markdown.contains("READY_TO_IMPLEMENT with one note."));
        assertFalse(markdown.contains("## Git Evidence"));
        assertFalse(markdown.contains("\"available\":true"));
    }

    @Test
    void contextPackageAllowsExplicitSectionsAndSnippetLimits() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws");
        var output = workstreamDir.resolve("runs/run-003/outputs/long.md");
        Files.createDirectories(output.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Keep this focused.\n");
        Files.writeString(output, "abcdefghijklmnopqrstuvwxyz\n");

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                new NodeDefinition("context.custom", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "custom.agent",
                        "profile", "acceptance",
                        "include_sections", List.of("requirement", "recent_agent_outputs"),
                        "max_snippet_chars", 10,
                        "max_recent_agent_outputs", 1
                )),
                node("context.custom", NodeKind.TASK, "context.build_package", List.of())
        ));

        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Included Sections: requirement, recent_agent_outputs"));
        assertTrue(markdown.contains("Keep this focused."));
        assertTrue(markdown.contains("abcdefghij"));
        assertTrue(markdown.contains("[truncated; read full artifact"));
        assertFalse(markdown.contains("## Git Evidence"));
        assertFalse(markdown.contains("## Ledger Pointers"));
    }

    @Test
    void contextPackageIncludesHumanGateFeedbackFromNodeResult() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws");
        var nodesDir = workstreamDir.resolve("nodes");
        Files.createDirectories(nodesDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Revise the design.\n");
        Files.writeString(nodesDir.resolve("node-gate-human-design-approval-007.json"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-gate-human-design-approval-007",
                  "nodeDefinitionId": "gate.human_design_approval",
                  "kind": "GATE",
                  "handlerType": "human.review",
                  "status": "COMPLETED",
                  "stageId": "stage-gate",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "outputRefs": [],
                  "result": {
                    "human_action": "revise",
                    "note": "Codex Must Fix: narrow the implementation scope before coding."
                  },
                  "runRef": null,
                  "startedAt": null,
                  "finishedAt": "2026-05-24T01:00:00Z"
                }
                """);

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "implement.claude_implement"
                )),
                node("context.implement_claude", NodeKind.TASK, "context.build_package", List.of())
        ));

        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("## Human Feedback"));
        assertTrue(markdown.contains("gate.human_design_approval"));
        assertTrue(markdown.contains("Action: revise"));
        assertTrue(markdown.contains("Codex Must Fix: narrow the implementation scope before coding."));
    }

    @Test
    void contextPackageIncludesRecentWorkflowIterationHistory() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws");
        var iterationsDir = workstreamDir.resolve("iterations");
        Files.createDirectories(iterationsDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Continue the rejected implementation.\n");
        Files.writeString(iterationsDir.resolve("iter-001.json"), """
                {
                  "schemaVersion": 1,
                  "iterationId": "iter-001",
                  "parentIterationId": "",
                  "number": 1,
                  "stageId": "stage-intake",
                  "status": "active",
                  "triggerNodeInstanceId": "",
                  "triggerAction": "workflow_started",
                  "targetNodeDefinitionId": "intake.claude_brainstorm",
                  "reason": "Initial workflow iteration.",
                  "createdAt": "2026-05-24T00:00:00Z"
                }
                """);
        Files.writeString(iterationsDir.resolve("iter-002.json"), """
                {
                  "schemaVersion": 1,
                  "iterationId": "iter-002",
                  "parentIterationId": "iter-001",
                  "number": 2,
                  "stageId": "stage-implement",
                  "status": "active",
                  "triggerNodeInstanceId": "node-acceptance-decision-012",
                  "triggerAction": "REJECT",
                  "targetNodeDefinitionId": "context.implement_claude",
                  "reason": "Acceptance evidence is incomplete; add concrete verification before asking Codex again.",
                  "createdAt": "2026-05-24T01:00:00Z"
                }
                """);

        var handler = new ContextBuildPackageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "implement.claude_implement"
                )),
                node("context.implement_claude", NodeKind.TASK, "context.build_package", List.of())
        ));

        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("## Iteration History"));
        assertTrue(markdown.contains("### iter-002"));
        assertTrue(markdown.contains("- Parent: iter-001"));
        assertTrue(markdown.contains("- Trigger: REJECT"));
        assertTrue(markdown.contains("- Target Node: context.implement_claude"));
        assertTrue(markdown.contains("Acceptance evidence is incomplete"));
    }

    @Test
    void nextStagePromptHandlerGeneratesPromptFromHandoff() throws Exception {
        var handoff = tempDir.resolve(".agent/workstreams/ws/runs/run-004/outputs/stage-claude-handoff.md");
        Files.createDirectories(handoff.getParent());
        Files.writeString(handoff, """
                # Handoff

                ## Completed Work

                Implemented the accepted batch.

                ## Suggested Next Prompt

                Start the next batch by reviewing A6 before writing code.
                Keep scope narrow.

                ```dual-control
                decision: HANDOFF_READY
                summary: ready
                ```
                """);

        var handler = new PromptGenerateNextStageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                taskDefinition("stage.generate_next_prompt", "prompt.generate_next_stage"),
                node(
                        "stage.generate_next_prompt",
                        NodeKind.TASK,
                        "prompt.generate_next_stage",
                        List.of(".agent/workstreams/ws/runs/run-004/outputs/stage-claude-handoff.md")
                )
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var promptRef = ".agent/workstreams/ws/artifacts/next-stage/next-stage-prompt.md";
        assertTrue(result.outputRefs().contains(promptRef));
        assertEquals(promptRef, result.result().get("prompt_path"));
        assertEquals(true, result.result().get("used_suggested_prompt_section"));

        var markdown = Files.readString(tempDir.resolve(promptRef));
        assertTrue(markdown.contains("Start the next batch by reviewing A6 before writing code."));
        assertTrue(markdown.contains("Keep scope narrow."));
        assertTrue(markdown.contains("## Handoff Context"));
        assertTrue(markdown.contains("Implemented the accepted batch."));
    }

    @Test
    void finalReportHandlerGeneratesAcceptanceReport() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws");
        var selfTest = workstreamDir.resolve("runs/run-010/outputs/implement-claude-self-test.md");
        var acceptance = workstreamDir.resolve("runs/run-011/outputs/acceptance-codex-review.md");
        var evidence = workstreamDir.resolve("artifacts/git-evidence/git-evidence.json");
        Files.createDirectories(selfTest.getParent());
        Files.createDirectories(acceptance.getParent());
        Files.createDirectories(evidence.getParent());
        Files.writeString(workstreamDir.resolve("requirement.md"), "Ship the accepted lifecycle batch.\n");
        Files.writeString(selfTest, "Developer self-test passed.\n");
        Files.writeString(acceptance, "Codex accepts the implementation.\n");
        Files.writeString(evidence, "{\"available\":true,\"hasChanges\":true}\n");
        Files.writeString(evidence.getParent().resolve("changed-files.txt"), "README.md\n");

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                taskDefinition("acceptance.generate_final_report", "report.generate_final"),
                node("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(".agent/workstreams/ws/reports/iter-001-final-report.md", result.result().get("report_path"));
        assertTrue(result.outputRefs().contains(".agent/workstreams/ws/reports/latest-final-report.md"));

        var markdown = Files.readString(tempDir.resolve(".agent/workstreams/ws/reports/iter-001-final-report.md"));
        assertTrue(markdown.contains("# Dual Final Report"));
        assertTrue(markdown.contains("Ship the accepted lifecycle batch."));
        assertTrue(markdown.contains("Developer self-test passed."));
        assertTrue(markdown.contains("Codex accepts the implementation."));
        assertTrue(markdown.contains("changed-files.txt"));
        assertTrue(markdown.contains("\"hasChanges\":true"));
    }

    private static NodeDefinition gateDefinition() {
        return new NodeDefinition("gate.human_design_approval", NodeKind.GATE, "human.review", Map.of(
                "allowed_actions", List.of("approve", "revise")
        ));
    }

    private static NodeDefinition decisionDefinition(String id) {
        return new NodeDefinition(id, NodeKind.DECISION, "review.decision", Map.of());
    }

    private static NodeDefinition taskDefinition(String id, String handler) {
        return new NodeDefinition(id, NodeKind.TASK, handler, Map.of());
    }

    private static NodeInstance node(String id, NodeKind kind, String handlerType) {
        return node(id, kind, handlerType, List.of());
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

    private String writeOutput(String name, String content) throws Exception {
        var path = tempDir.resolve(".agent").resolve("workstreams").resolve("ws").resolve("runs").resolve("run-001").resolve("outputs").resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return tempDir.relativize(path).toString();
    }

    private static void git(Path workdir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("git command failed: " + command + "\n" + output);
        }
    }
}
