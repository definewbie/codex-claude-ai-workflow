package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.domain.PromptLanguage;
import dev.dual.runtime.handler.ContractRegistry;
import dev.dual.runtime.handler.AgentResultPromptContract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class AgentPromptRenderer {
    private static final String TEMPLATE_ROOT = "dev/dual/adapter/acpx/prompts/";
    private static final String PRINCIPLE_TEMPLATE_ROOT = "dev/dual/adapter/acpx/templates/";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> KNOWN_VARIABLES = Set.of(
            "workstream_id", "run_id", "node_id", "prompt_template",
            "ledger_path", "requirement", "context_package",
            "contract_review_principles", "allowed_decisions",
            "required_control_keys", "decision_example", "agent_result_contract"
    );
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-z_]+)}}");
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile(
            "(?<![<\\p{Alnum}._-])(/[^\\s\"'`)]+)",
            Pattern.UNICODE_CHARACTER_CLASS);

    String render(Path workspaceRoot, String workstreamId, String nodeId, String promptTemplate, List<String> inputRefs) {
        return renderWithSections(workspaceRoot, workstreamId, "", nodeId, promptTemplate, inputRefs).content();
    }

    /**
     * Same final prompt as {@link #render}, plus section provenance for prompt manifests.
     * Provenance records source metadata only and must not change prompt content.
     */
    RenderedPrompt renderWithSections(Path workspaceRoot, String workstreamId, String nodeId, String promptTemplate, List<String> inputRefs) {
        return renderWithSections(workspaceRoot, workstreamId, "", nodeId, promptTemplate, inputRefs);
    }

    RenderedPrompt renderWithSections(
            Path workspaceRoot,
            String workstreamId,
            String runId,
            String nodeId,
            String promptTemplate,
            List<String> inputRefs
    ) {
        var template = loadTemplate(promptTemplate);
        var workstreamDir = ".agent/workstreams/" + workstreamId;
        var requirementPath = workspaceRoot.resolve(workstreamDir).resolve("requirement.md");
        var requirement = readIfExists(requirementPath);
        var languagePolicy = languagePolicy(workspaceRoot.resolve(workstreamDir), requirement);
        var contextPackage = contextPackageSection(workspaceRoot, inputRefs);
        var contractReviewPrinciples = principleTemplate(workspaceRoot, "contract-review-principles.md");
        var promptContract = promptContract(nodeId);
        var workspaceGuard = workspaceGuard(workspaceRoot, nodeId);
        var structuredArtifactFieldSemantics = structuredArtifactFieldSemantics(nodeId);
        var readReuseContractSection = readReuseContractSection(workspaceRoot, inputRefs);
        var explorationPlanSection = explorationPlanSection(workspaceRoot, inputRefs);
        var hasInputs = inputRefs != null && !inputRefs.isEmpty();

        var content = workspaceGuard + "\n\n" + languagePolicy + "\n\n"
                + structuredArtifactFieldSemantics + "\n\n" + template
                .replace("{{workstream_id}}", workstreamId)
                .replace("{{run_id}}", runId)
                .replace("{{node_id}}", nodeId)
                .replace("{{prompt_template}}", promptTemplate)
                .replace("{{ledger_path}}", workstreamDir)
                .replace("{{requirement}}", requirement)
                .replace("{{context_package}}", contextPackage)
                .replace("{{contract_review_principles}}", contractReviewPrinciples)
                .replace("{{allowed_decisions}}", promptContract.allowedDecisions())
                .replace("{{required_control_keys}}", promptContract.requiredControlKeys())
                .replace("{{decision_example}}", promptContract.decisionExample());
        content = content.replace(
                "{{agent_result_contract}}",
                AgentResultPromptContract.render(
                        workstreamId,
                        runId,
                        nodeId,
                        promptContract.decisionExample(),
                        promptContract.allowedDecisionValues())
        );

        var sections = new ArrayList<PromptSection>();
        sections.add(new PromptSection("workspace_guard", "static_instruction", "workspace-guard",
                workspaceGuard, "execution safety boundary", true));
        sections.add(new PromptSection("language_policy", "static_instruction", "language-policy",
                languagePolicy, "output language policy", true));
        sections.add(new PromptSection("structured_artifact_field_semantics", "tool_policy",
                "structured-artifact-field-semantics",
                structuredArtifactFieldSemantics, "structured artifact field semantics",
                !structuredArtifactFieldSemantics.isBlank()));
        sections.add(new PromptSection("template_static_frame", "static_instruction",
                TEMPLATE_ROOT + promptTemplate + ".md",
                VARIABLE_PATTERN.matcher(template).replaceAll(""), "prompt skeleton instructions", true));
        sections.add(new PromptSection("requirement", "requirement", workstreamDir + "/requirement.md",
                requirement, "task requirement", true));
        sections.add(new PromptSection("context_package", "context_package",
                hasInputs ? String.join(",", inputRefs) : "(none)",
                contextPackage, "prior artifacts / assembled context", hasInputs));
        sections.add(new PromptSection("read_reuse_contract", "read_reuse_contract",
                hasInputs ? String.join(",", inputRefs) : "(none)",
                readReuseContractSection, "typed memory read-reuse projection",
                !readReuseContractSection.isBlank()));
        sections.add(new PromptSection("exploration_plan", "exploration_plan",
                hasInputs ? String.join(",", inputRefs) : "(none)",
                explorationPlanSection, "typed exploration plan projection",
                !explorationPlanSection.isBlank()));
        sections.add(new PromptSection("contract_review_principles", "static_instruction",
                "contract-review-principles.md", contractReviewPrinciples, "review principles", true));
        sections.add(new PromptSection("prompt_contract", "tool_policy", "ContractRegistry:" + nodeId,
                AgentResultPromptContract.render(
                        workstreamId,
                        runId,
                        nodeId,
                        promptContract.decisionExample(),
                        promptContract.allowedDecisionValues()),
                "strict agent result envelope", true));
        return new RenderedPrompt(content, sections);
    }

    private PromptContract promptContract(String nodeId) {
        if (!ContractRegistry.hasContract(nodeId)) {
            return new PromptContract(
                    "- (no runtime contract registered)",
                    "- decision\n- summary",
                    "<one allowed decision>",
                    Set.of());
        }
        var contract = ContractRegistry.forNode(nodeId);
        return new PromptContract(
                bulletList(contract.allowedDecisions()),
                bulletList(contract.requiredKeys()),
                contract.allowedDecisions().size() == 1
                        ? contract.allowedDecisions().iterator().next()
                        : "<one allowed decision>",
                contract.allowedDecisions()
        );
    }

    private String bulletList(Set<String> values) {
        return values.stream()
                .sorted()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- (none)");
    }

    private String loadTemplate(String promptTemplate) {
        var resource = TEMPLATE_ROOT + promptTemplate + ".md";
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("missing prompt template resource: " + resource);
            }
            var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            validateTemplateVariables(promptTemplate, content);
            return content;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load prompt template: " + resource, e);
        }
    }

    private void validateTemplateVariables(String templateName, String templateContent) {
        var matcher = VARIABLE_PATTERN.matcher(templateContent);
        var unknown = new LinkedHashSet<String>();
        while (matcher.find()) {
            var variable = matcher.group(1);
            if (!KNOWN_VARIABLES.contains(variable)) {
                unknown.add(variable);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "prompt template '" + templateName + "' references unknown variables: " + unknown
                            + ". Known: " + KNOWN_VARIABLES);
        }
    }

    private String readIfExists(Path path) {
        if (!Files.exists(path)) {
            return "(requirement.md not found)";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read requirement: " + path, e);
        }
    }

    private String languagePolicy(Path workstreamDir, String requirement) {
        var selected = resolvePromptLanguage(workstreamDir, requirement);
        return switch (selected) {
            case "zh-CN" -> """
                    ## Response Language Policy

                    - Use Simplified Chinese for human-readable string values inside the final JSON result.
                    - Keep JSON keys, enum values, file paths, and code identifiers exactly as specified.
                    - Do not wrap the final JSON in Markdown.
                    """.stripTrailing();
            case "en" -> """
                    ## Response Language Policy

                    - Use English for human-readable string values inside the final JSON result.
                    - Keep JSON keys, enum values, file paths, and code identifiers exactly as specified.
                    - Do not wrap the final JSON in Markdown.
                    """.stripTrailing();
            default -> throw new IllegalStateException("unexpected prompt language: " + selected);
        };
    }

    private String resolvePromptLanguage(Path workstreamDir, String requirement) {
        var configured = readConfiguredPromptLanguage(workstreamDir.resolve("settings.json"));
        if ("auto".equals(configured)) {
            return containsCjk(requirement) ? "zh-CN" : "en";
        }
        return configured;
    }

    private String structuredArtifactFieldSemantics(String nodeId) {
        if (!ContractRegistry.hasContract(nodeId)) {
            return "";
        }
        var contract = ContractRegistry.forNode(nodeId);
        if (!contract.emitsArtifact("agent_output_contract")
                && !contract.emitsArtifact("plan_result")
                && !contract.emitsArtifact("acceptance_review")) {
            return "";
        }
        return """
                ## Structured Artifact Field Semantics

                - `must_fix`, `missing_evidence`, `open_risks`, and `failure.reason` mean unresolved blockers for the current decision.
                - If the decision is positive, ready, or accepted, keep blocking fields empty: `blocking=false`, `must_fix=[]`, `missing_evidence=[]`, and `failure.reason=""`.
                - Do not use `must_fix` to list review comments that you already resolved. Put resolved review comments in `checks`, `evidence`, or `acceptance_notes` instead.
                """.stripTrailing();
    }

    private String readReuseContractSection(Path workspaceRoot, List<String> inputRefs) {
        return contextPackageSubsection(workspaceRoot, inputRefs, "Read reuse contract:");
    }

    private String explorationPlanSection(Path workspaceRoot, List<String> inputRefs) {
        return contextPackageSubsection(workspaceRoot, inputRefs, "## Exploration Plan");
    }

    private String contextPackageSubsection(Path workspaceRoot, List<String> inputRefs, String marker) {
        if (inputRefs == null || inputRefs.isEmpty()) {
            return "";
        }
        for (String inputRef : inputRefs) {
            if (!inputRef.contains("/context/") || !inputRef.endsWith(".md")) {
                continue;
            }
            var path = workspaceRoot.resolve(inputRef).normalize();
            if (!Files.isRegularFile(path)) {
                continue;
            }
            var extracted = extractContextPackageSubsection(readIfExists(path), marker);
            if (!extracted.isBlank()) {
                return extracted;
            }
        }
        return "";
    }

    private String extractContextPackageSubsection(String contextPackage, String marker) {
        var start = contextPackage.indexOf(marker);
        if (start < 0) {
            return "";
        }
        var end = contextPackage.indexOf("\n## ", start + marker.length());
        var userRequirement = contextPackage.indexOf("## User Requirement", start);
        if (userRequirement >= 0 && (end < 0 || userRequirement < end)) {
            end = userRequirement;
        }
        if (end < 0) {
            end = contextPackage.length();
        }
        return contextPackage.substring(start, end).stripTrailing();
    }

    private String readConfiguredPromptLanguage(Path settingsPath) {
        if (!Files.isRegularFile(settingsPath)) {
            return "auto";
        }
        try {
            var node = OBJECT_MAPPER.readTree(settingsPath.toFile());
            return PromptLanguage.normalize(node.path("promptLanguage").asText("auto"));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read workstream settings: " + settingsPath, e);
        }
    }

    private boolean containsCjk(String text) {
        return text != null && text.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                        || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                        || (codePoint >= 0x20000 && codePoint <= 0x2A6DF)
        );
    }

    private String principleTemplate(Path workspaceRoot, String templateName) {
        var builder = new StringBuilder(loadPrincipleResource(templateName).stripTrailing());
        appendProjectPrinciple(builder, workspaceRoot.resolve(".agent").resolve("templates").resolve(templateName));
        appendProjectPrinciple(builder, workspaceRoot.resolve(".agent").resolve("templates").resolve("domain-principles.md"));
        return builder.toString().stripTrailing();
    }

    private String loadPrincipleResource(String templateName) {
        var resource = PRINCIPLE_TEMPLATE_ROOT + templateName;
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return "(no built-in principle template found: " + templateName + ")";
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load principle template: " + resource, e);
        }
    }

    private void appendProjectPrinciple(StringBuilder builder, Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        builder.append("\n\n## Project Principles: ")
                .append(path.getFileName())
                .append("\n\n")
                .append(readIfExists(path).stripTrailing());
    }

    private String contextPackageSection(Path workspaceRoot, List<String> inputRefs) {
        if (inputRefs == null || inputRefs.isEmpty()) {
            return "(no context package provided)";
        }
        var contextRefs = inputRefs.stream()
                .filter(ref -> ref.contains("/context/") && ref.endsWith(".md"))
                .toList();
        var directInputRefs = inputRefs.stream()
                .filter(ref -> !contextRefs.contains(ref))
                .toList();
        if (contextRefs.isEmpty() && directInputRefs.isEmpty()) {
            return "(no context package provided)";
        }
        var builder = new StringBuilder();
        if (!directInputRefs.isEmpty()) {
            builder.append("### Direct Input Artifacts\n\n");
            for (String inputRef : directInputRefs) {
                var path = workspaceRoot.resolve(inputRef).normalize();
                builder.append("- ").append(inputRef).append("\n");
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    builder.append("\n```text\n");
                    builder.append(readContextPreview(workspaceRoot, path));
                    builder.append("\n```\n\n");
                }
            }
        }
        for (String contextRef : contextRefs) {
            var path = workspaceRoot.resolve(contextRef).normalize();
            builder.append("- ").append(contextRef).append("\n");
            if (Files.exists(path)) {
                builder.append("\n```text\n");
                builder.append(readCompleteContextPackage(workspaceRoot, path));
                builder.append("\n```\n\n");
            }
        }
        return builder.toString().stripTrailing();
    }

    private String readCompleteContextPackage(Path workspaceRoot, Path path) {
        try {
            return sanitizeOutsideWorkspacePaths(
                    workspaceRoot,
                    Files.readString(path, StandardCharsets.UTF_8)).stripTrailing();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read context package: " + path, e);
        }
    }

    private String readContextPreview(Path workspaceRoot, Path path) {
        try {
            var content = Files.readString(path, StandardCharsets.UTF_8);
            content = sanitizeOutsideWorkspacePaths(workspaceRoot, content);
            if (content.length() <= 6_000) {
                return content.stripTrailing();
            }
            return content.substring(0, 6_000).stripTrailing()
                    + "\n\n[truncated; read the full context package at " + path.toAbsolutePath().normalize() + "]";
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read context package: " + path, e);
        }
    }

    private String workspaceGuard(Path workspaceRoot, String nodeId) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        if ("intake.claude_brainstorm".equals(nodeId)) {
            return """
                    ## Execution Workspace Guard

                    - Effective workspace: %s
                    - This intake run uses restricted contract discovery. Use orchestrator-provided context and bounded discovery only.
                    - Do not output shell commands or Bash code fences as a substitute for the required structured artifact.
                    - Modify files only inside the effective workspace.
                    - Ignore absolute paths outside the effective workspace even if they appear in quoted prior outputs.
                    - Do not run commands with `git -C` pointing outside the effective workspace.
                    """.formatted(root).stripTrailing();
        }
        return """
                ## Execution Workspace Guard

                - Effective workspace: %s
                - Run all shell commands from the effective workspace above.
                - Modify files only inside the effective workspace.
                - Ignore absolute paths outside the effective workspace even if they appear in quoted prior outputs.
                - Do not run commands with `git -C` pointing outside the effective workspace.
                """.formatted(root).stripTrailing();
    }

    private String sanitizeOutsideWorkspacePaths(Path workspaceRoot, String content) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var matcher = ABSOLUTE_PATH_PATTERN.matcher(content);
        var result = new StringBuilder();
        while (matcher.find()) {
            var rawPath = matcher.group(1);
            var replacement = rawPath;
            try {
                var path = Path.of(rawPath).toAbsolutePath().normalize();
                if (!path.startsWith(root)) {
                    replacement = "[outside-workspace path removed]";
                }
            } catch (RuntimeException ignored) {
                replacement = "[outside-workspace path removed]";
            }
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private record PromptContract(
            String allowedDecisions,
            String requiredControlKeys,
            String decisionExample,
            Set<String> allowedDecisionValues
    ) {}

    /** Source metadata for one prompt section; content is only used to compute bytes/sha. */
    record PromptSection(String name, String sourceType, String sourceRef, String content,
                         String includedReason, boolean required) {
    }

    /** Rendered prompt text plus its section provenance. */
    record RenderedPrompt(String content, List<PromptSection> sections) {
    }
}
