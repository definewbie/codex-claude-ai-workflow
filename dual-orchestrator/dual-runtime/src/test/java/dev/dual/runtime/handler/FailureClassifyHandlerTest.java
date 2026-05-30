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
        // No git evidence at all — changedFileCount = -1, no contract gaps, no scope files,
        // no keywords in summary, no ambiguity → arbiter rules: Rule 6 REVIEW_NOISE
        // if contract is clean, otherwise Rule 7 IMPLEMENTATION_BUG.
        // But: contract has < 2 AC items triggers CONTRACT_AMBIGUITY (Rule 2).
        // Use a clean contract to get predictable behavior.
        writeCleanContract();
        // No git evidence → changedFileCount = -1

        var handler = new FailureClassifyHandler();
        var result = handler.execute(taskContext(
                Map.of("decision", "REJECT", "summary", "Nothing was done")
        ));

        // With clean contract (2 AC items, no ambiguity), no gaps, no out-of-scope,
        // no keywords, changedFileCount=-1: Rule 6 → REVIEW_NOISE
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
        assertNotNull(result.result().get("classification_ref"));

        assertEquals("REJECT", result.result().get("review_decision"));
        assertTrue(((String) result.result().get("classification_ref"))
                .contains("failure-classification.json"));
    }

    // --- Helpers ---

    private void writeMinimalContract() throws Exception {
        var contract = new TaskContract(
                "tc-test", 1, Instant.parse("2026-05-30T10:00:00Z"),
                "Test objective",
                new TaskContract.Scope(List.of("src/"), List.of()),
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
                new TaskContract.Scope(List.of("src/"), List.of()),
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

    private TaskHandler.TaskContext taskContext(Map<String, Object> instanceResult) {
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
                        List.of(),
                        List.of(),
                        instanceResult,
                        "run-001",
                        Instant.now(),
                        null
                )
        );
    }
}
