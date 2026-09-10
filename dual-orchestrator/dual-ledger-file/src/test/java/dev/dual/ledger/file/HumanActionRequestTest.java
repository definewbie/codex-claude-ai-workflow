package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HumanActionRequestTest {
    @TempDir Path root;

    @Test void requestIsIdempotentNonResolvingAndPreservedByLaterHumanDecision() throws Exception {
        var paths = waiting();
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        var gate = current.activeNodeInstanceIds().getFirst();
        var request = request(gate, "device-1", "Obtain device evidence");
        var recorded = ledger.requestAction(paths, request);
        var events = Files.readString(paths.eventsJsonl());
        assertEquals(recorded, new WorkflowFileLedger().requestAction(paths, request));
        assertEquals(events, Files.readString(paths.eventsJsonl()));
        assertEquals(current, ledger.loadCurrent(paths));
        assertEquals("open", ledger.loadOpenGateForNode(paths, gate).status());
        assertThrows(java.io.IOException.class, () -> ledger.requestAction(paths, request(gate, "device-1", "Different")));
        assertThrows(java.io.IOException.class, () -> ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "request_action", "note"));
        ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", "Choose an existing legal repair");
        assertEquals(List.of(recorded), ledger.loadNode(paths, gate).result().get("action_requests"));
        assertEquals("after", ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()).nodeDefinitionId());
        assertThrows(java.io.IOException.class, () -> ledger.requestAction(paths, request));
    }

    @Test void staleGateOrSourceIsRejectedBeforeAnyWrite() throws Exception {
        var paths = waiting();
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        var gate = current.activeNodeInstanceIds().getFirst();
        var before = Files.readString(paths.nodeJson(gate));
        var events = Files.readString(paths.eventsJsonl());
        assertThrows(java.io.IOException.class, () -> ledger.requestAction(paths, request("old-gate", "r", "need")));
        assertThrows(java.io.IOException.class, () -> ledger.requestAction(paths,
                new HumanActionRequest("r", gate, List.of("forged"), "need", "Unavailable")));
        assertEquals(before, Files.readString(paths.nodeJson(gate)));
        assertEquals(events, Files.readString(paths.eventsJsonl()));
        assertEquals(current, ledger.loadCurrent(paths));
    }

    @Test void auditAppendFailureDoesNotLoseOrRepeatDurableRequest() throws Exception {
        var paths = waiting();
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        var gate = current.activeNodeInstanceIds().getFirst();
        var backup = root.resolve("events-backup");
        Files.move(paths.eventsJsonl(), backup);
        Files.createDirectory(paths.eventsJsonl());
        var request = request(gate, "r", "Need offline verification");
        assertThrows(java.io.IOException.class, () -> ledger.requestAction(paths, request));
        assertEquals(1, ((List<?>) ledger.loadNode(paths, gate).result().get("action_requests")).size());
        Files.delete(paths.eventsJsonl());
        Files.move(backup, paths.eventsJsonl());
        var before = Files.readString(paths.eventsJsonl());
        new WorkflowFileLedger().requestAction(paths, request);
        assertEquals(before, Files.readString(paths.eventsJsonl()));
        assertEquals(current, ledger.loadCurrent(paths));
    }

    private HumanActionRequest request(String gate, String id, String result) {
        return new HumanActionRequest(id, gate, List.of("self-test.json"), result, "Existing actions cannot acquire it");
    }

    private WorkstreamPaths waiting() throws Exception {
        var paths = new WorkstreamPaths(root, new DualId("ws-request"));
        var definition = new WorkflowDefinition("request", 1, "start", List.of(
                new NodeDefinition("start", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of(
                        "gate_purpose", "implementation_repair", "allowed_actions", List.of("revise", "request_action"))),
                new NodeDefinition("after", NodeKind.TASK, "noop.task", Map.of())), List.of(
                new EdgeDefinition("start", "completed", "repair"), new EdgeDefinition("repair", "revise", "after")));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-request", definition);
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of("self-test.json"), Map.of());
        return paths;
    }
}
