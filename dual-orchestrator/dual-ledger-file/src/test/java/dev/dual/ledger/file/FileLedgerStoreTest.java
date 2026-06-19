package dev.dual.ledger.file;

import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLedgerStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void initializesWorkstreamLedger() throws Exception {
        var workstreamId = new DualId("ws-20260522T101500Z-7K3M");
        var workspaceId = new DualId("wsp-20260522T101500Z-4F8D");
        var workstream = Workstream.created(
                workstreamId,
                "A6 design",
                new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                workspaceId,
                Instant.parse("2026-05-22T10:15:00Z")
        );
        var workspace = Workspace.primaryGitWorktree(
                workspaceId,
                workstreamId,
                tempDir.resolve("repo"),
                tempDir.resolve("repo-agent-runs/ws-20260522T101500Z-7K3M"),
                "ai/ws-20260522T101500Z-7K3M",
                "main",
                "abc123"
        );

        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(paths, workstream, workspace, "# Requirement\n");

        assertTrue(Files.exists(paths.workstreamJson()));
        assertTrue(Files.exists(paths.workspaceJson(workspaceId)));
        assertTrue(Files.exists(paths.settingsJson()));
        assertTrue(Files.readString(paths.settingsJson()).contains("\"promptLanguage\" : \"auto\""));
        assertTrue(Files.exists(paths.requirementMarkdown()));
        assertTrue(Files.readString(paths.timelineJsonl()).contains("workstream_created"));
    }

    @Test
    void initializesWorkstreamSettingsWithPromptLanguage() throws Exception {
        var workstreamId = new DualId("ws-20260522T101500Z-7K3M");
        var workspaceId = new DualId("wsp-20260522T101500Z-4F8D");
        var workstream = Workstream.created(
                workstreamId,
                "A6 design",
                new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                workspaceId,
                Instant.parse("2026-05-22T10:15:00Z")
        );
        var workspace = Workspace.primaryGitWorktree(
                workspaceId,
                workstreamId,
                tempDir.resolve("repo"),
                tempDir.resolve("repo-agent-runs/ws-20260522T101500Z-7K3M"),
                "ai/ws-20260522T101500Z-7K3M",
                "main",
                "abc123"
        );

        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(
                paths,
                workstream,
                workspace,
                "# Requirement\n",
                WorkstreamSettingsDocument.of("zh")
        );

        assertTrue(Files.readString(paths.settingsJson()).contains("\"promptLanguage\" : \"zh-CN\""));
    }

    @Test
    void preparesClaudeDesignRunFromInitialCheckpoint() throws Exception {
        var workstreamId = new DualId("ws-20260522T101500Z-7K3M");
        var workspaceId = new DualId("wsp-20260522T101500Z-4F8D");
        var runId = new DualId("run-20260522T101600Z-8H2K");
        var workstream = Workstream.created(
                workstreamId,
                "A6 design",
                new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                workspaceId,
                Instant.parse("2026-05-22T10:15:00Z")
        );
        var workspace = Workspace.primaryGitWorktree(
                workspaceId,
                workstreamId,
                tempDir.resolve("repo"),
                tempDir,
                "ai/ws-20260522T101500Z-7K3M",
                "main",
                "abc123"
        );

        var store = new FileLedgerStore();
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        store.initializeWorkstream(paths, workstream, workspace, "# Requirement\n");
        var prepared = store.prepareClaudeDesignRun(tempDir, workstreamId.value(), runId);

        assertTrue(Files.exists(prepared.promptPath()));
        assertTrue(Files.exists(paths.runJson(runId)));
        assertTrue(Files.readString(prepared.promptPath()).contains("Current stage: design."));
        assertTrue(Files.readString(paths.currentCheckpointJson()).contains("claude_design_acpx_run"));
        var timeline = Files.readString(paths.timelineJsonl());
        assertTrue(timeline.contains("claude_design_prompt_prepared"));
        assertTrue(timeline.contains(runId.value()));
        assertTrue(timeline.contains("202"));
        assertTrue(timeline.lines().allMatch(line -> line.startsWith("{") && line.endsWith("}")));
    }

    @Test
    void preparesAndCompletesCodexDesignReviewAfterClaudeHandoff() throws Exception {
        var workstreamId = new DualId("ws-20260522T101500Z-7K3M");
        var workspaceId = new DualId("wsp-20260522T101500Z-4F8D");
        var claudeRunId = new DualId("run-20260522T101600Z-8H2K");
        var codexRunId = new DualId("run-20260522T101700Z-9J3L");
        var workstream = Workstream.created(
                workstreamId,
                "A6 design",
                new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                workspaceId,
                Instant.parse("2026-05-22T10:15:00Z")
        );
        var workspace = Workspace.primaryGitWorktree(
                workspaceId,
                workstreamId,
                tempDir.resolve("repo"),
                tempDir,
                "ai/ws-20260522T101500Z-7K3M",
                "main",
                "abc123"
        );

        var store = new FileLedgerStore();
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        store.initializeWorkstream(paths, workstream, workspace, "# Requirement\n");
        var claudeRun = store.prepareClaudeDesignRun(tempDir, workstreamId.value(), claudeRunId);
        var iterationDir = paths.workstreamDir().resolve("stages/01-design/iterations/001");
        var handoffMd = iterationDir.resolve("claude-handoff.md");
        var handoffJson = iterationDir.resolve("claude-handoff.json");
        Files.writeString(handoffMd, "# Claude Handoff\n");
        Files.writeString(handoffJson, "{\"status\":\"ready_for_external_review\"}");
        Files.writeString(claudeRun.stdoutPath(), """
                <<<DUAL_ARTIFACT_MANIFEST>>>
                {"status":"ready","artifacts":[{"type":"claude_design_handoff_markdown","path":"%s"},{"type":"claude_design_handoff_metadata","path":"%s"}]}
                <<<END_DUAL_ARTIFACT_MANIFEST>>>
                """.formatted(handoffMd, handoffJson));
        store.completeClaudeDesignRun(tempDir, workstreamId.value(), claudeRun.runId());

        var codexRun = store.prepareCodexDesignReviewRun(tempDir, workstreamId.value(), codexRunId);
        assertTrue(Files.readString(codexRun.promptPath()).contains("# Claude Handoff"));

        var reviewMd = iterationDir.resolve("codex-review.md");
        var reviewJson = iterationDir.resolve("codex-review.json");
        Files.writeString(reviewMd, "# Codex Review\nDecision: PASS\n");
        Files.writeString(reviewJson, validPassCodexReviewJson());
        Files.writeString(codexRun.stdoutPath(), """
                <<<DUAL_ARTIFACT_MANIFEST>>>
                {"status":"ready","artifacts":[{"type":"codex_design_review_markdown","path":"%s"},{"type":"codex_design_review_control","path":"%s"}]}
                <<<END_DUAL_ARTIFACT_MANIFEST>>>
                """.formatted(reviewMd, reviewJson));

        assertEquals("PASS", store.completeCodexDesignReviewRun(tempDir, workstreamId.value(), codexRun.runId()));
        assertTrue(Files.readString(paths.currentCheckpointJson()).contains("human_design_review"));
        assertTrue(Files.exists(paths.workstreamDir().resolve("current/human-gate.json")));
        assertTrue(Files.exists(iterationDir.resolve("human-review-package.md")));
        assertEquals("waiting_for_human", store.loadStatus(tempDir, workstreamId.value()).workstream().status());
        assertEquals("human_review_pending", store.loadStatus(tempDir, workstreamId.value()).stage().status());
        assertEquals("passed", store.loadStatus(tempDir, workstreamId.value()).iteration().status());
        assertTrue(Files.readString(paths.timelineJsonl()).contains("codex_decision_applied"));
    }

    @Test
    void approvesOpenHumanDesignGate() throws Exception {
        var fixture = completedCodexPassFixture();

        fixture.store().approveDesign(tempDir, fixture.workstreamId().value());

        var status = fixture.store().loadStatus(tempDir, fixture.workstreamId().value());
        assertEquals("design_approved", status.workstream().status());
        assertEquals("approved", status.stage().status());
        assertEquals("human_design_review_approved", status.checkpoint().lastCompletedAction());
        assertEquals("plan_not_implemented", status.checkpoint().pendingAction());
        assertTrue(Files.readString(fixture.paths().workstreamDir().resolve("current/human-gate.json")).contains("\"status\" : \"approved\""));
        assertTrue(Files.readString(fixture.paths().timelineJsonl()).contains("human_decision_saved"));
    }

    @Test
    void requestsRevisionFromOpenHumanDesignGate() throws Exception {
        var fixture = completedCodexPassFixture();
        var feedback = tempDir.resolve("human-feedback.md");
        Files.writeString(feedback, "请补充边界条件和验收口径。");

        fixture.store().requestDesignRevision(tempDir, fixture.workstreamId().value(), feedback);

        var status = fixture.store().loadStatus(tempDir, fixture.workstreamId().value());
        assertEquals("running", status.workstream().status());
        assertEquals("revision_pending", status.stage().status());
        assertEquals("claude_design_revision", status.checkpoint().pendingAction());
        assertTrue(Files.exists(fixture.paths().workstreamDir().resolve("stages/01-design/iterations/001/human-feedback.md")));
        assertTrue(Files.readString(fixture.paths().timelineJsonl()).contains("human_decision_saved"));
    }

    @Test
    void rejectsCodexReviewControlWithMissingRequiredFields() throws Exception {
        var fixture = preparedCodexReviewFixture("""
                {
                  "schema_version": 1,
                  "artifact_type": "codex_design_review",
                  "decision": "PASS",
                  "system_decision": "pause_for_human",
                  "requires_human_review": true
                }
                """);

        var failure = assertThrows(
                java.io.IOException.class,
                () -> fixture.store().completeCodexDesignReviewRun(tempDir, fixture.workstreamId().value(), fixture.codexRun().runId())
        );

        assertTrue(failure.getMessage().contains("invalid Codex review control"));
        assertTrue(Files.notExists(fixture.paths().currentHumanGateJson()));
    }

    @Test
    void rejectsCodexReviewControlForWrongWorkstream() throws Exception {
        var fixture = preparedCodexReviewFixture("""
                {
                  "schema_version": 1,
                  "artifact_type": "codex_design_review",
                  "artifact_id": "artifact-codex-review-001",
                  "workstream_id": "ws-wrong",
                  "stage_id": "stage-design",
                  "iteration_id": "iter-001",
                  "decision": "PASS",
                  "system_decision": "pause_for_human",
                  "markdown_path": "codex-review.md",
                  "must_fix": [],
                  "risks": [],
                  "recommended_next_action": "human_design_review",
                  "requires_human_review": true,
                  "next_prompt_ref": "next-claude-prompt.md"
                }
                """);

        var failure = assertThrows(
                java.io.IOException.class,
                () -> fixture.store().completeCodexDesignReviewRun(tempDir, fixture.workstreamId().value(), fixture.codexRun().runId())
        );

        assertTrue(failure.getMessage().contains("workstream_id"));
        assertTrue(Files.notExists(fixture.paths().currentHumanGateJson()));
    }

    @Test
    void rejectsCodexReviewControlWithInvalidDecision() throws Exception {
        var fixture = preparedCodexReviewFixture("""
                {
                  "schema_version": 1,
                  "artifact_type": "codex_design_review",
                  "artifact_id": "artifact-codex-review-001",
                  "workstream_id": "ws-20260522T101500Z-7K3M",
                  "stage_id": "stage-design",
                  "iteration_id": "iter-001",
                  "decision": "MAYBE",
                  "system_decision": "pause_for_human",
                  "markdown_path": "codex-review.md",
                  "must_fix": [],
                  "risks": [],
                  "recommended_next_action": "human_design_review",
                  "requires_human_review": true,
                  "next_prompt_ref": "next-claude-prompt.md"
                }
                """);

        var failure = assertThrows(
                java.io.IOException.class,
                () -> fixture.store().completeCodexDesignReviewRun(tempDir, fixture.workstreamId().value(), fixture.codexRun().runId())
        );

        assertTrue(failure.getMessage().contains("decision"));
        assertTrue(Files.notExists(fixture.paths().currentHumanGateJson()));
    }

    @Test
    void preparesClaudeDesignRevisionFromHumanFeedback() throws Exception {
        var fixture = completedCodexPassFixture();
        var feedback = tempDir.resolve("human-feedback.md");
        Files.writeString(feedback, "请补充边界条件和验收口径。");
        fixture.store().requestDesignRevision(tempDir, fixture.workstreamId().value(), feedback);

        var revisionRunId = new DualId("run-20260522T101800Z-4R2V");
        var prepared = fixture.store().prepareClaudeDesignRevisionRun(tempDir, fixture.workstreamId().value(), revisionRunId);

        var iteration002 = fixture.paths().workstreamDir().resolve("stages/01-design/iterations/002");
        assertTrue(Files.exists(iteration002.resolve("iteration.json")));
        assertTrue(Files.readString(prepared.promptPath()).contains("Current iteration: iter-002."));
        assertTrue(Files.readString(prepared.promptPath()).contains("请补充边界条件和验收口径。"));
        assertEquals("iter-002", fixture.store().loadStatus(tempDir, fixture.workstreamId().value()).iteration().iterationId());
        assertEquals("claude_design_acpx_run", fixture.store().loadStatus(tempDir, fixture.workstreamId().value()).checkpoint().pendingAction());
        assertTrue(Files.readString(fixture.paths().timelineJsonl()).contains("design_iteration_created"));
    }

    @Test
    void completesClaudeDesignRevisionUsingIteration002Artifacts() throws Exception {
        var fixture = completedCodexPassFixture();
        var feedback = tempDir.resolve("human-feedback.md");
        Files.writeString(feedback, "请补充边界条件和验收口径。");
        fixture.store().requestDesignRevision(tempDir, fixture.workstreamId().value(), feedback);

        var revisionRunId = new DualId("run-20260522T101800Z-4R2V");
        var prepared = fixture.store().prepareClaudeDesignRevisionRun(tempDir, fixture.workstreamId().value(), revisionRunId);
        var iteration002 = fixture.paths().workstreamDir().resolve("stages/01-design/iterations/002");
        var handoffMd = iteration002.resolve("claude-handoff.md");
        var handoffJson = iteration002.resolve("claude-handoff.json");
        Files.writeString(handoffMd, "# Revised Handoff\n");
        Files.writeString(handoffJson, "{\"status\":\"ready_for_external_review\"}");
        Files.writeString(prepared.stdoutPath(), """
                <<<DUAL_ARTIFACT_MANIFEST>>>
                {"status":"ready","artifacts":[{"type":"claude_design_handoff_markdown","path":"%s"},{"type":"claude_design_handoff_metadata","path":"%s"}]}
                <<<END_DUAL_ARTIFACT_MANIFEST>>>
                """.formatted(handoffMd, handoffJson));

        fixture.store().completeClaudeDesignRun(tempDir, fixture.workstreamId().value(), prepared.runId());

        var status = fixture.store().loadStatus(tempDir, fixture.workstreamId().value());
        assertEquals("iter-002", status.checkpoint().iterationId());
        assertEquals("codex_design_review", status.checkpoint().pendingAction());
    }

    private CodexPassFixture completedCodexPassFixture() throws Exception {
        var fixture = preparedCodexReviewFixture(validPassCodexReviewJson());
        fixture.store().completeCodexDesignReviewRun(tempDir, fixture.workstreamId().value(), fixture.codexRun().runId());
        return new CodexPassFixture(fixture.store(), fixture.paths(), fixture.workstreamId());
    }

    private PreparedCodexReviewFixture preparedCodexReviewFixture(String reviewJsonContent) throws Exception {
        var workstreamId = new DualId("ws-20260522T101500Z-7K3M");
        var workspaceId = new DualId("wsp-20260522T101500Z-4F8D");
        var claudeRunId = new DualId("run-20260522T101600Z-8H2K");
        var codexRunId = new DualId("run-20260522T101700Z-9J3L");
        var workstream = Workstream.created(
                workstreamId,
                "A6 design",
                new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                workspaceId,
                Instant.parse("2026-05-22T10:15:00Z")
        );
        var workspace = Workspace.primaryGitWorktree(
                workspaceId,
                workstreamId,
                tempDir.resolve("repo"),
                tempDir,
                "ai/ws-20260522T101500Z-7K3M",
                "main",
                "abc123"
        );

        var store = new FileLedgerStore();
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        store.initializeWorkstream(paths, workstream, workspace, "# Requirement\n");
        var claudeRun = store.prepareClaudeDesignRun(tempDir, workstreamId.value(), claudeRunId);
        var iterationDir = paths.workstreamDir().resolve("stages/01-design/iterations/001");
        var handoffMd = iterationDir.resolve("claude-handoff.md");
        var handoffJson = iterationDir.resolve("claude-handoff.json");
        Files.writeString(handoffMd, "# Claude Handoff\n");
        Files.writeString(handoffJson, "{\"status\":\"ready_for_external_review\"}");
        Files.writeString(claudeRun.stdoutPath(), """
                <<<DUAL_ARTIFACT_MANIFEST>>>
                {"status":"ready","artifacts":[{"type":"claude_design_handoff_markdown","path":"%s"},{"type":"claude_design_handoff_metadata","path":"%s"}]}
                <<<END_DUAL_ARTIFACT_MANIFEST>>>
                """.formatted(handoffMd, handoffJson));
        store.completeClaudeDesignRun(tempDir, workstreamId.value(), claudeRun.runId());

        var codexRun = store.prepareCodexDesignReviewRun(tempDir, workstreamId.value(), codexRunId);
        var reviewMd = iterationDir.resolve("codex-review.md");
        var reviewJson = iterationDir.resolve("codex-review.json");
        Files.writeString(reviewMd, "# Codex Review\nDecision: PASS\n");
        Files.writeString(reviewJson, reviewJsonContent);
        Files.writeString(codexRun.stdoutPath(), """
                <<<DUAL_ARTIFACT_MANIFEST>>>
                {"status":"ready","artifacts":[{"type":"codex_design_review_markdown","path":"%s"},{"type":"codex_design_review_control","path":"%s"}]}
                <<<END_DUAL_ARTIFACT_MANIFEST>>>
                """.formatted(reviewMd, reviewJson));
        return new PreparedCodexReviewFixture(store, paths, workstreamId, codexRun);
    }

    private String validPassCodexReviewJson() {
        return """
                {
                  "schema_version": 1,
                  "artifact_type": "codex_design_review",
                  "artifact_id": "artifact-codex-review-001",
                  "workstream_id": "ws-20260522T101500Z-7K3M",
                  "stage_id": "stage-design",
                  "iteration_id": "iter-001",
                  "decision": "PASS",
                  "system_decision": "pause_for_human",
                  "markdown_path": "codex-review.md",
                  "must_fix": [],
                  "risks": [],
                  "recommended_next_action": "human_design_review",
                  "requires_human_review": true,
                  "next_prompt_ref": "next-claude-prompt.md"
                }
                """;
    }

    private record CodexPassFixture(FileLedgerStore store, WorkstreamPaths paths, DualId workstreamId) {
    }

    private record PreparedCodexReviewFixture(
            FileLedgerStore store,
            WorkstreamPaths paths,
            DualId workstreamId,
            PreparedRunView codexRun
    ) {
    }
}
