package dev.dual.adapter.acpx;

@FunctionalInterface
interface AcpxExecutionEventSink {
    AcpxExecutionEventSink NOOP = event -> {
    };

    void accept(AcpxExecutionEvent event);
}
