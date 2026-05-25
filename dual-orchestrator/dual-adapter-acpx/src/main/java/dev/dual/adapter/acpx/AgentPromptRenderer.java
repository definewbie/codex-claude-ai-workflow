package dev.dual.adapter.acpx;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class AgentPromptRenderer {
    private static final String TEMPLATE_ROOT = "dev/dual/adapter/acpx/prompts/";

    String render(Path workspaceRoot, String workstreamId, String nodeId, String promptTemplate, List<String> inputRefs) {
        var template = loadTemplate(promptTemplate);
        var workstreamDir = ".agent/workstreams/" + workstreamId;
        var requirementPath = workspaceRoot.resolve(workstreamDir).resolve("requirement.md");
        var requirement = readIfExists(requirementPath);
        var contextPackage = contextPackageSection(workspaceRoot, inputRefs);
        return template
                .replace("{{workstream_id}}", workstreamId)
                .replace("{{node_id}}", nodeId)
                .replace("{{prompt_template}}", promptTemplate)
                .replace("{{ledger_path}}", workstreamDir)
                .replace("{{requirement}}", requirement)
                .replace("{{context_package}}", contextPackage);
    }

    private String loadTemplate(String promptTemplate) {
        var resource = TEMPLATE_ROOT + promptTemplate + ".md";
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalArgumentException("missing prompt template resource: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load prompt template: " + resource, e);
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
                    builder.append(readContextPreview(path));
                    builder.append("\n```\n\n");
                }
            }
        }
        for (String contextRef : contextRefs) {
            var path = workspaceRoot.resolve(contextRef).normalize();
            builder.append("- ").append(contextRef).append("\n");
            if (Files.exists(path)) {
                builder.append("\n```text\n");
                builder.append(readContextPreview(path));
                builder.append("\n```\n\n");
            }
        }
        return builder.toString().stripTrailing();
    }

    private String readContextPreview(Path path) {
        try {
            var content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() <= 6_000) {
                return content.stripTrailing();
            }
            return content.substring(0, 6_000).stripTrailing()
                    + "\n\n[truncated; read the full context package at " + path.toAbsolutePath().normalize() + "]";
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read context package: " + path, e);
        }
    }
}
