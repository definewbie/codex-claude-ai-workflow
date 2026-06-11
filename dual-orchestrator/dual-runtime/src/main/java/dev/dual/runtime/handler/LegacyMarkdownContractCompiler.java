package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class LegacyMarkdownContractCompiler {
    private static final Pattern FILE_REF_PATTERN = Pattern.compile("`([^`]+)`");

    TaskContract compile(String requirementText, String workstreamId) {
        var objective = extractObjective(requirementText);
        var included = extractIncludedScope(requirementText);
        var excluded = extractExcludedScope(requirementText);
        var constraints = extractConstraints(requirementText);
        var nonGoals = extractSectionList(requirementText, "non-goals", "non goals", "不做", "非目标");
        var riskAreas = extractRiskAreas(requirementText);
        var testRequirements = extractTestRequirements(requirementText);
        var acceptanceCriteria = extractAcceptanceCriteria(requirementText, objective);

        return new TaskContract(
                "tc-" + workstreamId + "-v1",
                1,
                Instant.now(),
                objective,
                new TaskContract.Scope(included, excluded),
                acceptanceCriteria,
                testRequirements,
                constraints,
                nonGoals,
                riskAreas,
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source(
                        ".agent/workstreams/" + workstreamId + "/requirement.md",
                        "legacy_markdown_contract_compiler"
                )
        );
    }

    private String extractObjective(String text) {
        for (var rawLine : text.lines().toList()) {
            if (rawLine.trim().startsWith("#")) {
                continue;
            }
            var trimmed = cleanLine(rawLine);
            if (trimmed.contains("选定任务")) {
                var objective = afterMarker(trimmed, "选定任务");
                if (!objective.isBlank()) {
                    return objective;
                }
            }
            var lower = trimmed.toLowerCase();
            if (lower.contains("proposed task") && lower.contains("readme")) {
                return trimmed;
            }
            if (lower.contains("create") && lower.contains("readme")) {
                return trimmed;
            }
        }

        for (var rawLine : text.lines().toList()) {
            if (rawLine.trim().startsWith("#")) {
                continue;
            }
            var line = cleanLine(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (line.endsWith("：") || line.endsWith(":")) {
                continue;
            }
            return line;
        }
        return "Undefined objective";
    }

    private List<String> extractIncludedScope(String text) {
        var matcher = FILE_REF_PATTERN.matcher(text);
        var included = new java.util.LinkedHashSet<String>();
        while (matcher.find()) {
            var fileRef = normalizeFileRef(matcher.group(1));
            if (!fileRef.isBlank()) {
                included.add(fileRef);
            }
        }
        if (included.size() > 1) {
            included.removeIf(ref -> "README.md".equals(ref));
        }
        return List.copyOf(included);
    }

    private List<String> extractExcludedScope(String text) {
        var excluded = new java.util.LinkedHashSet<String>();
        excluded.addAll(extractSectionList(text, "non-goals", "non goals", "不做", "非目标"));
        for (var line : text.lines().map(LegacyMarkdownContractCompiler::cleanLine).toList()) {
            var lower = line.toLowerCase();
            if (line.startsWith("No ")
                    || lower.startsWith("no ")
                    || line.startsWith("不修改")
                    || line.startsWith("不改")
                    || line.startsWith("不做")
                    || line.startsWith("不要")
                    || line.startsWith("不扩大")) {
                excluded.add(line);
            }
        }
        return List.copyOf(excluded);
    }

    private List<String> extractConstraints(String text) {
        var constraints = new java.util.LinkedHashSet<String>();
        constraints.addAll(extractSectionList(text, "constraints", "constraint", "约束", "必须遵守"));
        text.lines()
                .map(LegacyMarkdownContractCompiler::cleanLine)
                .filter(line -> line.startsWith("Constraints:")
                        || line.startsWith("Constraint:")
                        || line.startsWith("约束")
                        || line.startsWith("必须遵守")
                        || line.startsWith("不要修改")
                        || line.startsWith("不要改动")
                        || line.startsWith("不要扩大")
                        || line.startsWith("不要做")
                        || line.startsWith("不修改")
                        || line.startsWith("不改")
                        || line.startsWith("Do not ")
                        || line.startsWith("do not "))
                .forEach(constraints::add);
        return List.copyOf(constraints);
    }

    private List<String> extractRiskAreas(String text) {
        var risks = new java.util.LinkedHashSet<String>();
        risks.addAll(extractSectionList(text, "risks", "risk", "风险", "risk areas"));
        for (var line : text.lines().map(LegacyMarkdownContractCompiler::cleanLine).toList()) {
            var lower = line.toLowerCase();
            if (lower.contains("risk") || line.contains("风险")) {
                if (!line.equalsIgnoreCase("Risk") && !line.equalsIgnoreCase("Risks") && !line.equals("风险")) {
                    risks.add(line);
                }
            }
        }
        return List.copyOf(risks);
    }

    private List<String> extractTestRequirements(String text) {
        var tests = new java.util.LinkedHashSet<String>();
        tests.addAll(extractSectionList(text, "test strategy", "test requirements", "测试方案", "测试要求", "self-test checklist"));
        for (var line : text.lines().map(LegacyMarkdownContractCompiler::cleanLine).toList()) {
            var lower = line.toLowerCase();
            if (lower.contains("npm run")
                    || lower.contains("mvn ")
                    || lower.contains("grep ")
                    || lower.contains("pytest")
                    || lower.contains("build")
                    || line.contains("验证")
                    || line.contains("测试")) {
                if (!isHeading(line)) {
                    tests.add(line);
                }
            }
        }
        return List.copyOf(tests);
    }

    private List<String> extractAcceptanceCriteria(String text, String objective) {
        var criteria = new java.util.LinkedHashSet<String>();
        var capture = false;
        for (var rawLine : text.lines().toList()) {
            var rawTrimmed = rawLine.trim();
            var line = cleanLine(rawLine);
            var lower = line.toLowerCase();
            if (lower.contains("acceptance should require") || lower.contains("user acceptance notes")
                    || line.contains("验收标准")) {
                capture = true;
                continue;
            }
            if (capture && line.startsWith("## ")) {
                capture = false;
            }
            if (capture && criteria.size() > 0 && line.isBlank()) {
                capture = false;
            }
            if (capture && (rawTrimmed.startsWith("- ") || rawTrimmed.startsWith("* "))) {
                addCleanValue(criteria, line);
            }
        }

        if (criteria.isEmpty()) {
            criteria.add("Implementation achieves: " + objective);
            criteria.add("No files changed outside scope");
            criteria.add("Tests pass");
        }
        return List.copyOf(criteria);
    }

    private static List<String> extractSectionList(String text, String... headings) {
        var values = new java.util.LinkedHashSet<String>();
        var capture = false;
        for (var rawLine : text.lines().toList()) {
            var rawTrimmed = rawLine.trim();
            var line = cleanLine(rawLine);
            if (isHeading(rawTrimmed)) {
                capture = headingMatches(rawTrimmed, headings);
                continue;
            }
            if (!capture) {
                continue;
            }
            if (rawTrimmed.startsWith("- ") || rawTrimmed.startsWith("* ")) {
                addCleanValue(values, line);
                continue;
            }
            if (rawTrimmed.startsWith("|") && rawTrimmed.endsWith("|")) {
                addTableRowValue(values, rawTrimmed);
                continue;
            }
            if (!line.isBlank() && !line.endsWith(":") && !line.endsWith("：")) {
                addCleanValue(values, line);
            }
        }
        return List.copyOf(values);
    }

    private static String cleanLine(String line) {
        var cleaned = line.trim();
        while (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1).trim();
        }
        while (cleaned.startsWith("- ") || cleaned.startsWith("* ")) {
            cleaned = cleaned.substring(2).trim();
        }
        return cleaned
                .replace("**", "")
                .replace("`", "")
                .trim();
    }

    private static boolean isHeading(String line) {
        return line.trim().startsWith("#");
    }

    private static boolean headingMatches(String heading, String... needles) {
        var normalized = cleanLine(heading).toLowerCase();
        for (var needle : needles) {
            if (normalized.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static void addCleanValue(java.util.Set<String> values, String value) {
        var cleaned = cleanLine(value);
        if (cleaned.isBlank()
                || cleaned.equals("|")
                || cleaned.matches("[-| ]+")
                || cleaned.equalsIgnoreCase("n/a")
                || isMetadataNoise(cleaned)) {
            return;
        }
        values.add(cleaned);
    }

    private static boolean isMetadataNoise(String value) {
        var lower = value.toLowerCase();
        return lower.startsWith("workstream:")
                || lower.startsWith("builder node:")
                || lower.startsWith("target node:")
                || lower.startsWith("generated at:")
                || lower.startsWith("profile:")
                || lower.startsWith("total:")
                || lower.startsWith("section | level")
                || lower.startsWith("degraded sections")
                || lower.startsWith("context package")
                || lower.startsWith("context governance");
    }

    private static void addTableRowValue(java.util.Set<String> values, String row) {
        var cells = java.util.Arrays.stream(row.split("\\|"))
                .map(String::trim)
                .filter(cell -> !cell.isBlank())
                .toList();
        if (cells.isEmpty() || cells.stream().allMatch(cell -> cell.matches("-+"))) {
            return;
        }
        var first = cells.get(0);
        if ("risk".equalsIgnoreCase(first)
                || "severity".equalsIgnoreCase(first)
                || "mitigation".equalsIgnoreCase(first)) {
            return;
        }
        values.add(String.join(" / ", cells));
    }

    private String afterMarker(String line, String marker) {
        var index = line.indexOf(marker);
        if (index < 0) {
            return "";
        }
        var value = line.substring(index + marker.length()).trim();
        while (value.startsWith("：") || value.startsWith(":")) {
            value = value.substring(1).trim();
        }
        return value;
    }

    private String normalizeFileRef(String value) {
        var cleaned = value.trim()
                .replaceAll("[。。，，；;：:]+$", "")
                .trim();
        if (!cleaned.matches("(?i).+\\.(md|txt|json|ya?ml|vue|ts|js|java|py|sh)$")) {
            return "";
        }
        return cleaned;
    }
}
