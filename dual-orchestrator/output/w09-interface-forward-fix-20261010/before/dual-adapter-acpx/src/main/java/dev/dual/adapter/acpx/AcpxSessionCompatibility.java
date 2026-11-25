package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;

/** Last recorded ACP handshake, not proof of current engine bytes or arbitrary tool capability. */
final class AcpxSessionCompatibility {
    private static final ObjectMapper JSON = new ObjectMapper();
    record Assessment(boolean compatible, Map<String, Object> observation) {}

    static Assessment assess(String output, Path cwd, String session) {
        var fact = new LinkedHashMap<String, Object>();
        fact.put("source", "acpx.sessions.show");
        fact.put("native_version", "UNKNOWN");
        String failure = "invalid session metadata";
        try {
            var doc = JSON.readTree(output);
            if (doc == null || !doc.isObject()) throw new IllegalArgumentException(failure);
            for (var key : List.of("schema", "acpxRecordId", "acpSessionId", "agentSessionId", "cwd", "name")) {
                var field = doc.path(key);
                if (field.isTextual()) fact.put(key, field.asText());
            }
            if (doc.path("protocolVersion").isIntegralNumber()) fact.put("protocolVersion", doc.path("protocolVersion").intValue());
            fact.put("closed", doc.path("closed").asBoolean(false));
            var version = doc.path("agentInfo").path("version");
            if (version.isTextual() && !version.asText().isBlank() && version.asText().length() <= 120)
                fact.put("native_version", version.asText());
            var capabilities = new LinkedHashMap<String, Object>();
            var caps = doc.path("agentCapabilities");
            if (caps.path("loadSession").isBoolean()) capabilities.put("loadSession", caps.path("loadSession").booleanValue());
            var prompt = new LinkedHashMap<String, Object>();
            for (var key : List.of("image", "audio", "embeddedContext"))
                if (caps.path("promptCapabilities").path(key).isBoolean())
                    prompt.put(key, caps.path("promptCapabilities").path(key).booleanValue());
            capabilities.put("promptCapabilities", prompt);
            fact.put("agentCapabilities", capabilities);
            failure = failure(doc, cwd, session);
        } catch (Exception invalid) {
            // Never retain the full record: it may contain chat history, config or credentials.
        }
        fact.put("compatible", failure == null);
        if (failure != null) fact.put("failure", failure);
        return new Assessment(failure == null, Collections.unmodifiableMap(fact));
    }

    private static String failure(JsonNode doc, Path cwd, String session) throws Exception {
        if (!"acpx.session.v1".equals(doc.path("schema").asText())) return "unsupported acpx session metadata schema";
        for (var key : List.of("acpxRecordId", "acpSessionId", "agentSessionId"))
            if (!doc.path(key).isTextual() || doc.path(key).asText().isBlank()) return "missing session identity: " + key;
        if (!session.equals(doc.path("name").asText())) return "session name differs from invocation";
        if (!doc.path("cwd").isTextual() || !Path.of(doc.path("cwd").asText()).toRealPath().equals(cwd.toRealPath()))
            return "session workspace differs from invocation";
        if (!doc.path("closed").isBoolean() || doc.path("closed").booleanValue()) return "session is closed or has unknown open state";
        if (!doc.path("protocolVersion").isIntegralNumber() || doc.path("protocolVersion").intValue() != 1)
            return "required ACP protocol version 1 is unavailable";
        return null;
    }
}
