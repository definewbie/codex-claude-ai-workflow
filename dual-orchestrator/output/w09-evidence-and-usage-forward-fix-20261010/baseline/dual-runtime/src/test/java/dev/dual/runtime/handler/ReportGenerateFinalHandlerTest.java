package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.GoalDomainOutcome;
import dev.dual.ledger.file.GoalSatisfactionDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGenerateFinalHandlerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @TempDir
    Path tempDir;

    @Test
    void generatesVerificationSnapshotFromResolvedTypedEvidence() throws Exception {
        var fixture = prepare("ws-rpt");
        var wsDir = workstreamDir("ws-rpt");
        Files.writeString(wsDir.resolve("requirement.md"), "Implement feature X.", StandardCharsets.UTF_8);
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}");

        var result = new ReportGenerateFinalHandler().execute(context("ws-rpt", fixture));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("Implement feature X."));
        assertTrue(report.contains("Codex accepts the implementation."));
        assertTrue(report.contains("Tests: 42 passed"));
        assertTrue(report.contains("diff matches contract"));
        assertTrue(report.contains("unit tests passed"));
        assertTrue(report.contains("## Selected Typed Outputs"));
        assertTrue(report.contains("pre-human-delivery verification snapshot"));
        assertTrue(report.contains("git-evidence.json"));
    }

    @Test
    void missingGoalFailsWithoutPublishingAReport() throws Exception {
        var fixture = prepare("ws-rpt-missing");
        var wsDir = workstreamDir("ws-rpt-missing");
        Files.delete(wsDir.resolve("domain/goal-satisfaction.json"));
        Files.createDirectories(wsDir.resolve("reports"));
        Files.writeString(wsDir.resolve("requirement.md"), "Minimal requirement.");
        Files.writeString(wsDir.resolve("reports/latest-final-report.md"), "historical report bytes");

        var result = new ReportGenerateFinalHandler().execute(new TaskHandler.TaskContext(tempDir, "ws-rpt-missing", definition(), emptyReportNode("ws-rpt-missing"), dev.dual.runtime.RuntimeTestLedger.ledger()));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals(CompletionEvidenceLineageResolver.GOAL_NOT_CONFIGURED, result.result().get("failure_code"));
        assertTrue(result.outputRefs().isEmpty());
        assertEquals("historical report bytes", Files.readString(wsDir.resolve("reports/latest-final-report.md")));
    }

    @Test
    void configuredReportFailsBeforePublishingWithoutContractQualification() throws Exception {
        var definition = new NodeDefinition(
                "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                Map.of("required_contract_phase", "EXECUTION"));

        var result = new ReportGenerateFinalHandler().execute(new TaskHandler.TaskContext(tempDir, "ws-rpt-qualified", definition, emptyReportNode("ws-rpt-qualified"), dev.dual.runtime.RuntimeTestLedger.ledger()));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals(TaskContractProjectionVerifier.FAILURE_OUTCOME, result.outcome());
        assertFalse(Files.exists(workstreamDir("ws-rpt-qualified").resolve("reports")));
    }

    @Test
    void nonFinalGoalFailsWithoutBeingRelabeledAsCorruption() throws Exception {
        var fixture = prepare("ws-rpt-not-final");
        var goalPath = workstreamDir("ws-rpt-not-final").resolve("domain/goal-satisfaction.json");
        var goal = MAPPER.readValue(goalPath.toFile(), GoalSatisfactionDocument.class);
        MAPPER.writeValue(goalPath.toFile(), new GoalSatisfactionDocument(
                goal.schemaVersion(), goal.workstreamId(), goal.workflowInstanceId(), goal.nodeInstanceId(),
                "design.decision", goal.iterationId(), "NEEDS_REVISION", GoalDomainOutcome.NEEDS_REDESIGN,
                "design needs revision", goal.evidenceRefs(), goal.sourceResult(), goal.generatedAt()));

        var result = new ReportGenerateFinalHandler().execute(context("ws-rpt-not-final", fixture));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals(CompletionEvidenceLineageResolver.GOAL_NOT_FINAL, result.result().get("failure_code"));
        assertTrue(result.outputRefs().isEmpty());
    }

    @Test
    void acceptWithNotesClassificationIsRenderedAsNotesNotFailure() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepareApproved(
                tempDir, "ws-rpt-notes", "ACCEPT_WITH_NOTES", "Accepted with runtime validation note.");
        var wsDir = workstreamDir("ws-rpt-notes");
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("requirement.md"), "Notes report.");
        Files.writeString(wsDir.resolve("artifacts/failure-classification.json"), """
                {
                  "category": "REVIEW_NOISE",
                  "reasoning": "No blocking issue.",
                  "suggested_action": "Record non-blocking acceptance notes and continue",
                  "matched_rule": 6,
                  "review_decision": "ACCEPT_WITH_NOTES",
                  "source": "deterministic",
                  "classified_at": "2026-06-03T00:00:00Z"
                }
                """);

        var result = new ReportGenerateFinalHandler().execute(context("ws-rpt-notes", fixture));
        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## Acceptance Notes Classification"));
        assertTrue(report.contains("Record non-blocking acceptance notes and continue"));
        assertFalse(report.contains("## Failure Classification"));
    }

    @Test
    void gitEvidenceHygieneWarningsAreRendered() throws Exception {
        var fixture = prepare("ws-rpt-hygiene");
        var wsDir = workstreamDir("ws-rpt-hygiene");
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Hygiene report.");
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}");
        Files.writeString(evidenceDir.resolve("hygiene-warnings.txt"),
                "__pycache__/ looks like a generated self-test byproduct");

        var result = new ReportGenerateFinalHandler().execute(context("ws-rpt-hygiene", fixture));
        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## Repository Hygiene Warnings"));
        assertTrue(report.contains("__pycache__/ looks like a generated self-test byproduct"));
    }

    @Test
    void unroutedNewerOutputsCannotReplaceResolvedEvidence() throws Exception {
        var fixture = prepare("ws-rpt-routed");
        var wsDir = workstreamDir("ws-rpt-routed");
        Files.writeString(wsDir.resolve("requirement.md"), "Routed evidence report.");
        var sibling = wsDir.resolve("runs/run-999/outputs/acceptance-codex-review.md");
        Files.createDirectories(sibling.getParent());
        Files.writeString(sibling, "Unrouted newer acceptance must not appear.");

        var result = new ReportGenerateFinalHandler().execute(context("ws-rpt-routed", fixture));
        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("Codex accepts the implementation."));
        assertFalse(report.contains("Unrouted newer acceptance must not appear."));
    }

    @Test
    void contractResolutionIsRendered() throws Exception {
        var fixture = prepare("ws-rpt-resolution");
        var wsDir = workstreamDir("ws-rpt-resolution");
        var artifactsDir = wsDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Resolution report.");
        Files.writeString(artifactsDir.resolve("contract-resolution.json"), """
                {
                  "decision": "APPROVE",
                  "resolved_questions": [{
                    "question": "Should review be mandatory?",
                    "resolution": "Yes, mandatory review was approved.",
                    "source": "human"
                  }],
                  "accepted_risks": ["Existing shortcut is intentionally removed."],
                  "approved_by": "human_gate"
                }
                """);

        var result = new ReportGenerateFinalHandler().execute(context("ws-rpt-resolution", fixture));
        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## Requirement Resolution"));
        var contract = TaskContractProjectionVerifier.resolve(dev.dual.runtime.RuntimeTestLedger.ledger(), tempDir, "ws-rpt-resolution").qualified().orElseThrow();
        assertTrue(report.contains(contract.resolutionRef()));
        assertTrue(report.contains(contract.resolutionSha256()));
        assertFalse(report.contains("Should review be mandatory?"), "unbound display files are not approval facts");
        assertFalse(report.contains("Existing shortcut is intentionally removed."));
    }

    @Test
    void filenamesIncludeIterationId() throws Exception {
        var fixture = CompletionEvidenceLineageFixture.prepareApproved(
                tempDir, "ws-rpt-filename", "ACCEPT", "Accepted.");
        Files.writeString(workstreamDir("ws-rpt-filename").resolve("requirement.md"), "Filename test.");

        var result = new ReportGenerateFinalHandler().execute(context("ws-rpt-filename", fixture));

        assertTrue(((String) result.result().get("report_path")).contains(fixture.reportNode().iterationId() + "-final-report.md"));
        assertTrue(((String) result.result().get("latest_report_path")).contains("latest-final-report.md"));
    }

    private CompletionEvidenceLineageFixture.Prepared prepare(String workstreamId) throws Exception {
        return CompletionEvidenceLineageFixture.prepareApproved(tempDir, workstreamId);
    }

    private Path workstreamDir(String workstreamId) {
        return tempDir.resolve(".agent/workstreams").resolve(workstreamId);
    }

    private TaskHandler.TaskContext context(
            String workstreamId,
            CompletionEvidenceLineageFixture.Prepared fixture
    ) {
        return new TaskHandler.TaskContext(tempDir, workstreamId, definition(), fixture.reportNode(), dev.dual.runtime.RuntimeTestLedger.ledger());
    }

    private static NodeDefinition definition() {
        return new NodeDefinition(
                "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of("required_contract_phase", "EXECUTION"));
    }

    private static NodeInstance emptyReportNode(String workstreamId) {
        return new NodeInstance(
                "node-report-" + workstreamId,
                "acceptance.generate_final_report",
                NodeKind.TASK,
                "report.generate_final",
                NodeStatus.RUNNING,
                "stage-acceptance",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                "",
                Instant.now(),
                null);
    }
}
