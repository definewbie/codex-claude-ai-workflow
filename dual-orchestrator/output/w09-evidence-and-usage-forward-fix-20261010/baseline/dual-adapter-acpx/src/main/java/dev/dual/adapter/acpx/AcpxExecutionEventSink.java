package dev.dual.adapter.acpx;

@FunctionalInterface
interface AcpxExecutionEventSink {
    AcpxExecutionEventSink NOOP = event -> {
    };

    void accept(AcpxExecutionEvent event);

    default boolean permissionRepliesEnabled() { return false; }
    default void permissionRequest(java.util.Map<String, Object> request) throws java.io.IOException {
        throw new java.io.IOException("no durable native permission request owner");
    }
    default java.util.Optional<java.util.Map<String, Object>> permissionReply(String key) throws java.io.IOException {
        return java.util.Optional.empty();
    }
    default void permissionObservation(String key, String phase, java.util.Map<String, Object> value) throws java.io.IOException {}
    default boolean permissionRepliesClosed() throws java.io.IOException { return false; }
}
