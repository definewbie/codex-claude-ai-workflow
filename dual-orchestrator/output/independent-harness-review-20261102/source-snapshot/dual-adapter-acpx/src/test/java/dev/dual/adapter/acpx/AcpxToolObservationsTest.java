package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcpxToolObservationsTest {
    @Test void ordinaryClaudeSuccessIsAnObservationNotAnActualExit() {
        var p = start();
        call(p, "one");
        update(p, "s1", "one", "\"rawOutput\":\"10 tests passed\",\"_meta\":{\"terminal_exit\":{\"exit_code\":0}}");
        var fact = p.toolObservations().getFirst();
        assertTrue(fact.hasExecutionResult());
        assertEquals("completed", fact.nativeStatus());
        assertEquals(0, fact.reportedExitCode());
        assertNull(fact.actualExitCode());
        assertEquals("", fact.actualExitSource());
        assertEquals("run-tests", fact.input().path("command").asText());
        assertEquals(2, fact.rawUpdates().size());
    }

    @Test void auditedRawReturnCodeIsPreservedIncludingFailure() {
        var p = start(); call(p, "one");
        update(p, "s1", "one", "\"rawOutput\":{\"type\":\"bash_code_execution_result\",\"return_code\":7,\"stdout\":\"\",\"stderr\":\"failed\"}");
        assertEquals(7, p.toolObservations().getFirst().actualExitCode());
        assertFalse(p.toolObservations().getFirst().actualExitSource().isBlank());
    }

    @Test void historyForeignSessionProseMissingInputAndLateResultCannotBecomeEvidence() {
        var p = new AcpxSessionStreamProjector();
        update(p, "s1", "history", "\"rawOutput\":\"passed\"");
        assertTrue(p.toolObservations().isEmpty());
        p = start(); call(p, "one");
        update(p, "other", "one", "\"rawOutput\":\"passed\"");
        assertFalse(p.toolObservations().getFirst().hasExecutionResult());
        update(p, "s1", "missing", "\"rawOutput\":\"passed\"");
        assertTrue(p.toolObservations().get(1).invalid());
        p.accept(5, AcpxStreamWireLine.parse("{\"id\":1,\"result\":{\"stopReason\":\"end_turn\"}}"));
        update(p, "s1", "one", "\"rawOutput\":\"late\"");
        assertFalse(p.toolObservations().getFirst().hasExecutionResult());
    }

    @Test void duplicateIdentityCannotReplaceACompletedCall() {
        var p = start(); call(p, "one");
        update(p, "s1", "one", "\"rawOutput\":\"first\"");
        call(p, "one");
        assertFalse(p.toolObservations().getFirst().hasExecutionResult());
    }

    @Test void statusWithoutAResultCannotBecomeEvidenceAndStreamingOutputIsRetained() {
        var p = start(); call(p, "one");
        update(p, "s1", "one", "\"title\":\"success\"");
        assertFalse(p.toolObservations().getFirst().hasExecutionResult());
        p = start(); call(p, "two");
        p.accept(2, AcpxStreamWireLine.parse("""
                {"method":"session/update","params":{"sessionId":"s1","update":{
                  "sessionUpdate":"tool_call_update","toolCallId":"two","status":"in_progress",
                  "_meta":{"terminal_output":{"terminal_id":"two","data":"actual test results"}}}}}
                """));
        update(p, "s1", "two", "\"content\":[{\"type\":\"terminal\",\"terminalId\":\"two\"}]");
        assertTrue(p.toolObservations().getFirst().hasExecutionResult());
        assertTrue(p.toolObservations().getFirst().output().toString().contains("actual test results"));
    }

    private static AcpxSessionStreamProjector start() {
        var p = new AcpxSessionStreamProjector();
        p.accept(0, AcpxStreamWireLine.parse("{\"id\":1,\"method\":\"session/prompt\",\"params\":{\"sessionId\":\"s1\",\"prompt\":[]}}"));
        return p;
    }
    private static void call(AcpxSessionStreamProjector p, String id) {
        p.accept(1, AcpxStreamWireLine.parse("{\"method\":\"session/update\",\"params\":{\"sessionId\":\"s1\",\"update\":{\"sessionUpdate\":\"tool_call\",\"toolCallId\":\""+id+"\",\"kind\":\"execute\",\"rawInput\":{\"command\":\"run-tests\"},\"_meta\":{\"claudeCode\":{\"toolName\":\"Bash\"}}}}}"));
    }
    private static void update(AcpxSessionStreamProjector p, String session, String id, String result) {
        p.accept(2, AcpxStreamWireLine.parse("{\"method\":\"session/update\",\"params\":{\"sessionId\":\""+session+"\",\"update\":{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\""+id+"\",\"status\":\"completed\","+result+"}}}"));
    }
}
