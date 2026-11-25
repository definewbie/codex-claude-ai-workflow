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
        assertEquals(2_500, taskContractSection.get().softBudget(),
                "acceptance must retain complete contracts that legitimately exceed the old 2k limit");
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
                "custom workflows may omit task contracts, so acceptance context must render a placeholder instead of failing");
    }

    @Test
    void contextPackageRendersTaskContractSection() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);

        var fixture = new AuthoredStageSequenceTest.Fixture(tempDir, "file1.java",
                AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, "ws-ctx");
        var approved = fixture.before("implement.claude_implement");

        // Also create git evidence for the acceptance profile
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);

        var handler = new ContextBuildPackageHandler();
        var context = new TaskHandler.TaskContext(tempDir, "ws-ctx", new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.node",
                        "profile", "acceptance", "required_contract_phase", "EXECUTION"
                )), new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null), dev.dual.runtime.RuntimeTestLedger.ledger());

        var result = handler.execute(context);

        assertEquals(NodeStatus.COMPLETED, result.status(), "Handler should complete successfully");
        assertNotNull(result.result().get("context_package"));

        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")), StandardCharsets.UTF_8);

        assertTrue(markdown.contains("TaskContract"),
                "Context package should contain TaskContract section header");
        assertTrue(markdown.contains("Verify output"),
                "Context package should contain the objective text");
        assertTrue(markdown.contains("AC-1"),
                "Context package should contain acceptance criteria");
        assertTrue(markdown.contains("AC-2"),
                "Context package should contain acceptance criteria");
    }

    @Test
    void acceptanceConsumerCannotOmitItsRequiredContractPhase() throws Exception {
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Build the feature.", StandardCharsets.UTF_8);

        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("diff.stat"), "1 file changed\n", StandardCharsets.UTF_8);
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        var definition = new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of("target_node", "next.node", "profile", "acceptance"));
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger,
                new dev.dual.ledger.file.WorkstreamPaths(tempDir, new dev.dual.core.id.DualId("ws-ctx")), "wf-thin",
                new dev.dual.core.workflow.WorkflowDefinition("thin", 1, definition.id(), List.of(definition), List.of()));

        var handler = new ContextBuildPackageHandler();
        var context = new TaskHandler.TaskContext(tempDir, "ws-ctx", new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.node",
                        "profile", "acceptance"
                )), new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null), dev.dual.runtime.RuntimeTestLedger.ledger());

        var failure = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> handler.execute(context));
        assertTrue(failure.getMessage().contains("required_contract_phase"));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(wsDir.resolve("context/node-001.md")),
                "placeholder display policy cannot waive qualification");
    }
}
