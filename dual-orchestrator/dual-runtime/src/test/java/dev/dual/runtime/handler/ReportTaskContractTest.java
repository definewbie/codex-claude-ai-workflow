package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportTaskContractTest {
    @TempDir
    Path tempDir;

    @Test
    void finalReportIncludesTaskContractSection() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-001");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Add project description to README", StandardCharsets.UTF_8);

        var artifactsDir = wsDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);

        var contract = new TaskContract(
                "tc-001",
                1,
                Instant.now(),
                "Add project description to README",
                new TaskContract.Scope(
                        List.of(new TaskContract.ScopeEntry(
                                "README.md", TaskContract.ScopeOperation.MODIFY)),
                        List.of("code")),
                List.of("AC1", "AC2", "AC3"),
                List.of(),
                List.of(),
                List.of("No Python"),
                List.of(),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                null
        );

        var mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        mapper.writeValue(artifactsDir.resolve("task-contract.json").toFile(), contract);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-001",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-001", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));

        assertTrue(report.contains("TaskContract"), "Report should contain TaskContract section heading");
        assertTrue(report.contains("tc-001"), "Report should contain contract ID tc-001");
        assertTrue(report.contains("Add project description to README"), "Report should contain objective");
        assertTrue(report.contains("3 items"), "Report should show 3 acceptance criteria items");
    }

    @Test
    void finalReportShowsLegacyPlaceholderWhenNoContract() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-legacy");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Legacy task", StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-legacy",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-002", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("no TaskContract"), "Report should show legacy placeholder when no contract exists");
    }
}
