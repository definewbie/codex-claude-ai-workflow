package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.domain.TimelineEvent;
import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FileLedgerStore {
    private final AtomicJsonWriter jsonWriter;
    private final ObjectMapper objectMapper;
    private final ObjectMapper jsonlObjectMapper;

    public FileLedgerStore() {
        this(new AtomicJsonWriter());
    }

    FileLedgerStore(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
        this.objectMapper = jsonWriter.objectMapper();
        this.jsonlObjectMapper = jsonWriter.objectMapper().copy()
                .disable(SerializationFeature.INDENT_OUTPUT);
    }

    public void initializeWorkstream(WorkstreamPaths paths, Workstream workstream, Workspace workspace, String requirement)
            throws IOException {
        Files.createDirectories(paths.workstreamDir().resolve("current"));
        Files.createDirectories(paths.workstreamDir().resolve("stages").resolve("01-design").resolve("iterations").resolve("001"));
        Files.createDirectories(paths.workstreamDir().resolve("artifacts"));
        Files.createDirectories(paths.workstreamDir().resolve("checkpoints"));
        Files.createDirectories(paths.workstreamDir().resolve("reports"));
        Files.createDirectories(paths.runsDir());

        jsonWriter.write(paths.workstreamJson(), WorkstreamDocument.from(workstream));
        var workspaceDocument = WorkspaceDocument.from(workspace);
        var stageDocument = StageDocument.initialDesign(workstream.workstreamId().value(), workspace.workspaceId().value());
        var iterationDocument = IterationDocument.initialDesign();
        var checkpointDocument = CheckpointDocument.initial(workstream.workstreamId().value(), workspace.workspaceId().value());

        jsonWriter.write(paths.workspaceJson(workspace.workspaceId()), workspaceDocument);
        jsonWriter.write(paths.designStageJson(), stageDocument);
        jsonWriter.write(paths.currentStageJson(), stageDocument);
        jsonWriter.write(paths.designIterationJson("001"), iterationDocument);
        jsonWriter.write(paths.currentIterationJson(), iterationDocument);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve("checkpoint-initial.json"), checkpointDocument);
        jsonWriter.write(paths.currentCheckpointJson(), checkpointDocument);
        Files.writeString(paths.requirementMarkdown(), requirement, StandardCharsets.UTF_8);
        appendTimeline(paths, TimelineEvent.of(
                workstream.workstreamId(),
                workspace.workspaceId(),
                "orchestrator",
                "workstream_created",
                "created design-loop workstream"
        ));
    }

    public WorkstreamStatusView loadStatus(Path workspaceRoot, String workstreamId) throws IOException {
        var paths = new WorkstreamPaths(workspaceRoot, new dev.dual.core.id.DualId(workstreamId));
        var workstream = objectMapper.readValue(paths.workstreamJson().toFile(), WorkstreamDocument.class);
        var workspace = objectMapper.readValue(
                paths.workspaceJson(new dev.dual.core.id.DualId(workstream.primaryWorkspaceId())).toFile(),
                WorkspaceDocument.class
        );
        var stage = objectMapper.readValue(paths.currentStageJson().toFile(), StageDocument.class);
        var iteration = objectMapper.readValue(paths.currentIterationJson().toFile(), IterationDocument.class);
        var checkpoint = objectMapper.readValue(paths.currentCheckpointJson().toFile(), CheckpointDocument.class);
        return new WorkstreamStatusView(paths.workstreamDir(), workstream, workspace, stage, iteration, checkpoint);
    }

    public PreparedRunView prepareClaudeDesignRun(Path workspaceRoot, String workstreamId, DualId runId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        if (!"claude_design".equals(status.checkpoint().pendingAction())) {
            throw new IOException("checkpoint pending action is not claude_design: " + status.checkpoint().pendingAction());
        }

        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var runDir = paths.runDir(runId);
        var promptPath = paths.runPrompt(runId, "01-claude-design.md");
        var stdoutPath = paths.runOutputsDir(runId).resolve("01-claude-design.md");
        var stderrPath = paths.runOutputsDir(runId).resolve("01-claude-design.stderr.log");

        Files.createDirectories(paths.runPromptsDir(runId));
        Files.createDirectories(paths.runOutputsDir(runId));
        Files.createDirectories(runDir.resolve("events"));

        Files.writeString(promptPath, claudeDesignPrompt(status), StandardCharsets.UTF_8);
        jsonWriter.write(paths.runJson(runId), RunDocument.claudeDesign(
                runId.value(),
                workstreamId,
                status.workspace().workspaceId(),
                workspaceRoot.relativize(promptPath).toString(),
                workspaceRoot.relativize(stdoutPath).toString(),
                workspaceRoot.relativize(stderrPath).toString()
        ));

        var checkpoint = CheckpointDocument.claudeDesignPrepared(
                workstreamId,
                status.workspace().workspaceId(),
                runId.value(),
                workspaceRoot.relativize(promptPath).toString()
        );
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                runId,
                "orchestrator",
                "claude_design_prompt_prepared",
                "prepared Claude design prompt without invoking acpx"
        ));

        return new PreparedRunView(runId.value(), runDir, promptPath, stdoutPath, stderrPath);
    }

    public PreparedRunView loadPreparedClaudeRun(Path workspaceRoot, String workstreamId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        if (!"claude_design_acpx_run".equals(status.checkpoint().pendingAction())) {
            throw new IOException("checkpoint pending action is not claude_design_acpx_run: " + status.checkpoint().pendingAction());
        }
        var runId = inferPreparedRunIdFromCheckpoint(status.checkpoint());
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var promptPath = workspaceRoot.resolve(status.checkpoint().recommendedContextPackageRef()).normalize();
        var stepName = promptPath.getFileName().toString();
        var stdoutPath = paths.runOutputsDir(runId).resolve(stepName);
        var stderrPath = paths.runOutputsDir(runId).resolve(stepName.replaceFirst("\\.md$", ".stderr.log"));
        return new PreparedRunView(runId.value(), paths.runDir(runId), promptPath, stdoutPath, stderrPath);
    }

    public PreparedRunView prepareClaudeDesignRevisionRun(Path workspaceRoot, String workstreamId, DualId runId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        if (!"claude_design_revision".equals(status.checkpoint().pendingAction())) {
            throw new IOException("checkpoint pending action is not claude_design_revision: " + status.checkpoint().pendingAction());
        }

        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var iteration = IterationDocument.designRevision(2);
        var iterationDir = paths.workstreamDir().resolve("stages/01-design/iterations/002");
        var promptPath = paths.runPrompt(runId, "01-claude-design-revision-002.md");
        var stdoutPath = paths.runOutputsDir(runId).resolve("01-claude-design-revision-002.md");
        var stderrPath = paths.runOutputsDir(runId).resolve("01-claude-design-revision-002.stderr.log");

        Files.createDirectories(iterationDir);
        Files.createDirectories(paths.runPromptsDir(runId));
        Files.createDirectories(paths.runOutputsDir(runId));
        jsonWriter.write(paths.designIterationJson("002"), iteration);
        jsonWriter.write(paths.currentIterationJson(), iteration);
        jsonWriter.write(paths.currentStageJson(), status.stage().withStatusAndCurrentIteration("in_progress", iteration.iterationId()));
        jsonWriter.write(paths.designStageJson(), status.stage().withStatusAndCurrentIteration("in_progress", iteration.iterationId()));
        jsonWriter.write(paths.workstreamJson(), status.workstream().withStatusAndCurrentIteration("running", iteration.iterationId()));

        Files.writeString(promptPath, claudeDesignRevisionPrompt(status, iteration), StandardCharsets.UTF_8);
        jsonWriter.write(paths.runJson(runId), RunDocument.claudeDesign(
                runId.value(),
                workstreamId,
                status.workspace().workspaceId(),
                workspaceRoot.relativize(promptPath).toString(),
                workspaceRoot.relativize(stdoutPath).toString(),
                workspaceRoot.relativize(stderrPath).toString()
        ));

        var checkpoint = CheckpointDocument.claudeDesignRevisionPrepared(
                workstreamId,
                status.workspace().workspaceId(),
                iteration.iterationId(),
                runId.value(),
                workspaceRoot.relativize(promptPath).toString()
        );
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                runId,
                "orchestrator",
                "design_iteration_created",
                "Created design revision iteration " + iteration.iterationId()
        ));
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                runId,
                "orchestrator",
                "claude_design_revision_prompt_prepared",
                "Prepared Claude design revision prompt without invoking acpx"
        ));

        return new PreparedRunView(runId.value(), paths.runDir(runId), promptPath, stdoutPath, stderrPath);
    }

    public PreparedRunView prepareCodexDesignReviewRun(Path workspaceRoot, String workstreamId, DualId runId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        if (!"codex_design_review".equals(status.checkpoint().pendingAction())) {
            throw new IOException("checkpoint pending action is not codex_design_review: " + status.checkpoint().pendingAction());
        }

        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var runDir = paths.runDir(runId);
        var promptPath = paths.runPrompt(runId, "02-codex-design-review.md");
        var stdoutPath = paths.runOutputsDir(runId).resolve("02-codex-design-review.md");
        var stderrPath = paths.runOutputsDir(runId).resolve("02-codex-design-review.stderr.log");

        Files.createDirectories(paths.runPromptsDir(runId));
        Files.createDirectories(paths.runOutputsDir(runId));
        Files.writeString(promptPath, codexDesignReviewPrompt(status), StandardCharsets.UTF_8);
        jsonWriter.write(paths.runJson(runId), RunDocument.codexDesignReview(
                runId.value(),
                workstreamId,
                status.workspace().workspaceId(),
                workspaceRoot.relativize(promptPath).toString(),
                workspaceRoot.relativize(stdoutPath).toString(),
                workspaceRoot.relativize(stderrPath).toString()
        ));

        var checkpoint = CheckpointDocument.codexDesignReviewPrepared(
                workstreamId,
                status.workspace().workspaceId(),
                runId.value(),
                workspaceRoot.relativize(promptPath).toString()
        );
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                runId,
                "orchestrator",
                "codex_design_review_prompt_prepared",
                "prepared Codex design review prompt without invoking acpx"
        ));

        return new PreparedRunView(runId.value(), runDir, promptPath, stdoutPath, stderrPath);
    }

    public PreparedRunView loadPreparedCodexReviewRun(Path workspaceRoot, String workstreamId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        if (!"codex_design_review_acpx_run".equals(status.checkpoint().pendingAction())) {
            throw new IOException("checkpoint pending action is not codex_design_review_acpx_run: " + status.checkpoint().pendingAction());
        }
        var runId = inferLatestPreparedRunId(workspaceRoot);
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var promptPath = paths.runPrompt(runId, "02-codex-design-review.md");
        var stdoutPath = paths.runOutputsDir(runId).resolve("02-codex-design-review.md");
        var stderrPath = paths.runOutputsDir(runId).resolve("02-codex-design-review.stderr.log");
        return new PreparedRunView(runId.value(), paths.runDir(runId), promptPath, stdoutPath, stderrPath);
    }

    public void completeClaudeDesignRun(Path workspaceRoot, String workstreamId, String runId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var promptPath = workspaceRoot.resolve(status.checkpoint().recommendedContextPackageRef()).normalize();
        var stdoutPath = paths.runOutputsDir(new DualId(runId)).resolve(promptPath.getFileName().toString());
        ArtifactManifest.parse(stdoutPath);

        var iterationSuffix = iterationSuffix(status.iteration().iterationId());
        var handoffMd = status.workstreamDir().resolve("stages/01-design/iterations/" + iterationSuffix + "/claude-handoff.md");
        var handoffJson = status.workstreamDir().resolve("stages/01-design/iterations/" + iterationSuffix + "/claude-handoff.json");
        if (!Files.exists(handoffMd) || !Files.exists(handoffJson)) {
            throw new IOException("Claude handoff files are missing: " + handoffMd + ", " + handoffJson);
        }

        var checkpoint = CheckpointDocument.claudeDesignCompleted(workstreamId, status.workspace().workspaceId(), status.iteration().iterationId(), runId);
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                new DualId(runId),
                "orchestrator",
                "claude_design_completed",
                "Claude design handoff manifest and files validated"
        ));
    }

    public void failClaudeDesignRun(Path workspaceRoot, String workstreamId, String runId, int exitCode) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var checkpoint = CheckpointDocument.claudeDesignFailed(workstreamId, status.workspace().workspaceId(), runId, exitCode);
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                new DualId(runId),
                "orchestrator",
                "agent_run_failed",
                "Claude design acpx run failed with exit code " + exitCode
        ));
    }

    public String completeCodexDesignReviewRun(Path workspaceRoot, String workstreamId, String runId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var runDualId = new DualId(runId);
        var stdoutPath = paths.runOutputsDir(runDualId).resolve("02-codex-design-review.md");
        ArtifactManifest.parse(stdoutPath);

        var reviewMd = status.workstreamDir().resolve("stages/01-design/iterations/001/codex-review.md");
        var reviewJson = status.workstreamDir().resolve("stages/01-design/iterations/001/codex-review.json");
        if (!Files.exists(reviewMd) || !Files.exists(reviewJson)) {
            throw new IOException("Codex review files are missing: " + reviewMd + ", " + reviewJson);
        }

        var control = objectMapper.readValue(reviewJson.toFile(), CodexReviewControl.class);
        control.validateFor(workstreamId, status.stage().stageId(), status.iteration().iterationId());
        var decision = control.normalizedDecision();
        var checkpoint = switch (decision) {
            case "PASS" -> CheckpointDocument.codexDesignReviewPassed(workstreamId, status.workspace().workspaceId(), runId);
            case "NEEDS_REVISION" -> CheckpointDocument.codexDesignReviewNeedsRevision(workstreamId, status.workspace().workspaceId(), runId);
            case "BLOCKED" -> CheckpointDocument.codexDesignReviewBlocked(workstreamId, status.workspace().workspaceId(), runId);
            default -> throw new IOException("invalid Codex design decision: " + decision);
        };
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        if ("PASS".equals(decision)) {
            openHumanDesignGate(paths, status);
        }
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                runDualId,
                "orchestrator",
                "codex_decision_applied",
                "Codex design decision applied: " + decision
        ));
        return decision;
    }

    public void approveDesign(Path workspaceRoot, String workstreamId) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        if (!"human_design_review".equals(status.checkpoint().pendingAction())) {
            throw new IOException("checkpoint pending action is not human_design_review: " + status.checkpoint().pendingAction());
        }
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var gate = objectMapper.readValue(paths.currentHumanGateJson().toFile(), HumanGateDocument.class);
        jsonWriter.write(paths.currentHumanGateJson(), gate.decided("approved", "approve"));
        jsonWriter.write(paths.workstreamJson(), status.workstream().withStatus("design_approved"));
        jsonWriter.write(paths.currentStageJson(), status.stage().withStatus("approved"));
        jsonWriter.write(paths.designStageJson(), status.stage().withStatus("approved"));
        var checkpoint = CheckpointDocument.designApproved(workstreamId, status.workspace().workspaceId());
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                "human",
                "human_decision_saved",
                "Human approved design gate"
        ));
    }

    public void requestDesignRevision(Path workspaceRoot, String workstreamId, Path feedbackFile) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        if (!"human_design_review".equals(status.checkpoint().pendingAction())) {
            throw new IOException("checkpoint pending action is not human_design_review: " + status.checkpoint().pendingAction());
        }
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var gate = objectMapper.readValue(paths.currentHumanGateJson().toFile(), HumanGateDocument.class);
        var iterationDir = paths.workstreamDir().resolve("stages/01-design/iterations/001");
        var targetFeedback = iterationDir.resolve("human-feedback.md");
        Files.writeString(targetFeedback, Files.readString(feedbackFile, StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        jsonWriter.write(paths.currentHumanGateJson(), gate.decided("revision_requested", "revise"));
        jsonWriter.write(paths.workstreamJson(), status.workstream().withStatus("running"));
        jsonWriter.write(paths.currentStageJson(), status.stage().withStatus("revision_pending"));
        jsonWriter.write(paths.designStageJson(), status.stage().withStatus("revision_pending"));
        var checkpoint = CheckpointDocument.humanRequestedDesignRevision(workstreamId, status.workspace().workspaceId());
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                "human",
                "human_decision_saved",
                "Human requested design revision"
        ));
    }

    public void failCodexDesignReviewRun(Path workspaceRoot, String workstreamId, String runId, int exitCode) throws IOException {
        var status = loadStatus(workspaceRoot, workstreamId);
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var checkpoint = CheckpointDocument.codexDesignReviewFailed(workstreamId, status.workspace().workspaceId(), runId, exitCode);
        jsonWriter.write(paths.currentCheckpointJson(), checkpoint);
        jsonWriter.write(paths.workstreamDir().resolve("checkpoints").resolve(checkpoint.checkpointId() + ".json"), checkpoint);
        appendTimeline(paths, TimelineEvent.of(
                new DualId(workstreamId),
                new DualId(status.workspace().workspaceId()),
                new DualId(runId),
                "orchestrator",
                "agent_run_failed",
                "Codex design review acpx run failed with exit code " + exitCode
        ));
    }

    private void openHumanDesignGate(WorkstreamPaths paths, WorkstreamStatusView status) throws IOException {
        var iterationDir = paths.workstreamDir().resolve("stages/01-design/iterations/001");
        var reviewPackage = iterationDir.resolve("human-review-package.md");
        var reviewPackageRef = paths.workstreamDir().relativize(reviewPackage).toString();
        var packageContent = """
                # Human Design Review

                ## User Requirement

                See: ../../../../requirement.md

                ## Claude Handoff

                See: claude-handoff.md

                ## Codex Review

                See: codex-review.md

                ## Available Decisions

                - approve: accept the current design and stop at the MVP design gate.
                - revise: request another design revision with human feedback.
                """;
        Files.writeString(reviewPackage, packageContent, StandardCharsets.UTF_8);
        jsonWriter.write(paths.currentHumanGateJson(), HumanGateDocument.openDesignGate(status.workstream().workstreamId(), reviewPackageRef));
        jsonWriter.write(paths.workstreamJson(), status.workstream().withStatus("waiting_for_human"));
        jsonWriter.write(paths.currentStageJson(), status.stage().withStatus("human_review_pending"));
        jsonWriter.write(paths.designStageJson(), status.stage().withStatus("human_review_pending"));
        jsonWriter.write(paths.currentIterationJson(), status.iteration().withStatus("passed"));
        jsonWriter.write(paths.designIterationJson("001"), status.iteration().withStatus("passed"));
        appendTimeline(paths, TimelineEvent.of(
                new DualId(status.workstream().workstreamId()),
                new DualId(status.workspace().workspaceId()),
                "orchestrator",
                "human_gate_opened",
                "Codex PASS opened the default human design review gate"
        ));
    }

    public void appendTimeline(WorkstreamPaths paths, TimelineEvent event) throws IOException {
        Files.createDirectories(paths.timelineJsonl().getParent());
        var line = jsonlObjectMapper.writeValueAsString(TimelineEventDocument.from(event)) + System.lineSeparator();
        Files.writeString(
                paths.timelineJsonl(),
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private String claudeDesignPrompt(WorkstreamStatusView status) throws IOException {
        var requirement = Files.readString(status.workstreamDir().resolve("requirement.md"), StandardCharsets.UTF_8);
        return """
                # Role

                You are Claude Code, the main design agent for a dual lifecycle workstream.

                # Stage

                Current stage: design.
                Current iteration: %s.

                Use the repository's Superpowers workflow when applicable.
                Do not implement code in this stage.
                Produce a design artifact and a handoff artifact for external Codex review.

                # Output Files

                Write the human-readable design handoff to:
                %s

                Write the JSON metadata handoff to:
                %s

                End stdout with a DUAL_ARTIFACT_MANIFEST block that lists both files.

                Manifest format:

                <<<DUAL_ARTIFACT_MANIFEST>>>
                {
                  "status": "ready",
                  "artifacts": [
                    {
                      "type": "claude_design_handoff_markdown",
                      "path": "%s"
                    },
                    {
                      "type": "claude_design_handoff_metadata",
                      "path": "%s"
                    }
                  ]
                }
                <<<END_DUAL_ARTIFACT_MANIFEST>>>

                # User Requirement

                %s
                """.formatted(
                status.iteration().iterationId(),
                status.workstreamDir().resolve("stages/01-design/iterations/001/claude-handoff.md"),
                status.workstreamDir().resolve("stages/01-design/iterations/001/claude-handoff.json"),
                status.workstreamDir().resolve("stages/01-design/iterations/001/claude-handoff.md"),
                status.workstreamDir().resolve("stages/01-design/iterations/001/claude-handoff.json"),
                requirement
        );
    }

    private String claudeDesignRevisionPrompt(WorkstreamStatusView status, IterationDocument iteration) throws IOException {
        var requirement = Files.readString(status.workstreamDir().resolve("requirement.md"), StandardCharsets.UTF_8);
        var previousIterationDir = status.workstreamDir().resolve("stages/01-design/iterations/001");
        var previousHandoff = readOptional(previousIterationDir.resolve("claude-handoff.md"));
        var codexReview = readOptional(previousIterationDir.resolve("codex-review.md"));
        var nextClaudePrompt = readOptional(previousIterationDir.resolve("next-claude-prompt.md"));
        var humanFeedback = readOptional(previousIterationDir.resolve("human-feedback.md"));
        var outputDir = status.workstreamDir().resolve("stages/01-design/iterations/002");
        return """
                # Role

                You are Claude Code, the main design agent for a dual lifecycle workstream.

                # Stage

                Current stage: design revision.
                Current iteration: %s.

                Revise the design using Codex review feedback and human feedback.
                Do not implement code in this stage.
                Produce a revised design handoff for external Codex review.

                # Output Files

                Write the revised human-readable design handoff to:
                %s

                Write the revised JSON metadata handoff to:
                %s

                End stdout with a DUAL_ARTIFACT_MANIFEST block that lists both files.

                Manifest format:

                <<<DUAL_ARTIFACT_MANIFEST>>>
                {
                  "status": "ready",
                  "artifacts": [
                    {
                      "type": "claude_design_handoff_markdown",
                      "path": "%s"
                    },
                    {
                      "type": "claude_design_handoff_metadata",
                      "path": "%s"
                    }
                  ]
                }
                <<<END_DUAL_ARTIFACT_MANIFEST>>>

                # User Requirement

                %s

                # Previous Claude Handoff

                %s

                # Codex Review

                %s

                # Codex Next Claude Prompt

                %s

                # Human Feedback

                %s
                """.formatted(
                iteration.iterationId(),
                outputDir.resolve("claude-handoff.md"),
                outputDir.resolve("claude-handoff.json"),
                outputDir.resolve("claude-handoff.md"),
                outputDir.resolve("claude-handoff.json"),
                requirement,
                previousHandoff,
                codexReview,
                nextClaudePrompt,
                humanFeedback
        );
    }

    private String readOptional(Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
    }

    private String iterationSuffix(String iterationId) {
        if (iterationId != null && iterationId.startsWith("iter-")) {
            return iterationId.substring("iter-".length());
        }
        return "001";
    }

    private String codexDesignReviewPrompt(WorkstreamStatusView status) throws IOException {
        var requirement = Files.readString(status.workstreamDir().resolve("requirement.md"), StandardCharsets.UTF_8);
        var iterationDir = status.workstreamDir().resolve("stages/01-design/iterations/001");
        var handoffMd = iterationDir.resolve("claude-handoff.md");
        var handoffJson = iterationDir.resolve("claude-handoff.json");
        if (!Files.exists(handoffMd) || !Files.exists(handoffJson)) {
            throw new IOException("Claude handoff files are missing: " + handoffMd + ", " + handoffJson);
        }
        var handoff = Files.readString(handoffMd, StandardCharsets.UTF_8);
        return """
                # Role

                You are Codex, the external design reviewer for a dual lifecycle workstream.

                # Stage

                Current stage: design.
                Current iteration: %s.

                Review from product manager, architect, and final-user perspectives.
                Do not modify repository files except the exact review artifact paths listed below.
                Do not do line-by-line code review. Focus on product completeness, architecture consistency, acceptance risk, and whether the design is ready for human approval.

                # Output Files

                Write the human-readable review to:
                %s

                Write the JSON control review to:
                %s

                Write a next Claude prompt to:
                %s

                The JSON decision must be exactly one of:
                PASS, NEEDS_REVISION, BLOCKED.

                End stdout with a DUAL_ARTIFACT_MANIFEST block that lists the review markdown and JSON control files.

                Manifest format:

                <<<DUAL_ARTIFACT_MANIFEST>>>
                {
                  "status": "ready",
                  "artifacts": [
                    {
                      "type": "codex_design_review_markdown",
                      "path": "%s"
                    },
                    {
                      "type": "codex_design_review_control",
                      "path": "%s"
                    }
                  ]
                }
                <<<END_DUAL_ARTIFACT_MANIFEST>>>

                # Required JSON Shape

                {
                  "schema_version": 1,
                  "artifact_type": "codex_design_review",
                  "artifact_id": "artifact-codex-review-001",
                  "workstream_id": "%s",
                  "stage_id": "stage-design",
                  "iteration_id": "iter-001",
                  "decision": "PASS",
                  "system_decision": "pause_for_human",
                  "markdown_path": "%s",
                  "must_fix": [],
                  "risks": [],
                  "recommended_next_action": "human_design_review",
                  "requires_human_review": true,
                  "next_prompt_ref": "%s"
                }

                # User Requirement

                %s

                # Claude Design Handoff

                %s
                """.formatted(
                status.iteration().iterationId(),
                iterationDir.resolve("codex-review.md"),
                iterationDir.resolve("codex-review.json"),
                iterationDir.resolve("next-claude-prompt.md"),
                iterationDir.resolve("codex-review.md"),
                iterationDir.resolve("codex-review.json"),
                status.workstream().workstreamId(),
                iterationDir.resolve("codex-review.md"),
                iterationDir.resolve("next-claude-prompt.md"),
                requirement,
                handoff
        );
    }

    private DualId inferLatestPreparedRunId(Path workspaceRoot) throws IOException {
        var runs = workspaceRoot.resolve(".agent").resolve("runs");
        try (var stream = Files.list(runs)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> new DualId(path.getFileName().toString()))
                    .max(java.util.Comparator.comparing(DualId::value))
                    .orElseThrow(() -> new IOException("no prepared run found in " + runs));
        }
    }

    private DualId inferPreparedRunIdFromCheckpoint(CheckpointDocument checkpoint) {
        var ref = checkpoint.recommendedContextPackageRef();
        var marker = ".agent/runs/";
        var index = ref.indexOf(marker);
        if (index >= 0) {
            var start = index + marker.length();
            var end = ref.indexOf('/', start);
            if (end > start) {
                return new DualId(ref.substring(start, end));
            }
        }
        return new DualId(ref.split("/")[2]);
    }
}
