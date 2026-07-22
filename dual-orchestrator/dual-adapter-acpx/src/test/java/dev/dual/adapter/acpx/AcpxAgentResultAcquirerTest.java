package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.AgentResultPromptContract;
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
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"));

        assertFalse(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
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
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"));

        assertTrue(acquisition.correctionAttempted());
        assertFalse(acquisition.initialParse().valid());
        assertTrue(acquisition.finalParse().valid());
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
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"));

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
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"));

        assertTrue(acquisition.finalParse().valid());
        assertFalse(acquisition.correctionAttempted());
        assertEquals(1, transport.calls);
    }

    @Test
    void unusableProcessDoesNotStartCorrection() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "prefix\n" + strictEnvelope("run-1"), 1));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"));

        assertFalse(acquisition.correctionAttempted());
        assertEquals(1, transport.calls);
    }

    @Test
    void typedProviderFailureDoesNotStartCorrectionEvenWhenProcessExitedZero() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "prefix\n" + strictEnvelope("run-1"), 0));
        transport.stderrText = "[error] RUNTIME: HTTP 429 rate limit exceeded\n";
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"));

        assertFalse(acquisition.correctionAttempted());
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
        var contract = ContractRegistry.forNode("design.claude_design");
        return AgentResultPromptContract.render(
                "ws-test",
                runId,
                "design.claude_design",
                "READY_FOR_ARCH_REVIEW",
                contract.allowedDecisions()
        );
    }

    private static String strictEnvelope(String runId) {
        var rendered = correctionPrompt(runId);
        var start = rendered.lastIndexOf("{\n  \"schema_version\"");
        if (start < 0) {
            throw new AssertionError("strict AgentResultEnvelope example missing");
        }
        return rendered.substring(start).strip();
    }

    private static AcpxRunResult validResult(String requestId, String message, int exitCode) {
        var now = Instant.parse("2026-07-22T00:00:00Z");
        return new AcpxRunResult(
                exitCode,
                now,
                now.plusSeconds(1),
                List.of("acpx", "claude"),
                List.of(),
                null,
                new AcpxPromptCompletion(requestId, "end_turn", message)
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
