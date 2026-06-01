package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTaskContractSectionTest {

    @TempDir
    Path tempDir;

    private Path wsDir;

    @BeforeEach
    void setup() {
        wsDir = tempDir.resolve(".agent/workstreams/ws-ctx");
    }

    @Test
    void acceptanceProfileIncludesTaskContractSection() {
        var budget = ContextProfiles.acceptance();
        var sectionNames = budget.sections().stream()
                .map(SectionRequirement::section)
                .toList();
        assertTrue(sectionNames.contains("task_contract"),
                "acceptance profile should contain 'task_contract' section, got: " + sectionNames);
    }

    @Test
    void taskContractSectionIsCriticalPriority() {
        var budget = ContextProfiles.acceptance();
        var taskContractSection = budget.sections().stream()
                .filter(sr -> "task_contract".equals(sr.section()))
                .findFirst();
        assertTrue(taskContractSection.isPresent(),
                "task_contract section should exist in acceptance profile");
        assertEquals(Priority.CRITICAL, taskContractSection.get().priority(),
                "task_contract should be CRITICAL priority");
    }

    @Test
    void taskContractSectionIsOptionalForThinWorkflowAcceptance() {
        var budget = ContextProfiles.acceptance();
        var taskContractSection = budget.sections().stream()
                .filter(sr -> "task_contract".equals(sr.section()))
                .findFirst();

        assertTrue(taskContractSection.isPresent(),
                "task_contract section should exist in acceptance profile");
        assertEquals(MissingPolicy.PLACEHOLDER, taskContractSection.get().missingPolicy(),
                "thin workflow has no task contract, so acceptance context must render a placeholder instead of failing");
    }

    @Test
    void contextPackageRendersTaskContractSection() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);

        // Create task-contract.json
        var artifactsDir = wsDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);

        var contract = new TaskContract(
                "tc-001", 1, Instant.now(), "Test objective",
                new TaskContract.Scope(List.of("file1.java"), List.of("file2.java")),
                List.of("AC1", "AC2"),
                List.of("TR1"),
                List.of("Use Java"),
                List.of("No Python"),
                List.of(), List.of(), List.of(),
                ContractStatus.READY_FOR_REVIEW, 1,
                new TaskContract.Source("requirement.md", "contract_compiler")
        );

        var mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.writeValue(artifactsDir.resolve("task-contract.json").toFile(), contract);

        // Also create events.jsonl for ledger_pointers availability
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        // Also create git evidence for the acceptance profile
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);

        var handler = new ContextBuildPackageHandler();
        var context = new TaskHandler.TaskContext(
                tempDir, "ws-ctx",
                new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.node",
                        "profile", "acceptance"
                )),
                new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        );

        var result = handler.execute(context);

        assertEquals(NodeStatus.COMPLETED, result.status(), "Handler should complete successfully");
        assertNotNull(result.result().get("context_package"));

        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")), StandardCharsets.UTF_8);

        assertTrue(markdown.contains("TaskContract"),
                "Context package should contain TaskContract section header");
        assertTrue(markdown.contains("Test objective"),
                "Context package should contain the objective text");
        assertTrue(markdown.contains("AC1"),
                "Context package should contain acceptance criteria");
        assertTrue(markdown.contains("AC2"),
                "Context package should contain acceptance criteria");
    }

    @Test
    void acceptancePackageWithoutTaskContractRendersPlaceholder() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);

        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("diff.stat"), "1 file changed\n", StandardCharsets.UTF_8);
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var handler = new ContextBuildPackageHandler();
        var context = new TaskHandler.TaskContext(
                tempDir, "ws-ctx",
                new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.node",
                        "profile", "acceptance"
                )),
                new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        );

        var result = handler.execute(context);

        assertEquals(NodeStatus.COMPLETED, result.status(), "Thin workflow acceptance context should not require task-contract.json");
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")), StandardCharsets.UTF_8);
        assertTrue(markdown.contains("TaskContract"),
                "Context package should keep a stable TaskContract section");
        assertTrue(markdown.contains("(not available)"),
                "Missing task contract should be visible to the reviewer as unavailable");
    }
}
