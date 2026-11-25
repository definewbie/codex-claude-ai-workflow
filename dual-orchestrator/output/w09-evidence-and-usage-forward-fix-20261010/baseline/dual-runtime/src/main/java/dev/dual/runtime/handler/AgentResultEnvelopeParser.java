package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** The only JSON grammar owner for final agent workflow results. */
public final class AgentResultEnvelopeParser {
    private final JsonMapper objectMapper;

    public AgentResultEnvelopeParser() {
        this.objectMapper = JsonMapper.builder(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build())
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    public ParseResult parse(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return ParseResult.failed("AGENT_RESULT_MISSING", "Final ACP assistant message is empty.", null);
        }
        try {
            return ParseResult.valid(objectMapper.readValue(candidate, AgentResultEnvelope.class));
        } catch (JsonEOFException failure) {
            return ParseResult.failed(
                    "AGENT_RESULT_PARSE_FAILED",
                    failure.getMessage(),
                    ParseDiagnostic.at(ParseFailureKind.UNEXPECTED_END_OF_INPUT, failure.getLocation())
            );
        } catch (Exception failure) {
            return ParseResult.failed(
                    "AGENT_RESULT_PARSE_FAILED",
                    failure.getMessage(),
                    ParseDiagnostic.at(ParseFailureKind.INVALID_JSON, null)
            );
        }
    }

    public enum ParseFailureKind {
        UNEXPECTED_END_OF_INPUT,
        INVALID_JSON
    }

    public record ParseDiagnostic(ParseFailureKind kind, long offset, int line, int column) {
        private static ParseDiagnostic at(ParseFailureKind kind, JsonLocation location) {
            return new ParseDiagnostic(
                    kind,
                    location == null ? -1L : location.getCharOffset(),
                    location == null ? -1 : location.getLineNr(),
                    location == null ? -1 : location.getColumnNr()
            );
        }
    }

    public record ParseResult(
            AgentResultEnvelope envelope,
            String errorCode,
            String message,
            ParseDiagnostic diagnostic
    ) {
        static ParseResult valid(AgentResultEnvelope envelope) {
            return new ParseResult(envelope, "", "", null);
        }

        static ParseResult failed(String errorCode, String message, ParseDiagnostic diagnostic) {
            return new ParseResult(null, errorCode, message == null ? "" : message, diagnostic);
        }

        public boolean valid() {
            return envelope != null;
        }
    }
}
