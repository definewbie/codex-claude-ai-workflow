package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

public final class DeliveryPrepareHandler implements TaskHandler {
    private static final int MAX_SECTION_CHARS = 4_000;

    @Override
    public String type() {
        return "delivery.prepare_pr";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var deliveryDir = workstreamDir.resolve("delivery");
        Files.createDirectories(deliveryDir);

        var commitMessagePath = deliveryDir.resolve("commit-message.txt");
        var prBodyPath = deliveryDir.resolve("pr-body.md");
        Files.writeString(commitMessagePath, renderCommitMessage(context.workstreamId(), workstreamDir), StandardCharsets.UTF_8);
        Files.writeString(prBodyPath, renderPrBody(context, workstreamDir), StandardCharsets.UTF_8);

        var commitMessageRef = toWorkspaceRef(context.workspaceRoot(), commitMessagePath);
        var prBodyRef = toWorkspaceRef(context.workspaceRoot(), prBodyPath);
        var result = new LinkedHashMap<String, Object>();
        result.put("commit_message_path", commitMessageRef);
        result.put("pr_body_path", prBodyRef);
        result.put("prepared_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(commitMessageRef, prBodyRef), result);
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
                .append(context.workstreamId())
                .append("/reports/latest-final-report.md\n\n");

        builder.append("## User Request\n\n");
        builder.append(readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)")).append("\n\n");

        builder.append("## Summary\n\n");
        builder.append(snippet(readOrDefault(workstreamDir.resolve("reports/latest-final-report.md"), "(final report not generated)"))).append("\n\n");

        builder.append("## Verification\n\n");
        builder.append(readEvidenceFile(workstreamDir, "diff.stat", "(diff stat not available)")).append("\n\n");

        builder.append("## Codex Final Acceptance\n\n");
        builder.append(snippet(readLatestOutput(workstreamDir, "acceptance-codex-review.md"))).append("\n\n");

        builder.append("## Changed Files\n\n");
        builder.append(readChangedFiles(workstreamDir)).append("\n\n");

        builder.append("## Run Artifacts\n\n");
        builder.append("- Final report: .agent/workstreams/").append(context.workstreamId()).append("/reports/latest-final-report.md\n");
        builder.append("- Git evidence: .agent/workstreams/").append(context.workstreamId()).append("/artifacts/git-evidence/\n");
        builder.append("- Events: .agent/workstreams/").append(context.workstreamId()).append("/events.jsonl\n");
        builder.append("- Runs: .agent/workstreams/").append(context.workstreamId()).append("/runs/\n\n");

        builder.append("## Risks / Notes\n\n");
        builder.append("Review the Codex final acceptance section and final report before merging.\n");
        return builder.toString();
    }

    private static String readChangedFiles(Path workstreamDir) throws IOException {
        var changed = readEvidenceFile(workstreamDir, "changed-files.txt", "");
        if (changed.isBlank()) {
            return "(no changed files recorded)";
        }
        var builder = new StringBuilder();
        for (String line : changed.split("\\R")) {
            if (!line.isBlank()) {
                builder.append("- ").append(line.trim()).append("\n");
            }
        }
        return builder.toString().stripTrailing();
    }

    private static String readEvidenceFile(Path workstreamDir, String fileName, String fallback) throws IOException {
        return readOrDefault(workstreamDir.resolve("artifacts").resolve("git-evidence").resolve(fileName), fallback);
    }

    private static String readLatestOutput(Path workstreamDir, String fileName) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.exists(runsDir)) {
            return "(not available)";
        }
        try (var paths = Files.walk(runsDir)) {
            var latest = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .max((left, right) -> Long.compare(lastModified(left), lastModified(right)));
            if (latest.isEmpty()) {
                return "(not available)";
            }
            return Files.readString(latest.get(), StandardCharsets.UTF_8).stripTrailing();
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
    }

    private static String firstNonBlankLine(String text) {
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("Dual agent task");
    }

    private static String snippet(String content) {
        if (content.length() <= MAX_SECTION_CHARS) {
            return content;
        }
        return content.substring(0, MAX_SECTION_CHARS).stripTrailing() + "\n\n[truncated]";
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }
}
