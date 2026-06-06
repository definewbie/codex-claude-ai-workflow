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
    void acceptWithNotesClassificationIsRenderedAsNotesNotFailure() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt-notes");
        Files.createDirectories(wsDir.resolve("artifacts"));
        Files.writeString(wsDir.resolve("requirement.md"), "Notes report.", StandardCharsets.UTF_8);
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
                """, StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt-notes",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-005", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## Acceptance Notes Classification"));
        assertTrue(report.contains("Record non-blocking acceptance notes and continue"));
        assertTrue(!report.contains("## Failure Classification"));
    }

    @Test
    void gitEvidenceHygieneWarningsAreRendered() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt-hygiene");
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Hygiene report.", StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), """
                {
                  "available": true,
                  "hygieneWarningCount": 1
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(evidenceDir.resolve("hygiene-warnings.txt"), """
                __pycache__/ looks like a generated self-test byproduct; remove it unless it is an intentional deliverable.
                """, StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt-hygiene",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-006", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## Repository Hygiene Warnings"));
        assertTrue(report.contains("__pycache__/ looks like a generated self-test byproduct"));
    }

    @Test
    void structuredVerificationEvidenceIsRendered() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt-evidence");
        var outputsDir = wsDir.resolve("runs/run-007/outputs");
        Files.createDirectories(outputsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Evidence report.", StandardCharsets.UTF_8);
        Files.writeString(outputsDir.resolve("implement-claude-self-test.contract.json"), """
                {
                  "node_id": "implement.claude_self_test_report",
                  "decision": "SELF_TEST_COMPLETE",
                  "summary": "Self-test completed with one runtime gap.",
                  "checks_run": ["npm test"],
                  "checks_skipped": ["device smoke: simulator unavailable"],
                  "evidence": ["Test output showed 24 passing tests."],
                  "missing_evidence": ["No physical device validation."],
                  "verification_summary": "Developer evidence is mostly sufficient."
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(outputsDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Accepted with runtime validation note.",
                  "missing_evidence": ["No physical device validation."],
                  "acceptance_notes": ["Run visual smoke before release."],
                  "verification_summary": "Evidence is adequate for merge with notes."
                }
                """, StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt-evidence",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-007", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## Verification Evidence"));
        assertTrue(report.contains("Self-test completed with one runtime gap."));
        assertTrue(report.contains("npm test"));
        assertTrue(report.contains("device smoke: simulator unavailable"));
        assertTrue(report.contains("No physical device validation."));
        assertTrue(report.contains("Run visual smoke before release."));
    }

    @Test
    void contractResolutionIsRendered() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-rpt-resolution");
        var artifactsDir = wsDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);
        Files.writeString(wsDir.resolve("requirement.md"), "Resolution report.", StandardCharsets.UTF_8);
        Files.writeString(artifactsDir.resolve("task-contract.json"), taskContractJson("Resolution objective"), StandardCharsets.UTF_8);
        Files.writeString(artifactsDir.resolve("contract-resolution.json"), """
                {
                  "schema_version": 1,
                  "decision": "APPROVE",
                  "resolved_questions": [
                    {
                      "question": "Should review be mandatory?",
                      "resolution": "Yes, mandatory review was approved.",
                      "source": "human"
                    }
                  ],
                  "assumptions": ["Human approved the requirement gate."],
                  "accepted_risks": ["Existing shortcut is intentionally removed."],
                  "gate_ref": ".agent/workstreams/ws-rpt-resolution/gates/gate-node-001.json",
                  "approved_by": "human_gate",
                  "created_at": "2026-06-06T00:00:00Z"
                }
                """, StandardCharsets.UTF_8);

        var handler = new ReportGenerateFinalHandler();
        var result = handler.execute(new TaskHandler.TaskContext(
                tempDir, "ws-rpt-resolution",
                new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of()),
                new NodeInstance("node-resolution", "acceptance.generate_final_report", NodeKind.TASK, "report.generate_final",
                        NodeStatus.READY, "stage-test", "iter-001", List.of(), List.of(), Map.of(), null, null, null)
        ));

        var report = Files.readString(tempDir.resolve((String) result.result().get("report_path")));
        assertTrue(report.contains("## Contract Resolution"));
        assertTrue(report.contains("Should review be mandatory?"));
        assertTrue(report.contains("Yes, mandatory review was approved."));
        assertTrue(report.contains("Existing shortcut is intentionally removed."));
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

    private static String taskContractJson(String objective) {
        return """
                {
                  "id": "task-ws",
                  "version": 1,
                  "created_at": "2026-06-03T00:00:00Z",
                  "objective": "%s",
                  "scope": {
                    "included": ["src/issueWorkflow.js"],
                    "excluded": ["No external dependencies"]
                  },
                  "acceptance_criteria": ["Criterion one", "Criterion two"],
                  "test_requirements": ["Run tests"],
                  "constraints": ["Keep scope narrow"],
                  "non_goals": ["Do not add UI"],
                  "risk_areas": [],
                  "ambiguity_notes": [],
                  "human_questions": [],
                  "status": "READY_FOR_REVIEW",
                  "compilation_pass": 1,
                  "source": {"raw_request_ref": "requirement.md", "created_by": "dual"}
                }
                """.formatted(objective);
    }
}
