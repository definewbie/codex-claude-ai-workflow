package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGenerateFinalHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesFullReportWithAllData() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Implement feature X.", StandardCharsets.UTF_8);

        var acceptance = wsDir.resolve("runs/run-003/outputs/acceptance-codex-review.md");
        Files.createDirectories(acceptance.getParent());
        Files.writeString(acceptance, "PASS: all checks green", StandardCharsets.UTF_8);

        var selfTest = wsDir.resolve("runs/run-002/outputs/implement-claude-self-test.md");
        Files.createDirectories(selfTest.getParent());
        Files.writeString(selfTest, "Tests: 42 passed", StandardCharsets.UTF_8);

        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-001", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("Implement feature X."));
        assertTrue(report.contains("PASS: all checks green"));
        assertTrue(report.contains("Tests: 42 passed"));
        assertTrue(report.contains("git-evidence.json"));
    }

    @Test
    void missingOptionalDataShowsNotAvailable() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt-empty");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Minimal requirement.", StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt-empty",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-002", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("not available"));
    }

    @Test
    void noAgentOutputsShowsPlaceholder() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt-no-outputs");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "No outputs.", StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt-no-outputs",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-003", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("no agent outputs"));
    }

    @Test
    void filenamesIncludeIterationId() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt-filename");
        Files.createDirectories(wsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Filename test.", StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt-filename",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-004", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-003", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var reportRef = (String) result.result().get("report_path");
        var latestRef = (String) result.result().get("latest_report_path");
        assertTrue(reportRef.contains("iter-003-final-report.md"));
        assertTrue(latestRef.contains("latest-final-report.md"));
        assertTrue(Files.exists(tempDir.resolve(reportRef)));
        assertTrue(Files.exists(tempDir.resolve(latestRef)));
    }
}
