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
    void historicalHhzlEnumErrorReachesCorrectionWithoutLosingItsLegalValuesOrBuyingAnotherAttempt() throws Exception {
        final byte[] bytes;
        try (var input = getClass().getResourceAsStream("/agent-result/hhzl-invalid-remediation.corrected.json")) {
            bytes = java.util.Objects.requireNonNull(input, "HHZL raw response fixture").readAllBytes();
        }
        assertEquals("b33d3a2ecbac2dd8f41ca3728157b5e6cd72be498813ba07be4e04e9c4139502",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        var historical = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        var ws = "ws-20260903T225544Z-HHZL";
        var run = "run-1788476621271";
        var contract = ContractRegistry.forNode("intake.codex_product_review");
        var contractPrompt = AgentResultPromptContract.render(ws, run, contract.nodeId(),
                "NEEDS_REVISION", contract.allowedDecisions());
        for (var agent : List.of("claude", "codex")) {
            var transport = new ScriptedTransport(validResult("request-1", historical, 0),
                    validResult("request-2", historical, 0));
            var requests = requests("hhzl-" + agent);
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire(agent,
                    requests.initial(), requests.correction(), contractPrompt, ws, run, contract);
            var prompt = Files.readString(requests.correction().promptPath());
            assertTrue(prompt.contains("kind: INVALID_ENUM_VALUE"), prompt);
            assertTrue(prompt.contains("code: AGENT_RESULT_PARSE_FAILED"));
            assertTrue(prompt.contains("field path: \"/result/failure/verification_remediation\""));
            assertTrue(prompt.contains("actual value: \"REVISE_PROPOSAL\""));
            assertTrue(prompt.contains("allowed values: [\"NONE\",\"EVIDENCE_COLLECTION\",\"TEST_CODE_CHANGE\"]"));
            assertFalse(prompt.contains("kind: INVALID_JSON"));
            assertFalse(prompt.contains("Cannot deserialize"), "do not copy Jackson exception prose");
            assertFalse(prompt.contains("候选任务契约"), "do not copy the rejected task response into diagnostics");
            assertTrue(acquisition.correctionAttempted());
            assertFalse(acquisition.finalParse().valid(), "an unchanged illegal enum must remain invalid");
            assertEquals(2, transport.calls, "the diagnostic must not create a third attempt");
            assertEquals(List.of("hhzl-" + agent, "hhzl-" + agent), transport.sessions);
        }
    }

    @Test
    void historicalHhzlProseThenIllegalEnumDoesNotBypassGrammarOrBuyAThirdAttempt() throws Exception {
        final byte[] initial;
        final byte[] corrected;
        try (var input = getClass().getResourceAsStream("/agent-result/hhzl-invalid-remediation.json");
             var correction = getClass().getResourceAsStream("/agent-result/hhzl-invalid-remediation.corrected.json")) {
            initial = java.util.Objects.requireNonNull(input).readAllBytes();
            corrected = java.util.Objects.requireNonNull(correction).readAllBytes();
        }
        assertEquals("72956cadc7da1033068fe58b77ffcbac7f1ddcd874439ad1b8cdc7428568bbb9",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(initial)));
        var transport = new ScriptedTransport(
                validResult("initial", new String(initial, java.nio.charset.StandardCharsets.UTF_8), 0),
                validResult("correction", new String(corrected, java.nio.charset.StandardCharsets.UTF_8), 0));
        var requests = requests("hhzl-original-sequence");
        var node = "intake.codex_product_review";
        var acquisition = new AcpxAgentResultAcquirer(transport).acquire("codex", requests.initial(), requests.correction(),
                AgentResultPromptContract.render("ws-20260903T225544Z-HHZL", "run-1788476621271", node,
                        "NEEDS_REVISION", ContractRegistry.forNode(node).allowedDecisions()),
                "ws-20260903T225544Z-HHZL", "run-1788476621271", ContractRegistry.forNode(node));
        assertEquals("INVALID_JSON", acquisition.initialParse().diagnostic().kind().name());
        assertEquals("INVALID_ENUM_VALUE", acquisition.finalParse().diagnostic().kind().name());
        assertFalse(acquisition.finalParse().valid());
        assertTrue(Files.readString(requests.correction().promptPath()).contains("kind: INVALID_JSON"));
        assertEquals(2, transport.calls, "the original sequence exhausts its single correction; no rescue attempt");
    }

    @Test
    void enumCorrectionRemainsSubjectToIdentityAndNodeContractValidation() throws Exception {
        var node = "intake.codex_product_review";
        var valid = strictEnvelope(node, "run-1");
        var invalid = valid.replace("\"verification_remediation\" : \"NONE\"",
                "\"verification_remediation\" : \"INVALID\"");
        assertFalse(valid.equals(invalid), "fixture must contain the enum");
        var wrongIdentity = valid.replace("\"run_id\" : \"run-1\"", "\"run_id\" : \"stale-run\"");
        var wrongNodeValue = valid.replace("\"verification_remediation\" : \"NONE\"",
                "\"verification_remediation\" : \"EVIDENCE_COLLECTION\"");
        for (var second : List.of(valid, wrongIdentity, wrongNodeValue)) {
            var transport = new ScriptedTransport(validResult("initial", invalid, 0), validResult("correction", second, 0));
            var requests = requests("enum-validation");
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire("codex", requests.initial(),
                    requests.correction(), correctionPrompt(node, "run-1"), "ws-test", "run-1", ContractRegistry.forNode(node));
            assertTrue(acquisition.finalParse().valid());
            assertEquals(second.equals(valid), acquisition.correctionValidation().valid());
            assertEquals(2, transport.calls);
            assertTrue(Files.readString(requests.correction().promptPath())
                    .contains("node contract below may restrict those values further"));
        }
    }

    @Test
    void enumActualValueIsJsonEscapedInsideTheCorrectionDiagnostic() throws Exception {
        var node = "intake.codex_product_review";
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var raw = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(strictEnvelope(node, "run-1"));
        var value = "BAD\"\n- kind: ACCEPTED\\value";
        ((com.fasterxml.jackson.databind.node.ObjectNode) raw.path("result").path("failure"))
                .put("verification_remediation", value);
        var transport = new ScriptedTransport(validResult("initial", raw.toString(), 0), validResult("correction", raw.toString(), 0));
        var requests = requests("enum-escaping");
        new AcpxAgentResultAcquirer(transport).acquire("codex", requests.initial(), requests.correction(),
                correctionPrompt(node, "run-1"), "ws-test", "run-1", ContractRegistry.forNode(node));
        var prompt = Files.readString(requests.correction().promptPath());
        assertTrue(prompt.contains("actual value: " + mapper.writeValueAsString(value)));
        assertFalse(prompt.contains("\n- kind: ACCEPTED"));
        assertEquals(2, transport.calls);
    }

    @Test
    void planCorrectionRetainsFixedMethodAndDetailResponsibilitiesWithoutExtraAttempts() throws Exception {
        var valid = strictEnvelope("plan.claude_plan", "run-1");
        var malformed = "NON_JSON_PREFIX " + valid;
        for (var second : List.of(valid, malformed)) {
            var transport = new ScriptedTransport(validResult("request-1", malformed, 0),
                    validResult("request-2", second, 0));
            var requests = requests("plan-method-correction");
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire("claude",
                    requests.initial(), requests.correction(), correctionPrompt("plan.claude_plan", "run-1"),
                    "ws-test", "run-1", ContractRegistry.forNode("plan.claude_plan"));
            var prompt = Files.readString(requests.correction().promptPath());
            assertTrue(prompt.contains("copy its `method` exactly as the decoded JSON string"));
            assertTrue(prompt.contains("in `plan.validation_plan`, labeled with approved criterion IDs"));
            assertTrue(prompt.contains("never weaken a required exit check"));
            assertTrue(prompt.contains("This is the only contract-correction attempt"));
            assertEquals(second.equals(valid), acquisition.finalParse().valid());
            assertEquals(2, transport.calls);
        }
    }

    @Test
    void unknownRevisionResponseFieldReachesOnlyOneCorrectionWithExactLegalKeys() throws Exception {
        var complete = strictEnvelope("intake.claude_brainstorm", "run-1");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(complete);
        var response = ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.path("result"))
                .putArray("revision_responses").addObject();
        response.put("review_obligation_id", "CO-1");
        response.putArray("resolved_by_clause_ids").add("AC-1");
        response.put("response", "UNSUPPORTED_VALUE_MUST_NOT_BE_COPIED");
        var invalid = mapper.writeValueAsString(envelope);
        response.remove("response");
        var valid = mapper.writeValueAsString(envelope);
        for (var second : java.util.List.of(valid, invalid)) {
            var transport = new ScriptedTransport(validResult("request-1", invalid, 0),
                    validResult("request-2", second, 0));
            var requests = requests("closed-revision-session");
            var context = new dev.dual.runtime.handler.AgentResultPromptContract.ProposalEmissionContext(
                    dev.dual.runtime.handler.AgentResultPromptContract.EmissionMode.REVISION_PATCH,
                    "base", "a".repeat(64), java.util.List.of()).withGoalReproposal(true);
            var contract = dev.dual.runtime.handler.AgentResultPromptContract.render(
                    "ws-test", "run-1", "intake.claude_brainstorm", "READY_FOR_PRODUCT_REVIEW",
                    java.util.Set.of("READY_FOR_PRODUCT_REVIEW"), context);
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                    "claude", requests.initial(), requests.correction(), contract,
                    "ws-test", "run-1", ContractRegistry.forNode("intake.claude_brainstorm"));
            assertFalse(acquisition.initialParse().valid());
            assertEquals("response", acquisition.initialParse().diagnostic().fieldName());
            assertEquals(java.util.List.of("resolved_by_clause_ids", "review_obligation_id"),
                    acquisition.initialParse().diagnostic().allowedFields());
            assertEquals(second.equals(valid), acquisition.finalParse().valid());
            assertEquals(2, transport.calls, "a second unknown key cannot buy another correction");
            var prompt = Files.readString(requests.correction().promptPath());
            assertTrue(prompt.contains("kind: UNKNOWN_FIELD"));
            assertTrue(prompt.contains("unknown field: \"response\""));
            assertTrue(prompt.contains("allowed fields in this object: [\"resolved_by_clause_ids\",\"review_obligation_id\"]"));
            assertTrue(prompt.contains("result.revision_responses` item is a closed object"));
            assertFalse(prompt.contains("UNSUPPORTED_VALUE_MUST_NOT_BE_COPIED"));
            assertEquals(dev.dual.runtime.handler.AgentResultEnvelopeParser.ParseFailureKind.UNKNOWN_FIELD,
                    acquisition.initialParse().diagnostic().kind());
        }
    }

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
        assertTrue(writtenCorrectionPrompt.contains("design_result"));
        assertTrue(writtenCorrectionPrompt.contains("planned_files"));
        assertFalse(writtenCorrectionPrompt.contains("verification_remediation"));
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
    void planScopeShapeNeverStartsCorrectionOrWritesACorrectionPrompt() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (var edits : List.of(List.of("../escape"), List.of("src/a", "src/a"), List.<String>of())) {
            var candidate = (com.fasterxml.jackson.databind.node.ObjectNode)
                    json.readTree(strictEnvelope("plan.claude_plan", "run-1"));
            var plan = (com.fasterxml.jackson.databind.node.ObjectNode) candidate.at("/result/plan");
            var files = plan.putArray("edit_files");
            edits.forEach(files::add);
            var transport = new ScriptedTransport(validResult("request-1", candidate.toString(), 0));
            var requests = requests("plan-shape");
            var contract = correctionPrompt("plan.claude_plan", "run-1");
            assertTrue(contract.contains("later EXECUTION approval"), "plan proposes files before exact execution approval");
            assertFalse(contract.contains("within the approved TaskContract scope"), "GOAL is not prior file authorization");
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire(
                    "claude", requests.initial(), requests.correction(), contract,
                    "ws-test", "run-1", ContractRegistry.forNode("plan.claude_plan"));
            assertFalse(acquisition.initialValidation().valid());
            assertEquals(AgentResultEnvelopeValidator.CorrectionDisposition.NOT_REPAIRABLE,
                    acquisition.initialValidation().correctionDisposition());
            assertFalse(acquisition.correctionAttempted());
            assertEquals(1, transport.calls);
            assertFalse(Files.exists(requests.correction().promptPath()));
        }
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

    @Test
    void malformedAuthoredReferencesUseOneSameSessionSchemaCorrectionForBothAuthors() throws Exception {
        var id = "sha256:" + "a".repeat(64) + "#human-note:0";
        for (var node : List.of("design.claude_design", "plan.claude_plan")) {
            var valid = authoredEnvelope(node, id);
            var invalid = authoredEnvelope(node, id + ".old");
            var transport = new ScriptedTransport(validResult("initial", invalid, 0), validResult("correction", valid, 0));
            var requests = requests("authored-same-session");
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire("claude", requests.initial(), requests.correction(),
                    correctionPrompt(node, "run-1"), "ws-test", "run-1", ContractRegistry.forNode(node), List.of(id));
            assertEquals(2, transport.calls);
            assertEquals(List.of("authored-same-session", "authored-same-session"), transport.sessions);
            assertEquals(AcpxAgentResultAcquirer.CorrectionReason.SCHEMA_FAILURE, acquisition.correctionReason());
            assertTrue(acquisition.initialValidation().correctsAuthoredReferences());
            assertTrue(acquisition.correctionValidation().valid(), acquisition.correctionValidation().errors().toString());
            assertEquals(invalid, acquisition.initialCandidate());
            assertEquals(valid, acquisition.finalCandidate());
            var prompt = Files.readString(requests.correction().promptPath());
            assertTrue(prompt.contains("result.revision_responses.0.obligation_id"));
            assertTrue(prompt.contains("Keep every already valid ID"));
            assertTrue(prompt.contains("Do not re-run the task, use tools"));
        }
    }

    @Test
    void failedReferenceCorrectionNeverStartsAThirdPrompt() throws Exception {
        var id = "sha256:" + "a".repeat(64) + "#human-note:0";
        var stale = "sha256:" + "b".repeat(64) + "#human-note:0";
        for (var node : List.of("design.claude_design", "plan.claude_plan")) {
            for (var returned : List.of(authoredEnvelope(node, id + ".old"), authoredEnvelope(node, stale),
                    authoredEnvelope(node, id).replace("Frozen response", "Changed response"))) {
                var transport = new ScriptedTransport(validResult("initial", authoredEnvelope(node, id + ".old"), 0),
                        validResult("correction", returned, 0));
                var requests = requests("single-correction");
                var acquisition = new AcpxAgentResultAcquirer(transport).acquire("claude", requests.initial(), requests.correction(),
                        correctionPrompt(node, "run-1"), "ws-test", "run-1", ContractRegistry.forNode(node), List.of(id));
                assertEquals(2, transport.calls);
                assertFalse(acquisition.correctionValidation().valid(), returned);
                assertTrue(acquisition.correctionAttempted());
            }
        }
    }

    @Test
    void noActiveSnapshotAndNonAuthoredSiblingsDoNotAcquireReferenceCorrection() throws Exception {
        var id = "sha256:" + "a".repeat(64) + "#human-note:0";
        for (var node : List.of("design.claude_design", "plan.claude_plan", "implement.claude_implement", "implement.claude_self_test_report")) {
            var author = node.startsWith("design") || node.startsWith("plan");
            var transport = new ScriptedTransport(validResult("initial", author
                    ? authoredEnvelope(node, id + ".old") : strictEnvelope(node, "run-1"), 0));
            var requests = requests("no-reference-correction");
            var acquisition = new AcpxAgentResultAcquirer(transport).acquire("claude", requests.initial(), requests.correction(),
                    correctionPrompt(node, "run-1"), "ws-test", "run-1", ContractRegistry.forNode(node),
                    author ? List.of() : List.of(id));
            assertEquals(1, transport.calls);
            assertFalse(acquisition.correctionAttempted());
            assertFalse(acquisition.initialValidation().correctsAuthoredReferences());
        }
    }

    private static String authoredEnvelope(String node, String id) throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(strictEnvelope(node, "run-1"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.get("result")).putArray("revision_responses")
                .addObject().put("obligation_id", id).put("disposition", "ADDRESSED").put("evidence", "Frozen response");
        return envelope.toString();
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
        public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws java.io.IOException {
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
