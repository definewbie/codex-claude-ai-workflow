package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Extracts adapter-level tool telemetry from an acpx transcript.
 *
 * This reader intentionally produces execution facts only. Workflow routing and
 * product acceptance must continue to use typed artifacts and ledger state, not
 * these transcript heuristics.
 */
final class AcpxTranscriptToolUsageReader {
    private static final Pattern JSON_TOOL = Pattern.compile(
            "\"(?:name|tool|tool_name|toolName)\"\\s*:\\s*\"(?<tool>Read|Edit|Write|MultiEdit|Glob|Grep|Bash|LS|Task)\"",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TEXT_TOOL = Pattern.compile(
            "^\\s*(?:tool[_ -]?use\\s*)?(?<tool>Read|Edit|Write|MultiEdit|Glob|Grep|Bash|LS|Task)\\s*(?:\\(|\\{|:|$)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PATH_VALUE = Pattern.compile(
            "\"(?:file_path|path|notebook_path)\"\\s*:\\s*\"(?<path>[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\""
    );
    private static final Pattern COMMAND_VALUE = Pattern.compile(
            "\"command\"\\s*:\\s*\"(?<command>[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\""
    );
    private static final Pattern COMMAND_ARRAY = Pattern.compile(
            "\"command\"\\s*:\\s*\\[(?<command>[^]]*)]"
    );
    private static final Pattern JSON_STRING = Pattern.compile("\"(?<value>[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
    private static final Pattern BRACKET_TOOL = Pattern.compile(
            "^\\s*\\[tool]\\s*(?<body>.*?)\\s*\\((?<state>pending|running|completed)\\)\\s*$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INPUT_LINE = Pattern.compile("^\\s*input:\\s*(?<body>.*)$", Pattern.CASE_INSENSITIVE);

    AcpxToolUsage read(Path transcriptPath, Path workspaceRoot) throws IOException {
        if (transcriptPath == null || !Files.isRegularFile(transcriptPath)) {
            return AcpxToolUsage.empty();
        }
        var byTool = new LinkedHashMap<String, Integer>();
        var readFiles = new LinkedHashSet<String>();
        var writeFiles = new LinkedHashSet<String>();
        var bashCommands = new LinkedHashSet<String>();
        PendingTool pending = null;
        for (var line : Files.readAllLines(transcriptPath, StandardCharsets.UTF_8)) {
            var bracketTool = bracketToolIn(line, workspaceRoot);
            if (bracketTool.isPresent()) {
                if (pending != null) {
                    record(pending, byTool, readFiles, writeFiles, bashCommands);
                }
                pending = bracketTool.get();
                if (pending.hasEvidence()) {
                    record(pending, byTool, readFiles, writeFiles, bashCommands);
                    pending = null;
                }
                continue;
            }
            if (pending != null && mergeInputLine(pending, line, workspaceRoot)) {
                record(pending, byTool, readFiles, writeFiles, bashCommands);
                pending = null;
                continue;
            }
            if (pending != null && !line.trim().isBlank()) {
                record(pending, byTool, readFiles, writeFiles, bashCommands);
                pending = null;
            }
            consumeLine(line, workspaceRoot, byTool, readFiles, writeFiles, bashCommands);
        }
        if (pending != null) {
            record(pending, byTool, readFiles, writeFiles, bashCommands);
        }
        var readCount = countMatching(byTool, "Read", "Glob", "Grep", "LS");
        var writeCount = countMatching(byTool, "Edit", "Write", "MultiEdit");
        var bashCount = countMatching(byTool, "Bash");
        var total = byTool.values().stream().mapToInt(Integer::intValue).sum();
        var known = readCount + writeCount + bashCount + countMatching(byTool, "Task");
        return new AcpxToolUsage(
                1,
                total,
                readCount,
                writeCount,
                bashCount,
                Math.max(0, total - known),
                Map.copyOf(byTool),
                List.copyOf(readFiles),
                List.copyOf(writeFiles),
                List.copyOf(bashCommands)
        );
    }

    private static void consumeLine(
            String line,
            Path workspaceRoot,
            Map<String, Integer> byTool,
            Set<String> readFiles,
            Set<String> writeFiles,
            Set<String> bashCommands
    ) {
        var tools = toolsIn(line);
        if (tools.isEmpty()) {
            return;
        }
        for (var tool : tools) {
            byTool.merge(tool, 1, Integer::sum);
        }
        var paths = pathsIn(line, workspaceRoot);
        for (var tool : tools) {
            if (isReadTool(tool)) {
                readFiles.addAll(paths);
            } else if (isWriteTool(tool)) {
                writeFiles.addAll(paths);
            } else if ("Bash".equals(tool)) {
                commandIn(line).ifPresent(bashCommands::add);
            }
        }
    }

    private static Optional<PendingTool> bracketToolIn(String line, Path workspaceRoot) {
        var matcher = BRACKET_TOOL.matcher(line);
        if (!matcher.find() || !"completed".equalsIgnoreCase(matcher.group("state"))) {
            return Optional.empty();
        }
        var body = matcher.group("body").trim();
        var tool = toolFromBracketBody(body);
        var pending = new PendingTool(tool);
        pathsIn(body, workspaceRoot).forEach(pending::addPath);
        fallbackPathFromBody(tool, body, workspaceRoot).ifPresent(pending::addPath);
        if ("Bash".equals(tool) && !isGenericBashBody(body)) {
            pending.setCommand(body);
        }
        return Optional.of(pending);
    }

    private static boolean mergeInputLine(PendingTool pending, String line, Path workspaceRoot) {
        var matcher = INPUT_LINE.matcher(line);
        if (!matcher.find()) {
            return false;
        }
        var body = matcher.group("body");
        pathsIn(body, workspaceRoot).forEach(pending::addPath);
        commandIn(body).ifPresent(command -> {
            pending.setCommand(command);
            classifyCommand(command).ifPresent(pending::setTool);
            commandPath(command, workspaceRoot).ifPresent(pending::addPath);
        });
        return true;
    }

    private static void record(
            PendingTool pending,
            Map<String, Integer> byTool,
            Set<String> readFiles,
            Set<String> writeFiles,
            Set<String> bashCommands
    ) {
        var tool = pending.tool();
        byTool.merge(tool, 1, Integer::sum);
        if (isReadTool(tool)) {
            readFiles.addAll(pending.paths());
        } else if (isWriteTool(tool)) {
            writeFiles.addAll(pending.paths());
        } else if ("Bash".equals(tool)) {
            pending.command().ifPresent(bashCommands::add);
        }
    }

    private static List<String> toolsIn(String line) {
        var tools = new LinkedHashSet<String>();
        var json = JSON_TOOL.matcher(line);
        while (json.find()) {
            tools.add(canonicalTool(json.group("tool")));
        }
        var text = TEXT_TOOL.matcher(line);
        if (text.find()) {
            tools.add(canonicalTool(text.group("tool")));
        }
        return List.copyOf(tools);
    }

    private static List<String> pathsIn(String line, Path workspaceRoot) {
        var paths = new LinkedHashSet<String>();
        var matcher = PATH_VALUE.matcher(line);
        while (matcher.find()) {
            var normalized = normalizePath(unescapeJson(matcher.group("path")), workspaceRoot);
            if (!normalized.isBlank()) {
                paths.add(normalized);
            }
        }
        return List.copyOf(paths);
    }

    private static Optional<String> commandIn(String line) {
        var matcher = COMMAND_VALUE.matcher(line);
        if (matcher.find()) {
            var command = unescapeJson(matcher.group("command")).trim();
            return normalizedCommand(command);
        }
        var array = COMMAND_ARRAY.matcher(line);
        if (!array.find()) {
            return Optional.empty();
        }
        return commandFromArray(array.group("command"));
    }

    private static Optional<String> commandFromArray(String rawArray) {
        var values = new ArrayList<String>();
        var strings = JSON_STRING.matcher(rawArray);
        while (strings.find()) {
            values.add(unescapeJson(strings.group("value")));
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() >= 3 && "-lc".equals(values.get(1))) {
            return normalizedCommand(values.get(2));
        }
        return normalizedCommand(String.join(" ", values));
    }

    private static Optional<String> normalizedCommand(String command) {
        if (command.length() > 240) {
            command = command.substring(0, 240) + "...";
        }
        return command.isBlank() ? Optional.empty() : Optional.of(command);
    }

    private static String toolFromBracketBody(String body) {
        var command = body.trim();
        return classifyCommand(command).orElse("Bash");
    }

    private static Optional<String> classifyCommand(String command) {
        var normalized = command.trim();
        var lower = normalized.toLowerCase(Locale.ROOT);
        if (containsShellControlOperator(lower)) {
            return Optional.of("Bash");
        }
        if (lower.equals("read file") || lower.startsWith("read ") || lower.startsWith("rtk read ")) {
            return Optional.of("Read");
        }
        if (lower.startsWith("edit ")) {
            return Optional.of("Edit");
        }
        if (lower.startsWith("write ")) {
            return Optional.of("Write");
        }
        if (lower.startsWith("multiedit ")) {
            return Optional.of("MultiEdit");
        }
        if (lower.equals("find") || lower.startsWith("find ") || lower.startsWith("glob ") || lower.startsWith("rtk find ")) {
            return Optional.of("Glob");
        }
        if (lower.startsWith("grep ") || lower.startsWith("rg ") || lower.startsWith("rtk grep ")) {
            return Optional.of("Grep");
        }
        if (lower.equals("ls") || lower.startsWith("ls ") || lower.startsWith("rtk ls ")) {
            return Optional.of("LS");
        }
        if (lower.startsWith("task ")) {
            return Optional.of("Task");
        }
        return Optional.empty();
    }

    private static boolean containsShellControlOperator(String command) {
        return command.contains("&&") || command.contains("||") || command.contains(";") || command.contains("|");
    }

    private static Optional<String> fallbackPathFromBody(String tool, String body, Path workspaceRoot) {
        if (!isReadTool(tool) && !isWriteTool(tool)) {
            return Optional.empty();
        }
        if ("read file".equalsIgnoreCase(body.trim())) {
            return Optional.empty();
        }
        return commandPath(body, workspaceRoot);
    }

    private static boolean isGenericBashBody(String body) {
        var normalized = body.trim().toLowerCase(Locale.ROOT);
        return "bash".equals(normalized) || "terminal".equals(normalized);
    }

    private static Optional<String> commandPath(String command, Path workspaceRoot) {
        var parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return Optional.empty();
        }
        var index = pathArgumentIndex(parts);
        while (index >= 0 && index < parts.length && parts[index].startsWith("-")) {
            index++;
        }
        if (index < 0 || index >= parts.length) {
            return Optional.empty();
        }
        var path = normalizePath(unquote(parts[index]), workspaceRoot);
        return path.isBlank() ? Optional.empty() : Optional.of(path);
    }

    private static int pathArgumentIndex(String[] parts) {
        if (parts.length >= 3 && "rtk".equalsIgnoreCase(parts[0])) {
            return 2;
        }
        if (parts.length >= 2) {
            return 1;
        }
        return -1;
    }

    private static String unquote(String text) {
        if ((text.startsWith("`") && text.endsWith("`"))) {
            return text.substring(1, text.length() - 1);
        }
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }
        return text;
    }

    private static String canonicalTool(String tool) {
        return switch (tool.toLowerCase(Locale.ROOT)) {
            case "read" -> "Read";
            case "edit" -> "Edit";
            case "write" -> "Write";
            case "multiedit" -> "MultiEdit";
            case "glob" -> "Glob";
            case "grep" -> "Grep";
            case "bash" -> "Bash";
            case "ls" -> "LS";
            case "task" -> "Task";
            default -> tool;
        };
    }

    private static boolean isReadTool(String tool) {
        return "Read".equals(tool) || "Glob".equals(tool) || "Grep".equals(tool) || "LS".equals(tool);
    }

    private static boolean isWriteTool(String tool) {
        return "Edit".equals(tool) || "Write".equals(tool) || "MultiEdit".equals(tool);
    }

    private static int countMatching(Map<String, Integer> byTool, String... tools) {
        var total = 0;
        for (var tool : tools) {
            total += byTool.getOrDefault(tool, 0);
        }
        return total;
    }

    private static String normalizePath(String raw, Path workspaceRoot) {
        var text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            return "";
        }
        try {
            var path = Path.of(text);
            if (path.isAbsolute() && workspaceRoot != null) {
                var root = workspaceRoot.toAbsolutePath().normalize();
                var normalized = path.toAbsolutePath().normalize();
                if (normalized.startsWith(root)) {
                    return root.relativize(normalized).toString();
                }
            }
        } catch (RuntimeException ignored) {
            return stripRelativePrefix(text);
        }
        return stripRelativePrefix(text);
    }

    private static String stripRelativePrefix(String text) {
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static String unescapeJson(String text) {
        return text.replace("\\\"", "\"")
                .replace("\\/", "/")
                .replace("\\\\", "\\");
    }

    private static final class PendingTool {
        private String tool;
        private final List<String> paths = new ArrayList<>();
        private String command;

        private PendingTool(String tool) {
            this.tool = tool;
        }

        private String tool() {
            return tool;
        }

        private void setTool(String tool) {
            this.tool = tool;
        }

        private List<String> paths() {
            return paths;
        }

        private void addPath(String path) {
            if (path != null && !path.isBlank() && !paths.contains(path)) {
                paths.add(path);
            }
        }

        private Optional<String> command() {
            return command == null || command.isBlank() ? Optional.empty() : Optional.of(command);
        }

        private void setCommand(String command) {
            normalizedCommand(command).ifPresent(value -> this.command = value);
        }

        private boolean hasEvidence() {
            return !paths.isEmpty() || command().isPresent();
        }
    }
}
