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
