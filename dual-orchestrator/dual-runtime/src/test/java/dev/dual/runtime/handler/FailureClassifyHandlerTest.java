package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureClassifyHandlerTest {

    @TempDir
    Path tempDir;
    private TestLedgerFixture fixture;
    private Path artifactsDir;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new TestLedgerFixture(tempDir, "ws-fc");
        fixture.init();
        artifactsDir = tempDir.resolve(".agent/workstreams/ws-fc/artifacts");
        Files.createDirectories(artifactsDir);
    }

    @Test
    void handlerType() {
        var handler = new FailureClassifyHandler();
        assertEquals("acceptance.failure_classify", handler.type());
    }

    @Test
    void classifyReject_producesRejectOutcome() throws Exception {
        writeMinimalContract();
        writeGitEvidence("M src/A.java\nM src/B.java\n", null, null);

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "REJECT",
                        "summary", "Bad implementation",
                        "contract_gaps", List.of("missing scope definition"))
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertTrue(result.outcome().startsWith("REJECT_"));
        assertEquals("REJECT_CONTRACT_GAP", result.outcome());
        assertTrue(result.outputRefs().stream()
                .anyMatch(ref -> ref.endsWith("failure-classification.json")));
    }

    @Test
    void classifyAcceptWithNotes_producesNotedOutcome() throws Exception {
        writeCleanContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "ACCEPT_WITH_NOTES",
                        "summary", "Mostly good but unclear tests",
                        "out_of_scope_files", List.of("src/extra/File.java"))
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertTrue(result.outcome().startsWith("NOTED_"));
        assertEquals("NOTED_IMPLEMENTATION_BUG", result.outcome());
    }

    @Test
    void classifyAcceptWithNotesReviewNoiseUsesNonBlockingAction() throws Exception {
        writeCleanContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "ACCEPT_WITH_NOTES",
                        "summary", "Accepted with only manual runtime verification notes.",
                        "category_hint", "REVIEW_NOISE")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("NOTED_REVIEW_NOISE", result.outcome());
        assertEquals("REVIEW_NOISE", result.result().get("category"));
        assertEquals("Record non-blocking acceptance notes and continue", result.result().get("suggested_action"));
        assertFalse(Files.exists(tempDir.resolve(".agent/workstreams/ws-fc/recovery/current-failure.md")),
                "ACCEPT_WITH_NOTES review noise should not create a current failure summary");
    }

    @Test
    void classifyReadsAcceptWithNotesDecisionFromInputContractSidecar() throws Exception {
        writeMinimalContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var outputDir = tempDir.resolve(".agent/workstreams/ws-fc/runs/run-acceptance/outputs");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Implementation accepted with notes about contract quality."
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(outputDir.resolve("acceptance-codex-review.contract.json"));

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of(),
                List.of(".agent/workstreams/ws-fc/runs/run-acceptance/outputs/acceptance-codex-review.contract.json")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("NOTED_CONTRACT_AMBIGUITY", result.outcome());
        assertEquals("ACCEPT_WITH_NOTES", result.result().get("review_decision"));
        assertFalse(Files.exists(tempDir.resolve(".agent/workstreams/ws-fc/recovery/current-failure.md")),
                "ACCEPT_WITH_NOTES classification should not create a current failure summary");
    }

    @Test
    void classifyReadsContextGapsFromInputContractSidecar() throws Exception {
        writeCleanContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var outputDir = tempDir.resolve(".agent/workstreams/ws-fc/runs/run-acceptance/outputs");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "REJECT",
                  "summary": "Verifier cannot finish acceptance.",
                  "failure": {
                    "category_hint": "CONTEXT_INSUFFICIENT",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": ["Missing API usage artifact for treatment event status updates"],
                    "reason": "Required context artifact was absent"
                  }
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(outputDir.resolve("acceptance-codex-review.contract.json"));

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of(),
                List.of(".agent/workstreams/ws-fc/runs/run-acceptance/outputs/acceptance-codex-review.contract.json")
        ));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("REJECT_CONTEXT_INSUFFICIENT", result.outcome());
        assertEquals("CONTEXT_INSUFFICIENT", result.result().get("category"));
    }

    @Test
    void artifactWritten() throws Exception {
        writeMinimalContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var handler = new FailureClassifyHandler();
        handler.execute(taskContext(
                Map.of("decision", "REJECT", "summary", "Implementation has issues")
        ));

        var classificationPath = artifactsDir.resolve("failure-classification.json");
        assertTrue(Files.exists(classificationPath));

        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var json = mapper.readTree(classificationPath.toFile());
        assertTrue(json.has("category"));
        assertTrue(json.has("reasoning"));
        assertTrue(json.has("matched_rule"));
    }

    @Test
    void outcomeStringFormat() throws Exception {
        writeMinimalContract();
        writeCleanContract();
        // No git evidence → changedFileCount = -1

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "REJECT",
                        "summary", "minor stylistic issue",
                        "category_hint", "REVIEW_NOISE")
        ));

        // REVIEW_NOISE is now reserved for explicit non-blocking review noise signals.
        assertEquals("REJECT_REVIEW_NOISE", result.outcome());
        assertEquals("REVIEW_NOISE", result.result().get("category"));
    }

    @Test
    void toolLimitationWhenZeroChangedFiles() throws Exception {
        writeCleanContract();
        // Empty git evidence → 0 changed files
        writeGitEvidence("", null, null);

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "REJECT", "summary", "Nothing was changed")
        ));

        assertEquals("REJECT_TOOL_LIMITATION", result.outcome());
        assertEquals("TOOL_LIMITATION", result.result().get("category"));
    }

    @Test
    void resultMapContainsExpectedFields() throws Exception {
        writeMinimalContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "REJECT",
                        "summary", "Has issues",
                        "contract_gaps", List.of("gap-1"))
        ));

        assertNotNull(result.result().get("category"));
        assertNotNull(result.result().get("reasoning"));
        assertNotNull(result.result().get("suggested_action"));
        assertNotNull(result.result().get("matched_rule"));
        assertNotNull(result.result().get("review_decision"));
        assertNotNull(result.result().get("stage_outcome"));
        assertNotNull(result.result().get("classification_ref"));

        assertEquals("REJECT", result.result().get("review_decision"));
        assertEquals("REJECTED", result.result().get("stage_outcome"));
        assertTrue(((String) result.result().get("classification_ref"))
                .contains("failure-classification.json"));
    }

    @Test
    void classifyPreservesInsufficientEvidenceStageOutcomeFromAcceptanceSidecar() throws Exception {
        writeCleanContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var outputDir = tempDir.resolve(".agent/workstreams/ws-fc/runs/run-acceptance/outputs");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "REJECT",
                  "stage_outcome": "INSUFFICIENT_EVIDENCE",
                  "lifecycle_status": "INSUFFICIENT_EVIDENCE",
                  "summary": "Generated evidence does not prove the implementation.",
                  "failure": {
                    "category_hint": "insufficient_verification",
                    "missing_tests": ["manual walkthrough evidence"],
                    "reason": "Verification evidence is incomplete."
                  }
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(outputDir.resolve("acceptance-codex-review.contract.json"));

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of(),
                List.of(".agent/workstreams/ws-fc/runs/run-acceptance/outputs/acceptance-codex-review.contract.json")
        ));

        assertEquals("REJECT", result.result().get("review_decision"));
        assertEquals("INSUFFICIENT_EVIDENCE", result.result().get("stage_outcome"));
        assertEquals("REJECT_VERIFICATION_INSUFFICIENT", result.outcome());
        assertEquals("VERIFICATION_INSUFFICIENT", result.result().get("category"));
    }

    @Test
    void explicitMissingTestsRouteToSelfTestEvenWhenReviewerAlsoReportsContextGap() throws Exception {
        writeCleanContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var outputDir = tempDir.resolve(".agent/workstreams/ws-fc/runs/run-acceptance/outputs");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "node_id": "acceptance.codex_verifier_review",
                  "decision": "REJECT",
                  "summary": "Self-test evidence is not visible.",
                  "failure": {
                    "category_hint": "MISSING_TEST_EVIDENCE",
                    "missing_tests": ["node --test"],
                    "context_gaps": ["self-test report is absent from review context"],
                    "reason": "Verification evidence is incomplete."
                  }
                }
                """, StandardCharsets.UTF_8);
        publishSidecar(outputDir.resolve("acceptance-codex-review.contract.json"));

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of(),
                List.of(".agent/workstreams/ws-fc/runs/run-acceptance/outputs/acceptance-codex-review.contract.json")
        ));

        assertEquals("REJECT_VERIFICATION_INSUFFICIENT", result.outcome());
        assertEquals("VERIFICATION_INSUFFICIENT", result.result().get("category"));
        assertEquals(4, result.result().get("matched_rule"));
    }

    @Test
    void rejectContractGapWritesCalibrationCandidate() throws Exception {
        writeMinimalContract();
        writeGitEvidence("M src/A.java\n", null, null);

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "REJECT",
                        "summary", "Missing acceptance coverage",
                        "contract_gaps", List.of("A6 source/status preservation was not explicit"))
        ));

        var candidatesDir = tempDir.resolve(".agent/workstreams/ws-fc/calibration/candidates");
        assertTrue(Files.exists(candidatesDir));
        List<Path> candidateFiles;
        try (var stream = Files.list(candidatesDir)) {
            candidateFiles = stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .toList();
        }
        assertEquals(1, candidateFiles.size());

        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var json = mapper.readTree(candidateFiles.get(0).toFile());
        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("ws-fc", json.get("sourceWorkstreamId").asText());
        assertEquals("acceptance.classify_failure", json.get("sourceNodeId").asText());
        assertEquals("CONTRACT_GAP", json.get("failureCategory").asText());
        assertEquals("REJECT", json.get("reviewDecision").asText());
        assertEquals("CONTRACT_TEMPLATE", json.get("proposedUpdateType").asText());
        assertEquals("DRAFT", json.get("status").asText());
        assertTrue(result.outputRefs().stream()
                .anyMatch(ref -> ref.contains("/calibration/candidates/")));
        assertTrue(result.result().containsKey("calibration_candidate_ref"));
    }

    @Test
    void rejectWritesCurrentFailureSummary() throws Exception {
        writeCleanContract();
        writeGitEvidence("", null, null);

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "REJECT", "summary", "Nothing was changed")
        ));

        var summaryPath = tempDir.resolve(".agent/workstreams/ws-fc/recovery/current-failure.md");
        assertTrue(Files.exists(summaryPath));
        var summary = Files.readString(summaryPath, StandardCharsets.UTF_8);
        assertTrue(summary.contains("# Current Failure"));
        assertTrue(summary.contains("acceptance.classify_failure"));
        assertTrue(summary.contains("REJECT"));
        assertTrue(summary.contains("TOOL_LIMITATION"));
        assertTrue(summary.contains("failure-classification.json"));
        assertEquals(".agent/workstreams/ws-fc/recovery/current-failure.md",
                result.result().get("failure_summary_ref"));
    }

    // --- Helpers ---

    private void writeMinimalContract() throws Exception {
        var contract = new TaskContract(
                "tc-test", 1, Instant.parse("2026-05-30T10:00:00Z"),
                "Test objective",
                new TaskContract.Scope(
                        List.of(new TaskContract.ScopeEntry(
                                "src/", TaskContract.ScopeOperation.MODIFY)),
                        List.of()),
                List.of("AC1"),  // Only 1 AC → triggers CONTRACT_AMBIGUITY (Rule 2) when no gaps
                List.of(), List.of(), List.of(), List.of(),
                List.of("ambiguous requirement"),  // ambiguity notes → also Rule 2
                List.of(),
                ContractStatus.READY_FOR_REVIEW, 1,
                new TaskContract.Source("req.md", "test")
        );
        writeContract(contract);
    }

    private void writeCleanContract() throws Exception {
        var contract = new TaskContract(
                "tc-clean", 1, Instant.parse("2026-05-30T10:00:00Z"),
                "Clean objective",
                new TaskContract.Scope(
                        List.of(new TaskContract.ScopeEntry(
                                "src/", TaskContract.ScopeOperation.MODIFY)),
                        List.of()),
                List.of("AC1: must work", "AC2: no regressions"),  // 2 AC items — no ambiguity
                List.of(), List.of(), List.of(), List.of(),
                List.of(),  // no ambiguity notes
                List.of(),
                ContractStatus.READY_FOR_REVIEW, 1,
                new TaskContract.Source("req.md", "test")
        );
        writeContract(contract);
    }

    private void writeContract(TaskContract contract) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(artifactsDir.resolve("task-contract.json").toFile(), contract);
    }

    private void writeGitEvidence(String statusShort, String diffStat, String diffPatch)
            throws Exception {
        fixture.writeGitEvidence(statusShort, diffStat, diffPatch);
    }

    private void publishSidecar(Path sidecar) throws Exception {
        AgentOutputLineageFixture.completeAndPublish(tempDir, "ws-fc", sidecar);
    }

    private TaskHandler.TaskContext taskContext(Map<String, Object> instanceResult) {
        return taskContext(instanceResult, List.of());
    }

    private TaskHandler.TaskContext taskContext(Map<String, Object> instanceResult, List<String> inputRefs) {
        return new TaskHandler.TaskContext(
                tempDir,
                "ws-fc",
                new NodeDefinition("acceptance.classify_failure", NodeKind.TASK,
                        "acceptance.failure_classify", Map.of()),
                new NodeInstance(
                        "node-classify-001",
                        "acceptance.classify_failure",
                        NodeKind.TASK,
                        "acceptance.failure_classify",
                        NodeStatus.READY,
                        "stage-acceptance",
                        "iter-001",
                        inputRefs,
                        List.of(),
                        instanceResult,
                        "run-001",
                        Instant.now(),
                        null
                )
        );
    }
}
