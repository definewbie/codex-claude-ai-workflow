package dev.dual.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class NotificationService {
    private static final Set<String> FAILURE_STATUSES = Set.of("WAITING_FOR_RECOVERY", "FAILED", "BLOCKED");

    private final boolean enabled;
    private final String command;
    private final Set<String> notifyOn;
    private final long timeoutMs;
    private final Set<String> delivered = ConcurrentHashMap.newKeySet();
    private volatile Map<String, Object> lastNotification = Map.of(
            "status", "never_run",
            "message", "notification hook has not run yet"
    );

    public NotificationService(
            @Value("${dual.notification.enabled:false}") boolean enabled,
            @Value("${dual.notification.command:}") String command,
            @Value("${dual.notification.on:gate,failure,completion}") String notifyOn,
            @Value("${dual.notification.timeout-ms:10000}") long timeoutMs
    ) {
        this.enabled = enabled;
        this.command = command == null ? "" : command.strip();
        this.notifyOn = parseNotifyOn(notifyOn);
        this.timeoutMs = timeoutMs <= 0 ? 10000 : timeoutMs;
    }

    static NotificationService disabled() {
        return new NotificationService(false, "", "gate,failure,completion", 10000);
    }

    public Map<String, Object> status() {
        var status = new LinkedHashMap<String, Object>();
        status.put("enabled", enabled);
        status.put("configured", !command.isBlank());
        status.put("on", notifyOn);
        status.put("lastNotification", lastNotification);
        return status;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> notifySchedulerResult(Path workdir, Map<String, Object> schedulerResult) {
        var response = new LinkedHashMap<String, Object>();
        response.put("enabled", enabled);
        response.put("configured", !command.isBlank());
        var sent = 0;
        var skipped = 0;
        var failed = 0;
        var workstreams = schedulerResult.get("workstreams");
        if (!(workstreams instanceof Iterable<?> iterable)) {
            response.put("sent", 0);
            response.put("skipped", 0);
            response.put("failed", 0);
            return response;
        }
        for (Object item : iterable) {
            if (!(item instanceof Map<?, ?> raw)) {
                skipped++;
                continue;
            }
            var result = (Map<String, Object>) raw;
            var event = eventFor(result);
            if (event.isBlank() || !notifyOn.contains(event)) {
                skipped++;
                continue;
            }
            var key = notificationKey(workdir, result, event);
            if (!delivered.add(key)) {
                skipped++;
                continue;
            }
            var attempt = send(workdir, result, event);
            if ("sent".equals(attempt.get("status"))) {
                sent++;
            } else if ("skipped".equals(attempt.get("status"))) {
                skipped++;
            } else {
                failed++;
            }
            lastNotification = attempt;
        }
        response.put("sent", sent);
        response.put("skipped", skipped);
        response.put("failed", failed);
        return response;
    }

    static String eventFor(Map<String, Object> result) {
        var status = effectiveStatus(result);
        if ("WAITING_FOR_HUMAN".equals(status)) {
            return "gate";
        }
        if (FAILURE_STATUSES.contains(status)) {
            return "failure";
        }
        if ("COMPLETED".equals(status)) {
            return "completion";
        }
        return "";
    }

    private Map<String, Object> send(Path workdir, Map<String, Object> result, String event) {
        if (!enabled || command.isBlank()) {
            return notificationResult("skipped", workdir, result, event, "notification hook is disabled", "", "", -1);
        }
        try {
            var process = new ProcessBuilder(shellCommand(command))
                    .directory(workdir.toFile())
                    .redirectErrorStream(true);
            var env = process.environment();
            env.put("DUAL_WORKDIR", workdir.toAbsolutePath().normalize().toString());
            env.put("DUAL_WORKSTREAM_ID", text(result.get("workstreamId")));
            env.put("DUAL_STATUS", effectiveStatus(result));
            env.put("DUAL_EVENT", event);
            env.put("DUAL_ACTIVE_NODE", text(result.get("activeNode")));
            env.put("DUAL_MESSAGE", text(result.get("message")));
            var child = process.start();
            var stdout = CompletableFuture.supplyAsync(() -> readStream(child));
            var finished = child.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                child.destroyForcibly();
                return notificationResult("failed", workdir, result, event, "notification command timed out", "", "", -1);
            }
            var status = child.exitValue() == 0 ? "sent" : "failed";
            return notificationResult(
                    status,
                    workdir,
                    result,
                    event,
                    status.equals("sent") ? "notification command completed" : "notification command failed",
                    stdout.get(1, TimeUnit.SECONDS),
                    "",
                    child.exitValue()
            );
        } catch (Exception e) {
            return notificationResult("failed", workdir, result, event, e.getMessage(), "", "", -1);
        }
    }

    private static Map<String, Object> notificationResult(
            String status,
            Path workdir,
            Map<String, Object> result,
            String event,
            String message,
            String stdout,
            String stderr,
            int exitCode
    ) {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", status);
        map.put("event", event);
        map.put("workdir", workdir.toAbsolutePath().normalize().toString());
        map.put("workstreamId", text(result.get("workstreamId")));
        map.put("workflowStatus", effectiveStatus(result));
        map.put("activeNode", text(result.get("activeNode")));
        map.put("message", message == null ? "" : message);
        map.put("exitCode", exitCode);
        map.put("stdout", abbreviate(stdout));
        map.put("stderr", abbreviate(stderr));
        map.put("at", Instant.now().toString());
        return map;
    }

    private static String notificationKey(Path workdir, Map<String, Object> result, String event) {
        return workdir.toAbsolutePath().normalize()
                + "|" + text(result.get("workstreamId"))
                + "|" + event
                + "|" + effectiveStatus(result)
                + "|" + text(result.get("activeNode"));
    }

    private static String effectiveStatus(Map<String, Object> result) {
        var status = text(result.get("status"));
        if (!status.isBlank()) {
            return status;
        }
        var reason = text(result.get("reason"));
        return switch (reason) {
            case "WAITING_FOR_HUMAN", "WAITING_FOR_RECOVERY", "FAILED", "BLOCKED", "COMPLETED" -> reason;
            default -> "";
        };
    }

    private static Set<String> parseNotifyOn(String value) {
        if (value == null || value.isBlank()) {
            return Set.of("gate", "failure", "completion");
        }
        var parsed = ConcurrentHashMap.<String>newKeySet();
        for (String item : value.split(",")) {
            var normalized = item.strip().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                parsed.add(normalized);
            }
        }
        return Set.copyOf(parsed);
    }

    private static String[] shellCommand(String command) {
        var os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return new String[]{"cmd", "/c", command};
        }
        return new String[]{"sh", "-c", command};
    }

    private static String readStream(Process child) {
        try {
            var output = new ByteArrayOutputStream();
            child.getInputStream().transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 2000) {
            return value == null ? "" : value;
        }
        return value.substring(0, 2000);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
