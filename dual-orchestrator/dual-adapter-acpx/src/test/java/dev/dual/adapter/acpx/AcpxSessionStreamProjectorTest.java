package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxSessionStreamProjectorTest {
    @Test
    void projectsClaudeUngroupedDeltasAndFinalSnapshotOnce() {
        var projector = new AcpxSessionStreamProjector();
        accept(projector, 1, """
                {"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{}}
                """);
        accept(projector, 2, update("", "{\"run_id\":"));
        accept(projector, 3, update("", "\"run-1\"}"));
        accept(projector, 4, update("message-1", "{\"run_id\":\"run-1\"}"));
        accept(projector, 5, """
                {"jsonrpc":"2.0","id":3,"result":{"stopReason":"end_turn"}}
                """);

        assertEquals("3", projector.promptCompletion().requestId());
        assertEquals("end_turn", projector.promptCompletion().stopReason());
        assertEquals("{\"run_id\":\"run-1\"}", projector.promptCompletion().finalAssistantMessage());
        assertTrue(projector.promptCompletion().completed());
    }

    @Test
    void coalescesGroupedDeltasAndTerminalSnapshot() {
        var projector = new AcpxSessionStreamProjector();
        accept(projector, 1, """
                {"jsonrpc":"2.0","id":4,"method":"session/prompt","params":{}}
                """);
        accept(projector, 2, update("message-2", "{\"decision\":"));
        accept(projector, 3, update("message-2", "\"PASS\"}"));
        accept(projector, 4, update("message-2", "{\"decision\":\"PASS\"}"));
        accept(projector, 5, """
                {"jsonrpc":"2.0","id":4,"result":{"stopReason":"end_turn"}}
                """);

        assertEquals("{\"decision\":\"PASS\"}", projector.promptCompletion().finalAssistantMessage());
    }

    @Test
    void ignoresToolPayloadJsonAndReturnsOnlyAssistantMessage() {
        var projector = new AcpxSessionStreamProjector();
        accept(projector, 1, """
                {"jsonrpc":"2.0","id":5,"method":"session/prompt","params":{}}
                """);
        accept(projector, 2, """
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"tool_call_update","messageId":"tool-1","content":{"type":"text","text":"{\"decision\":\"WRONG\"}"}}}}
                """);
        accept(projector, 3, update("message-3", "{\"decision\":\"PASS\"}"));
        accept(projector, 4, """
                {"jsonrpc":"2.0","id":5,"result":{"stopReason":"end_turn"}}
                """);

        assertEquals("{\"decision\":\"PASS\"}", projector.promptCompletion().finalAssistantMessage());
    }

    @Test
    void newPromptOccurrenceCannotReusePriorTurnMessage() {
        var projector = new AcpxSessionStreamProjector();
        accept(projector, 1, """
                {"jsonrpc":"2.0","id":6,"method":"session/prompt","params":{}}
                """);
        accept(projector, 2, update("message-4", "{\"run_id\":\"old\"}"));
        accept(projector, 3, """
                {"jsonrpc":"2.0","id":6,"result":{"stopReason":"end_turn"}}
                """);
        accept(projector, 4, """
                {"jsonrpc":"2.0","id":7,"method":"session/prompt","params":{}}
                """);
        accept(projector, 5, update("message-5", "{\"run_id\":\"new\"}"));
        accept(projector, 6, """
                {"jsonrpc":"2.0","id":7,"result":{"stopReason":"end_turn"}}
                """);

        assertEquals("7", projector.promptCompletion().requestId());
        assertEquals("{\"run_id\":\"new\"}", projector.promptCompletion().finalAssistantMessage());
    }

    @Test
    void historicalBootstrapLinesCannotCreatePromptCompletion() {
        var projector = new AcpxSessionStreamProjector();
        projector.accept(1, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":8,"method":"session/prompt","params":{}}
                """), false);
        projector.accept(2, AcpxStreamWireLine.parse(update("message-old", "{\"run_id\":\"old\"}")), false);
        projector.accept(3, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":8,"result":{"stopReason":"end_turn"}}
                """), false);

        assertFalse(projector.promptCompletion().completed());
    }

    @Test
    void proseToolResultsAndUsageCannotBecomeProviderFailure() {
        var projector = project("""
                {"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"quota rate limit 429"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"tool_call_update","rawOutput":{"formatted_output":"rate_limit_exceeded is_error=false"}}}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"usage_update","used":100,"size":200000}}}
                """);
        accept(projector, 3, update("", "{\"decision\":\"PASS\"}"));
        accept(projector, 4, """
                {"jsonrpc":"2.0","id":3,"result":{"stopReason":"end_turn","_meta":{"quota":{"totalTokens":100}}}}
                """);

        assertTrue(projector.evidence().errors().isEmpty());
        assertTrue(projector.promptCompletion().completed());
    }

    @Test
    void sessionResumeErrorIsRemovedOnlyByLaterSessionCreation() {
        var recovered = project("""
                {"jsonrpc":"2.0","id":1,"method":"session/resume","params":{"sessionId":"old"}}
                {"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"session not found"}}
                {"jsonrpc":"2.0","id":2,"method":"session/new","params":{}}
                {"jsonrpc":"2.0","id":2,"result":{"sessionId":"fresh"}}
                """);
        var unrecovered = project("""
                {"jsonrpc":"2.0","id":2,"method":"session/new","params":{}}
                {"jsonrpc":"2.0","id":2,"result":{"sessionId":"fresh"}}
                {"jsonrpc":"2.0","id":1,"method":"session/resume","params":{"sessionId":"old"}}
                {"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"session not found"}}
                """);

        assertTrue(recovered.evidence().errors().isEmpty());
        assertEquals(1, unrecovered.evidence().errors().size());
        assertEquals("session/resume", unrecovered.evidence().errors().get(0).method());
    }

    @Test
    void nestedProviderErrorFieldsRemainTyped() {
        var projector = project("""
                {"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{}}
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"Internal error","data":{"message":"status=429 rate_limit_exceeded","details":"provider detail"}}}
                """);

        var error = projector.evidence().errors().get(0);
        assertEquals("session/prompt", error.method());
        assertEquals("status=429 rate_limit_exceeded", error.dataMessage());
        assertEquals("provider detail", error.dataDetails());
    }

    @Test
    void malformedLinesCannotCreateControlFacts() {
        var projector = project("""
                not-json
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"rate lim
                """);

        assertTrue(projector.evidence().errors().isEmpty());
        assertFalse(projector.promptCompletion().completed());
    }

    @Test
    void correlatesCurrentPermissionRequestAndDeniedDecision() {
        var projector = new AcpxSessionStreamProjector();
        var requested = projector.accept(1, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":0,"method":"session/request_permission","params":{"toolCall":{"title":"Run tests","rawInput":{"command":"mvn test"}}}}
                """)).orElseThrow();
        var denied = projector.accept(2, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":0,"result":{"outcome":{"outcome":"selected","optionId":"reject"}}}
                """)).orElseThrow();

        assertEquals(AgentLifecycleEvent.Kind.PERMISSION_REQUESTED, requested.kind());
        assertEquals(AgentLifecycleEvent.Kind.PERMISSION_DENIED, denied.kind());
        assertTrue(denied.text().contains("mvn test"));
    }

    @Test
    void historicalPermissionDecisionCannotBecomeCurrentEvidence() {
        var projector = new AcpxSessionStreamProjector();
        assertTrue(projector.accept(1, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":0,"method":"session/request_permission","params":{"toolCall":{"title":"Old request"}}}
                """), false).isEmpty());
        assertTrue(projector.accept(2, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":0,"result":{"outcome":{"outcome":"selected","optionId":"reject"}}}
                """), false).isEmpty());
    }

    @Test
    void currentPermissionResultWithoutCurrentRequestCannotCreateEvidence() {
        var projector = new AcpxSessionStreamProjector();
        assertTrue(projector.accept(1, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":0,"method":"session/request_permission","params":{"toolCall":{"title":"Historical request"}}}
                """), false).isEmpty());
        assertTrue(projector.accept(2, AcpxStreamWireLine.parse("""
                {"jsonrpc":"2.0","id":0,"result":{"outcome":{"outcome":"selected","optionId":"reject"}}}
                """), true).isEmpty());
    }

    private static String update(String messageId, String text) {
        var messageField = messageId.isBlank() ? "" : "\"messageId\":\"" + messageId + "\",";
        return "{\"jsonrpc\":\"2.0\",\"method\":\"session/update\",\"params\":{\"update\":{"
                + messageField
                + "\"sessionUpdate\":\"agent_message_chunk\",\"content\":{\"type\":\"text\",\"text\":"
                + quote(text) + "}}}}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void accept(AcpxSessionStreamProjector projector, long sequence, String line) {
        projector.accept(sequence, AcpxStreamWireLine.parse(line));
    }

    private static AcpxSessionStreamProjector project(String ndjson) {
        var projector = new AcpxSessionStreamProjector();
        long sequence = 0;
        for (var line : ndjson.lines().toList()) {
            accept(projector, sequence++, line);
        }
        return projector;
    }
}
