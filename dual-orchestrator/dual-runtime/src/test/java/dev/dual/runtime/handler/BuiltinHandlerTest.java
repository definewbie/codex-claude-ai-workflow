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
    void nextStagePromptHandlerReturnsArtifactReference() throws Exception {
        var handler = new PromptGenerateNextStageHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir,
                "ws",
                taskDefinition("stage.generate_next_prompt", "prompt.generate_next_stage"),
                node("stage.generate_next_prompt", NodeKind.TASK, "prompt.generate_next_stage")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertTrue(result.outputRefs().contains("artifact://next-stage-prompt"));
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
