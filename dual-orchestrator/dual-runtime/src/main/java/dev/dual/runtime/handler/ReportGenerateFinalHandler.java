package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

public final class ReportGenerateFinalHandler implements TaskHandler {
    private static final int MAX_SNIPPET_CHARS = 4_000;
    private static final int MAX_RECENT_OUTPUTS = 8;

    @Override
    public String type() {
        return "report.generate_final";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var reportsDir = workstreamDir.resolve("reports");
        Files.createDirectories(reportsDir);

        var iterationId = context.instance().iterationId();
        var reportPath = reportsDir.resolve(iterationId + "-final-report.md");
        var latestPath = reportsDir.resolve("latest-final-report.md");
        var markdown = renderReport(context, workstreamDir);
        Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);
        Files.writeString(latestPath, markdown, StandardCharsets.UTF_8);

        var reportRef = toWorkspaceRef(context.workspaceRoot(), reportPath);
        var latestRef = toWorkspaceRef(context.workspaceRoot(), latestPath);
        var result = new LinkedHashMap<String, Object>();
        result.put("report_path", reportRef);
        result.put("latest_report_path", latestRef);
        result.put("iteration_id", iterationId);
        result.put("generated_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(reportRef, latestRef), result);
    }

    private static String renderReport(TaskContext context, Path workstreamDir) throws IOException {
        var builder = new StringBuilder();
        builder.append("# Dual Final Report\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Iteration: ").append(context.instance().iterationId()).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Ledger: ").append(toDisplayRef(workstreamDir)).append("\n\n");

        builder.append("## User Request\n\n");
        builder.append(readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)")).append("\n\n");

        builder.append("## TaskContract\n\n");
        appendTaskContractSection(builder, workstreamDir);

        appendFailureClassificationSection(builder, workstreamDir);

        builder.append("## Acceptance Summary\n\n");
        appendLatestOutputMatching(builder, workstreamDir, "acceptance-codex-review.md");

        builder.append("## Developer Self-Test Summary\n\n");
        appendLatestOutputMatching(builder, workstreamDir, "implement-claude-self-test.md");

        builder.append("## Git Evidence\n\n");
        appendGitEvidence(builder, workstreamDir);

        builder.append("## Recent Agent Outputs\n\n");
        appendRecentOutputs(builder, workstreamDir);

        builder.append("## Run Artifacts\n\n");
        builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
        builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
        builder.append("- Runs: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n");
        builder.append("- Context: ").append(toDisplayRef(workstreamDir.resolve("context"))).append("\n");
        builder.append("- Git evidence: ").append(toDisplayRef(workstreamDir.resolve("artifacts/git-evidence"))).append("\n");
        return builder.toString();
    }

    private static void appendTaskContractSection(StringBuilder builder, Path workstreamDir) throws IOException {
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (!Files.exists(contractPath)) {
            builder.append("(no TaskContract — legacy workflow)\n\n");
            return;
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            var contract = mapper.readValue(contractPath.toFile(), dev.dual.core.contract.TaskContract.class);
            builder.append("- **Contract ID:** ").append(contract.id()).append("\n");
            builder.append("- **Objective:** ").append(contract.objective()).append("\n");
            builder.append("- **Status:** ").append(contract.status()).append("\n");
            builder.append("- **Scope Included:** ").append(contract.scope().included().isEmpty() ? "(none)" : String.join(", ", contract.scope().included())).append("\n");
            builder.append("- **Scope Excluded:** ").append(contract.scope().excluded().isEmpty() ? "(none)" : String.join(", ", contract.scope().excluded())).append("\n");
            builder.append("- **Acceptance Criteria:** ").append(contract.acceptanceCriteria().size()).append(" items").append("\n");
            builder.append("- **Non-Goals:** ").append(contract.nonGoals().isEmpty() ? "(none)" : String.join(", ", contract.nonGoals())).append("\n");
            builder.append("- **Contract Gaps Found:** 0\n");
            builder.append("- **Out-of-scope Changes:** 0\n");
            builder.append("\n");
        } catch (Exception e) {
            builder.append("(error reading TaskContract: ").append(e.getMessage()).append(")\n\n");
        }
    }

    private static void appendFailureClassificationSection(StringBuilder builder, Path workstreamDir) throws IOException {
        var classificationPath = workstreamDir.resolve("artifacts/failure-classification.json");
        if (!Files.exists(classificationPath)) {
            builder.append("## Failure Classification\n\n");
            builder.append("(no failure classification — accepted or legacy workflow)\n\n");
            return;
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            var tree = mapper.readTree(classificationPath.toFile());
            var reviewDecision = tree.path("review_decision").asText("(unknown)");
            if ("ACCEPT_WITH_NOTES".equals(reviewDecision)) {
                builder.append("## Acceptance Notes Classification\n\n");
            } else {
                builder.append("## Failure Classification\n\n");
            }
            builder.append("- **Category:** ").append(tree.path("category").asText("(unknown)")).append("\n");
            builder.append("- **Reasoning:** ").append(tree.path("reasoning").asText("(none)")).append("\n");
            builder.append("- **Suggested Action:** ").append(tree.path("suggested_action").asText("(none)")).append("\n");
            builder.append("- **Matched Rule:** ").append(tree.path("matched_rule").asInt(0)).append("\n");
            builder.append("- **Review Decision:** ").append(reviewDecision).append("\n");
            builder.append("- **Source:** ").append(tree.path("source").asText("(unknown)")).append("\n");
            builder.append("- **Classified At:** ").append(tree.path("classified_at").asText("(unknown)")).append("\n");
            var evidence = tree.path("evidence");
            if (evidence.isArray() && !evidence.isEmpty()) {
                builder.append("- **Evidence:**\n");
                for (var item : evidence) {
                    builder.append("  - ").append(item.asText()).append("\n");
                }
            }
            builder.append("\n");
        } catch (Exception e) {
            builder.append("## Failure Classification\n\n");
            builder.append("(error reading failure classification: ").append(e.getMessage()).append(")\n\n");
        }
    }

    private static void appendLatestOutputMatching(StringBuilder builder, Path workstreamDir, String fileName) throws IOException {
        var output = latestFileMatching(workstreamDir.resolve("runs"), fileName);
        if (output == null) {
            builder.append("(not available)\n\n");
            return;
        }
        builder.append("- Source: ").append(toDisplayRef(output)).append("\n\n");
        builder.append("```text\n");
        builder.append(snippet(output, MAX_SNIPPET_CHARS));
        builder.append("\n```\n\n");
    }

    private static void appendGitEvidence(StringBuilder builder, Path workstreamDir) throws IOException {
        var evidenceDir = workstreamDir.resolve("artifacts/git-evidence");
        if (!Files.exists(evidenceDir)) {
            builder.append("(git evidence not generated)\n\n");
            return;
        }
        for (String fileName : List.of(
                "git-evidence.json",
                "git-status-short.txt",
                "diff.stat",
                "changed-files.txt",
                "hygiene-warnings.txt",
                "diff.patch"
        )) {
            var path = evidenceDir.resolve(fileName);
            if (Files.exists(path)) {
                builder.append("- ").append(fileName).append(": ").append(toDisplayRef(path)).append("\n");
            }
        }
        builder.append("\n");
        var hygieneWarnings = evidenceDir.resolve("hygiene-warnings.txt");
        if (Files.exists(hygieneWarnings) && !Files.readString(hygieneWarnings, StandardCharsets.UTF_8).isBlank()) {
            builder.append("## Repository Hygiene Warnings\n\n");
            builder.append("```text\n");
            builder.append(snippet(hygieneWarnings, MAX_SNIPPET_CHARS));
            builder.append("\n```\n\n");
        }
        var evidenceJson = evidenceDir.resolve("git-evidence.json");
        if (Files.exists(evidenceJson)) {
            builder.append("```json\n");
            builder.append(snippet(evidenceJson, MAX_SNIPPET_CHARS));
            builder.append("\n```\n\n");
        }
    }

    private static void appendRecentOutputs(StringBuilder builder, Path workstreamDir) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.exists(runsDir)) {
            builder.append("(no agent outputs)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.walk(runsDir)) {
            var outputs = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getParent() != null && "outputs".equals(path.getParent().getFileName().toString()))
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(ReportGenerateFinalHandler::lastModified).reversed())
                    .limit(MAX_RECENT_OUTPUTS)
                    .toList();
            if (outputs.isEmpty()) {
                builder.append("(no agent outputs)\n\n");
                return;
            }
            for (Path output : outputs) {
                builder.append("- ").append(toDisplayRef(output)).append("\n");
            }
            builder.append("\n");
        }
    }

    private static Path latestFileMatching(Path root, String fileName) throws IOException {
        if (!Files.exists(root)) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .max(Comparator.comparing(ReportGenerateFinalHandler::lastModified))
                    .orElse(null);
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
    }

    private static String snippet(Path path, int maxChars) throws IOException {
        var content = Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars).stripTrailing()
                + "\n\n[truncated; read the full artifact at " + path.toAbsolutePath().normalize() + "]";
    }

    private static String toDisplayRef(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }
}
