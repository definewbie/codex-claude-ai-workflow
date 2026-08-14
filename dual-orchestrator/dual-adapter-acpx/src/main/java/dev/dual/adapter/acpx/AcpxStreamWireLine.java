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
            String promptText,
            String permissionToolCallId,
            String permissionToolKind,
            String permissionTitle,
            String permissionCommand,
            List<String> permissionOptions
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

    record Result(String sessionId, String stopReason, Usage usage, String permissionOutcome, String permissionOptionId) {
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
            UsageUpdate usageUpdate
    ) {
        Notification {
            sessionId = sessionId == null ? "" : sessionId;
            updateKind = updateKind == null ? "" : updateKind;
            messageId = messageId == null ? "" : messageId;
            contentType = contentType == null ? "" : contentType;
            text = text == null ? "" : text;
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
                            text(resultNode.at("/outcome/optionId"))
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
                            text(node.at("/params/prompt/0/text")),
                            text(node.at("/params/toolCall/toolCallId")),
                            text(node.at("/params/toolCall/kind")),
                            text(node.at("/params/toolCall/title")),
                            text(node.at("/params/toolCall/rawInput/command")),
                            strings(node.at("/params/options"), "optionId")
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
                            usageUpdate(updateKind, update)
                    ),
                    null
            );
        }
        return malformed();
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

    private static Usage usage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return null;
        }
        return new Usage(
                number(usage.get("inputTokens")),
                number(usage.get("outputTokens")),
                number(usage.get("cachedReadTokens")),
                number(usage.get("cachedWriteTokens")),
                number(usage.get("totalTokens"))
        );
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
