package dev.dual.runtime.handler;

import dev.dual.core.workflow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReportTaskContractTest {
    @TempDir Path tempDir;

    @Test void finalReportUsesApprovedContractInsteadOfMutableDisplayCopy() throws Exception {
        var f = CompletionEvidenceLineageFixture.prepareApproved(tempDir, "ws-report-contract");
        var qualified = TaskContractProjectionVerifier.resolve(dev.dual.runtime.RuntimeTestLedger.ledger(), tempDir, "ws-report-contract").qualified().orElseThrow();
        Files.writeString(tempDir.resolve(".agent/workstreams/ws-report-contract/artifacts/task-contract.json"),
                "{\"id\":\"forged-display-id\",\"objective\":\"unapproved objective\"}");
        var result = new ReportGenerateFinalHandler().execute(context("ws-report-contract", f));
        assertEquals(NodeStatus.COMPLETED, result.status(), result.toString());
        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## TaskContract"));
        assertTrue(report.contains(qualified.contract().id()));
        assertTrue(report.contains(qualified.contract().objective()));
        assertTrue(report.contains(qualified.contract().acceptanceCriteria().size() + " items"));
        assertTrue(report.contains(qualified.sha256()));
        assertFalse(report.contains("forged-display-id"));
        assertFalse(report.contains("unapproved objective"));
    }

    @Test void missingApprovedContractRejectsReportRatherThanPublishingLegacyPlaceholder() throws Exception {
        var f = CompletionEvidenceLineageFixture.prepareApproved(tempDir, "ws-report-missing-contract");
        var qualified = TaskContractProjectionVerifier.resolve(dev.dual.runtime.RuntimeTestLedger.ledger(), tempDir, "ws-report-missing-contract").qualified().orElseThrow();
        Files.delete(tempDir.resolve(qualified.contractRef()));
        var result = new ReportGenerateFinalHandler().execute(context("ws-report-missing-contract", f));
        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals(TaskContractProjectionVerifier.FAILURE_OUTCOME, result.outcome());
        assertTrue(result.outputRefs().isEmpty());
        assertFalse(Files.exists(tempDir.resolve(".agent/workstreams/ws-report-missing-contract/reports/latest-final-report.md")));
    }

    private TaskHandler.TaskContext context(String ws, CompletionEvidenceLineageFixture.Prepared f) {
        return new TaskHandler.TaskContext(tempDir, ws, new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK,
                "report.generate_final", Map.of("required_contract_phase", "EXECUTION")), f.reportNode(), dev.dual.runtime.RuntimeTestLedger.ledger());
    }
}
