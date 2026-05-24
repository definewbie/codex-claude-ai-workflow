package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.stream.Stream;

public final class ContextBuildPackageHandler implements TaskHandler {
    private static final int MAX_SNIPPET_CHARS = 4_000;
    private static final int MAX_RECENT_AGENT_OUTPUTS = 8;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final List<String> FULL_SECTIONS = List.of(
            "requirement",
            "input_artifacts",
            "iteration_history",
            "human_feedback",
            "recent_agent_outputs",
            "git_evidence",
            "ledger_pointers"
    );

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
        result.put("profile", profile(context));
        result.put("sections", sections(context));
        result.put("input_ref_count", context.instance().inputRefs().size());
        result.put("generated_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(packageRef), result);
    }

    private static String renderPackage(TaskContext context, Path workstreamDir) throws IOException {
        var targetNode = configText(context, "target_node", "(not specified)");
        var purpose = configText(context, "purpose", "Prepare focused context for the next agent step.");
        var profile = profile(context);
        var sections = sections(context);
        var maxSnippetChars = maxSnippetChars(context);
        var maxRecentAgentOutputs = maxRecentAgentOutputs(context);
        var requirement = readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)");
        var builder = new StringBuilder();
        builder.append("# Context Package\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Target Node: ").append(targetNode).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Purpose: ").append(purpose).append("\n\n");

        builder.append("## Context Contract\n\n");
        builder.append("- Profile: ").append(profile).append("\n");
        builder.append("- Included Sections: ").append(String.join(", ", sections)).append("\n");
        builder.append("- Max Snippet Chars: ").append(maxSnippetChars).append("\n");
        builder.append("- Max Recent Agent Outputs: ").append(maxRecentAgentOutputs).append("\n\n");

        if (sections.contains("requirement")) {
            builder.append("## User Requirement\n\n");
            builder.append(requirement).append("\n\n");
        }

        if (sections.contains("input_artifacts")) {
            builder.append("## Input Artifacts\n\n");
            appendInputArtifacts(builder, context, maxSnippetChars);
        }

        if (sections.contains("iteration_history")) {
            builder.append("## Iteration History\n\n");
            appendIterationHistory(builder, workstreamDir);
        }

        if (sections.contains("human_feedback")) {
            builder.append("## Human Feedback\n\n");
            appendHumanFeedback(builder, workstreamDir);
        }

        if (sections.contains("recent_agent_outputs")) {
            builder.append("## Recent Agent Outputs\n\n");
            appendRecentAgentOutputs(builder, workstreamDir, maxSnippetChars, maxRecentAgentOutputs);
        }

        if (sections.contains("git_evidence")) {
            builder.append("## Git Evidence\n\n");
            appendGitEvidence(builder, workstreamDir, maxSnippetChars);
        }

        if (sections.contains("ledger_pointers")) {
            builder.append("## Ledger Pointers\n\n");
            builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
            builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
            builder.append("- Node state directory: ").append(toDisplayRef(workstreamDir.resolve("nodes"))).append("\n");
            builder.append("- Agent runs directory: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n");
        }
        return builder.toString();
    }

    private static void appendInputArtifacts(StringBuilder builder, TaskContext context, int maxSnippetChars) throws IOException {
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
            builder.append(snippet(path, maxSnippetChars));
            builder.append("\n```\n\n");
        }
    }

    private static void appendGitEvidence(StringBuilder builder, Path workstreamDir, int maxSnippetChars) throws IOException {
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
            builder.append(snippet(evidenceJson, maxSnippetChars));
            builder.append("\n```\n\n");
        }
    }

    private static void appendHumanFeedback(StringBuilder builder, Path workstreamDir) throws IOException {
        var nodesDir = workstreamDir.resolve("nodes");
        if (!Files.exists(nodesDir)) {
            builder.append("(no workflow node state yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(nodesDir)) {
            var feedbackNodes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextBuildPackageHandler::readNodeUnchecked)
                    .filter(ContextBuildPackageHandler::hasHumanFeedback)
                    .sorted(Comparator.comparing(ContextBuildPackageHandler::finishedAtText).reversed())
                    .limit(5)
                    .toList();
            if (feedbackNodes.isEmpty()) {
                builder.append("(no human gate feedback yet)\n\n");
                return;
            }
            for (Map<String, Object> node : feedbackNodes) {
                var result = resultMap(node);
                builder.append("### ").append(text(node.get("nodeDefinitionId"))).append("\n\n");
                builder.append("- Action: ").append(text(result.get("human_action"))).append("\n");
                builder.append("- Finished At: ").append(text(node.get("finishedAt"))).append("\n\n");
                builder.append("```text\n");
                builder.append(text(result.get("note")).stripTrailing());
                builder.append("\n```\n\n");
            }
        }
    }

    private static void appendIterationHistory(StringBuilder builder, Path workstreamDir) throws IOException {
        var iterationsDir = workstreamDir.resolve("iterations");
        if (!Files.exists(iterationsDir)) {
            builder.append("(no workflow iteration history yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(iterationsDir)) {
            var iterations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextBuildPackageHandler::readJsonUnchecked)
                    .filter(iteration -> !text(iteration.get("iterationId")).isBlank())
                    .sorted(Comparator.comparingInt(ContextBuildPackageHandler::iterationNumber).reversed())
                    .limit(5)
                    .toList();
            if (iterations.isEmpty()) {
                builder.append("(no workflow iteration history yet)\n\n");
                return;
            }
            for (Map<String, Object> iteration : iterations) {
                builder.append("### ").append(text(iteration.get("iterationId"))).append("\n\n");
                builder.append("- Parent: ").append(blankAsNone(text(iteration.get("parentIterationId")))).append("\n");
                builder.append("- Trigger: ").append(text(iteration.get("triggerAction"))).append("\n");
                builder.append("- Target Node: ").append(text(iteration.get("targetNodeDefinitionId"))).append("\n");
                builder.append("- Created At: ").append(text(iteration.get("createdAt"))).append("\n");
                var reason = text(iteration.get("reason"));
                if (!reason.isBlank()) {
                    builder.append("\n```text\n");
                    builder.append(reason.stripTrailing());
                    builder.append("\n```\n");
                }
                builder.append("\n");
            }
        }
    }

    private static Map<String, Object> readNodeUnchecked(Path path) {
        return readJsonUnchecked(path);
    }

    private static Map<String, Object> readJsonUnchecked(Path path) {
        try {
            return OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static boolean hasHumanFeedback(Map<String, Object> node) {
        var result = resultMap(node);
        return result.containsKey("human_action") && !text(result.get("note")).isBlank();
    }

    private static String finishedAtText(Map<String, Object> node) {
        return text(node.get("finishedAt"));
    }

    private static Map<String, Object> resultMap(Map<String, Object> node) {
        var result = node.get("result");
        if (result instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, entry.getValue());
                }
            }
            return copy;
        }
        return Map.of();
    }

    private static void appendRecentAgentOutputs(StringBuilder builder, Path workstreamDir, int maxSnippetChars, int maxRecentAgentOutputs) throws IOException {
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
                    .limit(maxRecentAgentOutputs)
                    .toList();
            if (outputs.isEmpty()) {
                builder.append("(no markdown agent outputs yet)\n\n");
                return;
            }
            for (Path output : outputs) {
                builder.append("### ").append(toDisplayRef(output)).append("\n\n");
                builder.append("```text\n");
                builder.append(snippet(output, maxSnippetChars));
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

    private static String snippet(Path path, int maxSnippetChars) throws IOException {
        var content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.length() <= maxSnippetChars) {
            return content.stripTrailing();
        }
        return content.substring(0, maxSnippetChars).stripTrailing()
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

    private static String profile(TaskContext context) {
        return configText(context, "profile", "full");
    }

    private static List<String> sections(TaskContext context) {
        var override = context.definition().config().get("include_sections");
        if (override instanceof List<?> values && !values.isEmpty()) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(section -> FULL_SECTIONS.contains(section))
                    .distinct()
                    .toList();
        }
        return switch (profile(context)) {
            case "product_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "architecture_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "plan_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "implementation" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "acceptance" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "git_evidence",
                    "ledger_pointers"
            );
            default -> FULL_SECTIONS;
        };
    }

    private static int maxSnippetChars(TaskContext context) {
        return positiveIntConfig(context, "max_snippet_chars", MAX_SNIPPET_CHARS);
    }

    private static int maxRecentAgentOutputs(TaskContext context) {
        return positiveIntConfig(context, "max_recent_agent_outputs", MAX_RECENT_AGENT_OUTPUTS);
    }

    private static int positiveIntConfig(TaskContext context, String key, int fallback) {
        var value = context.definition().config().get(key);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (value instanceof String text && text.matches("\\d+")) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String toDisplayRef(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int iterationNumber(Map<String, Object> iteration) {
        var number = iteration.get("number");
        if (number instanceof Number numeric) {
            return numeric.intValue();
        }
        var iterationId = text(iteration.get("iterationId"));
        if (iterationId.matches("iter-\\d{3}")) {
            return Integer.parseInt(iterationId.substring("iter-".length()));
        }
        return 0;
    }

    private static String blankAsNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}
