package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

public final class PromptGenerateNextStageHandler implements TaskHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return "prompt.generate_next_stage";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var outputDir = workstreamDir.resolve("artifacts").resolve("next-stage");
        Files.createDirectories(outputDir);

        var sourceRefs = readableInputRefs(context);
        var handoffText = combinedInputText(context, sourceRefs);
        var suggestedPrompt = extractSuggestedPrompt(handoffText);
        var promptPath = outputDir.resolve("next-stage-prompt.md");
        Files.writeString(promptPath, renderPrompt(context, sourceRefs, suggestedPrompt, handoffText), StandardCharsets.UTF_8);

        var handoffJsonPath = outputDir.resolve("handoff.json");
        Files.writeString(handoffJsonPath, renderHandoffJson(context, sourceRefs, handoffText), StandardCharsets.UTF_8);

        var promptRef = toWorkspaceRef(context.workspaceRoot(), promptPath);
        var handoffJsonRef = toWorkspaceRef(context.workspaceRoot(), handoffJsonPath);
        var result = new LinkedHashMap<String, Object>();
        result.put("generated", true);
        result.put("mode", "handoff-derived");
        result.put("prompt_path", promptRef);
        result.put("handoff_json_path", handoffJsonRef);
        result.put("source_refs", sourceRefs);
        result.put("used_suggested_prompt_section", !suggestedPrompt.isBlank());
        result.put("generated_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(promptRef, handoffJsonRef), result);
    }

    private static String renderPrompt(TaskContext context, List<String> sourceRefs, String suggestedPrompt, String handoffText) {
        var builder = new StringBuilder();
        builder.append("# Next Stage Prompt\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Source Handoff Refs: ").append(sourceRefs.isEmpty() ? "(none)" : String.join(", ", sourceRefs)).append("\n\n");

        builder.append("## Prompt\n\n");
        if (!suggestedPrompt.isBlank()) {
            builder.append(suggestedPrompt.stripTrailing()).append("\n\n");
        } else {
            builder.append("Continue the next stage using the handoff below. Preserve the existing scope, review open risks first, and do not invent unrelated requirements.\n\n");
        }

        builder.append("## Handoff Context\n\n");
        if (handoffText.isBlank()) {
            builder.append("(no handoff artifact was available)\n");
        } else {
            builder.append(handoffText.stripTrailing()).append("\n");
        }
        return builder.toString();
    }

    private static List<String> readableInputRefs(TaskContext context) {
        return context.instance().inputRefs().stream()
                .filter(ref -> !ref.startsWith("artifact://"))
                .filter(ref -> Files.isRegularFile(context.workspaceRoot().resolve(ref).normalize()))
                .toList();
    }

    private static String combinedInputText(TaskContext context, List<String> refs) throws IOException {
        var builder = new StringBuilder();
        for (String ref : refs) {
            var path = context.workspaceRoot().resolve(ref).normalize();
            builder.append("### ").append(ref).append("\n\n");
            builder.append(Files.readString(path, StandardCharsets.UTF_8).stripTrailing()).append("\n\n");
        }
        return builder.toString().stripTrailing();
    }

    private static String extractSuggestedPrompt(String handoffText) {
        var lines = handoffText.lines().toList();
        var start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (headingMatches(lines.get(i), "Suggested Next Prompt", "建议的下一步提示", "下一步提示")) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        var builder = new StringBuilder();
        for (int i = start; i < lines.size(); i++) {
            var line = lines.get(i);
            if (isMarkdownHeading(line) || line.trim().equals("```dual-control")) {
                break;
            }
            builder.append(line).append("\n");
        }
        return builder.toString().strip();
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String renderHandoffJson(TaskContext context, List<String> sourceRefs, String handoffText) throws IOException {
        var json = new LinkedHashMap<String, Object>();
        json.put("schemaVersion", 1);
        json.put("workstreamId", context.workstreamId());
        json.put("stageId", context.instance().stageId());
        json.put("iterationId", context.instance().iterationId());
        json.put("sourceHandoffPath", sourceRefs.isEmpty() ? "" : sourceRefs.get(0));
        json.put("completedWork", extractListSection(handoffText,
                "Completed Work", "已完成的工作", "已完成工作", "完成的工作", "工作完成情况"));
        var changedFiles = extractListSection(handoffText,
                "Changed Files", "Changed File", "变更文件", "变更的文件", "修改文件", "改动文件");
        if (changedFiles.isEmpty()) {
            changedFiles = changedFilesFromGitEvidence(context);
        }
        json.put("changedFiles", changedFiles);
        json.put("openRisks", extractListSection(handoffText,
                "Open Risks", "Residual Risks", "开放风险", "残余风险", "剩余风险", "风险点"));
        json.put("verificationState", extractTextSection(handoffText,
                "Verification State", "Verification", "验证状态", "验证结果", "自测状态", "自测结果"));
        json.put("generatedAt", Instant.now().toString());
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
    }

    private static List<String> extractListSection(String text, String... sectionNames) {
        var content = extractSectionContent(text, sectionNames);
        if (content.isBlank()) return List.of();
        var tableItems = extractTableFirstColumn(content);
        if (!tableItems.isEmpty()) {
            return tableItems;
        }

        var items = new ArrayList<String>();
        for (var rawLine : content.lines().toList()) {
            var line = rawLine.strip();
            if (line.isBlank() || line.startsWith("```") || line.startsWith("|")) {
                continue;
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                line = line.substring(2).strip();
            }
            if (!line.isBlank()) {
                items.add(line);
            }
        }
        return items;
    }

    private static List<String> changedFilesFromGitEvidence(TaskContext context) throws IOException {
        var path = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("artifacts")
                .resolve("git-evidence")
                .resolve("changed-files.txt");
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String extractTextSection(String text, String... sectionNames) {
        return extractSectionContent(text, sectionNames).strip();
    }

    private static String extractSectionContent(String text, String... sectionNames) {
        var lines = text.lines().toList();
        var start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (headingMatches(lines.get(i), sectionNames)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) return "";

        var builder = new StringBuilder();
        for (int i = start; i < lines.size(); i++) {
            var line = lines.get(i);
            if (isMarkdownHeading(line) || line.trim().equals("```dual-control")) {
                break;
            }
            builder.append(line).append("\n");
        }
        return builder.toString().stripTrailing();
    }

    private static List<String> extractTableFirstColumn(String content) {
        var items = new ArrayList<String>();
        var insideTableBody = false;
        for (var rawLine : content.lines().toList()) {
            var line = rawLine.strip();
            if (!line.startsWith("|") || !line.endsWith("|")) {
                continue;
            }

            var cells = parseTableCells(line);
            if (cells.isEmpty()) {
                continue;
            }
            if (isTableSeparator(cells)) {
                insideTableBody = true;
                continue;
            }
            if (!insideTableBody) {
                continue;
            }

            var firstCell = cleanMarkdownCell(cells.get(0));
            if (!firstCell.isBlank()) {
                items.add(firstCell);
            }
        }
        return items;
    }

    private static List<String> parseTableCells(String line) {
        var trimmed = line;
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return Pattern.compile("\\|").splitAsStream(trimmed)
                .map(String::strip)
                .toList();
    }

    private static boolean isTableSeparator(List<String> cells) {
        return cells.stream()
                .allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private static String cleanMarkdownCell(String cell) {
        var cleaned = cell.strip();
        if (cleaned.startsWith("`") && cleaned.endsWith("`") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.strip();
    }

    private static boolean headingMatches(String line, String... sectionNames) {
        var title = normalizedHeadingTitle(line);
        if (title.isBlank()) {
            return false;
        }
        for (var sectionName : sectionNames) {
            if (title.equalsIgnoreCase(sectionName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMarkdownHeading(String line) {
        return !normalizedHeadingTitle(line).isBlank();
    }

    private static String normalizedHeadingTitle(String line) {
        var matcher = Pattern.compile("^#{1,6}\\s+(.+?)\\s*$").matcher(line);
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(1)
                .replaceFirst("^\\d+[.)]\\s+", "")
                .strip();
    }
}
