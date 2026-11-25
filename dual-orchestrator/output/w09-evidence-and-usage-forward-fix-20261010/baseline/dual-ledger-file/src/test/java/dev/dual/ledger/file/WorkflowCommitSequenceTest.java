package dev.dual.ledger.file;

import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** User sequence: receive result, inspect human gate, decide, continue. No providers. */
class WorkflowCommitSequenceTest {
    @TempDir Path root;
    private static final Map<String, String> IDENTITY = Map.of("test_binary", "fixed-test-build");
    private static final WorkflowDefinition DEFINITION = new WorkflowDefinition("commit-sequence", 1, "author",
            List.of(new NodeDefinition("author", NodeKind.TASK, "noop", Map.of()),
                    new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of()),
                    new NodeDefinition("next", NodeKind.TASK, "noop", Map.of())),
            List.of(new EdgeDefinition("author", "READY", "gate.review"),
                    new EdgeDefinition("gate.review", "approved", "next")));

    @Test void initializationAndGateDecisionHaveCoherentReadSnapshots() throws Exception {
        var paths = initialize("normal");
        var ledger = ledger((p, f) -> {});
        var source = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, DEFINITION, source, NodeStatus.COMPLETED, "READY", List.of(), Map.of());
        try (var snapshot = ledger.readSession(paths)) {
            var current = snapshot.ledger().loadCurrent(paths);
            assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, current.status());
            var gate = snapshot.ledger().loadOpenGateForNode(paths, current.activeNodeInstanceIds().getFirst());
            assertEquals("open", gate.status());
            assertEquals("READY", gate.result().get("source_outcome"));
            assertEquals("ws-normal", snapshot.store().loadStatus(root.resolve("normal"), "ws-normal")
                    .workstream().workstreamId());
            assertThrows(WorkstreamLockedException.class, () -> ledger.readSession(paths));
        }
        ledger.resolveOpenGate(paths, DEFINITION, "approved", "Proceed with the reviewed result");
        assertNext(ledger, paths);
    }

    @Test void eachDecisionWritePrefixCanFinishWithoutReplayingTheDecision() throws Exception {
        var observed = new ArrayList<String>();
        var baseline = readyForDecision("prefix-baseline");
        ledger((point, target) -> observed.add(point + ":" + target.getFileName()))
                .resolveOpenGate(baseline, DEFINITION, "approved", "proceed");
        assertTrue(observed.size() > 8, "exercise the real gate/node/event/current write set");
        for (int prefix = 0; prefix < observed.size(); prefix++) {
            var paths = readyForDecision("prefix-" + prefix);
            int failAt = prefix;
            var calls = new AtomicInteger();
            var interrupted = ledger((point, target) -> {
                if (calls.getAndIncrement() == failAt) throw new IOException("interrupted at " + point);
            });
            assertThrows(IOException.class, () -> interrupted.resolveOpenGate(paths, DEFINITION, "approved", "proceed"));
            var cold = ledger((p, f) -> {});
            if (prefix == 0) {
                // No prepared decision exists: the gate remains open and the user may decide normally.
                assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, cold.loadCurrent(paths).status());
                cold.resolveOpenGate(paths, DEFINITION, "approved", "proceed");
            } else {
                try (var completion = cold.writeSession(paths, "complete prepared local decision", "recover-" + prefix)) {
                    completion.commit();
                }
            }
            assertNext(cold, paths);
            var events = Files.readAllLines(paths.eventsJsonl());
            assertEquals(events.size(), events.stream().distinct().count());
            assertThrows(IOException.class, () -> cold.resolveOpenGate(paths, DEFINITION, "approved", "proceed"));
            assertEquals(events, Files.readAllLines(paths.eventsJsonl()), "a repeated decision must not append events");
        }
    }

    @Test void readinessRegistrationDoesNotApproveOrRouteAndSurvivesEveryWritePrefix() throws Exception {
        var observed = new ArrayList<String>();
        var baseline = readyForDecision("readiness-baseline");
        registerReadiness(ledger((point, target) -> observed.add(point)), baseline);
        assertTrue(observed.size() > 5, "exercise bytes, subject, event and commit cleanup");
        assertReadiness(ledger((p, f) -> {}), baseline);
        for (int prefix = 0; prefix < observed.size(); prefix++) {
            var paths = readyForDecision("readiness-prefix-" + prefix);
            int failAt = prefix;
            var calls = new AtomicInteger();
            var interrupted = ledger((point, target) -> {
                if (calls.getAndIncrement() == failAt) throw new IOException("readiness interrupted at " + point);
            });
            assertThrows(IOException.class, () -> registerReadiness(interrupted, paths));
            var cold = ledger((p, f) -> {});
            if (prefix == 0) {
                assertFalse(Files.exists(readinessPath(paths)));
                assertFalse(Files.readString(paths.eventsJsonl()).contains("verification_readiness_recorded"));
                registerReadiness(cold, paths);
            } else {
                try (var completion = cold.writeSession(paths, "finish prepared registration", "recovery")) {
                    completion.commit();
                }
            }
            assertReadiness(cold, paths);
            var beforeEvents = Files.readAllBytes(paths.eventsJsonl());
            assertThrows(IOException.class, () -> registerReadiness(cold, paths),
                    "low-level writer cannot replace an already registered immutable record");
            assertArrayEquals(beforeEvents, Files.readAllBytes(paths.eventsJsonl()));
            // Registration did not consume the real human decision; normal approval still works.
            cold.resolveOpenGate(paths, DEFINITION, "approved", "Proceed after reviewing the new material");
            assertNext(cold, paths);
        }
    }

    private void registerReadiness(WorkflowFileLedger ledger, WorkstreamPaths paths) throws Exception {
        var bytes = "{\"fixture\":\"operator declaration, not execution evidence\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var ref = paths.workspaceRoot().relativize(readinessPath(paths)).toString();
        var sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        try (var write = ledger.writeSession(paths, "record readiness fixture", "ready-1")) {
            var current = write.ledger().loadCurrent(paths);
            write.ledger().recordReadiness(paths, current.activeNodeInstanceIds().getFirst(),
                    new dev.dual.core.contract.TaskContract.Reference(ref, sha), bytes);
            write.commit();
        }
    }

    private static Path readinessPath(WorkstreamPaths paths) {
        return paths.workstreamDir().resolve("artifacts/verification-readiness/ready-1.json");
    }

    private static void assertReadiness(WorkflowFileLedger ledger, WorkstreamPaths paths) throws Exception {
        try (var read = ledger.readSession(paths)) {
            var current = read.ledger().loadCurrent(paths);
            assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, current.status());
            var subject = current.activeNodeInstanceIds().getFirst();
            assertEquals(NodeStatus.WAITING, read.ledger().loadNode(paths, subject).status());
            assertEquals("open", read.ledger().loadOpenGateForNode(paths, subject).status());
            assertEquals(1, ((List<?>) read.ledger().loadNode(paths, subject).result().get("readiness_refs")).size());
            assertEquals(1, read.files().readLines(paths.eventsJsonl()).stream()
                    .filter(line -> line.contains("verification_readiness_recorded")).count());
            assertTrue(read.files().exists(readinessPath(paths)));
        }
    }

    @Test void unsupportedDefinitionIsRejectedWithoutReaderMutation() throws Exception {
        var paths = initialize("old");
        var writer = new AtomicJsonWriter();
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) writer.readTree(paths.workflowDefinitionJson());
        json.put("schemaVersion", 1);
        writer.write(paths.workflowDefinitionJson(), json);
        var before = Files.readAllBytes(paths.workflowDefinitionJson());
        assertThrows(IOException.class, () -> ledger((p, f) -> {}).loadDefinition(paths));
        assertArrayEquals(before, Files.readAllBytes(paths.workflowDefinitionJson()));
    }

    @Test void changedWorkstreamIdentityBlocksPendingReplayBeforeAnyBusinessWrite() throws Exception {
        var paths = readyForDecision("foreign-identity");
        var interrupted = ledger((point, target) -> {
            if (point.equals("after_manifest")) throw new IOException("stop before applying decision");
        });
        assertThrows(IOException.class, () -> interrupted.resolveOpenGate(paths, DEFINITION, "approved", "proceed"));
        var beforeEvents = Files.readAllBytes(paths.eventsJsonl());
        var beforeCurrent = Files.readAllBytes(paths.workflowCurrentJson());
        var writer = new AtomicJsonWriter();
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) writer.readTree(paths.workstreamJson());
        document.set("frozenRuntimeIdentity", writer.objectMapper().valueToTree(Map.of("test_binary", "other-build")));
        writer.write(paths.workstreamJson(), document);
        assertThrows(IOException.class, () -> ledger((p, f) -> {})
                .writeSession(paths, "recover", "wrong-workstream-version"));
        assertArrayEquals(beforeEvents, Files.readAllBytes(paths.eventsJsonl()));
        assertArrayEquals(beforeCurrent, Files.readAllBytes(paths.workflowCurrentJson()));
    }

    private WorkstreamPaths readyForDecision(String name) throws Exception {
        var paths = initialize(name);
        var ledger = ledger((p, f) -> {});
        ledger.completeAndRoute(paths, DEFINITION, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "READY", List.of(), Map.of());
        return paths;
    }

    private WorkstreamPaths initialize(String name) throws Exception {
        var workspace = root.resolve(name);
        var id = new DualId("ws-" + name);
        var workspaceId = new DualId("workspace-" + name);
        var paths = new WorkstreamPaths(workspace, id);
        ledger((p, f) -> {}).initialize(paths,
                Workstream.created(id, "review a completed result", new Workstream.RepositoryRef(workspace, workspace),
                        workspaceId, Instant.now()),
                Workspace.primaryGitWorktree(workspaceId, id, workspace, workspace, "test", "main", "test-base"),
                "Review and continue", WorkstreamSettingsDocument.of("auto"), "wf-" + name, DEFINITION);
        return paths;
    }

    private WorkflowFileLedger ledger(LedgerSession.FaultPoint point) {
        return new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), IDENTITY, point);
    }

    private static void assertNext(WorkflowFileLedger ledger, WorkstreamPaths paths) throws Exception {
        try (var read = ledger.readSession(paths)) {
            var current = read.ledger().loadCurrent(paths);
            assertEquals(WorkflowStatus.RUNNING, current.status());
            assertEquals(List.of(), current.openGateIds());
            assertEquals("next", read.ledger().loadNode(paths, current.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
        }
    }
}
