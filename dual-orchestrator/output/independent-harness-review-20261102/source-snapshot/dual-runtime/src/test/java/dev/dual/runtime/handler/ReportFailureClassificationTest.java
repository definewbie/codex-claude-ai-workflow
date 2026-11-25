package dev.dual.runtime.handler;

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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFailureClassificationTest {
    @TempDir
    Path tempDir;
    private TestLedgerFixture fixture;
    private CompletionEvidenceLineageFixture.Prepared lineage;

    @BeforeEach
    void setUp() throws Exception {
        lineage = CompletionEvidenceLineageFixture.prepareApproved(tempDir, "ws-rfc");
    }

    @Test
    void reportIncludesFailureClassification() throws Exception {
        // Write a minimal failure classification artifact
        var artifactsDir = tempDir.resolve(".agent/workstreams/ws-rfc/artifacts");
        Files.createDirectories(artifactsDir);
        var classificationJson = """
                {
                  "category": "IMPLEMENTATION_BUG",
                  "reasoning": "Out-of-scope files were modified",
                  "suggested_action": "Re-implement staying within scope",
                  "source": "deterministic",
                  "classified_at": "2026-05-30T12:00:00Z",
                  "matched_rule": 3,
                  "review_decision": "REJECT",
                  "evidence": ["src/extra/File.java"]
                }
                """;
        Files.writeString(artifactsDir.resolve("failure-classification.json"), classificationJson, StandardCharsets.UTF_8);

        // Also need a task-contract.json (required by acceptance profile in some integration tests)
        // But for report handler alone, it's not strictly required since it just checks file existence

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-rfc", new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of("required_contract_phase", "EXECUTION")), lineage.reportNode(), dev.dual.runtime.RuntimeTestLedger.ledger()));

        var reportPath = (String) result.result().get("report_path");
        var report = Files.readString(tempDir.resolve(reportPath), StandardCharsets.UTF_8);

        assertTrue(report.contains("## Failure Classification"), "Report should have Failure Classification section");
        assertTrue(report.contains("IMPLEMENTATION_BUG"), "Report should show category");
        assertTrue(report.contains("Out-of-scope files were modified"), "Report should show reasoning");
        assertTrue(report.contains("Re-implement staying within scope"), "Report should show suggested action");
        assertTrue(report.contains("Matched Rule:** 3"), "Report should show matched rule");
        assertTrue(report.contains("REJECT"), "Report should show review decision");
        assertTrue(report.contains("src/extra/File.java"), "Report should show evidence");
    }

    @Test
    void reportHandlesAbsentClassification() throws Exception {
        // No failure-classification.json written
        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-rfc", new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of("required_contract_phase", "EXECUTION")), lineage.reportNode(), dev.dual.runtime.RuntimeTestLedger.ledger()));

        var reportPath = (String) result.result().get("report_path");
        var report = Files.readString(tempDir.resolve(reportPath), StandardCharsets.UTF_8);

        assertTrue(report.contains("## Failure Classification"), "Section header should always be present");
        assertTrue(report.contains("no failure classification"), "Should show absent message");
        assertFalse(report.contains("IMPLEMENTATION_BUG"), "Should NOT show classification details");
    }
}
