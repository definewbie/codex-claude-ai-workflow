package dev.dual.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class WorkstreamStreamService {
    private static final long DEFAULT_INTERVAL_MS = 2000;
    private static final long MIN_INTERVAL_MS = 500;

    private final WorkstreamQueryService queryService;
    private final ObjectMapper objectMapper;
    private final Map<SseEmitter, ScheduledExecutorService> activeStreams = new ConcurrentHashMap<>();

    public WorkstreamStreamService(WorkstreamQueryService queryService, ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    public SseEmitter overviewStream(Path workspaceRoot, String workstreamId, long intervalMs) {
        var emitter = new SseEmitter(0L);
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "dual-overview-stream-" + workstreamId);
            thread.setDaemon(true);
            return thread;
        });
        activeStreams.put(emitter, executor);
        var lastPayload = new AtomicReference<>("");
        Runnable cleanup = () -> cleanup(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());

        executor.scheduleWithFixedDelay(
                () -> sendSnapshot(emitter, workspaceRoot, workstreamId, lastPayload),
                0,
                safeInterval(intervalMs),
                TimeUnit.MILLISECONDS
        );
        return emitter;
    }

    @PreDestroy
    void closeActiveStreams() {
        for (var entry : new ArrayList<>(activeStreams.entrySet())) {
            shutdown(entry.getValue());
            try {
                entry.getKey().complete();
            } catch (IllegalStateException ignored) {
                // The client or container already closed the async response.
            } finally {
                activeStreams.remove(entry.getKey());
            }
        }
    }

    int activeStreamCount() {
        return activeStreams.size();
    }

    private void sendSnapshot(
            SseEmitter emitter,
            Path workspaceRoot,
            String workstreamId,
            AtomicReference<String> lastPayload
    ) {
        try {
            var overview = queryService.overview(workspaceRoot, workstreamId);
            var payload = objectMapper.writeValueAsString(overview);
            if (!payload.equals(lastPayload.getAndSet(payload))) {
                emitter.send(SseEmitter.event()
                        .name("overview")
                        .data(overview));
            } else {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(heartbeat(workstreamId)));
            }
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(streamError(workstreamId, e)));
            } catch (IOException ignored) {
                // Connection is already broken; shutdown below is enough.
            }
            emitter.complete();
            cleanup(emitter);
        }
    }

    private static Map<String, Object> heartbeat(String workstreamId) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("workstreamId", workstreamId);
        payload.put("at", Instant.now().toString());
        return payload;
    }

    private static Map<String, Object> streamError(String workstreamId, Exception error) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("workstreamId", workstreamId);
        payload.put("error", error.getClass().getSimpleName());
        payload.put("message", error.getMessage() == null ? "" : error.getMessage());
        return payload;
    }

    private static long safeInterval(long intervalMs) {
        if (intervalMs <= 0) {
            return DEFAULT_INTERVAL_MS;
        }
        return Math.max(MIN_INTERVAL_MS, intervalMs);
    }

    private static void shutdown(ScheduledExecutorService executor) {
        executor.shutdownNow();
    }

    private void cleanup(SseEmitter emitter) {
        var executor = activeStreams.remove(emitter);
        if (executor != null) {
            shutdown(executor);
        }
    }
}
