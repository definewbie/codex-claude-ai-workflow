package dev.dual.adapter.acpx;

import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
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
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.NOT_NEEDED,
                acquisition.initialPresentation().disposition()
        );
        assertEquals(acquisition.initialPresentation().rawSha256(),
                acquisition.initialPresentation().candidateSha256());
    }

    @Test
    void exactSingleJsonFenceProducesStrictCandidateWithoutCorrection() throws Exception {
        var envelope = strictEnvelope("run-1");
        var raw = " \n```json\n" + envelope + "\n```\n ";
        var transport = new ScriptedTransport(validResult("request-1", raw, 0));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
        assertEquals(envelope, acquisition.finalCandidate());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.SINGLE_JSON_FENCE_REMOVED,
                acquisition.initialPresentation().disposition()
        );
        assertFalse(acquisition.initialPresentation().rawSha256()
                .equals(acquisition.initialPresentation().candidateSha256()));
        assertEquals(1, transport.calls);

        var crlfRaw = "```json\r\n" + envelope + "\r\n```";
        var crlfTransport = new ScriptedTransport(validResult("request-crlf", crlfRaw, 0));
        var crlfRequests = requests("crlf-session");
        var crlfAcquisition = new AcpxAgentResultAcquirer(crlfTransport).acquire(
                "claude", crlfRequests.initial(), crlfRequests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));
        assertTrue(crlfAcquisition.finalParse().valid());
        assertEquals(envelope, crlfAcquisition.finalCandidate());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.SINGLE_JSON_FENCE_REMOVED,
                crlfAcquisition.initialPresentation().disposition()
        );
        assertEquals(1, crlfTransport.calls);
    }

    @Test
    void exactJsonFenceOpeningWithCompletePayloadProducesStrictCandidateWithoutCorrection() throws Exception {
        var envelope = strictEnvelope("run-1");
        var raw = "```json\n" + envelope;
        var transport = new ScriptedTransport(validResult("request-1", raw, 0));
        var requests = requests("inline-fence-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
        assertTrue(acquisition.initialValidation().valid());
        assertEquals(envelope, acquisition.finalCandidate());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.SINGLE_JSON_FENCE_OPENING_REMOVED,
                acquisition.initialPresentation().disposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void missingOnlyRootObjectClosureIsRecoveredAndRevalidatedWithoutCorrection() throws Exception {
        var envelope = strictEnvelope("run-1");
        var missingRootClosure = envelope.substring(0, envelope.length() - 1);
        var transport = new ScriptedTransport(validResult("request-1", missingRootClosure, 0));
        var requests = requests("root-closure-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));
        var document = acquisition.toDocument(
                tempDir,
                tempDir.resolve("outputs/initial.json"),
                tempDir.resolve("outputs/correction.json"),
                null,
                null
        );

        assertTrue(acquisition.finalParse().valid());
        assertTrue(acquisition.initialValidation().valid());
        assertFalse(acquisition.correctionAttempted());
        assertEquals(envelope, acquisition.finalCandidate());
        assertEquals(AcpxAgentResultAcquirer.CorrectionReason.NONE, acquisition.correctionReason());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.ROOT_OBJECT_CLOSURE_RECOVERED,
                acquisition.initialPresentation().disposition()
        );
        assertEquals(1, transport.calls);
        assertFalse(document.occurrences().get(0).rawAssistantSha256()
                .equals(document.occurrences().get(0).candidateSha256()));
        assertEquals("ROOT_OBJECT_CLOSURE_RECOVERED",
                document.occurrences().get(0).presentationDisposition());
    }

    @Test
    void fencedPayloadCanRecoverOnlyItsMissingRootObjectClosure() throws Exception {
        var envelope = strictEnvelope("run-1");
        var missingRootClosure = envelope.substring(0, envelope.length() - 1);
        var raw = "Design result follows.\n```json\n" + missingRootClosure + "\n```\nDone.";
        var transport = new ScriptedTransport(validResult("request-1", raw, 0));
        var requests = requests("fenced-root-closure-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.finalParse().valid());
        assertEquals(envelope, acquisition.finalCandidate());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition
                        .SINGLE_FENCED_JSON_EXTRACTED_AND_ROOT_OBJECT_CLOSURE_RECOVERED,
                acquisition.initialPresentation().disposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void rootClosureRecoveryRejectsEveryDeeperOrAmbiguousEofShape() throws Exception {
        var envelope = strictEnvelope("run-1");
        var beforeResultClosure = envelope.substring(0, envelope.lastIndexOf("\n  }"));
        var rejected = List.of(
                beforeResultClosure,
                envelope.substring(0, envelope.length() - 1) + ",",
                "{\"schema_version\":1,\"value\":\"unterminated",
                "{\"schema_version\":1,\"value\":[1,2",
                "{\"schema_version\":1,\"value\":",
                "{\"schema_version\":1,\"value\":1e",
                "[1,2"
        );

        for (var candidate : rejected) {
            var transport = new ScriptedTransport(validResult("request-1", candidate, 0));
            var requests = requests("rejected-root-closure-" + Math.abs(candidate.hashCode()));
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                    "claude", requests.initial(), requests.correction(), "",
                    "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

            assertFalse(acquisition.finalParse().valid(), candidate);
            assertEquals(candidate, acquisition.finalCandidate(), candidate);
            assertEquals(
                    AcpxAgentResultAcquirer.PresentationDisposition.NOT_NEEDED,
                    acquisition.initialPresentation().disposition(),
                    candidate
            );
            assertEquals(1, transport.calls);
        }
    }

    @Test
    void proseAroundOneCompleteJsonFenceIsDeterministicallyExtracted() throws Exception {
        var envelope = strictEnvelope("run-1");
        var raw = "Design completed after reading the approved contract.\n\n```json\n"
                + envelope
                + "\n```\n\nThis is the implementation-ready artifact.";
        var transport = new ScriptedTransport(validResult("request-1", raw, 0));
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertFalse(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
        assertTrue(acquisition.initialValidation().valid());
        assertEquals(envelope, acquisition.finalCandidate());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.SINGLE_FENCED_JSON_EXTRACTED,
                acquisition.initialPresentation().disposition()
        );
        assertEquals(1, transport.calls);
    }

    @Test
    void ambiguousFencePresentationsAreNeverExtracted() throws Exception {
        var envelope = strictEnvelope("run-1");
        var cases = List.of(
                "```json\n" + envelope + "\n```\n```json\n" + envelope + "\n```",
                "```JSON\n" + envelope + "\n```",
                "```python\n" + envelope + "\n```",
                "```text\n" + envelope + "\n```",
                "```json\n\n```",
                "before\n```json\n" + envelope + "\n```\nafter\n```text\nextra\n```",
                "before ```json" + envelope + "```",
                "```json" + envelope + "``` after",
                "```json" + envelope + "```json" + envelope + "```",
                "before\n```json\n" + envelope,
                "```json\n" + envelope + "\n```text\nextra",
                "```" + envelope + "```"
        );

        for (var raw : cases) {
            var transport = new ScriptedTransport(validResult("request-1", raw, 0));
            var requests = requests("same-session-" + Math.abs(raw.hashCode()));
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                    "claude", requests.initial(), requests.correction(), "",
                    "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

            assertFalse(acquisition.finalParse().valid(), raw);
            assertEquals(raw, acquisition.finalCandidate());
            assertEquals(
                    AcpxAgentResultAcquirer.PresentationDisposition.REJECTED_AMBIGUOUS_PRESENTATION,
                    acquisition.initialPresentation().disposition(),
                    raw
            );
            assertEquals(1, transport.calls);
        }
    }

    @Test
    void normalizationCannotBypassStrictJsonOrSchemaValidation() throws Exception {
        var envelope = strictEnvelope("run-1");
        var strictFailures = List.of(
                envelope.replaceFirst("\\\"schema_version\\\" : 1,",
                        "\\\"schema_version\\\" : 1, \\\"schema_version\\\" : 1,"),
                envelope + " true",
                envelope.replaceFirst("\\\"schema_version\\\" : 1,",
                        "\\\"schema_version\\\" : 1, \\\"unknown\\\" : true,")
        );
        for (var invalid : strictFailures) {
            var transport = new ScriptedTransport(
                    validResult("request-1", "Result follows.\n```json\n" + invalid + "\n```\nEnd result.", 0));
            var requests = requests("strict-" + Math.abs(invalid.hashCode()));
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                    "claude", requests.initial(), requests.correction(), "",
                    "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

            assertFalse(acquisition.finalParse().valid(), invalid);
            assertEquals(
                    AcpxAgentResultAcquirer.PresentationDisposition.SINGLE_FENCED_JSON_EXTRACTED,
                    acquisition.initialPresentation().disposition()
            );
        }

        var wrongSchema = envelope.replace("\"schema_version\" : 1", "\"schema_version\" : 2");
        var transport = new ScriptedTransport(
                validResult("request-schema", "Result follows.\n```json\n" + wrongSchema + "\n```\nEnd result.", 0));
        var requests = requests("schema-invalid");
        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));
        assertTrue(acquisition.finalParse().valid());
        assertFalse(acquisition.initialValidation().valid());
        assertFalse(acquisition.correctionAttempted());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.SINGLE_FENCED_JSON_EXTRACTED,
                acquisition.initialPresentation().disposition()
        );
    }

    @Test
    void correctionOccurrenceUsesSamePresentationPipelineAndAuditFields() throws Exception {
        var envelope = strictEnvelope("run-1");
        var correctionRaw = "```json\n" + envelope + "\n```";
        var transport = new ScriptedTransport(
                validResult("request-1", "not-json", 0),
                validResult("request-2", correctionRaw, 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));
        var document = acquisition.toDocument(
                tempDir,
                tempDir.resolve("outputs/initial.json"),
                tempDir.resolve("outputs/correction.json"),
                null,
                null
        );

        assertTrue(acquisition.finalParse().valid());
        assertEquals(envelope, acquisition.finalCandidate());
        assertEquals(2, transport.calls);
        assertEquals(2, document.schemaVersion());
        assertEquals("NOT_NEEDED", document.occurrences().get(0).presentationDisposition());
        assertEquals("SINGLE_JSON_FENCE_REMOVED",
                document.occurrences().get(1).presentationDisposition());
        assertEquals(64, document.occurrences().get(1).rawAssistantSha256().length());
        assertEquals(64, document.occurrences().get(1).candidateSha256().length());
        assertFalse(document.occurrences().get(1).rawAssistantSha256()
                .equals(document.occurrences().get(1).candidateSha256()));
    }

    @Test
    void correctionOccurrenceUsesTheSameRootClosureRecovery() throws Exception {
        var envelope = strictEnvelope("run-1");
        var transport = new ScriptedTransport(
                validResult("request-1", "not-json", 0),
                validResult("request-2", envelope.substring(0, envelope.length() - 1), 0)
        );
        var requests = requests("root-closure-correction-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
        assertTrue(acquisition.correctionValidation().valid());
        assertEquals(envelope, acquisition.finalCandidate());
        assertEquals(
                AcpxAgentResultAcquirer.PresentationDisposition.ROOT_OBJECT_CLOSURE_RECOVERED,
                acquisition.correctionPresentation().disposition()
        );
        assertEquals(2, transport.calls);
    }

    @Test
    void prosePrefixedResultGetsExactlyOneSameSessionCorrection() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "I have completed the task.\n" + strictEnvelope("run-1"), 0),
                validResult("request-2", strictEnvelope("run-1"), 0)
        );
        var requests = requests("same-session");

        var selectedContract = correctionPrompt("run-1")
                + "\nREVISION_MODE_SENTINEL base_sha256=" + "a".repeat(64);
        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), selectedContract,
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
        var writtenCorrectionPrompt = Files.readString(requests.correction().promptPath());
        assertTrue(writtenCorrectionPrompt
                .contains("Return exactly one JSON object as the final assistant message."));
        assertTrue(writtenCorrectionPrompt.contains(
                "Allowed `result.failure.verification_remediation` values for this node: [NONE]"));
        assertTrue(writtenCorrectionPrompt.contains("REVISION_MODE_SENTINEL base_sha256=" + "a".repeat(64)),
                "bounded correction must reuse the exact contract selected before initial dispatch");
    }

    @Test
    void failedCorrectionDoesNotStartAThirdPrompt() throws Exception {
        var rawOnlySentinel = "UNIQUE_RAW_PAYLOAD_SENTINEL";
        var complete = strictEnvelope("run-1").replace("one short sentence", rawOnlySentinel);
        var deepTruncation = complete.substring(0, complete.lastIndexOf("\n  }"));
        var transport = new ScriptedTransport(
                validResult("request-1", deepTruncation, 0),
                validResult("request-2", deepTruncation, 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.correctionAttempted());
        assertFalse(acquisition.finalParse().valid());
        assertEquals("AGENT_RESULT_PARSE_FAILED", acquisition.finalParse().errorCode());
        assertEquals(2, transport.calls);
        var prompt = Files.readString(requests.correction().promptPath());
        assertTrue(prompt.contains("- kind: "));
        assertTrue(prompt.contains("Re-emit the complete AgentResultEnvelope"));
        assertFalse(prompt.contains(rawOnlySentinel), "correction prompt must not duplicate malformed raw output");
    }

    @Test
    void sequentialSyntaxAndUnknownFieldDefectsStillUseOnlyOneClosedContractCorrection() throws Exception {
        var complete = strictEnvelope("intake.claude_brainstorm", "run-1");
        var missingColon = complete.replaceFirst("\"text\" :", "\"text\" ");
        assertFalse(missingColon.equals(complete), "fixture must remove one clause text colon");

        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var corrected = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(complete);
        var responses = ((com.fasterxml.jackson.databind.node.ObjectNode) corrected.path("result"))
                .putArray("revision_responses");
        var response = responses.addObject();
        response.put("review_obligation_id", "CO-1");
        response.putArray("resolved_by_clause_ids").add("AC-1");
        response.put("note", "undeclared rationale");

        var transport = new ScriptedTransport(
                validResult("request-1", missingColon, 0),
                validResult("request-2", objectMapper.writeValueAsString(corrected), 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude",
                requests.initial(),
                requests.correction(),
                correctionPrompt("intake.claude_brainstorm", "run-1"),
                "ws-test",
                "run-1",
                ContractRegistry.forNode("intake.claude_brainstorm")
        );

        assertTrue(acquisition.correctionAttempted());
        assertFalse(acquisition.initialParse().valid());
        assertFalse(acquisition.finalParse().valid());
        assertTrue(acquisition.finalParse().message().contains("note"));
        assertEquals(2, transport.calls, "a second defect must not open a third ACP turn");
        var prompt = Files.readString(requests.correction().promptPath());
        assertTrue(prompt.contains("Remove every unsupported key"));
        assertTrue(prompt.contains(
                "result.revision_responses` item is a closed object with exactly these fields: "
                        + "[review_obligation_id, resolved_by_clause_ids]"));
        assertFalse(prompt.contains("undeclared rationale"), "correction prompt must not copy malformed output");
    }

    @Test
    void genericInvalidJsonCorrectionDoesNotClaimUnexpectedEndOfInput() throws Exception {
        var transport = new ScriptedTransport(
                validResult("request-1", "not-json", 0),
                validResult("request-2", strictEnvelope("run-1"), 0)
        );
        var requests = requests("same-session");

        var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                "claude", requests.initial(), requests.correction(), correctionPrompt("run-1"),
                "ws-test", "run-1", ContractRegistry.forNode("design.claude_design"));

        assertTrue(acquisition.correctionAttempted());
        assertTrue(acquisition.finalParse().valid());
        var prompt = Files.readString(requests.correction().promptPath());
        assertTrue(prompt.contains("kind: INVALID_JSON"));
        assertFalse(prompt.contains("UNEXPECTED_END_OF_INPUT"));
        assertFalse(prompt.contains("root JSON object was closed"));
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
        var completed = validResult("request-1", "prefix\n" + strictEnvelope("run-1"), 0);
        var transport = new ScriptedTransport(new AcpxRunResult(
                completed.startedAt(),
                completed.finishedAt(),
                completed.command(),
                completed.executionTimeline(),
                completed.usage(),
                completed.promptCompletion(),
                completed.processResult(),
                List.of(new AdapterSignal(
                        AdapterSignalType.RATE_LIMITED,
                        "HTTP 429 rate limit exceeded",
                        1,
                        "acpx_stream",
                        "",
                        false
                ))
        ));
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
        var completion = new AcpxPromptCompletion(requestId, stopReason, message);
        return new AcpxRunResult(
                now,
                now.plusSeconds(1),
                List.of("acpx", "claude"),
                List.of(),
                null,
                completion,
                new ProcessResult(
                        "acpx claude",
                        null,
                        exitCode,
                        exitCode == 0
                                ? ProcessUsability.EXITED_ZERO
                                : completion.completed()
                                        ? ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT
                                        : ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT,
                        1000,
                        "",
                        "",
                        false,
                        false
                ),
                List.of()
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
