package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
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
            return ParseResult.failed("AGENT_RESULT_MISSING", "Final ACP assistant message is empty.");
        }
        try {
            return ParseResult.valid(objectMapper.readValue(candidate, AgentResultEnvelope.class));
        } catch (Exception failure) {
            return ParseResult.failed("AGENT_RESULT_PARSE_FAILED", failure.getMessage());
        }
    }

    public record ParseResult(AgentResultEnvelope envelope, String errorCode, String message) {
        static ParseResult valid(AgentResultEnvelope envelope) {
            return new ParseResult(envelope, "", "");
        }

        static ParseResult failed(String errorCode, String message) {
            return new ParseResult(null, errorCode, message == null ? "" : message);
        }

        public boolean valid() {
            return envelope != null;
        }
    }
}
