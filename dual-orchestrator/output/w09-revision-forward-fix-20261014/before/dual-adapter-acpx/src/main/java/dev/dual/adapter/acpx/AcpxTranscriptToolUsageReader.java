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
        var toolIds = new LinkedHashSet<String>();
        var readFiles = new LinkedHashSet<String>();
        var directReadFiles = new LinkedHashSet<String>();
        var writeFiles = new LinkedHashSet<String>();
        var bashCommands = new LinkedHashSet<String>();
        var commandExecutables = new LinkedHashSet<String>();
        PendingTool pending = null;
        for (var line : Files.readAllLines(transcriptPath, StandardCharsets.UTF_8)) {
            var bracketTool = bracketToolIn(line, workspaceRoot);
            if (bracketTool.isPresent()) {
                if (pending != null) {
                    record(pending, byTool, toolIds, readFiles, directReadFiles, writeFiles, bashCommands, commandExecutables);
                }
                pending = bracketTool.get();
                if (pending.hasEvidence()) {
                    record(pending, byTool, toolIds, readFiles, directReadFiles, writeFiles, bashCommands, commandExecutables);
                    pending = null;
                }
                continue;
            }
            if (pending != null && mergeInputLine(pending, line, workspaceRoot)) {
                record(pending, byTool, toolIds, readFiles, directReadFiles, writeFiles, bashCommands, commandExecutables);
                pending = null;
                continue;
            }
            if (pending != null && !line.trim().isBlank()) {
                record(pending, byTool, toolIds, readFiles, directReadFiles, writeFiles, bashCommands, commandExecutables);
                pending = null;
            }
            consumeLine(line, workspaceRoot, byTool, toolIds, readFiles, directReadFiles, writeFiles, bashCommands, commandExecutables);
        }
        if (pending != null) {
            record(pending, byTool, toolIds, readFiles, directReadFiles, writeFiles, bashCommands, commandExecutables);
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
                List.copyOf(toolIds),
                List.copyOf(readFiles),
                List.copyOf(directReadFiles),
                List.copyOf(writeFiles),
                List.copyOf(bashCommands),
                List.copyOf(commandExecutables)
        );
    }

    private static void consumeLine(
            String line,
            Path workspaceRoot,
            Map<String, Integer> byTool,
            Set<String> toolIds,
            Set<String> readFiles,
            Set<String> directReadFiles,
            Set<String> writeFiles,
            Set<String> bashCommands,
            Set<String> commandExecutables
    ) {
        var tools = toolsIn(line);
        if (tools.isEmpty()) {
            return;
        }
        var paths = new LinkedHashSet<>(pathsIn(line, workspaceRoot));
        for (var tool : tools) {
            var behaviorTool = tool;
            toolIds.add(tool);
            if ("Bash".equals(tool)) {
                var command = commandIn(line);
                if (command.isPresent()) {
                    recordCommand(command.get(), bashCommands, commandExecutables);
                    behaviorTool = classifyCommand(command.get()).orElse("Bash");
                    commandPath(behaviorTool, command.get(), workspaceRoot).ifPresent(paths::add);
                }
            }
            byTool.merge(behaviorTool, 1, Integer::sum);
            if (isReadTool(behaviorTool)) {
                readFiles.addAll(paths);
                if (isDirectReadTool(behaviorTool)) {
                    directReadFiles.addAll(paths);
                }
            } else if (isWriteTool(behaviorTool)) {
                writeFiles.addAll(paths);
            }
        }
    }

    private static Optional<PendingTool> bracketToolIn(String line, Path workspaceRoot) {
        var matcher = BRACKET_TOOL.matcher(line);
        if (!matcher.find() || !"completed".equalsIgnoreCase(matcher.group("state"))) {
            return Optional.empty();
        }
        var body = matcher.group("body").trim();
        var toolId = toolIdFromBracketBody(body);
        var tool = toolFromBracketBody(body);
        var pending = new PendingTool(toolId, tool);
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
            commandPath(pending.tool(), command, workspaceRoot).ifPresent(pending::addPath);
        });
        return true;
    }

    private static void record(
            PendingTool pending,
            Map<String, Integer> byTool,
            Set<String> toolIds,
            Set<String> readFiles,
            Set<String> directReadFiles,
            Set<String> writeFiles,
            Set<String> bashCommands,
            Set<String> commandExecutables
    ) {
        var tool = pending.tool();
        byTool.merge(tool, 1, Integer::sum);
        toolIds.add(pending.toolId());
        if (isReadTool(tool)) {
            readFiles.addAll(pending.paths());
            if (isDirectReadTool(tool)) {
                directReadFiles.addAll(pending.paths());
            }
        } else if (isWriteTool(tool)) {
            writeFiles.addAll(pending.paths());
        } else if ("Bash".equals(tool)) {
            pending.command().ifPresent(command -> recordCommand(command, bashCommands, commandExecutables));
        }
        pending.command().ifPresent(command -> executableIn(command).ifPresent(commandExecutables::add));
    }

    private static void recordCommand(String command, Set<String> bashCommands, Set<String> commandExecutables) {
        bashCommands.add(command);
        executableIn(command).ifPresent(commandExecutables::add);
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

    private static Optional<String> executableIn(String command) {
        var tokens = shellTokens(command);
        for (var token : tokens) {
            var text = unquote(token);
            if (text.isBlank() || text.contains("=") && text.indexOf('=') > 0 && !text.startsWith("./")) {
                continue;
            }
            if ("env".equals(text)) {
                continue;
            }
            return Optional.of(normalizeExecutable(text));
        }
        return Optional.empty();
    }

    private static List<String> shellTokens(String command) {
        var tokens = new ArrayList<String>();
        var current = new StringBuilder();
        char quote = 0;
        for (var i = 0; i < command.length(); i++) {
            var ch = command.charAt(i);
            if (quote != 0) {
                if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                addShellToken(tokens, current);
                continue;
            }
            if (ch == '&' || ch == '|' || ch == ';') {
                addShellToken(tokens, current);
                break;
            }
            current.append(ch);
        }
        addShellToken(tokens, current);
        return List.copyOf(tokens);
    }

    private static void addShellToken(List<String> tokens, StringBuilder current) {
        if (!current.isEmpty()) {
            tokens.add(current.toString());
            current.setLength(0);
        }
    }

    private static String normalizeExecutable(String value) {
        var text = value.toLowerCase(Locale.ROOT);
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        var slash = text.lastIndexOf('/');
        return slash >= 0 ? text.substring(slash + 1) : text;
    }

    private static String toolFromBracketBody(String body) {
        var command = body.trim();
        return classifyCommand(command).orElse("Bash");
    }

    private static String toolIdFromBracketBody(String body) {
        var command = body.trim();
        return nativeToolId(command).orElse("Bash");
    }

    private static Optional<String> nativeToolId(String command) {
        var lower = command.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("read file")) {
            return Optional.of("Read");
        }
        if (lower.equals("edit")) {
            return Optional.of("Edit");
        }
        if (lower.equals("write")) {
            return Optional.of("Write");
        }
        if (lower.equals("multiedit")) {
            return Optional.of("MultiEdit");
        }
        if (lower.equals("find") || lower.equals("glob")) {
            return Optional.of("Glob");
        }
        if (lower.equals("grep")) {
            return Optional.of("Grep");
        }
        if (lower.equals("ls")) {
            return Optional.of("LS");
        }
        if (lower.equals("task")) {
            return Optional.of("Task");
        }
        return Optional.empty();
    }

    private static Optional<String> classifyCommand(String command) {
        var normalized = command.trim();
        var lower = normalized.toLowerCase(Locale.ROOT);
        if (containsShellControlOperator(lower)) {
            return Optional.of("Bash");
        }
        var tokenTool = classifyCommandTokens(shellTokens(normalized));
        if (tokenTool.isPresent()) {
            return tokenTool;
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

    private static Optional<String> classifyCommandTokens(List<String> tokens) {
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        var executable = normalizeExecutable(unquote(tokens.get(0)));
        var commandIndex = 0;
        if ("rtk".equals(executable)) {
            if (tokens.size() < 2) {
                return Optional.empty();
            }
            executable = normalizeExecutable(unquote(tokens.get(1)));
            commandIndex = 1;
        }
        return switch (executable) {
            case "read" -> tokens.size() > commandIndex + 1 ? Optional.of("Read") : Optional.empty();
            case "edit" -> tokens.size() > commandIndex + 1 ? Optional.of("Edit") : Optional.empty();
            case "write" -> tokens.size() > commandIndex + 1 ? Optional.of("Write") : Optional.empty();
            case "multiedit" -> tokens.size() > commandIndex + 1 ? Optional.of("MultiEdit") : Optional.empty();
            case "find", "glob" -> Optional.of("Glob");
            case "grep", "rg" -> Optional.of("Grep");
            case "ls" -> Optional.of("LS");
            case "task" -> tokens.size() > commandIndex + 1 ? Optional.of("Task") : Optional.empty();
            default -> Optional.empty();
        };
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
        return commandPath(tool, body, workspaceRoot);
    }

    private static boolean isGenericBashBody(String body) {
        var normalized = body.trim().toLowerCase(Locale.ROOT);
        return "bash".equals(normalized) || "terminal".equals(normalized);
    }

    private static Optional<String> commandPath(String behaviorTool, String command, Path workspaceRoot) {
        var parts = command.trim().split("\\s+");
        if (parts.length == 0) {
            return Optional.empty();
        }
        var index = pathArgumentIndex(behaviorTool, parts);
        while (index >= 0 && index < parts.length && parts[index].startsWith("-")) {
            index++;
        }
        if (index < 0 || index >= parts.length) {
            return Optional.empty();
        }
        var path = normalizePath(unquote(parts[index]), workspaceRoot);
        return path.isBlank() ? Optional.empty() : Optional.of(path);
    }

    private static int pathArgumentIndex(String behaviorTool, String[] parts) {
        if ("Grep".equals(behaviorTool)) {
            return grepPathArgumentIndex(parts);
        }
        if (parts.length >= 3 && isCommand(parts[0], "rtk")) {
            return 2;
        }
        if (parts.length >= 2) {
            return 1;
        }
        return -1;
    }

    private static int grepPathArgumentIndex(String[] parts) {
        if (parts.length < 3) {
            return -1;
        }
        var commandIndex = isCommand(parts[0], "rtk") ? 1 : 0;
        if (commandIndex >= parts.length || !isGrepExecutable(parts[commandIndex])) {
            return -1;
        }
        for (var index = parts.length - 1; index > commandIndex; index--) {
            var value = unquote(parts[index]);
            if (!value.isBlank() && !value.startsWith("-")) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isGrepExecutable(String value) {
        return isCommand(value, "grep") || isCommand(value, "rg");
    }

    private static boolean isCommand(String value, String expected) {
        return normalizeExecutable(value).equals(expected);
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

    private static boolean isDirectReadTool(String tool) {
        return "Read".equals(tool);
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
        private final String toolId;
        private String tool;
        private final List<String> paths = new ArrayList<>();
        private String command;

        private PendingTool(String toolId, String tool) {
            this.toolId = toolId;
            this.tool = tool;
        }

        private String toolId() {
            return toolId;
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
