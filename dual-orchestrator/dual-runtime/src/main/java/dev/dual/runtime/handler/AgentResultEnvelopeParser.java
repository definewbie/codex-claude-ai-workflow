package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.util.Arrays;
import java.util.List;

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
        } catch (UnrecognizedPropertyException failure) {
            var known = failure.getKnownPropertyIds();
            return ParseResult.failed("AGENT_RESULT_PARSE_FAILED", failure.getMessage(),
                    ParseDiagnostic.unknownField(failure.getLocation(), failure.getPropertyName(),
                            known == null ? List.of() : known.stream().map(Object::toString).sorted().toList()));
        } catch (InvalidFormatException failure) {
            var target = failure.getTargetType();
            return ParseResult.failed("AGENT_RESULT_PARSE_FAILED", failure.getMessage(),
                    target != null && target.isEnum()
                            ? ParseDiagnostic.invalidEnum(failure)
                            : ParseDiagnostic.at(ParseFailureKind.INVALID_JSON, failure.getLocation()));
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
        UNKNOWN_FIELD,
        INVALID_ENUM_VALUE,
        INVALID_JSON
    }

    public record ParseDiagnostic(ParseFailureKind kind, long offset, int line, int column,
                                  String fieldName, List<String> allowedFields,
                                  String fieldPath, Object actualValue, List<String> allowedValues) {
        public ParseDiagnostic {
            fieldName = fieldName == null ? "" : fieldName;
            allowedFields = allowedFields == null ? List.of() : List.copyOf(allowedFields);
            fieldPath = fieldPath == null ? "" : fieldPath;
            allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        }
        private static ParseDiagnostic unknownField(JsonLocation location, String field, List<String> allowed) {
            var at = at(ParseFailureKind.UNKNOWN_FIELD, location);
            return new ParseDiagnostic(at.kind(), at.offset(), at.line(), at.column(), field, allowed,
                    "", null, List.of());
        }
        private static ParseDiagnostic invalidEnum(InvalidFormatException failure) {
            var at = at(ParseFailureKind.INVALID_ENUM_VALUE, failure.getLocation());
            var allowed = Arrays.stream(failure.getTargetType().getEnumConstants())
                    .map(value -> ((Enum<?>) value).name()).toList();
            return new ParseDiagnostic(at.kind(), at.offset(), at.line(), at.column(), "", List.of(),
                    jsonPointer(failure.getPath()), failure.getValue(), allowed);
        }
        private static String jsonPointer(List<JsonMappingException.Reference> path) {
            var pointer = new StringBuilder();
            for (var reference : path) {
                if (reference.getFieldName() != null) {
                    pointer.append('/').append(reference.getFieldName().replace("~", "~0").replace("/", "~1"));
                } else if (reference.getIndex() >= 0) {
                    pointer.append('/').append(reference.getIndex());
                }
            }
            return pointer.toString();
        }
        private static ParseDiagnostic at(ParseFailureKind kind, JsonLocation location) {
            return new ParseDiagnostic(
                    kind,
                    location == null ? -1L : location.getCharOffset(),
                    location == null ? -1 : location.getLineNr(),
                    location == null ? -1 : location.getColumnNr(),
                    "", List.of(), "", null, List.of()
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
