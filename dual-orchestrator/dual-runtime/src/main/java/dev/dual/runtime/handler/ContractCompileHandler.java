package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern FILE_REF_PATTERN = Pattern.compile("`([^`]+)`");
    private final AgentArtifactEnvelopeParser artifactParser = new AgentArtifactEnvelopeParser();
    private final TaskContractProposalValidator proposalValidator = new TaskContractProposalValidator();

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
        var proposalResult = compileFromProposal(buildTrustedProposalSourceText(context), context);
        if (proposalResult.failed() != null) {
            return proposalResult.failed();
        }
        var contract = proposalResult.contract() == null
                ? compile(sourceText, context.workstreamId())
                : proposalResult.contract();

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

    private ProposalCompileResult compileFromProposal(String sourceText, TaskContext context) {
        var envelope = artifactParser.latest(sourceText, "task_contract_proposal");
        if (envelope.isEmpty()) {
            return new ProposalCompileResult(null, null);
        }
        try {
            var proposal = OBJECT_MAPPER.readValue(envelope.get().json(), TaskContractProposal.class);
            var errors = proposalValidator.validate(proposal, context.workspaceRoot().toAbsolutePath().normalize());
            if (!errors.isEmpty()) {
                return new ProposalCompileResult(null, proposalFailure(
                        "TASK_CONTRACT_PROPOSAL_INVALID",
                        "task_contract_proposal failed mechanical validation",
                        errors
                ));
            }
            return new ProposalCompileResult(toTaskContract(proposal, context.workstreamId()), null);
        } catch (Exception e) {
            return new ProposalCompileResult(null, proposalFailure(
                    "TASK_CONTRACT_PROPOSAL_PARSE_FAILED",
                    "task_contract_proposal JSON could not be parsed: " + e.getMessage(),
                    List.of(e.getMessage())
            ));
        }
    }

    private static TaskContract toTaskContract(TaskContractProposal proposal, String workstreamId) {
        return new TaskContract(
                "tc-" + workstreamId + "-v1",
                1,
                Instant.now(),
                proposal.objective().trim(),
                new TaskContract.Scope(
                        cleanList(proposal.scope().included()),
                        cleanList(proposal.scope().excluded())
                ),
                cleanList(proposal.acceptanceCriteria()),
                cleanList(proposal.testRequirements()),
                cleanList(proposal.constraints()),
                cleanList(proposal.nonGoals()),
                cleanList(proposal.riskAreas()),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source(
                        ".agent/workstreams/" + workstreamId + "/requirement.md",
                        "task_contract_proposal"
                )
        );
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static NodeResult proposalFailure(String reason, String message, List<String> errors) {
        return new NodeResult(
                reason,
                NodeStatus.FAILED,
                List.of(),
                Map.of(
                        "reason", reason,
                        "message", message,
                        "validation_errors", List.copyOf(errors)
                ),
                message
        );
    }

    private record ProposalCompileResult(TaskContract contract, NodeResult failed) {}

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
                        "contract.compile"
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
        for (var line : text.lines().map(ContractCompileHandler::cleanLine).toList()) {
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
                .map(ContractCompileHandler::cleanLine)
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
        for (var line : text.lines().map(ContractCompileHandler::cleanLine).toList()) {
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
        for (var line : text.lines().map(ContractCompileHandler::cleanLine).toList()) {
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

    private String buildTrustedProposalSourceText(TaskContext context) throws IOException {
        var builder = new StringBuilder();
        for (var inputRef : context.instance().inputRefs()) {
            if (!isClaudeIntakeOutputRef(inputRef)) {
                continue;
            }
            appendMarkdownRef(context, builder, "Trusted Proposal Input", inputRef);
        }
        appendLatestClaudeIntakeArtifacts(context, builder);
        return builder.toString();
    }

    private void appendMarkdownRef(
            TaskContext context,
            StringBuilder builder,
            String label,
            String inputRef
    ) throws IOException {
        var path = context.workspaceRoot().resolve(inputRef).normalize();
        if (Files.isRegularFile(path) && path.toString().endsWith(".md")) {
            builder.append("\n\n## ").append(label).append(": ").append(inputRef).append("\n\n");
            builder.append(Files.readString(path, StandardCharsets.UTF_8));
        }
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

    private void appendLatestClaudeIntakeArtifacts(TaskContext context, StringBuilder builder) throws IOException {
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
                    .filter(path -> isClaudeIntakeOutputRef(context.workspaceRoot().relativize(path).toString()))
                    .sorted()
                    .toList();
            for (var artifact : artifacts) {
                builder.append("\n\n## Trusted Claude Intake Artifact: ")
                        .append(context.workspaceRoot().relativize(artifact))
                        .append("\n\n");
                builder.append(Files.readString(artifact, StandardCharsets.UTF_8));
            }
        }
    }

    private static boolean isClaudeIntakeOutputRef(String ref) {
        var normalized = ref.replace('\\', '/');
        return normalized.contains("/outputs/intake-claude-brainstorm.md")
                || normalized.endsWith("outputs/intake-claude-brainstorm.md");
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
