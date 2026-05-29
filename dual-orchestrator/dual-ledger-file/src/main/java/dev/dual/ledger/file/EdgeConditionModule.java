package dev.dual.ledger.file;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.dual.core.workflow.EdgeCondition;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Jackson module for serializing/deserializing {@link EdgeCondition} polymorphic instances.
 * Uses the {@code type()} and {@code params()} methods on EdgeCondition so the module
 * stays decoupled from concrete implementation classes.
 */
public final class EdgeConditionModule extends SimpleModule {

    public EdgeConditionModule() {
        super("EdgeConditionModule");
        addSerializer(EdgeCondition.class, new Serializer());
        addDeserializer(EdgeCondition.class, new Deserializer());
    }

    static final class Serializer extends StdSerializer<EdgeCondition> {
        Serializer() { super(EdgeCondition.class); }

        @Override
        public void serialize(EdgeCondition value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            // Unconditional → write null (consumers treat missing/null condition as unconditional)
            if ("unconditional".equals(value.type())) {
                gen.writeNull();
                return;
            }
            gen.writeStartObject();
            gen.writeStringField("type", value.type());
            gen.writeObjectFieldStart("params");
            for (var entry : value.params().entrySet()) {
                gen.writeObjectField(entry.getKey(), entry.getValue());
            }
            gen.writeEndObject();
            gen.writeEndObject();
        }
    }

    static final class Deserializer extends StdDeserializer<EdgeCondition> {
        Deserializer() { super(EdgeCondition.class); }

        @Override
        public EdgeCondition deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node == null || node.isNull()) {
                return EdgeCondition.unconditional();
            }
            String type = node.has("type") ? node.get("type").asText() : "unconditional";
            Map<String, Object> params = new LinkedHashMap<>();
            if (node.has("params") && node.get("params").isObject()) {
                var fields = node.get("params").fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    params.put(entry.getKey(), textValue(entry.getValue()));
                }
            }
            return EdgeCondition.fromTypeAndParams(type, params);
        }

        private static Object textValue(JsonNode node) {
            if (node.isTextual()) return node.asText();
            if (node.isNumber()) return node.numberValue();
            if (node.isBoolean()) return node.asBoolean();
            return node.asText();
        }
    }
}
