package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter-local wire view of one acpx session-stream NDJSON line.
 *
 * <p>This is the single owner of the top-level acpx JSON-RPC grammar. Consumers
 * receive typed request, result, notification, or error fields and must not
 * parse the raw NDJSON line again.</p>
 */
record AcpxStreamWireLine(
        Kind kind,
        String requestId,
        String method,
        Request request,
        Result result,
        Notification notification,
        Error error
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    enum Kind {
        JSONRPC_ERROR,
        TYPED_FAILURE_EVENT,
        JSONRPC_OK,
        JSONRPC_REQUEST,
        NOTIFICATION,
        MALFORMED
    }

    record Request(
            String sessionId,
            String promptText,
            String permissionToolCallId,
            String permissionToolKind,
            String permissionTitle,
            String permissionCommand,
            List<String> permissionOptions,
            java.util.Map<String,String> permissionOptionKinds,
            JsonNode permissionRawInput,
            String permissionRequestJson
    ) {
        Request {
            promptText = promptText == null ? "" : promptText;
            permissionToolCallId = permissionToolCallId == null ? "" : permissionToolCallId;
            permissionToolKind = permissionToolKind == null ? "" : permissionToolKind;
            permissionTitle = permissionTitle == null ? "" : permissionTitle;
            permissionCommand = permissionCommand == null ? "" : permissionCommand;
            permissionOptions = permissionOptions == null ? List.of() : List.copyOf(permissionOptions);
        }
    }

    record Result(String sessionId, String stopReason, Usage usage, String permissionOutcome, String permissionOptionId, String responseJson) {
        Result {
            sessionId = sessionId == null ? "" : sessionId;
            stopReason = stopReason == null ? "" : stopReason;
            permissionOutcome = permissionOutcome == null ? "" : permissionOutcome;
            permissionOptionId = permissionOptionId == null ? "" : permissionOptionId;
        }
    }

    record Notification(
            String sessionId,
            String updateKind,
            String messageId,
            String contentType,
            String text,
            String toolCallId,
            String toolName,
            UsageUpdate usageUpdate,
            JsonNode toolUpdate,
            JsonNode nativePermission
    ) {
        Notification {
            sessionId = sessionId == null ? "" : sessionId;
            updateKind = updateKind == null ? "" : updateKind;
            messageId = messageId == null ? "" : messageId;
            contentType = contentType == null ? "" : contentType;
            text = text == null ? "" : text;
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolName = toolName == null ? "" : toolName;
        }
    }

    record Error(Integer code, String message, String dataMessage, String dataDetails) {
        Error {
            message = message == null ? "" : message;
            dataMessage = dataMessage == null ? "" : dataMessage;
            dataDetails = dataDetails == null ? "" : dataDetails;
        }
    }

    record Usage(
            long inputTokens,
            long outputTokens,
            long cachedReadTokens,
            long cachedWriteTokens,
            long totalTokens
    ) {
    }

    record UsageUpdate(long used, BigDecimal costAmount, String costCurrency) {
        UsageUpdate {
            costCurrency = costCurrency == null ? "" : costCurrency;
        }
    }

    static AcpxStreamWireLine parse(String line) {
        if (line == null) {
            return malformed();
        }
        var trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return malformed();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(trimmed);
        } catch (Exception ignored) {
            return malformed();
        }
        if (node == null || !node.isObject()) {
            return malformed();
        }

        return fromNode(node, trimmed);
    }

    /**
     * Human-readable stdout mixes pretty-printed model text with complete RPC lines.
     * Only successfully parsed RPC-shaped objects are frames here; the strict
     * session NDJSON entry point above retains its existing MALFORMED semantics.
     */
    static java.util.Optional<AcpxStreamWireLine> transcriptFrame(String line) {
        if (line == null || !line.stripLeading().startsWith("{")) return java.util.Optional.empty();
        JsonNode node;
        try {
            node = MAPPER.reader().with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(line);
        } catch (Exception readableFragment) {
            return java.util.Optional.empty();
        }
        if (node == null || !node.isObject()
                || !(node.has("jsonrpc") || node.has("method")
                    || (node.has("id") && (node.has("result") || node.has("error")))
                    || isTypedFailure(text(node.get("type")), "", node.get("error"))))
            return java.util.Optional.empty();
        // A recognized frame with invalid structure must not disappear into text
        // or let a complete historical response wash it into success.
        if ((node.has("jsonrpc") && (!node.get("jsonrpc").isTextual() || !"2.0".equals(node.get("jsonrpc").asText())))
                || (node.has("id") && !(node.get("id").isIntegralNumber()
                    || (node.get("id").isTextual() && !node.get("id").asText().isBlank())))
                || (node.has("method") && (!node.get("method").isTextual() || node.get("method").asText().isBlank()))
                || (node.has("params") && !node.get("params").isObject())
                || (node.has("result") && (!node.get("result").isObject() || !node.has("id")
                    || node.has("error") || node.has("method")))
                || (node.has("error") && (!node.get("error").isObject() || !node.has("id"))))
            return java.util.Optional.of(malformed());
        return java.util.Optional.of(fromNode(node, line.strip()));
    }

    private static AcpxStreamWireLine fromNode(JsonNode node, String trimmed) {
        var requestId = id(node.get("id"));
        var method = text(node.get("method"));
        var errorNode = node.get("error");
        if (errorNode != null && errorNode.isObject()) {
            return new AcpxStreamWireLine(
                    Kind.JSONRPC_ERROR,
                    requestId,
                    method,
                    null,
                    null,
                    null,
                    error(errorNode)
            );
        }

        var type = text(node.get("type"));
        if (isTypedFailure(type, method, errorNode)) {
            var failureType = !type.isBlank() ? type : method;
            return new AcpxStreamWireLine(
                    Kind.TYPED_FAILURE_EVENT,
                    requestId,
                    methodForFailureType(failureType),
                    null,
                    null,
                    null,
                    new Error(null, textualError(node, errorNode), "", "")
            );
        }

        if (node.has("result")) {
            var resultNode = node.path("result");
            return new AcpxStreamWireLine(
                    Kind.JSONRPC_OK,
                    requestId,
                    method,
                    null,
                    new Result(
                            text(resultNode.get("sessionId")),
                            text(resultNode.get("stopReason")),
                            usage(resultNode.get("usage")),
                            text(resultNode.at("/outcome/outcome")),
                            text(resultNode.at("/outcome/optionId")),
                            trimmed
                    ),
                    null,
                    null
            );
        }

        if (!method.isBlank() && !requestId.isBlank()) {
            return new AcpxStreamWireLine(
                    Kind.JSONRPC_REQUEST,
                    requestId,
                    method,
                    new Request(
                            text(node.at("/params/sessionId")),
                            text(node.at("/params/prompt/0/text")),
                            text(node.at("/params/toolCall/toolCallId")),
                            text(node.at("/params/toolCall/kind")),
                            text(node.at("/params/toolCall/title")),
                            permissionResource(node.at("/params/toolCall/rawInput")),
                            strings(node.at("/params/options"), "optionId"),
                            optionKinds(node.at("/params/options")),
                            node.at("/params/toolCall/rawInput").deepCopy(), trimmed
                    ),
                    null,
                    null,
                    null
            );
        }

        if (!method.isBlank()) {
            var update = node.at("/params/update");
            var updateKind = text(update.get("sessionUpdate"));
            return new AcpxStreamWireLine(
                    Kind.NOTIFICATION,
                    "",
                    method,
                    null,
                    null,
                    new Notification(
                            text(node.at("/params/sessionId")),
                            updateKind,
                            text(update.get("messageId")),
                            text(update.at("/content/type")),
                            text(update.at("/content/text")),
                            text(update.get("toolCallId")),
                            text(update.at("/_meta/claudeCode/toolName")),
                            usageUpdate(updateKind, update),
                            ("tool_call".equals(updateKind) || "tool_call_update".equals(updateKind))
                                    ? update.deepCopy() : null,
                            "dual/permission/request".equals(method) ? node.path("params").deepCopy() : null
                    ),
                    null
            );
        }
        return malformed();
    }

    private static java.util.Map<String,String> optionKinds(JsonNode options) {
        var map = new java.util.LinkedHashMap<String,String>();
        if (options != null && options.isArray()) for (var item : options) {
            var id = text(item.get("optionId")); var kind = text(item.get("kind"));
            if (!id.isBlank()) map.merge(id, kind, (old, next) -> "AMBIGUOUS");
        }
        return java.util.Map.copyOf(map);
    }

    private static Error error(JsonNode errorNode) {
        var data = errorNode.path("data");
        var code = errorNode.get("code");
        return new Error(
                code != null && code.isInt() ? code.asInt() : null,
                text(errorNode.get("message")),
                text(data.get("message")),
                text(data.get("details"))
        );
    }

    private static String permissionResource(JsonNode rawInput) {
        if (rawInput == null || !rawInput.isObject()) {
            return "";
        }
        var command = text(rawInput.get("command"));
        return command.isBlank() ? text(rawInput.get("query")) : command;
    }

    private static Usage usage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return null;
        }
        if (!validToken(usage.get("inputTokens")) || !validToken(usage.get("outputTokens"))
                || !validToken(usage.get("totalTokens"))
                || (usage.has("cachedReadTokens") && !validToken(usage.get("cachedReadTokens")))
                || (usage.has("cachedWriteTokens") && !validToken(usage.get("cachedWriteTokens")))) return null;
        return new Usage(number(usage.get("inputTokens")), number(usage.get("outputTokens")),
                number(usage.get("cachedReadTokens")), number(usage.get("cachedWriteTokens")),
                number(usage.get("totalTokens")));
    }

    private static boolean validToken(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0;
    }

    private static UsageUpdate usageUpdate(String updateKind, JsonNode update) {
        if (!"usage_update".equals(updateKind)) {
            return null;
        }
        var cost = update.path("cost");
        return new UsageUpdate(
                number(update.get("used")),
                decimal(cost.get("amount")),
                text(cost.get("currency"))
        );
    }

    private static boolean isTypedFailure(String type, String method, JsonNode errorNode) {
        return (type != null && type.endsWith("/failed"))
                || (method != null && method.endsWith("/failed") && errorNode != null && errorNode.isTextual());
    }

    private static String textualError(JsonNode node, JsonNode errorNode) {
        if (errorNode != null && errorNode.isTextual()) {
            return errorNode.asText();
        }
        return text(node.get("message"));
    }

    private static String methodForFailureType(String type) {
        return type == null ? "" : type.substring(0, type.length() - "/failed".length());
    }

    private static String id(JsonNode node) {
        return node == null || node.isNull() || (!node.isTextual() && !node.isNumber()) ? "" : node.asText();
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static long number(JsonNode node) {
        return node == null || !node.isNumber() ? 0 : node.asLong();
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.decimalValue();
    }

    private static List<String> strings(JsonNode node, String field) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var item : node) {
            var value = text(item.get(field));
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static AcpxStreamWireLine malformed() {
        return new AcpxStreamWireLine(Kind.MALFORMED, "", "", null, null, null, null);
    }
}
