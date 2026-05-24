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

public final class ContextBuildPackageHandler implements TaskHandler {
    private static final int MAX_SNIPPET_CHARS = 4_000;

    @Override
    public String type() {
        return "context.build_package";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var contextDir = workstreamDir.resolve("context");
        Files.createDirectories(contextDir);

        var packagePath = contextDir.resolve(context.instance().nodeInstanceId() + ".md");
        var markdown = renderPackage(context, workstreamDir);
        Files.writeString(packagePath, markdown, StandardCharsets.UTF_8);

        var packageRef = toWorkspaceRef(context.workspaceRoot(), packagePath);
        var result = new LinkedHashMap<String, Object>();
        result.put("context_package", packageRef);
        result.put("target_node", configText(context, "target_node", ""));
        result.put("input_ref_count", context.instance().inputRefs().size());
        result.put("generated_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(packageRef), result);
    }

    private static String renderPackage(TaskContext context, Path workstreamDir) throws IOException {
        var targetNode = configText(context, "target_node", "(not specified)");
        var purpose = configText(context, "purpose", "Prepare focused context for the next agent step.");
        var requirement = readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)");
        var builder = new StringBuilder();
        builder.append("# Context Package\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Target Node: ").append(targetNode).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Purpose: ").append(purpose).append("\n\n");

        builder.append("## User Requirement\n\n");
        builder.append(requirement).append("\n\n");

        builder.append("## Input Artifacts\n\n");
        appendInputArtifacts(builder, context);

        builder.append("## Recent Agent Outputs\n\n");
        appendRecentAgentOutputs(builder, workstreamDir);

        builder.append("## Git Evidence\n\n");
        appendGitEvidence(builder, workstreamDir);

        builder.append("## Ledger Pointers\n\n");
        builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
        builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
        builder.append("- Node state directory: ").append(toDisplayRef(workstreamDir.resolve("nodes"))).append("\n");
        builder.append("- Agent runs directory: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n");
        return builder.toString();
    }

    private static void appendInputArtifacts(StringBuilder builder, TaskContext context) throws IOException {
        if (context.instance().inputRefs().isEmpty()) {
            builder.append("(no direct input refs)\n\n");
            return;
        }
        for (String inputRef : context.instance().inputRefs()) {
            var path = context.workspaceRoot().resolve(inputRef).normalize();
            builder.append("### ").append(inputRef).append("\n\n");
            if (!Files.exists(path) || Files.isDirectory(path)) {
                builder.append("(artifact unavailable or not a file)\n\n");
                continue;
            }
            builder.append("```text\n");
            builder.append(snippet(path));
            builder.append("\n```\n\n");
        }
    }

    private static void appendGitEvidence(StringBuilder builder, Path workstreamDir) throws IOException {
        var evidenceDir = workstreamDir.resolve("artifacts/git-evidence");
        if (!Files.exists(evidenceDir)) {
            builder.append("(git evidence not generated yet)\n\n");
            return;
        }
        for (String fileName : List.of("git-evidence.json", "git-status-short.txt", "diff.stat", "changed-files.txt")) {
            var path = evidenceDir.resolve(fileName);
            builder.append("- ").append(fileName).append(": ").append(toDisplayRef(path)).append("\n");
        }
        var diffPatch = evidenceDir.resolve("diff.patch");
        if (Files.exists(diffPatch)) {
            builder.append("- diff.patch: ").append(toDisplayRef(diffPatch)).append("\n");
        }
        builder.append("\n");
        var evidenceJson = evidenceDir.resolve("git-evidence.json");
        if (Files.exists(evidenceJson)) {
            builder.append("```json\n");
            builder.append(snippet(evidenceJson));
            builder.append("\n```\n\n");
        }
    }

    private static void appendRecentAgentOutputs(StringBuilder builder, Path workstreamDir) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.exists(runsDir)) {
            builder.append("(no agent runs yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.walk(runsDir)) {
            var outputs = paths
                    .filter(Files::isRegularFile)
                    .filter(ContextBuildPackageHandler::isAgentOutput)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(ContextBuildPackageHandler::lastModified).reversed())
                    .limit(8)
                    .toList();
            if (outputs.isEmpty()) {
                builder.append("(no markdown agent outputs yet)\n\n");
                return;
            }
            for (Path output : outputs) {
                builder.append("### ").append(toDisplayRef(output)).append("\n\n");
                builder.append("```text\n");
                builder.append(snippet(output));
                builder.append("\n```\n\n");
            }
        }
    }

    private static boolean isAgentOutput(Path path) {
        var parent = path.getParent();
        return parent != null && "outputs".equals(parent.getFileName().toString());
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String snippet(Path path) throws IOException {
        var content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.length() <= MAX_SNIPPET_CHARS) {
            return content.stripTrailing();
        }
        return content.substring(0, MAX_SNIPPET_CHARS).stripTrailing()
                + "\n\n[truncated; read full artifact at " + toDisplayRef(path) + "]";
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
    }

    private static String configText(TaskContext context, String key, String fallback) {
        var value = context.definition().config().get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String toDisplayRef(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
