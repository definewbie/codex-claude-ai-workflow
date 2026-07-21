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
}
