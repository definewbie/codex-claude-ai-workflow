package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResultEnvelopeParserTest {
    private final AgentResultEnvelopeParser parser = new AgentResultEnvelopeParser();
    private final AgentResultEnvelopeValidator validator = new AgentResultEnvelopeValidator();
    private final NodeOutputContract contract = new NodeOutputContract(
            "design.claude_design",
            Set.of("decision", "summary"),
            Set.of("READY_FOR_ARCH_REVIEW"),
            Set.of(),
            Set.of("agent_output_contract")
    );

    @Test
    void invalidEnumCarriesTypedValuePathAndDefinitionAllowlistWithoutAcceptingIt() {
        var parsed = parser.parse(acceptanceRejectEnvelope("REVISE_PROPOSAL"));
        assertFalse(parsed.valid());
        assertEquals("AGENT_RESULT_PARSE_FAILED", parsed.errorCode());
        assertEquals(AgentResultEnvelopeParser.ParseFailureKind.INVALID_ENUM_VALUE, parsed.diagnostic().kind());
        assertEquals("/result/failure/verification_remediation", parsed.diagnostic().fieldPath());
        assertEquals("REVISE_PROPOSAL", parsed.diagnostic().actualValue());
        assertEquals(java.util.List.of("NONE", "EVIDENCE_COLLECTION", "TEST_CODE_CHANGE"),
                parsed.diagnostic().allowedValues());
        assertTrue(parsed.diagnostic().line() > 0);
        assertTrue(parsed.diagnostic().column() > 0);
        for (var value : parsed.diagnostic().allowedValues()) {
            assertTrue(parser.parse(acceptanceRejectEnvelope(value)).valid());
        }
    }

    @Test
    void siblingEnumsKeepNestedArrayPathsAndTheirOwnAllowlists() {
        var cases = java.util.List.of(
                new EnumCase("design_result", "\"design\":{\"planned_files\":[{\"operation\":\"INVALID\"}]}",
                        "/result/design/planned_files/0/operation", java.util.List.of("MODIFY", "CREATE")),
                new EnumCase("design_result", "\"revision_responses\":[{\"disposition\":\"INVALID\"}]",
                        "/result/revision_responses/0/disposition", java.util.List.of("ADDRESSED", "NOT_ADDRESSED")),
                new EnumCase("task_contract_revision_patch", "\"obligation_patches\":[{\"clause_changes\":[{\"operation\":\"INVALID\"}]}]",
                        "/result/obligation_patches/0/clause_changes/0/operation", java.util.List.of("ADD", "REPLACE")),
                new EnumCase("task_contract_revision_patch", "\"obligation_patches\":[{\"clause_changes\":[{\"section\":\"INVALID\"}]}]",
                        "/result/obligation_patches/0/clause_changes/0/section",
                        java.util.List.of("ACCEPTANCE_CRITERIA", "TEST_REQUIREMENTS", "CONSTRAINTS", "NON_GOALS", "RISK_AREAS")));
        for (var test : cases) {
            var json = "{\"result\":{\"artifact_type\":\"" + test.artifact() + "\"," + test.fields() + "}}";
            var parsed = parser.parse(json);
            assertFalse(parsed.valid(), json);
            assertEquals(AgentResultEnvelopeParser.ParseFailureKind.INVALID_ENUM_VALUE, parsed.diagnostic().kind(), json);
            assertEquals(test.path(), parsed.diagnostic().fieldPath());
            assertEquals("INVALID", parsed.diagnostic().actualValue());
            assertEquals(test.allowed(), parsed.diagnostic().allowedValues());
            assertTrue(parser.parse(json.replace("INVALID", test.allowed().getFirst())).valid());
        }
    }

    @Test
    void nonEnumTypeFailureDoesNotAcquireAnEnumAllowlist() {
        var parsed = parser.parse(validEnvelope().replace("\"schema_version\": 1", "\"schema_version\": \"invalid\""));
        assertFalse(parsed.valid());
        assertEquals(AgentResultEnvelopeParser.ParseFailureKind.INVALID_JSON, parsed.diagnostic().kind());
        assertTrue(parsed.diagnostic().allowedValues().isEmpty());
    }

    private record EnumCase(String artifact, String fields, String path, java.util.List<String> allowed) {}

    @Test
    void unknownFieldIsDiagnosedWithoutRelaxingTheClosedGrammar() {
        var parsed = new AgentResultEnvelopeParser().parse(validEnvelope().replace(
                "\"summary\": \"ready\"", "\"summary\": \"ready\", \"response\": \"extra\""));
        assertFalse(parsed.valid());
        assertEquals("UNKNOWN_FIELD", parsed.diagnostic().kind().name());
        assertEquals("response", parsed.diagnostic().fieldName());
        assertTrue(parsed.diagnostic().allowedFields().contains("summary"));
        assertFalse(parsed.diagnostic().allowedFields().contains("response"));
        assertEquals("AGENT_RESULT_PARSE_FAILED", parsed.errorCode());
    }

    @Test
    void parsesAndValidatesStrictTypedEnvelope() {
        var parsed = parser.parse(validEnvelope());

        assertTrue(parsed.valid());
        assertTrue(parsed.envelope().result() instanceof AgentResultEnvelope.AgentOutputResult);
        assertTrue(validator.validate(parsed.envelope(), "ws-1", "run-1", contract).valid());
    }

    @Test
    void rejectsDuplicateKeys() {
        var parsed = parser.parse(validEnvelope().replace(
                "\"schema_version\": 1,",
                "\"schema_version\": 1,\"schema_version\": 1,"));

        assertFalse(parsed.valid());
        assertEquals("AGENT_RESULT_PARSE_FAILED", parsed.errorCode());
        assertNotEquals(
                AgentResultEnvelopeParser.ParseFailureKind.UNEXPECTED_END_OF_INPUT,
                parsed.diagnostic().kind()
        );
    }

    @Test
    void classifiesUnclosedRootAsUnexpectedEndOfInputWithoutRelaxingParser() {
        var complete = validEnvelope().stripTrailing();
        var missingRootClose = complete.substring(0, complete.length() - 1);

        var parsed = parser.parse(missingRootClose);

        assertFalse(parsed.valid());
        assertEquals("AGENT_RESULT_PARSE_FAILED", parsed.errorCode());
        assertEquals(
                AgentResultEnvelopeParser.ParseFailureKind.UNEXPECTED_END_OF_INPUT,
                parsed.diagnostic().kind()
        );
        assertTrue(parsed.diagnostic().line() > 0);
        assertTrue(parsed.diagnostic().column() > 0);
        assertTrue(parser.parse(missingRootClose + "}").valid(), "strict parser must accept the complete original object");
    }

    @Test
    void rejectsTrailingProseAndMarkdownFence() {
        var trailing = parser.parse(validEnvelope() + "\nDone.");
        var fenced = parser.parse("```json\n" + validEnvelope() + "\n```");

        assertFalse(trailing.valid());
        assertFalse(fenced.valid());
        assertNotEquals(
                AgentResultEnvelopeParser.ParseFailureKind.UNEXPECTED_END_OF_INPUT,
                trailing.diagnostic().kind()
        );
        assertNotEquals(
                AgentResultEnvelopeParser.ParseFailureKind.UNEXPECTED_END_OF_INPUT,
                fenced.diagnostic().kind()
        );
    }

    @Test
    void rejectsUnknownFields() {
        var parsed = parser.parse(validEnvelope().replace(
                "\"summary\": \"ready\",",
                "\"summary\": \"ready\",\"unexpected\": true,"));

        assertFalse(parsed.valid());
        assertNotEquals(
                AgentResultEnvelopeParser.ParseFailureKind.UNEXPECTED_END_OF_INPUT,
                parsed.diagnostic().kind()
        );
    }

    @Test
    void rejectsStaleLineageAndInvalidDecision() {
        var parsed = parser.parse(validEnvelope()
                .replace("\"run_id\": \"run-1\"", "\"run_id\": \"old-run\"")
                .replace("READY_FOR_ARCH_REVIEW", "BLOCKED"));

        var result = validator.validate(parsed.envelope(), "ws-1", "run-1", contract);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("run_id")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("decision")));
    }

    @Test
    void rejectsBlankLineageEvenWhenCurrentExecutionIdentityIsAlsoBlank() {
        var parsed = parser.parse(validEnvelope()
                .replace("\"workstream_id\": \"ws-1\"", "\"workstream_id\": \"\"")
                .replace("\"run_id\": \"run-1\"", "\"run_id\": \"\""));

        var result = validator.validate(parsed.envelope(), "", "", contract);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("workstream_id is required"));
        assertTrue(result.errors().contains("run_id is required"));
    }

    @Test
    void rejectsMissingTypedPayloadFieldWithoutRelaxingJsonParser() {
        var parsed = parser.parse(validEnvelope().replace("\"checks\": [],", ""));

        assertTrue(parsed.valid(), "syntax parser should return the typed candidate");
        var result = validator.validate(parsed.envelope(), "ws-1", "run-1", contract);
        assertFalse(result.valid());
        assertTrue(result.errors().contains("result.checks is required"));
        assertTrue(result.schemaCorrectionAllowed());
    }

    @Test
    void distinguishesMissingSchemaVersionFromExplicitUnsupportedVersion() {
        var missing = parser.parse(validEnvelope().replace("\"schema_version\": 1,", ""));
        var unsupported = parser.parse(validEnvelope().replace("\"schema_version\": 1", "\"schema_version\": 2"));

        var missingResult = validator.validate(missing.envelope(), "ws-1", "run-1", contract);
        var unsupportedResult = validator.validate(unsupported.envelope(), "ws-1", "run-1", contract);

        assertTrue(missing.valid());
        assertTrue(missingResult.schemaCorrectionAllowed());
        assertTrue(unsupported.valid());
        assertEquals(
                AgentResultEnvelopeValidator.CorrectionDisposition.NOT_REPAIRABLE,
                unsupportedResult.correctionDisposition()
        );
    }

    @Test
    void rejectsBlankSummaryAtTheTypedContractBoundary() {
        var parsed = parser.parse(validEnvelope().replace("\"summary\": \"ready\"", "\"summary\": \"  \""));

        assertTrue(parsed.valid());
        var result = validator.validate(parsed.envelope(), "ws-1", "run-1", contract);
        assertFalse(result.valid());
        assertTrue(result.errors().contains("result.summary is required"));
        assertEquals(
                AgentResultEnvelopeValidator.CorrectionDisposition.NOT_REPAIRABLE,
                result.correctionDisposition()
        );
    }

    @Test
    void mixedSchemaAndIdentityFailureIsNotRepairable() {
        var parsed = parser.parse(validEnvelope()
                .replace("\"checks\": [],", "")
                .replace("\"run_id\": \"run-1\"", "\"run_id\": \"stale-run\""));

        var result = validator.validate(parsed.envelope(), "ws-1", "run-1", contract);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("result.checks is required"));
        assertTrue(result.errors().contains("run_id does not match current execution"));
        assertEquals(
                AgentResultEnvelopeValidator.CorrectionDisposition.NOT_REPAIRABLE,
                result.correctionDisposition()
        );
    }

    @Test
    void rejectsWrongPrimitiveType() {
        var parsed = parser.parse(validEnvelope().replace("\"blocking\": false", "\"blocking\": \"false\""));

        assertFalse(parsed.valid());
    }

    @Test
    void rejectWithMissingTestsRequiresTypedVerificationRemediation() {
        var acceptanceContract = new NodeOutputContract(
                "acceptance.codex_verifier_review",
                Set.of("decision", "summary"),
                Set.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT"),
                Set.of(),
                Set.of("acceptance_review")
        );
        var missingRemediation = parser.parse(acceptanceRejectEnvelope("NONE"));
        var codeChange = parser.parse(acceptanceRejectEnvelope("TEST_CODE_CHANGE"));
        var unsupportedCodeChange = parser.parse(acceptanceRejectEnvelope("TEST_CODE_CHANGE")
                .replace("\"missing_tests\": [\"equal-date cycle-number tie-breaker\"]", "\"missing_tests\": []"));

        var rejected = validator.validate(
                missingRemediation.envelope(), "ws-1", "run-1", acceptanceContract);
        var accepted = validator.validate(
                codeChange.envelope(), "ws-1", "run-1", acceptanceContract);
        var unsupported = validator.validate(
                unsupportedCodeChange.envelope(), "ws-1", "run-1", acceptanceContract);

        assertFalse(rejected.valid());
        assertTrue(rejected.errors().contains(
                "result.failure.verification_remediation is required when REJECT lists missing_tests"));
        assertTrue(accepted.valid());
        assertFalse(unsupported.valid());
        assertTrue(unsupported.errors().contains(
                "result.failure.verification_remediation is only allowed when REJECT lists missing_tests"));
    }

    private static String acceptanceRejectEnvelope(String remediation) {
        return """
                {
                  "schema_version": 1,
                  "workstream_id": "ws-1",
                  "run_id": "run-1",
                  "node_id": "acceptance.codex_verifier_review",
                  "result": {
                    "artifact_type": "acceptance_review",
                    "decision": "REJECT",
                    "summary": "A regression test is missing.",
                    "lifecycle_status": "REJECTED",
                    "blocking": true,
                    "checks": ["reviewed implementation"],
                    "evidence": ["implementation behavior is correct"],
                    "missing_evidence": [],
                    "must_fix": ["add the regression test"],
                    "acceptance_notes": [],
                    "open_risks": [],
                    "changed_files": ["backend/tests/test_chemo.py"],
                    "verification_summary": "A test source change is required.",
                    "verification_checks": [],
                    "failure": {
                      "category_hint": "IMPLEMENTATION_BUG",
                      "verification_remediation": "%s",
                      "contract_gaps": [],
                      "out_of_scope_files": [],
                      "missing_tests": ["equal-date cycle-number tie-breaker"],
                      "context_gaps": [],
                      "reason": "Missing regression test"
                    }
                  }
                }
                """.formatted(remediation);
    }

    private static String validEnvelope() {
        return """
                {
                  "schema_version": 1,
                  "workstream_id": "ws-1",
                  "run_id": "run-1",
                  "node_id": "design.claude_design",
                  "result": {
                    "artifact_type": "agent_output_contract",
                    "decision": "READY_FOR_ARCH_REVIEW",
                    "summary": "ready",
                    "lifecycle_status": "READY",
                    "blocking": false,
                    "checks": [],
                    "evidence": [],
                    "missing_evidence": [],
                    "must_fix": [],
                    "acceptance_notes": [],
                    "open_risks": [],
                    "changed_files": [],
                    "verification_summary": "not run",
                    "failure": {
                      "category_hint": "",
                      "contract_gaps": [],
                      "out_of_scope_files": [],
                      "missing_tests": [],
                      "context_gaps": [],
                      "reason": ""
                    },
                    "reread_reasons": []
                  }
                }
                """;
    }
}
