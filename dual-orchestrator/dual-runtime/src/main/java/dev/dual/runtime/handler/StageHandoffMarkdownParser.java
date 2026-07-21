package dev.dual.runtime.handler;

import dev.dual.core.agentoutput.DualControlProtocolParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * LEGACY display-only fallback reader for next-stage handoff fields from Markdown.
 *
 * <p>The main path is the typed {@code stage_handoff} structured artifact —
 * emitted in the {@code agent_output_contract} envelope and read via the
 * contract sidecar by {@code PromptGenerateNextStageHandler.structuredHandoff}.
 * This parser is used ONLY when no typed {@code stage_handoff} sidecar is
 * present (legacy / Markdown-only outputs).</p>
 *
 * <p>It is not a source of truth: routing, completion, plan-body, handoff, and
 * re-read decisions read typed JSON artifacts, not these Markdown sections. New
 * handoff semantics belong in the typed artifact, not here.</p>
 */
final class StageHandoffMarkdownParser {
    private static final Set<String> COMPLETED_WORK_HEADINGS = Set.of(
            "completed work", "已完成的工作", "已完成工作", "完成的工作", "工作完成情况"
    );
    private static final Set<String> CHANGED_FILES_HEADINGS = Set.of(
            "changed files", "changed file", "变更文件", "变更的文件", "修改文件", "改动文件"
    );
    private static final Set<String> IMPORTANT_DECISIONS_HEADINGS = Set.of(
            "important decisions", "key decisions", "重要决策", "关键决策"
    );
    private static final Set<String> OPEN_RISKS_HEADINGS = Set.of(
            "open risks", "residual risks", "开放风险", "残余风险", "剩余风险", "风险点"
    );
    private static final Set<String> VERIFICATION_HEADINGS = Set.of(
            "verification state", "verification", "验证状态", "验证结果", "自测状态", "自测结果"
    );
    private static final Set<String> SUGGESTED_PROMPT_HEADINGS = Set.of(
            "suggested next prompt", "建议的下一步提示", "下一步提示"
    );
    private static final Set<String> RECOMMENDED_NEXT_STAGE_HEADINGS = Set.of(
            "recommended next stage", "next stage", "推荐下一阶段", "建议下一阶段", "下一阶段"
    );

    private final String markdown;
    private final List<String> lines;

    private StageHandoffMarkdownParser(String markdown) {
        this.markdown = beforeTerminalControl(markdown);
        this.lines = this.markdown.lines().toList();
    }

    static StageHandoffMarkdownParser parse(String markdown) {
        return new StageHandoffMarkdownParser(markdown);
    }

    List<String> completedWork() {
        return extractListSection(COMPLETED_WORK_HEADINGS);
    }

    List<String> changedFiles() {
        return extractListSection(CHANGED_FILES_HEADINGS);
    }

    List<String> importantDecisions() {
        return extractListSection(IMPORTANT_DECISIONS_HEADINGS);
    }

    List<String> openRisks() {
        return extractListSection(OPEN_RISKS_HEADINGS);
    }

    String verificationState() {
        return sectionContent(VERIFICATION_HEADINGS).strip();
    }

    String suggestedPrompt() {
        var content = sectionContent(SUGGESTED_PROMPT_HEADINGS);
        return content.strip();
    }

    String recommendedNextStage() {
        return sectionContent(RECOMMENDED_NEXT_STAGE_HEADINGS).strip();
    }

    private List<String> extractListSection(Set<String> headings) {
        var content = sectionContent(headings);
        if (content.isBlank()) {
            return List.of();
        }
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
        return List.copyOf(items);
    }

    private String sectionContent(Set<String> headings) {
        var start = -1;
        var startLevel = 0;
        var inFence = false;
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (isFenceBoundary(line)) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }
            var heading = heading(line);
            if (heading.matches() && headings.contains(heading.normalizedTitle())) {
                start = i + 1;
                startLevel = heading.level();
                break;
            }
        }
        if (start < 0) {
            return "";
        }

        var builder = new StringBuilder();
        inFence = false;
        for (int i = start; i < lines.size(); i++) {
            var line = lines.get(i);
            if (isFenceBoundary(line)) {
                inFence = !inFence;
                builder.append(line).append("\n");
                continue;
            }
            if (!inFence) {
                var heading = heading(line);
                if (heading.matches() && heading.level() <= startLevel) {
                    break;
                }
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
        return List.copyOf(items);
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

    private static Heading heading(String line) {
        var matcher = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$").matcher(line);
        if (!matcher.matches()) {
            return Heading.none();
        }
        var title = matcher.group(2)
                .replaceFirst("^\\d+[.)]\\s+", "")
                .strip()
                .toLowerCase();
        return new Heading(true, matcher.group(1).length(), title);
    }

    private static boolean isFenceBoundary(String line) {
        return line.stripLeading().startsWith("```");
    }

    private static String beforeTerminalControl(String markdown) {
        var parsed = new DualControlProtocolParser().parse(markdown);
        return parsed.controlSpan() == null
                ? parsed.source()
                : parsed.source().substring(0, parsed.controlSpan().start());
    }

    private record Heading(boolean matches, int level, String normalizedTitle) {
        static Heading none() {
            return new Heading(false, 0, "");
        }
    }
}
