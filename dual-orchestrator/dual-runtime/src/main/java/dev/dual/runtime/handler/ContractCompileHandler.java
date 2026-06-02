package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ContractCompileHandler implements TaskHandler {

    private static final Pattern FILE_REF_PATTERN = Pattern.compile("`([^`]+)`");
    @Override
    public String type() {
        return "contract.compile";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());

        var requirementPath = workstreamDir.resolve("requirement.md");
        if (!Files.exists(requirementPath)) {
            return new NodeResult(
                    "failed", dev.dual.core.workflow.NodeStatus.FAILED,
                    List.of(),
                    Map.of("error", "requirement.md not found in workstream directory"),
                    "requirement.md not found: " + requirementPath
            );
        }

        var requirementText = Files.readString(requirementPath, StandardCharsets.UTF_8);
        var sourceText = buildSourceText(context, requirementText);
        var contract = compile(sourceText, context.workstreamId());

        // Validate and potentially patch
        var errors = TaskContractValidator.validate(contract);
        if (!errors.isEmpty()) {
            // If AC is empty and READY_FOR_REVIEW, add default AC
            if (contract.status() == ContractStatus.READY_FOR_REVIEW
                    && contract.acceptanceCriteria().isEmpty()) {
                var patchedAc = new ArrayList<>(contract.acceptanceCriteria());
                patchedAc.add("Implementation achieves: " + contract.objective());
                patchedAc.add("No files changed outside scope");
                patchedAc.add("Tests pass");
                contract = new TaskContract(
                        contract.id(), contract.version(), contract.createdAt(),
                        contract.objective(), contract.scope(), patchedAc,
                        contract.testRequirements(), contract.constraints(),
                        contract.nonGoals(), contract.riskAreas(),
                        contract.ambiguityNotes(), contract.humanQuestions(),
                        contract.status(), contract.compilationPass(), contract.source()
                );
                errors = TaskContractValidator.validate(contract);
            }

            // If still failing, set to NEEDS_CLARIFICATION
            if (!errors.isEmpty()) {
                var ambiguityNotes = new ArrayList<>(contract.ambiguityNotes());
                ambiguityNotes.addAll(errors);
                var humanQuestions = new ArrayList<>(contract.humanQuestions());
                humanQuestions.add("Contract compilation produced validation errors — please clarify the requirement");
                contract = new TaskContract(
                        contract.id(), contract.version(), contract.createdAt(),
                        contract.objective(), contract.scope(), contract.acceptanceCriteria(),
                        contract.testRequirements(), contract.constraints(),
                        contract.nonGoals(), contract.riskAreas(),
                        ambiguityNotes, humanQuestions,
                        ContractStatus.NEEDS_CLARIFICATION, contract.compilationPass(), contract.source()
                );
            }
        }

        // Write artifacts
        var artifactsDir = workstreamDir.resolve("artifacts");
        TaskContractArtifactWriter.writeJson(contract, artifactsDir);
        TaskContractArtifactWriter.writeMarkdown(contract, artifactsDir);

        var now = Instant.now();
        var result = new LinkedHashMap<String, Object>();
        result.put("contract_id", contract.id());
        result.put("contract_status", contract.status().name());
        result.put("contract_version", contract.version());
        result.put("compilation_pass", contract.compilationPass());
        result.put("artifacts", List.of(
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.json",
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.md"
        ));
        result.put("generated_at", now.toString());

        return NodeResult.completed("completed", List.of(
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.json",
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.md"
        ), result);
    }

    TaskContract compile(String requirementText, String workstreamId) {
        var objective = extractObjective(requirementText);
        var included = extractIncludedScope(requirementText);
        var constraints = extractConstraints(requirementText);
        var acceptanceCriteria = extractAcceptanceCriteria(requirementText, objective);

        return new TaskContract(
                "tc-" + workstreamId + "-v1",
                1,
                Instant.now(),
                objective,
                new TaskContract.Scope(included, List.of()),
                acceptanceCriteria,
                List.of(),
                constraints,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source(
                        ".agent/workstreams/" + workstreamId + "/requirement.md",
                        "contract.compile"
                )
        );
    }

    private String extractObjective(String text) {
        for (var line : text.lines().toList()) {
            var trimmed = cleanLine(line);
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

        var lines = text.lines().iterator();
        while (lines.hasNext()) {
            var line = cleanLine(lines.next());
            if (line.isBlank() || line.startsWith("#")) {
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
        if (text.contains("README.md")) {
            included.add("README.md");
        }
        if (included.contains("README.md") && text.toLowerCase().contains("readme")) {
            return List.of("README.md");
        }
        return List.copyOf(included);
    }

    private List<String> extractConstraints(String text) {
        return text.lines()
                .map(ContractCompileHandler::cleanLine)
                .filter(line -> line.startsWith("Constraints:")
                        || line.startsWith("Constraint:")
                        || line.startsWith("约束")
                        || line.startsWith("必须遵守")
                        || line.startsWith("不要修改")
                        || line.startsWith("不要改动")
                        || line.startsWith("不要扩大")
                        || line.startsWith("不要做"))
                .map(ContractCompileHandler::cleanLine)
                .toList();
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
            if (capture && (rawTrimmed.startsWith("- ") || rawTrimmed.startsWith("* "))) {
                criteria.add(cleanLine(rawLine));
            }
        }

        if (criteria.isEmpty()) {
            criteria.add("Implementation achieves: " + objective);
            criteria.add("No files changed outside scope");
            criteria.add("Tests pass");
        }
        return List.copyOf(criteria);
    }

    private String buildSourceText(TaskContext context, String requirementText) throws IOException {
        var builder = new StringBuilder(requirementText);
        for (var inputRef : context.instance().inputRefs()) {
            var path = context.workspaceRoot().resolve(inputRef).normalize();
            if (Files.isRegularFile(path) && path.toString().endsWith(".md")) {
                builder.append("\n\n## Input Artifact: ").append(inputRef).append("\n\n");
                builder.append(Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        appendLatestIntakeArtifacts(context, builder);
        return builder.toString();
    }

    private void appendLatestIntakeArtifacts(TaskContext context, StringBuilder builder) throws IOException {
        var runsDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("runs");
        if (!Files.isDirectory(runsDir)) {
            return;
        }
        try (var paths = Files.walk(runsDir)) {
            var artifacts = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("/outputs/intake-"))
                    .filter(path -> path.toString().endsWith(".md"))
                    .sorted()
                    .toList();
            for (var artifact : artifacts) {
                builder.append("\n\n## Intake Artifact: ")
                        .append(context.workspaceRoot().relativize(artifact))
                        .append("\n\n");
                builder.append(Files.readString(artifact, StandardCharsets.UTF_8));
            }
        }
    }

    private static String cleanLine(String line) {
        var cleaned = line.trim();
        while (cleaned.startsWith("- ") || cleaned.startsWith("* ")) {
            cleaned = cleaned.substring(2).trim();
        }
        return cleaned
                .replace("**", "")
                .replace("`", "")
                .trim();
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
