package dev.dual.core.execution;

public record AdapterSignal(
        AdapterSignalType type,
        String message,
        long observedSequence,
        String source,
        String sessionId,
        boolean superseded,
        OperationRef operationRef
) {
    public AdapterSignal {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        message = message == null ? "" : message;
        source = source == null ? "" : source;
        sessionId = sessionId == null ? "" : sessionId;
        if (observedSequence < 0) {
            throw new IllegalArgumentException("observedSequence must be non-negative");
        }
    }

    public AdapterSignal(AdapterSignalType type, String message, long sequence, String source, String session, boolean superseded) {
        this(type, message, sequence, source, session, superseded, null);
    }

    /** Raw request/response preserved by the adapter; the enclosing fixed observation binds run/execution. */
    public record OperationRef(String sessionId, String promptId, String requestId, String toolCallId,
                               long requestSequence, long decisionSequence, String kind,
                               String parametersSha256, String requestJson, String requestSha256,
                               String decisionJson, String decisionSha256) {
        public boolean complete() {
            return nonblank(sessionId) && nonblank(promptId) && nonblank(requestId) && nonblank(toolCallId)
                    && nonblank(kind) && requestSequence >= 0 && decisionSequence > requestSequence
                    && hash(parametersSha256) && hash(requestSha256) && hash(decisionSha256)
                    && nonblank(requestJson) && nonblank(decisionJson)
                    && requestSha256.equals(digest(requestJson)) && decisionSha256.equals(digest(decisionJson));
        }
        private static boolean nonblank(String s) { return s != null && !s.isBlank(); }
        private static boolean hash(String s) { return s != null && s.matches("[0-9a-f]{64}"); }
    }

    /** Canonical JSON parameter identity, preserving array order and every unknown parameter. */
    public static String parametersHash(com.fasterxml.jackson.databind.JsonNode value) {
        if (value == null || value.isNull()) return "";
        return digest(canonical(value).toString());
    }
    private static com.fasterxml.jackson.databind.JsonNode canonical(com.fasterxml.jackson.databind.JsonNode value) {
        if (value.isObject()) {
            var node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            var names = new java.util.TreeSet<String>(); value.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> node.set(name, canonical(value.get(name)))); return node;
        }
        if (value.isArray()) {
            var node = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            value.forEach(item -> node.add(canonical(item))); return node;
        }
        return value.deepCopy();
    }
    public static String digest(String text) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public AdapterSignal(AdapterSignalType type, String message, long observedSequence) {
        this(type, message, observedSequence, "", "", false);
    }

    public static AdapterSignal of(AdapterSignalType type) {
        return new AdapterSignal(type, "", 0);
    }
}
