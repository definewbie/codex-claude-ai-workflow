package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.adapter.git.GitPatchMergeService;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeResult;
import dev.dual.ledger.file.DeliveryCandidateDocument;
import dev.dual.ledger.file.DeliveryCandidateWriter;
import dev.dual.ledger.file.WorkstreamDocument;
import dev.dual.ledger.file.WorkspaceDocument;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeliveryPrepareHandler implements TaskHandler {
    private static final int MAX_SECTION_CHARS = 4_000;

    private final GitPatchMergeService git;
    private final DeliveryCandidateWriter candidateWriter;
    private final ObjectMapper objectMapper;

    public DeliveryPrepareHandler() {
        this(new GitPatchMergeService(), new DeliveryCandidateWriter(), new ObjectMapper().findAndRegisterModules());
    }

    DeliveryPrepareHandler(
            GitPatchMergeService git,
            DeliveryCandidateWriter candidateWriter,
            ObjectMapper objectMapper
    ) {
        this.git = git;
        this.candidateWriter = candidateWriter;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "delivery.prepare_pr";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var paths = new WorkstreamPaths(context.workspaceRoot(), new DualId(context.workstreamId()));
        var existing = candidateWriter.existing(paths, context.instance().nodeInstanceId());
        if (existing != null) {
            restoreStableProjections(paths, existing.document());
            return result(paths, existing);
        }

        var sourceIdentity = sourceIdentity(paths, context.workspaceRoot());
        var capture = git.capture(context.workspaceRoot());
        var commitMessage = renderCommitMessage(context.workstreamId(), paths.workstreamDir());
        var prBody = renderPrBody(context, paths.workstreamDir());
        var patchBytes = capture.patch().getBytes(StandardCharsets.UTF_8);
        var commitBytes = commitMessage.getBytes(StandardCharsets.UTF_8);
        var prBodyBytes = prBody.getBytes(StandardCharsets.UTF_8);
        var occurrenceDir = paths.deliveryCandidateDir(context.instance().nodeInstanceId());
        var patchRef = workspaceRef(paths.workspaceRoot(), occurrenceDir.resolve("merge.patch"));
        var commitRef = workspaceRef(paths.workspaceRoot(), occurrenceDir.resolve("commit-message.txt"));
        var prBodyRef = workspaceRef(paths.workspaceRoot(), occurrenceDir.resolve("pr-body.md"));
        var candidate = new DeliveryCandidateDocument(
                1,
                context.workstreamId(),
                context.instance().nodeInstanceId(),
                context.instance().iterationId(),
                sourceIdentity.workspaceId(),
                sourceIdentity.effectiveWorkdir(),
                capture.baseCommit(),
                capture.baseTree(),
                capture.acceptedFiles(),
                capture.excludedFiles(),
                new DeliveryCandidateDocument.ArtifactReference(patchRef, DeliveryCandidateWriter.sha256(patchBytes)),
                capture.expectedResultTree(),
                new DeliveryCandidateDocument.ArtifactReference(commitRef, DeliveryCandidateWriter.sha256(commitBytes)),
                new DeliveryCandidateDocument.ArtifactReference(prBodyRef, DeliveryCandidateWriter.sha256(prBodyBytes)),
                context.instance().startedAt() == null ? Instant.now() : context.instance().startedAt()
        );
        var published = candidateWriter.publish(paths, candidate, Map.of(
                "merge.patch", patchBytes,
                "commit-message.txt", commitBytes,
                "pr-body.md", prBodyBytes
        ));
        restoreStableProjections(paths, candidate);
        return result(paths, published);
    }

    private NodeResult result(WorkstreamPaths paths, DeliveryCandidateWriter.Published published) {
        var stableCommitRef = workspaceRef(paths.workspaceRoot(), paths.deliveryDir().resolve("commit-message.txt"));
        var stablePrBodyRef = workspaceRef(paths.workspaceRoot(), paths.deliveryDir().resolve("pr-body.md"));
        var result = new LinkedHashMap<String, Object>();
        result.put("commit_message_path", stableCommitRef);
        result.put("pr_body_path", stablePrBodyRef);
        result.put("delivery_candidate_ref", published.ref());
        result.put("delivery_candidate_sha256", published.sha256());
        result.put("expected_result_tree", published.document().expectedResultTree());
        result.put("prepared_at", published.document().createdAt().toString());
        return NodeResult.completed(
                "completed",
                List.of(stableCommitRef, stablePrBodyRef, published.ref()),
                result
        );
    }

    private SourceIdentity sourceIdentity(WorkstreamPaths paths, Path workspaceRoot) throws IOException {
        if (!Files.isRegularFile(paths.workstreamJson())) {
            throw new IOException("workstream identity is missing: " + paths.workstreamJson());
        }
        var workstream = objectMapper.readValue(paths.workstreamJson().toFile(), WorkstreamDocument.class);
        if (workstream.primaryWorkspaceId() == null || workstream.primaryWorkspaceId().isBlank()) {
            throw new IOException("workstream primary workspace identity is missing");
        }
        var workspacePath = paths.workspaceJson(new DualId(workstream.primaryWorkspaceId()));
        if (!Files.isRegularFile(workspacePath)) {
            throw new IOException("workspace identity is missing: " + workspacePath);
        }
        var workspace = objectMapper.readValue(workspacePath.toFile(), WorkspaceDocument.class);
        var effective = Path.of(workspace.effectiveWorkdir()).toAbsolutePath().normalize();
        if (!workspace.workstreamId().equals(workstream.workstreamId())
                || !workspace.workspaceId().equals(workstream.primaryWorkspaceId())
                || !effective.equals(workspaceRoot.toAbsolutePath().normalize())) {
            throw new IOException("delivery source workspace identity does not match the execution workspace");
        }
        return new SourceIdentity(workspace.workspaceId(), effective.toString());
    }

    private void restoreStableProjections(WorkstreamPaths paths, DeliveryCandidateDocument candidate) throws IOException {
        Files.createDirectories(paths.deliveryDir());
        Files.copy(resolveRef(paths.workspaceRoot(), candidate.commitMessage().ref()),
                paths.deliveryDir().resolve("commit-message.txt"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(resolveRef(paths.workspaceRoot(), candidate.prBody().ref()),
                paths.deliveryDir().resolve("pr-body.md"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String renderCommitMessage(String workstreamId, Path workstreamDir) throws IOException {
        var summary = firstNonBlankLine(readOrDefault(workstreamDir.resolve("requirement.md"), "Dual agent task"));
        return """
                feat: dual agent task %s

                %s

                Run report: .agent/workstreams/%s/reports/latest-final-report.md
                """.formatted(workstreamId, summary, workstreamId);
    }

    private static String renderPrBody(TaskContext context, Path workstreamDir) throws IOException {
        var builder = new StringBuilder();
        builder.append("# Dual Agent Run PR\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Final Report: .agent/workstreams/")
                .append(context.workstreamId()).append("/reports/latest-final-report.md\n\n");
        builder.append("## User Request\n\n")
                .append(readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)"))
                .append("\n\n");
        builder.append("## Summary\n\n")
                .append(snippet(readOrDefault(workstreamDir.resolve("reports/latest-final-report.md"), "(final report not generated)")))
                .append("\n\n");
        builder.append("## Verification\n\n")
                .append(readEvidenceFile(workstreamDir, "diff.stat", "(diff stat not available)"))
                .append("\n\n");
        builder.append("## Codex Final Acceptance\n\n")
                .append("See the typed acceptance evidence summarized in the final report.\n\n");
        builder.append("## Changed Files\n\n").append(readChangedFiles(workstreamDir)).append("\n\n");
        builder.append("## Run Artifacts\n\n")
                .append("- Final report: .agent/workstreams/").append(context.workstreamId()).append("/reports/latest-final-report.md\n")
                .append("- Git evidence: .agent/workstreams/").append(context.workstreamId()).append("/artifacts/git-evidence/\n")
                .append("- Events: .agent/workstreams/").append(context.workstreamId()).append("/events.jsonl\n")
                .append("- Runs: .agent/workstreams/").append(context.workstreamId()).append("/runs/\n\n")
                .append("## Risks / Notes\n\nReview the final acceptance evidence and immutable delivery candidate before merging.\n");
        return builder.toString();
    }

    private static String readChangedFiles(Path workstreamDir) throws IOException {
        var changed = readEvidenceFile(workstreamDir, "changed-files.txt", "");
        if (changed.isBlank()) return "(no changed files recorded)";
        var builder = new StringBuilder();
        for (var line : changed.split("\\R")) {
            if (!line.isBlank()) builder.append("- ").append(line.trim()).append("\n");
        }
        return builder.toString().stripTrailing();
    }

    private static String readEvidenceFile(Path workstreamDir, String fileName, String fallback) throws IOException {
        return readOrDefault(workstreamDir.resolve("artifacts/git-evidence").resolve(fileName), fallback);
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).stripTrailing() : fallback;
    }

    private static String firstNonBlankLine(String text) {
        return text.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse("Dual agent task");
    }

    private static String snippet(String content) {
        return content.length() <= MAX_SECTION_CHARS
                ? content
                : content.substring(0, MAX_SECTION_CHARS).stripTrailing() + "\n\n[truncated]";
    }

    private static String workspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static Path resolveRef(Path workspaceRoot, String ref) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var path = root.resolve(ref).normalize();
        if (!path.startsWith(root)) throw new IOException("delivery artifact ref escapes workspace: " + ref);
        return path;
    }

    private record SourceIdentity(String workspaceId, String effectiveWorkdir) {
    }
}
