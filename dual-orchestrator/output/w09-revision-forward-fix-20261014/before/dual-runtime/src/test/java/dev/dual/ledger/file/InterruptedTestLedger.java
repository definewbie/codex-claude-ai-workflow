package dev.dual.ledger.file;

/** Test-only access to the existing local-commit fault boundary. */
public final class InterruptedTestLedger {
    private InterruptedTestLedger() {}
    public static WorkflowFileLedger onTarget(java.util.function.Predicate<java.nio.file.Path> targetFilter,
                                              java.util.Map<String, String> identity) {
        return new WorkflowFileLedger(new dev.dual.core.flow.BuiltInFlowOrchestrationKernel(), identity,
                (point, target) -> {
                    if (point.equals("before_replace") && targetFilter.test(target))
                        throw new java.io.IOException("interrupted before replacing " + target);
                });
    }
    public static WorkflowFileLedger pauseAt(String point, java.util.Map<String,String> identity,
            java.util.concurrent.CountDownLatch reached, java.util.concurrent.CountDownLatch resume) {
        var fired = new java.util.concurrent.atomic.AtomicBoolean();
        return new WorkflowFileLedger(new dev.dual.core.flow.BuiltInFlowOrchestrationKernel(), identity, (actual,target) -> {
            if (actual.equals(point) && fired.compareAndSet(false,true)) {
                reached.countDown();
                try { if (!resume.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("barrier timed out"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new java.io.IOException(error); }
            }
        });
    }
    public static WorkflowFileLedger at(String point) {
        return at(point, java.util.Map.of("test_binary", "runtime-tests-v3"));
    }
    public static WorkflowFileLedger atWhen(String point, java.util.function.BooleanSupplier selected,
            java.util.Map<String, String> identity) {
        var fired = new java.util.concurrent.atomic.AtomicBoolean();
        return new WorkflowFileLedger(new dev.dual.core.flow.BuiltInFlowOrchestrationKernel(), identity, (actual, target) -> {
            if (actual.equals(point) && selected.getAsBoolean() && fired.compareAndSet(false, true))
                throw new java.io.IOException("interrupted selected phase at " + actual);
        });
    }
    public static WorkflowFileLedger at(String point, java.util.Map<String, String> identity) {
        var fired = new java.util.concurrent.atomic.AtomicBoolean();
        return new WorkflowFileLedger(new dev.dual.core.flow.BuiltInFlowOrchestrationKernel(),
                identity, (actual, target) -> {
                    if (actual.equals(point) && fired.compareAndSet(false, true))
                        throw new java.io.IOException("interrupted at " + actual);
                });
    }
}
