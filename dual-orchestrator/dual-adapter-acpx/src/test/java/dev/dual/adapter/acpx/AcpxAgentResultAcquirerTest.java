package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.AgentResultPromptContract;
import dev.dual.runtime.handler.AgentResultEnvelopeValidator;
import dev.dual.runtime.handler.ContractRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxAgentResultAcquirerTest {
    @TempDir
    Path tempDir;

    @Test
    void validFirstResultDoesNotStartCorrection() throws Exception {
        var transport = new ScriptedTransport(validResult("request-1", strictEnvelope("run-1"), 0));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.NONE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.NOT_NEEDED,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
        assertFalse(Files.exists(requests.correction().promptPath()));
    }

    @Test
    void prosePrefixedResultGetsExactlyOneSameSessionCorrection() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "I have completed the task.\n" + strictEnvelope("run-1"), 0),
                validResult("request-2", strictEnvelope("run-1"), 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.correctionAttempted());
        assertFalse(acquisition.initialParse().valid());
        assertTrue(acquisition.finalParse().valid());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.PARSE_FAILURE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.ATTEMPTED,
                acquisition.correctionDisposition()
        );
        assertEquals(2, transport.calls);
        assertEquals(List.of("same-session", "same-session"), transport.sessions);
        assertTrue(Files.readString(requests.correction().promptPath())
                .contains("Return exactly one JSON object as the final assistant message."));
    }

    @Test
    void failedCorrectionDoesNotStartAThirdPrompt() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "prefix\n" + strictEnvelope("run-1"), 0),
                validResult("request-2", "still not json", 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.correctionAttempted());
        assertFalse(acquisition.finalParse().valid());
        assertEquals("AGENT_RESULT_PARSE_FAILED", acquisition.finalParse().errorCode());
        assertEquals(2, transport.calls);
    }

    @Test
    void parseableEnvelopeWithWrongLineageIsLeftForRuntimeValidation() throws Exception {
        var transport = new ScriptedTransport(validResult("request-1", strictEnvelope("wrong-run"), 0));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.finalParse().valid());
        assertFalse(acquisition.correctionAttempted());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.NONE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.NOT_NEEDED,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void unusableProcessDoesNotStartCorrection() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "prefix\n" + strictEnvelope("run-1"), 1));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.PARSE_FAILURE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.SKIPPED_PROCESS_NON_ZERO,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void incompletePromptDoesNotStartCorrection() throws Exception {
        var transport = new ScriptedTransport(
                result("request-1", "prefix\n" + strictEnvelope("run-1"), 0, "cancelled"));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.PARSE_FAILURE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.SKIPPED_NOT_COMPLETED,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void missingResultFromIncompletePromptRecordsSkippedNotCompleted() throws Exception {
        var transport = new ScriptedTransport(result("", "", 0, ""));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals("AGENT_RESULT_MISSING", acquisition.initialParse().errorCode());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.NONE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.SKIPPED_NOT_COMPLETED,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void nonzeroProcessWithOtherwiseValidResultRecordsSkippedProcessNonZero() throws Exception {
        var transport = new ScriptedTransport(validResult("request-1", strictEnvelope("run-1"), 1));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.NONE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.SKIPPED_PROCESS_NON_ZERO,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void missingCorrectionPromptRecordsUnavailableWithoutStartingCorrection() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "prefix\n" + strictEnvelope("run-1"), 0));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), "",
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.PARSE_FAILURE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.SKIPPED_CORRECTION_UNAVAILABLE,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void typedProviderFailureDoesNotStartCorrectionEvenWhenProcessExitedZero() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "prefix\n" + strictEnvelope("run-1"), 0));
        transport.stderrText = "[error] RUNTIME: HTTP 429 rate limit exceeded\n";
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.PARSE_FAILURE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.CorrectionDisposition.SKIPPED_PROVIDER_FAILURE,
                acquisition.correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void parseableSelfTestMissingNullableSchemaFieldGetsOneSameSessionCorrection() throws Exception {
        var valid = strictEnvelope("implement.claude_self_test_report", "run-1");
        var invalid = valid.replace("    \"missing_evidence\" : [ ],\n", "");
        assertFalse(invalid.equals(valid), "fixture must remove missing_evidence");
        var transport = new ScriptedTransport(
                validResult("request-1", invalid, 0),
                validResult("request-2", valid, 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude",
                requests.initial(),
                requests.correction(),
                correctionPrompt("implement.claude_self_test_report", "run-1"),
                "ws-test",
                "run-1",
                ContractRegistry.forNode("implement.claude_self_test_report")
        );

        assertTrue(acquisition.initialParse().valid());
        assertEquals(
                AgentResultEnvelopeValidator.CorrectionDisposition.SCHEMA_REPAIRABLE,
                acquisition.initialValidation().correctionDisposition()
        );
        assertTrue(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
        assertTrue(acquisition.correctionValidation().valid());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.SCHEMA_FAILURE, acquisition.correctionReason());
        assertEquals(2, transport.calls);
        assertEquals(List.of("same-session", "same-session"), transport.sessions);
        var prompt = Files.readString(requests.correction().promptPath());
        assertTrue(prompt.contains("Typed correction reason: SCHEMA_FAILURE"));
        assertTrue(prompt.contains("result.missing_evidence is required"));
    }

    @Test
    void schemaCorrectionFailureDoesNotStartThirdPrompt() throws Exception {
        var valid = strictEnvelope("implement.claude_self_test_report", "run-1");
        var invalid = valid.replace("    \"missing_evidence\" : [ ],\n", "");
        var transport = new ScriptedTransport(
                validResult("request-1", invalid, 0),
                validResult("request-2", invalid, 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude",
                requests.initial(),
                requests.correction(),
                correctionPrompt("implement.claude_self_test_report", "run-1"),
                "ws-test",
                "run-1",
                ContractRegistry.forNode("implement.claude_self_test_report")
        );

        assertTrue(acquisition.correctionAttempted());
        assertFalse(acquisition.correctionValidation().valid());
        assertEquals(2, transport.calls);
    }

    @Test
    void schemaCorrectionCannotChangeExistingSemanticFields() throws Exception {
        var valid = strictEnvelope("design.claude_design", "run-1");
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var invalidNode = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(valid);
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalidNode.path("result")).remove("checks");
        var invalid = objectMapper.writeValueAsString(invalidNode);
        var correctedNode = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(valid);
        var correctedResult = (com.fasterxml.jackson.databind.node.ObjectNode) correctedNode.path("result");
        correctedResult.putArray("checks");
        correctedResult.put("summary", "a different conclusion");
        var correctedWithChangedSummary = objectMapper.writeValueAsString(correctedNode);
        var transport = new ScriptedTransport(
                validResult("request-1", invalid, 0),
                validResult("request-2", correctedWithChangedSummary, 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.correctionAttempted());
        assertFalse(acquisition.correctionValidation().valid());
        assertTrue(acquisition.correctionValidation().errors().contains(
                "schema correction changed fields outside the repairable schema targets"
        ));
        assertEquals(2, transport.calls);
    }

    @Test
    void illegalDecisionDoesNotStartSchemaCorrection() throws Exception {
        var invalid = strictEnvelope("design.claude_design", "run-1")
                .replace("READY_FOR_ARCH_REVIEW", "BLOCKED");
        var transport = new ScriptedTransport(validResult("request-1", invalid, 0));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(
                AgentResultEnvelopeValidator.CorrectionDisposition.NOT_REPAIRABLE,
                acquisition.initialValidation().correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void explicitUnsupportedSchemaVersionDoesNotStartCorrection() throws Exception {
        var invalid = strictEnvelope("design.claude_design", "run-1")
                .replace("\"schema_version\" : 1", "\"schema_version\" : 2");
        var transport = new ScriptedTransport(validResult("request-1", invalid, 0));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(
                AgentResultEnvelopeValidator.CorrectionDisposition.NOT_REPAIRABLE,
                acquisition.initialValidation().correctionDisposition()
        );
        assertEquals(1, transport.calls);
    }

    private Requests requests(String session) throws Exception {
        var initialPrompt = tempDir.resolve("prompts/design.md");
        var correctionPrompt = tempDir.resolve("prompts/design.contract-correction.md");
        Files.createDirectories(initialPrompt.getParent());
        Files.writeString(initialPrompt, "initial prompt");
        return new Requests(
                request(session, initialPrompt, "initial"),
                request(session, correctionPrompt, "correction")
        );
    }

    private AcpxRunRequest request(String session, Path prompt, String suffix) {
        return new AcpxRunRequest(
                tempDir,
                session,
                prompt,
                tempDir.resolve("outputs/" + suffix + ".transcript.log"),
                tempDir.resolve("outputs/" + suffix + ".stderr.log"),
                AcpxPermissionProfile.observe()
        );
    }

    private static String correctionPrompt(String runId) {
        return correctionPrompt("design.claude_design", runId);
    }

    private static String correctionPrompt(String nodeId, String runId) {
        var contract = ContractRegistry.forNode(nodeId);
        return AgentResultPromptContract.render(
                "ws-test",
                runId,
                nodeId,
                contract.allowedDecisions().stream().sorted().findFirst().orElseThrow(),
                contract.allowedDecisions()
        );
    }

    private static String strictEnvelope(String runId) {
        return strictEnvelope("design.claude_design", runId);
    }

    private static String strictEnvelope(String nodeId, String runId) {
        var rendered = correctionPrompt(nodeId, runId);
        var start = rendered.lastIndexOf("{\n  \"schema_version\"");
        if (start < 0) {
            throw new AssertionError("strict AgentResultEnvelope example missing");
        }
        return rendered.substring(start).strip();
    }

    private static AcpxRunResult validResult(String requestId, String message, int exitCode) {
        return result(requestId, message, exitCode, "end_turn");
    }

    private static AcpxRunResult result(String requestId, String message, int exitCode, String stopReason) {
        var now = Instant.parse("2026-07-22T00:00:00Z");
        return new AcpxRunResult(
                exitCode,
                now,
                now.plusSeconds(1),
                List.of("acpx", "claude"),
                List.of(),
                null,
                new AcpxPromptCompletion(requestId, stopReason, message)
        );
    }

    private record Requests(AcpxRunRequest initial, AcpxRunRequest correction) {
    }

    private static final class ScriptedTransport extends AcpxAgentTransport {
        private final ArrayDeque<AcpxRunResult> results;
        private final List<String> sessions = new ArrayList<>();
        private int calls;
        private String stderrText = "";

        private ScriptedTransport(AcpxRunResult... results) {
            this.results = new ArrayDeque<>(List.of(results));
        }

        @Override
        public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException {
            return next(request);
        }

        @Override
        AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request) throws java.io.IOException {
            return next(request);
        }

        private AcpxRunResult next(AcpxRunRequest request) throws java.io.IOException {
            calls++;
            sessions.add(request.sessionName());
            Files.createDirectories(request.stdoutPath().getParent());
            Files.writeString(request.stdoutPath(), "");
            Files.writeString(request.stderrPath(), stderrText);
            return results.removeFirst();
        }
    }
}
