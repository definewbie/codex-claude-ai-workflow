package dev.dual.flow.liteflow;

import com.yomahub.liteflow.core.FlowExecutorHolder;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.flow.FlowAdvanceResult;
import dev.dual.core.flow.FlowExecutionSnapshot;
import dev.dual.core.flow.FlowSignal;
import dev.dual.core.workflow.EdgeCondition;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LiteFlowLifecycleIsolationTest {
    @Test
    void twoInstancesSerializeSameAndDifferentChainNamesAndBothRouteCorrectly() throws Exception {
        for (boolean sameInstance : List.of(false, true)) {
            for (boolean differentChain : List.of(false, true)) {
                var barrier = new BlockingCondition(false);
                var aDefinition = definition("a", barrier, false);
                var bDefinition = definition("b", EdgeCondition.unconditional(), differentChain);
                var before = ruleFiles();
                try (var a = new LiteFlowPrototypeKernel(); var b = new LiteFlowPrototypeKernel()) {
                    var first = start(() -> route(a, aDefinition));
                    Job<FlowAdvanceResult> second = null;
                    Set<Path> activeRules = Set.of();
                    try {
                        assertTrue(barrier.entered.await(10, TimeUnit.SECONDS), "A must execute its real LiteFlow component");
                        activeRules = new java.util.HashSet<>(ruleFiles());
                        activeRules.removeAll(before);
                        assertEquals(1, activeRules.size(), "A has one live compiled rule file");
                        second = start(() -> route(sameInstance ? a : b, bDefinition));
                        assertWaitingForHolder(second, first);
                        for (var rule : activeRules) assertTrue(Files.isRegularFile(rule));
                    } finally {
                        barrier.release.countDown();
                        first.join();
                        if (second != null) second.join();
                    }
                    assertEquals(builtin(aDefinition).transitions(), first.result().transitions());
                    assertNotNull(second);
                    assertEquals(builtin(bDefinition).transitions(), second.result().transitions());
                    assertEquals("a.next", first.result().transitions().getFirst().toNodeDefinitionId());
                    assertEquals("b.next", second.result().transitions().getFirst().toNodeDefinitionId());
                    for (var rule : activeRules) assertFalse(Files.exists(rule), "completed rules are removed");
                }
            }
        }
    }

    @Test
    void closeWaitsForActiveRouteAcrossInstancesAndOnTheSameInstance() throws Exception {
        for (boolean sameInstance : List.of(false, true)) {
            var barrier = new BlockingCondition(false);
            var definition = definition("active", barrier, false);
            try (var a = new LiteFlowPrototypeKernel(); var b = new LiteFlowPrototypeKernel()) {
                var first = start(() -> route(a, definition));
                Job<Void> closing = null;
                try {
                    assertTrue(barrier.entered.await(10, TimeUnit.SECONDS));
                    closing = start(() -> { (sameInstance ? a : b).close(); return null; });
                    assertWaitingForHolder(closing, first);
                } finally {
                    barrier.release.countDown();
                    first.join();
                    if (closing != null) closing.join();
                }
                assertEquals(builtin(definition).transitions(), first.result().transitions());
                assertNotNull(closing);
                closing.result();
                a.close();
                b.close();
                // close is cleanup, not a new terminal protocol. Subsequent valid calls remain legal.
                assertEquals(builtin(definition).transitions(), route(a, definition).transitions());
            }
        }
    }

    @Test
    void failingExecutionReleasesWaitingRouteAndCleansItsRuleFile() throws Exception {
        var barrier = new BlockingCondition(true);
        var before = ruleFiles();
        var good = definition("good", EdgeCondition.unconditional(), true);
        try (var a = new LiteFlowPrototypeKernel(); var b = new LiteFlowPrototypeKernel()) {
            var first = start(() -> route(a, definition("bad", barrier, false)));
            Job<FlowAdvanceResult> second = null;
            Set<Path> activeRules = Set.of();
            try {
                assertTrue(barrier.entered.await(10, TimeUnit.SECONDS));
                activeRules = new java.util.HashSet<>(ruleFiles());
                activeRules.removeAll(before);
                assertEquals(1, activeRules.size());
                second = start(() -> route(b, good));
                assertWaitingForHolder(second, first);
            } finally {
                barrier.release.countDown();
                first.join();
                if (second != null) second.join();
            }
            var error = assertThrows(java.util.concurrent.ExecutionException.class, first::result);
            assertInstanceOf(IllegalStateException.class, error.getCause());
            assertNotNull(second);
            assertEquals(builtin(good).transitions(), second.result().transitions());
            for (var rule : activeRules) assertFalse(Files.exists(rule));
            assertEquals(builtin(good).transitions(), route(a, good).transitions());
        }
    }

    @Test
    void concurrentWorkstreamsPersistOnlyTheirOwnSuccessors() throws Exception {
        var root = Files.createTempDirectory("d08-ledgers-");
        var aPaths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId("ws-a"));
        var bPaths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId("ws-b"));
        var barrier = new BlockingCondition(false);
        var aDefinition = definition("a", barrier, false);
        var bDefinition = definition("b", EdgeCondition.unconditional(), true);
        try (var aKernel = (LiteFlowPrototypeKernel) FlowKernelProvider.create("liteflow");
             var bKernel = (LiteFlowPrototypeKernel) FlowKernelProvider.create("liteflow")) {
            var a = new dev.dual.ledger.file.WorkflowFileLedger(aKernel, Map.of("test_binary", "liteflow-tests-v3"));
            var b = new dev.dual.ledger.file.WorkflowFileLedger(bKernel, Map.of("test_binary", "liteflow-tests-v3"));
            FlowTestLedger.initialize(a, aPaths, "wf-a", aDefinition);
            FlowTestLedger.initialize(b, bPaths, "wf-b", bDefinition);
            var aNode = a.loadCurrent(aPaths).activeNodeInstanceIds().getFirst();
            var bNode = b.loadCurrent(bPaths).activeNodeInstanceIds().getFirst();
            var first = start(() -> {
                a.completeAndRoute(aPaths, aDefinition, aNode, dev.dual.core.workflow.NodeStatus.COMPLETED,
                        "DONE", List.of(), Map.of()); return null;
            });
            Job<Void> second = null;
            try {
                assertTrue(barrier.entered.await(10, TimeUnit.SECONDS));
                second = start(() -> {
                    b.completeAndRoute(bPaths, bDefinition, bNode, dev.dual.core.workflow.NodeStatus.COMPLETED,
                            "DONE", List.of(), Map.of()); return null;
                });
                assertWaitingForHolder(second, first);
            } finally {
                barrier.release.countDown();
                first.join();
                if (second != null) second.join();
            }
            first.result();
            assertNotNull(second);
            second.result();
            assertEquals("a.next", a.loadNode(aPaths, a.loadCurrent(aPaths).activeNodeInstanceIds().getFirst()).nodeDefinitionId());
            assertEquals("b.next", b.loadNode(bPaths, b.loadCurrent(bPaths).activeNodeInstanceIds().getFirst()).nodeDefinitionId());
            assertFalse(Files.readString(aPaths.eventsJsonl()).contains("b.next"));
            assertFalse(Files.readString(bPaths.eventsJsonl()).contains("a.next"));
        } finally {
            try (var files = Files.walk(root)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    @Test
    void invalidAndNoRouteCallsDoNotPoisonLaterRoutesAndTopologyIsObservable() throws Exception {
        try (var kernel = new LiteFlowPrototypeKernel()) {
            assertThrows(IllegalArgumentException.class,
                    () -> kernel.advance(null, FlowExecutionSnapshot.singleActiveNode("wf", "source"),
                            FlowSignal.completed("source", "DONE", Map.of())));
            var definition = definition("valid", EdgeCondition.unconditional(), false);
            assertTrue(kernel.advance(definition, FlowExecutionSnapshot.singleActiveNode("wf", "source"),
                    FlowSignal.completed("source", "NO_ROUTE", Map.of())).transitions().isEmpty());
            assertEquals(builtin(definition).transitions(), route(kernel, definition).transitions());
            System.out.println("D08 topology: pid=" + ProcessHandle.current().pid()
                    + ", kernel=" + kernel.getClass().getName()
                    + ", kernelLoader=" + kernel.getClass().getClassLoader()
                    + ", holderLoader=" + FlowExecutorHolder.class.getClassLoader()
                    + ", holderSource=" + FlowExecutorHolder.class.getProtectionDomain().getCodeSource().getLocation());
            assertSame(kernel.getClass().getClassLoader(), FlowExecutorHolder.class.getClassLoader(),
                    "this proof covers the actual single-loader test deployment");
        }
    }

    private static void assertWaitingForHolder(Job<?> waiting, Job<?> owner) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!waiting.task.isDone() && System.nanoTime() < deadline) {
            var info = ManagementFactory.getThreadMXBean().getThreadInfo(waiting.thread.threadId());
            if (info != null && info.getThreadState() == Thread.State.BLOCKED
                    && info.getLockOwnerId() == owner.thread.threadId()) {
                assertEquals(System.identityHashCode(FlowExecutorHolder.class), info.getLockInfo().getIdentityHashCode());
                return;
            }
            Thread.sleep(1);
        }
        fail("competing advance/close must wait on the active holder lifecycle, not enter or finish concurrently");
    }

    @Test
    void ruleFileCreationFailureDoesNotPoisonTheHolderInAFreshJvm(@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        var blocked = root.resolve("temporary-path"); Files.writeString(blocked, "not a directory");
        var log = root.resolve("child.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-Djava.io.tmpdir=" + blocked, "-cp", System.getProperty("java.class.path"),
                RuleFileFailureChild.class.getName(), blocked.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "bounded offline JVM must finish");
            assertEquals(0, process.exitValue(), Files.readString(log));
            assertTrue(Files.readString(log).contains("RULE_IO_FAILURE_THEN_LEGAL_ROUTE"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    public static final class RuleFileFailureChild {
        public static void main(String[] args) throws Exception {
            var blocked = Path.of(args[0]);
            var workflow = definition("io-recovery", EdgeCondition.unconditional(), false);
            try (var kernel = new LiteFlowPrototypeKernel()) {
                var failure = assertThrows(IllegalStateException.class, () -> route(kernel, workflow));
                assertTrue(failure.getMessage().contains("failed to load LiteFlow"));
                Files.delete(blocked); Files.createDirectory(blocked);
                assertEquals(builtin(workflow).transitions(), route(kernel, workflow).transitions());
                assertTrue(ruleFiles().isEmpty(), "successful continuation cleans generated rules");
                System.out.println("RULE_IO_FAILURE_THEN_LEGAL_ROUTE");
            }
        }
    }

    private static FlowAdvanceResult route(LiteFlowPrototypeKernel kernel, WorkflowDefinition definition) {
        return kernel.advance(definition, FlowExecutionSnapshot.singleActiveNode("wf-" + definition.workflowId(), "source"),
                FlowSignal.completed("source", "DONE", Map.of()));
    }

    private static FlowAdvanceResult builtin(WorkflowDefinition definition) {
        return new BuiltInFlowOrchestrationKernel().advance(definition,
                FlowExecutionSnapshot.singleActiveNode("wf-" + definition.workflowId(), "source"),
                FlowSignal.completed("source", "DONE", Map.of()));
    }

    private static WorkflowDefinition definition(String id, EdgeCondition condition, boolean secondChain) {
        var edges = new ArrayList<EdgeDefinition>();
        if (secondChain) edges.add(new EdgeDefinition("unused", "OTHER", id + ".next"));
        edges.add(new EdgeDefinition("source", "DONE", id + ".next", condition));
        if (secondChain) edges.add(new EdgeDefinition("source", "DONE", id + ".blocked", EdgeCondition.configEquals("absent", "yes")));
        return new WorkflowDefinition(id, 1, "source", List.of(
                new NodeDefinition("source", NodeKind.TASK, "test", Map.of()),
                new NodeDefinition("unused", NodeKind.TASK, "test", Map.of()),
                new NodeDefinition(id + ".next", NodeKind.TASK, "test", Map.of()),
                new NodeDefinition(id + ".blocked", NodeKind.TASK, "test", Map.of())), edges);
    }

    private static Set<Path> ruleFiles() throws Exception {
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(path -> path.getFileName().toString().startsWith("dual-liteflow-"))
                    .collect(Collectors.toSet());
        }
    }

    private static <T> Job<T> start(Callable<T> work) {
        var task = new FutureTask<>(work);
        var thread = new Thread(task, "d08-route");
        thread.setDaemon(true);
        thread.start();
        return new Job<>(thread, task);
    }

    private record Job<T>(Thread thread, FutureTask<T> task) {
        void join() throws Exception {
            thread.join(15000);
            assertFalse(thread.isAlive(), "test route must terminate after barrier release");
        }
        T result() throws Exception { return task.get(1, TimeUnit.SECONDS); }
    }

    /** Barrier inside the real edge component, after load and before emit; no production hook. */
    private static final class BlockingCondition implements EdgeCondition {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);
        final boolean fail;
        BlockingCondition(boolean fail) { this.fail = fail; }
        public boolean evaluate(Map<String, Object> context) {
            if (first.getAndSet(false)) {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                if (fail) throw new IllegalStateException("injected edge failure");
            }
            return true;
        }
        public String type() { return "unconditional"; }
        public Map<String, Object> params() { return Map.of(); }
    }
}
