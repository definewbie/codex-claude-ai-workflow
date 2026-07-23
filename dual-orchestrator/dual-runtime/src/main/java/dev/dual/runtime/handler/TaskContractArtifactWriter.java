package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

final class TaskContractArtifactWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TaskContractArtifactWriter() {}

    static void writeJson(TaskContract contract, Path artifactsDir) throws IOException {
        Files.createDirectories(artifactsDir);
        var jsonPath = artifactsDir.resolve("task-contract.json");
        MAPPER.writeValue(jsonPath.toFile(), contract);
    }

    static void writeMarkdown(TaskContract contract, Path artifactsDir) throws IOException {
        Files.createDirectories(artifactsDir);
        var md = new StringBuilder();
        md.append("# Task Contract\n\n");

        md.append("## Objective\n\n").append(contract.objective()).append("\n\n");

        md.append("## Scope\n\n");
        if (contract.scope() != null) {
            if (!contract.scope().included().isEmpty()) {
                md.append("**Included:**\n");
                for (var item : contract.scope().included()) {
                    md.append("- ").append(item.operation()).append(": ").append(item.path()).append("\n");
                }
                md.append("\n");
            }
            if (!contract.scope().excluded().isEmpty()) {
                md.append("**Excluded:**\n");
                for (var item : contract.scope().excluded()) {
                    md.append("- ").append(item).append("\n");
                }
                md.append("\n");
            }
        }

        md.append("## Acceptance Criteria\n\n");
        for (var criterion : contract.acceptanceCriteria()) {
            md.append("- [ ] ").append(criterion).append("\n");
        }
        md.append("\n");

        appendListSection(md, "Test Requirements", contract.testRequirements());
        appendListSection(md, "Constraints", contract.constraints());
        appendListSection(md, "Non-Goals", contract.nonGoals());
        appendListSection(md, "Risk Areas", contract.riskAreas());
        appendListSection(md, "Ambiguity Notes", contract.ambiguityNotes());
        appendListSection(md, "Human Questions", contract.humanQuestions());

        if (contract.source() != null) {
            md.append("## Source\n\n");
            md.append("- **Created by:** ").append(contract.source().createdBy()).append("\n");
            if (contract.source().rawRequestRef() != null) {
                md.append("- **Raw request:** ").append(contract.source().rawRequestRef()).append("\n");
            }
            md.append("\n");
        }

        var mdPath = artifactsDir.resolve("task-contract.md");
        Files.writeString(mdPath, md.toString(), StandardCharsets.UTF_8);
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }

    private static void appendListSection(StringBuilder md, String title, java.util.List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        md.append("## ").append(title).append("\n\n");
        for (var item : items) {
            md.append("- ").append(item).append("\n");
        }
        md.append("\n");
    }
}
