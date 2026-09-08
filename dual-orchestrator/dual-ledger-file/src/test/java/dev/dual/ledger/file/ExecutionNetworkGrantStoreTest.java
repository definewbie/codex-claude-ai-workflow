package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionNetworkGrantStoreTest {
    @TempDir Path temp;
    final WorkflowFileLedger ledger = new WorkflowFileLedger();
    final ExecutionNetworkGrantStore store = new ExecutionNetworkGrantStore();

    @Test void gateContextAndSiblingExecutionRouteToOneClaimWithoutResolvingTheGate() throws Exception {
        var paths = fixture();
        var before = Files.readString(paths.workflowCurrentJson());
        var ref = store.approve(paths, "verify.tests", List.of("packages.example.org"), "resolve approved dependencies");
        assertEquals(before, Files.readString(paths.workflowCurrentJson()));
        assertFalse((boolean) store.view(paths).get("canApprove"));
        assertTrue((boolean) store.view(paths).get("canCancel"));
        assertThrows(IOException.class, () -> store.approve(paths, "verify.tests", List.of("other.example.org"), "duplicate"));
        assertThrows(IOException.class, () -> store.claim(paths, active(paths).nodeInstanceId(), "run-premature"));
        ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", "prepare the existing tests");
        assertEquals("context.prepare", active(paths).nodeDefinitionId());
        complete(paths);
        assertEquals("build.code", active(paths).nodeDefinitionId());
        ledger.markRunning(paths, active(paths).nodeInstanceId(), 1);
        assertNull(store.claim(paths, active(paths).nodeInstanceId(), "run-build"));
        complete(paths);
        var test = active(paths);
        assertEquals("verify.tests", test.nodeDefinitionId());
        assertEquals(ref, test.inputTransition().get(ExecutionNetworkGrantStore.REF_KEY));
        ledger.markRunning(paths, test.nodeInstanceId(), 1);
        var claim = new ExecutionNetworkGrantStore().claim(paths, test.nodeInstanceId(), "run-test");
        assertEquals(List.of("packages.example.org"), claim.domains());
        store.verifyClaim(paths, claim, test.nodeInstanceId(), "run-test");
        assertThrows(IOException.class, () -> store.verifyClaim(paths, claim, test.nodeInstanceId(), "run-other"));
        assertNull(new ExecutionNetworkGrantStore().claim(paths, test.nodeInstanceId(), "run-after-crash"));
        complete(paths);
        assertEquals(NodeKind.GATE, active(paths).kind());
        assertFalse(active(paths).inputTransition().containsKey(ExecutionNetworkGrantStore.REF_KEY));
        assertThrows(IOException.class, () -> store.claim(paths, test.nodeInstanceId(), "run-terminal"));
    }

    @Test void failedExecutionRetryBurnsGrantBeforeProviderAndNeedsNewApproval() throws Exception {
        var paths = fixture();
        ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", "continue");
        complete(paths);
        var node = active(paths);
        ledger.completeAndRoute(paths, ledger.loadDefinition(paths), node.nodeInstanceId(), NodeStatus.FAILED,
                "failed", List.of(), Map.of());
        var ref = store.approve(paths, node.nodeDefinitionId(), List.of("api.example.org"), "public API");
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status());
        assertThrows(IOException.class, () -> store.approve(paths, "verify.tests", List.of("api.example.org"), "wrong node"));
        ledger.retryActiveNode(paths, "operator retry");
        ledger.markRunning(paths, node.nodeInstanceId(), 1);
        assertNotNull(store.claim(paths, node.nodeInstanceId(), "run-1"));
        ledger.completeAndRoute(paths, ledger.loadDefinition(paths), node.nodeInstanceId(), NodeStatus.FAILED,
                "failed", List.of(), Map.of());
        ledger.retryActiveNode(paths, "after startup failed");
        ledger.markRunning(paths, node.nodeInstanceId(), 1);
        assertNull(store.claim(paths, node.nodeInstanceId(), "run-2"));
        assertTrue(Files.readString(paths.workspaceRoot().resolve(ref)).contains("run-1"));
    }

    @Test void cancellationMissingFilesAndTamperedDomainsFailClosed() throws Exception {
        var paths = fixture();
        var ref = store.approve(paths, "verify.tests", List.of("api.example.org"), "public API");
        var path = paths.workspaceRoot().resolve(ref);
        var original = Files.readString(path);
        Files.writeString(path, original.replace("\"state\" : \"PENDING\"", "\"state\" : null"));
        assertEquals("INVALID", store.view(paths).get("status"));
        Files.writeString(path, original.replace("api.example.org", "evil.example.org"));
        assertEquals("INVALID", store.view(paths).get("status"));
        Files.writeString(path, original);
        store.cancel(paths);
        assertFalse((boolean) store.view(paths).get("canCancel"));
        assertTrue((boolean) store.view(paths).get("canApprove"));
        var replacement = store.approve(paths, "verify.tests", List.of("api.example.org"), "new explicit approval");
        Files.delete(paths.workspaceRoot().resolve(replacement));
        assertEquals("INVALID", store.view(paths).get("status"));
        assertThrows(IOException.class, () -> ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", "continue"));
        assertNotEquals(WorkflowStatus.RUNNING, ledger.loadCurrent(paths).status(), "no runnable successor on corrupt grant");
    }

    @Test void replacementPreservesHistoricalClaimWithoutReauthorizingIt() throws Exception {
        var paths = fixture();
        ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", "continue");
        complete(paths);
        var node = active(paths);
        ledger.completeAndRoute(paths, ledger.loadDefinition(paths), node.nodeInstanceId(), NodeStatus.FAILED, "failed", List.of(), Map.of());
        store.approve(paths, node.nodeDefinitionId(), List.of("api.example.org"), "first");
        ledger.retryActiveNode(paths, "first retry");
        ledger.markRunning(paths, node.nodeInstanceId(), 1);
        var first = store.claim(paths, node.nodeInstanceId(), "run-1");
        ledger.completeAndRoute(paths, ledger.loadDefinition(paths), node.nodeInstanceId(), NodeStatus.FAILED, "failed", List.of(), Map.of());
        var secondRef = store.approve(paths, node.nodeDefinitionId(), List.of("packages.example.org"), "replacement");
        new ExecutionNetworkGrantStore().verifyClaim(paths, first, node.nodeInstanceId(), "run-1");
        ledger.retryActiveNode(paths, "second retry");
        ledger.markRunning(paths, node.nodeInstanceId(), 1);
        var second = store.claim(paths, node.nodeInstanceId(), "run-2");
        assertEquals(secondRef, second.ref());
        assertEquals(List.of("packages.example.org"), second.domains());
        assertNull(store.claim(paths, node.nodeInstanceId(), "run-3"));
        assertThrows(IOException.class, () -> store.verifyClaim(paths, first, node.nodeInstanceId(), "run-2"));
    }

    @Test void resolvedGatePartialWriteHasNoProjectedOrExecutableApproval() throws Exception {
        var paths = fixture();
        store.approve(paths, "verify.tests", List.of("api.example.org"), "test");
        var gateId = ledger.loadCurrent(paths).openGateIds().getFirst();
        var writer = new AtomicJsonWriter();
        var gate = (com.fasterxml.jackson.databind.node.ObjectNode) writer.objectMapper().readTree(paths.gateJson(gateId).toFile());
        gate.put("status", "resolved");
        writer.write(paths.gateJson(gateId), gate);
        var view = store.view(paths);
        assertEquals("INVALID", view.get("status"));
        assertEquals(false, view.get("canApprove"));
        assertEquals(false, view.get("canCancel"));
        assertThrows(IOException.class, () -> store.cancel(paths));
        assertThrows(IOException.class, () -> store.approve(paths, "verify.tests", List.of("api.example.org"), "retry"));
    }

    @Test void profileAndDomainBoundariesRejectWithoutMutation() throws Exception {
        var paths = fixture();
        var before = Files.readString(paths.nodeJson(active(paths).nodeInstanceId()));
        for (var domain : List.of("*.example.org", "https://example.org", "127.0.0.1", "localhost", "host.local", "EXAMPLE.org", "example.org/path", "user@example.org")) {
            assertThrows(IOException.class, () -> store.approve(paths, "verify.tests", List.of(domain), "reason"));
        }
        assertThrows(IOException.class, () -> store.approve(paths, "context.prepare", List.of("example.org"), "no provider"));
        assertThrows(IOException.class, () -> store.approve(paths, "review.code", List.of("example.org"), "not implement"));
        assertThrows(IOException.class, () -> store.approve(paths, "after.gate", List.of("example.org"), "across gate"));
        assertThrows(IOException.class, () -> store.approve(paths, "verify.tests", List.of("example.org"), ""));
        assertEquals(before, Files.readString(paths.nodeJson(active(paths).nodeInstanceId())));
    }

    @Test void unusedGrantDoesNotCrossNextGateOrDefinitionChange() throws Exception {
        var paths = fixture();
        store.approve(paths, "verify.tests", List.of("api.example.org"), "test");
        var defBytes = Files.readString(paths.workflowDefinitionJson());
        Files.writeString(paths.workflowDefinitionJson(), defBytes + "\n");
        assertEquals("INVALID", store.view(paths).get("status"));
        Files.writeString(paths.workflowDefinitionJson(), defBytes);
        ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "approve", "take the alternate branch");
        assertEquals("gate.finish", active(paths).nodeDefinitionId());
        assertFalse(active(paths).inputTransition().containsKey(ExecutionNetworkGrantStore.REF_KEY));
    }

    private WorkstreamPaths fixture() throws Exception {
        var paths = new WorkstreamPaths(temp.toRealPath(), new DualId("ws-grant"));
        var definition = new WorkflowDefinition("network-test", 1, "entry.start", List.of(
                new NodeDefinition("entry.start", NodeKind.TASK, "test", Map.of()),
                new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of("allowed_actions", List.of("revise", "approve"))),
                new NodeDefinition("context.prepare", NodeKind.TASK, "test", Map.of()),
                agent("build.code", "implement"), agent("verify.tests", "implement"), agent("review.code", "review"),
                new NodeDefinition("gate.finish", NodeKind.GATE, "human.review", Map.of("allowed_actions", List.of("approve"))),
                agent("after.gate", "implement")
        ), List.of(new EdgeDefinition("entry.start", "completed", "gate.review"),
                new EdgeDefinition("gate.review", "revise", "context.prepare"),
                new EdgeDefinition("gate.review", "approve", "gate.finish"),
                new EdgeDefinition("context.prepare", "completed", "build.code"),
                new EdgeDefinition("build.code", "completed", "verify.tests"),
                new EdgeDefinition("verify.tests", "completed", "gate.finish"),
                new EdgeDefinition("gate.finish", "approve", "after.gate")));
        ledger.initialize(paths, "wf-grant", definition);
        complete(paths);
        return paths;
    }
    private NodeDefinition agent(String id, String profile) {
        return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of("agent", "claude", "permission_profile", profile));
    }
    private NodeInstanceDocument active(WorkstreamPaths paths) throws Exception {
        return ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
    }
    private void complete(WorkstreamPaths paths) throws Exception {
        ledger.completeAndRoute(paths, ledger.loadDefinition(paths), active(paths).nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of(), Map.of());
    }
}
