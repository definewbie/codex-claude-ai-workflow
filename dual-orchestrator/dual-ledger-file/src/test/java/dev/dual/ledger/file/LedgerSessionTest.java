package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LedgerSessionTest {
    private static final Map<String, String> IDENTITY = Map.of("executable", "fixture-build-v2");
    private static final LedgerSession.FaultPoint NO_FAULT = (point, path) -> {};
    @TempDir Path temp;

    @Test
    void planningSeesOwnChangesButPublishesNothingUntilCompleteCommit() throws Exception {
        var paths = paths(temp);
        try (var session = open(paths, true, true, NO_FAULT)) {
            initial(session);
            assertEquals("READY", session.readTree(paths.nodeJson("author-1")).path("status").asText());
            assertEquals(1, session.list(paths.nodesDir()).size());
            assertFalse(Files.exists(paths.nodeJson("author-1")));
            session.commit();
        }
        try (var reader = open(paths, false, false, NO_FAULT)) {
            assertEquals("READY", reader.readTree(paths.nodeJson("author-1")).path("status").asText());
            assertEquals(1, reader.readLines(paths.eventsJsonl()).size());
            assertThrows(IllegalStateException.class, () -> reader.write(paths.nodeJson("x"), Map.of()));
        }
    }

    @Test
    void everyDurableCutEitherLeavesOriginalStateOrFinishesFixedBytesWithoutDomainReplay() throws Exception {
        var points = new ArrayList<String>();
        try (var session = open(paths(temp.resolve("enumerate")), true, true,
                (point, path) -> points.add(point + ":" + path.getFileName()))) {
            initial(session); session.commit();
        }
        assertTrue(points.getFirst().startsWith("before_manifest:"));
        assertTrue(points.getLast().startsWith("after_pending_delete:"));
        for (int cut = 0; cut < points.size(); cut++) {
            var paths = paths(temp.resolve("cut-" + cut));
            var counter = new AtomicInteger();
            int stopAt = cut;
            try (var session = open(paths, true, true, (point, path) -> {
                if (counter.getAndIncrement() == stopAt) throw new IOException("injected durable cut");
            })) {
                initial(session);
                assertThrows(IOException.class, session::commit);
            }
            if (cut == 0) {
                assertFalse(Files.exists(paths.workstreamJson()));
                assertFalse(Files.exists(paths.eventsJsonl()));
                // No authoritative plan existed, so the user can legitimately initialize afresh.
                try (var session = open(paths, true, true, NO_FAULT)) { initial(session); session.commit(); }
            } else {
                if (!points.get(cut).startsWith("after_pending_delete"))
                    assertThrows(LedgerSession.PendingCommitException.class, () -> open(paths, false, false, NO_FAULT));
                // Constructor completes only saved bytes; there is no author/reviewer/kernel callback to rerun.
                try (var ignored = open(paths, true, false, NO_FAULT)) {}
            }
            try (var reader = open(paths, false, false, NO_FAULT)) {
                assertEquals("READY", reader.readTree(paths.nodeJson("author-1")).path("status").asText(), "cut " + cut);
                assertEquals(1, reader.readLines(paths.eventsJsonl()).size(), "duplicate event at cut " + cut);
            }
        }
    }

    @Test
    void interruptedEventTailRepairsOnlyThePreparedPrefixAndRejectsDifferentHistory() throws Exception {
        var paths = initialized(temp);
        leavePendingCompletion(paths);
        var old = Files.readString(paths.eventsJsonl());
        var event = completionEvent();
        Files.writeString(paths.eventsJsonl(), old + event.substring(0, event.length() / 2));
        try (var ignored = open(paths, true, false, NO_FAULT)) {}
        assertEquals(old + event, Files.readString(paths.eventsJsonl()));
        try (var session = open(paths, true, false, NO_FAULT)) {
            session.appendEvents(paths.eventsJsonl(), event.getBytes(StandardCharsets.UTF_8));
            session.commit();
        }
        assertEquals(old + event, Files.readString(paths.eventsJsonl()));
        try (var session = open(paths, true, false, NO_FAULT)) {
            assertThrows(IOException.class, () -> session.appendEvents(paths.eventsJsonl(),
                    event.replace("completed", "changed").getBytes(StandardCharsets.UTF_8)));
        }
        var broken = initialized(temp.resolve("broken"));
        leavePendingCompletion(broken);
        Files.writeString(broken.eventsJsonl(), Files.readString(broken.eventsJsonl()).replace("started", "outside"));
        var nodeBefore = Files.readString(broken.nodeJson("author-1"));
        assertThrows(IOException.class, () -> open(broken, true, false, NO_FAULT));
        assertEquals(nodeBefore, Files.readString(broken.nodeJson("author-1")), "validate every target before applying any");
    }

    @Test
    void foreignBytesAndWrongBinaryNeverGetOverwrittenByRecovery() throws Exception {
        var paths = initialized(temp);
        leavePendingCompletion(paths);
        var original = Files.readString(paths.nodeJson("author-1"));
        assertThrows(IOException.class, () -> new LedgerSession(paths, new AtomicJsonWriter(),
                Map.of("executable", "different-build"), true, false, "resume", "resume-1", NO_FAULT));
        assertEquals(original, Files.readString(paths.nodeJson("author-1")));
        Files.writeString(paths.nodeJson("author-1"), "{\"status\":\"outside edit\"}");
        assertThrows(IOException.class, () -> open(paths, true, false, NO_FAULT));
        assertTrue(Files.readString(paths.nodeJson("author-1")).contains("outside edit"));
    }

    @Test
    void readersCannotInterleaveAndOldCloseCannotEraseANewOwnersDiagnostic() throws Exception {
        var paths = initialized(temp);
        var first = open(paths, false, false, NO_FAULT);
        assertThrows(WorkstreamLockedException.class, () -> open(paths, false, false, NO_FAULT));
        first.close();
        try (var next = open(paths, true, false, NO_FAULT)) {
            var owner = paths.workstreamDir().resolve(".mutation-lock-owner.json");
            first.close();
            assertTrue(Files.exists(owner));
            assertThrows(WorkstreamLockedException.class, () -> open(paths, false, false, NO_FAULT));
        }
        try (var ignored = open(paths, false, false, NO_FAULT)) {}
    }

    @Test
    void pendingManifestCannotNameArbitraryFilesOrNoncanonicalAliases() throws Exception {
        var paths = initialized(temp);
        leavePendingCompletion(paths);
        Path manifest;
        try (var files = Files.list(paths.workstreamDir().resolve("pending"))) {
            manifest = files.filter(p -> p.toString().endsWith(".json")).findFirst().orElseThrow();
        }
        var mapper = new AtomicJsonWriter().objectMapper();
        var json = mapper.readTree(manifest.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.path("targets").get(0))
                .put("path", "nodes/../workstream.json");
        mapper.writeValue(manifest.toFile(), json);
        assertThrows(IOException.class, () -> open(paths, true, false, NO_FAULT));
        assertEquals(IDENTITY, mapper.convertValue(mapper.readTree(paths.workstreamJson().toFile()).get("frozenRuntimeIdentity"),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}));
    }

    @Test
    void coldJvmCanFinishAfterAbruptExitWithoutExecutingTheWorkflowAgain() throws Exception {
        var paths = initialized(temp);
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var log = temp.resolve("crash.log");
        var child = new ProcessBuilder(java, "-cp", classpath, ColdCommit.class.getName(), temp.toString(), "crash")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertEquals(27, child.waitFor()));
        assertThrows(LedgerSession.PendingCommitException.class, () -> open(paths, false, false, NO_FAULT));
        var recovery = new ProcessBuilder(java, "-cp", classpath, ColdCommit.class.getName(), temp.toString(), "recover")
                .redirectErrorStream(true).redirectOutput(temp.resolve("recovery.log").toFile()).start();
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> assertEquals(0, recovery.waitFor()));
        try (var reader = open(paths, false, false, NO_FAULT)) {
            assertEquals("COMPLETED", reader.readTree(paths.nodeJson("author-1")).path("status").asText());
            assertEquals(2, reader.readLines(paths.eventsJsonl()).size());
        }
    }

    public static class ColdCommit {
        public static void main(String[] args) throws Exception {
            var paths = paths(Path.of(args[0]));
            if (args[1].equals("recover")) {
                try (var ignored = open(paths, true, false, NO_FAULT)) {}
            } else {
                try (var session = open(paths, true, false, (point, path) -> {
                    if (point.equals("after_replace")) Runtime.getRuntime().halt(27);
                })) { completion(session); session.commit(); }
            }
        }
    }

    private static WorkstreamPaths paths(Path root) { return new WorkstreamPaths(root, new DualId("WS-SESSION")); }
    private static LedgerSession open(WorkstreamPaths paths, boolean write, boolean initialize,
                                      LedgerSession.FaultPoint fault) throws IOException {
        return new LedgerSession(paths, new AtomicJsonWriter(), IDENTITY, write, initialize, "test sequence", "request-1", fault);
    }
    private static WorkstreamPaths initialized(Path root) throws Exception {
        var paths = paths(root);
        try (var session = open(paths, true, true, NO_FAULT)) { initial(session); session.commit(); }
        return paths;
    }
    private static void initial(LedgerSession session) throws IOException {
        var paths = session.paths();
        session.write(paths.workstreamJson(), Map.of("frozenRuntimeIdentity", IDENTITY));
        session.write(paths.nodeJson("author-1"), Map.of("status", "READY"));
        session.write(paths.workflowCurrentJson(), Map.of("lastEventId", "evt-000001", "active", List.of("author-1")));
        session.appendEvents(paths.eventsJsonl(), "{\"event_id\":\"evt-000001\",\"event_type\":\"started\"}\n".getBytes(StandardCharsets.UTF_8));
    }
    private static String completionEvent() {
        return "{\"event_id\":\"evt-000002\",\"event_type\":\"completed\"}\n";
    }
    private static void completion(LedgerSession session) throws IOException {
        session.write(session.paths().nodeJson("author-1"), Map.of("status", "COMPLETED"));
        session.write(session.paths().workflowCurrentJson(), Map.of("lastEventId", "evt-000002", "active", List.of()));
        session.appendEvents(session.paths().eventsJsonl(), completionEvent().getBytes(StandardCharsets.UTF_8));
    }
    private static void leavePendingCompletion(WorkstreamPaths paths) throws Exception {
        try (var session = open(paths, true, false, (point, path) -> {
            if (point.equals("after_manifest")) throw new IOException("injected cut");
        })) { completion(session); assertThrows(IOException.class, session::commit); }
    }
}
