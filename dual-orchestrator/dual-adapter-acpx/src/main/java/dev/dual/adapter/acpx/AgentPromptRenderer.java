package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.ContractRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class AgentPromptRenderer {
    private static final String TEMPLATE_ROOT = "dev/dual/adapter/acpx/prompts/";
    private static final String PRINCIPLE_TEMPLATE_ROOT = "dev/dual/adapter/acpx/templates/";
    private static final Set<String> KNOWN_VARIABLES = Set.of(
            "workstream_id", "node_id", "prompt_template",
            "ledger_path", "requirement", "context_package",
            "contract_review_principles", "allowed_decisions",
            "required_control_keys", "decision_example"
    );
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-z_]+)}}");
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("(?<!<)(/[^\\s\"'`)]+)");

    String render(Path workspaceRoot, String workstreamId, String nodeId, String promptTemplate, List<String> inputRefs) {
        var template = loadTemplate(promptTemplate);
        var workstreamDir = ".agent/workstreams/" + workstreamId;
        var requirementPath = workspaceRoot.resolve(workstreamDir).resolve("requirement.md");
        var requirement = readIfExists(requirementPath);
        var contextPackage = contextPackageSection(workspaceRoot, inputRefs);
        var contractReviewPrinciples = principleTemplate(workspaceRoot, "contract-review-principles.md");
        var promptContract = promptContract(nodeId);
        return workspaceGuard(workspaceRoot) + "\n\n" + template
                .replace("{{workstream_id}}", workstreamId)
                .replace("{{node_id}}", nodeId)
                .replace("{{prompt_template}}", promptTemplate)
                .replace("{{ledger_path}}", workstreamDir)
                .replace("{{requirement}}", requirement)
                .replace("{{context_package}}", contextPackage)
                .replace("{{contract_review_principles}}", contractReviewPrinciples)
                .replace("{{allowed_decisions}}", promptContract.allowedDecisions())
                .replace("{{required_control_keys}}", promptContract.requiredControlKeys())
                .replace("{{decision_example}}", promptContract.decisionExample());
    }

    private PromptContract promptContract(String nodeId) {
        if (!ContractRegistry.hasContract(nodeId)) {
            return new PromptContract("- (no runtime contract registered)", "- decision\n- summary", "<one allowed decision>");
        }
        var contract = ContractRegistry.forNode(nodeId);
        return new PromptContract(
                bulletList(contract.allowedDecisions()),
                bulletList(contract.requiredKeys()),
                contract.allowedDecisions().size() == 1
                        ? contract.allowedDecisions().iterator().next()
                        : "<one allowed decision>"
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
                builder.append(readContextPreview(workspaceRoot, path));
                builder.append("\n```\n\n");
            }
        }
        return builder.toString().stripTrailing();
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

    private String workspaceGuard(Path workspaceRoot) {
        var root = workspaceRoot.toAbsolutePath().normalize();
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

    private record PromptContract(String allowedDecisions, String requiredControlKeys, String decisionExample) {}
}
