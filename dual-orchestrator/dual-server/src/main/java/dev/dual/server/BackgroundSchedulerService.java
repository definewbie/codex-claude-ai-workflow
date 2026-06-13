package dev.dual.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BackgroundSchedulerService {
    private final SchedulerService schedulerService;
    private final NotificationService notificationService;
    private final boolean enabled;
    private final List<Path> workdirs;
    private final int maxWorkstreams;
    private final int maxStepsPerWorkstream;
    private final boolean autoRecover;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Map<String, Object> lastRun = Map.of(
            "status", "never_run",
            "message", "background scheduler has not run yet"
    );

    public BackgroundSchedulerService(
            SchedulerService schedulerService,
            @Value("${dual.scheduler.enabled:false}") boolean enabled,
            @Value("${dual.scheduler.workdirs:}") String workdirs,
            @Value("${dual.scheduler.max-workstreams:10}") int maxWorkstreams,
            @Value("${dual.scheduler.max-steps-per-workstream:1}") int maxStepsPerWorkstream,
            @Value("${dual.scheduler.auto-recover:false}") boolean autoRecover,
            NotificationService notificationService
    ) {
        this.schedulerService = schedulerService;
        this.notificationService = notificationService;
        this.enabled = enabled;
        this.workdirs = parseWorkdirs(workdirs);
        this.maxWorkstreams = maxWorkstreams;
        this.maxStepsPerWorkstream = maxStepsPerWorkstream;
        this.autoRecover = autoRecover;
    }

    @Scheduled(
            fixedDelayString = "${dual.scheduler.interval-ms:30000}",
            initialDelayString = "${dual.scheduler.initial-delay-ms:5000}"
    )
    public void scheduledTick() {
        runOnce();
    }

    public Map<String, Object> status() {
        var response = new LinkedHashMap<String, Object>();
        response.put("action", "scheduler.background.status");
        response.put("enabled", enabled);
        response.put("running", running.get());
        response.put("workdirs", workdirs.stream().map(path -> path.toAbsolutePath().normalize().toString()).toList());
        response.put("maxWorkstreams", maxWorkstreams);
        response.put("maxStepsPerWorkstream", maxStepsPerWorkstream);
        response.put("autoRecover", autoRecover);
        response.put("notification", notificationService.status());
        response.put("lastRun", lastRun);
        return response;
    }

    public Map<String, Object> runOnce() {
        if (!enabled) {
            return remember(skipped("disabled", "background scheduler is disabled"));
        }
        if (workdirs.isEmpty()) {
            return remember(skipped("no_workdirs", "dual.scheduler.workdirs is empty"));
        }
        if (!running.compareAndSet(false, true)) {
            return skipped("already_running", "background scheduler tick is already running");
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("action", "scheduler.background.tick");
        response.put("startedAt", Instant.now().toString());
        response.put("enabled", true);
        response.put("maxWorkstreams", maxWorkstreams);
        response.put("maxStepsPerWorkstream", maxStepsPerWorkstream);
        response.put("autoRecover", autoRecover);
        var results = new ArrayList<Map<String, Object>>();
        var resumed = 0;
        var autoRecovered = 0;
        var skipped = 0;
        var errors = 0;
        var notificationsSent = 0;
        var notificationsFailed = 0;
        try {
            for (Path workdir : workdirs) {
                try {
                    var result = schedulerService.tick(workdir, maxWorkstreams, maxStepsPerWorkstream, autoRecover);
                    results.add(result);
                    resumed += intValue(result.get("resumed"));
                    autoRecovered += intValue(result.get("autoRecovered"));
                    skipped += intValue(result.get("skipped"));
                    var notification = notificationService.notifySchedulerResult(workdir, result);
                    notificationsSent += intValue(notification.get("sent"));
                    notificationsFailed += intValue(notification.get("failed"));
                } catch (Exception e) {
                    errors++;
                    results.add(error(workdir, e));
                }
            }
            response.put("status", errors == 0 ? "completed" : "completed_with_errors");
            response.put("finishedAt", Instant.now().toString());
            response.put("resumed", resumed);
            response.put("autoRecovered", autoRecovered);
            response.put("skipped", skipped);
            response.put("errors", errors);
            response.put("notificationsSent", notificationsSent);
            response.put("notificationsFailed", notificationsFailed);
            response.put("workdirs", results);
            return remember(response);
        } finally {
            running.set(false);
        }
    }

    private Map<String, Object> remember(Map<String, Object> result) {
        lastRun = result;
        return result;
    }

    private static Map<String, Object> skipped(String reason, String message) {
        var response = new LinkedHashMap<String, Object>();
        response.put("action", "scheduler.background.tick");
        response.put("status", "skipped");
        response.put("reason", reason);
        response.put("message", message);
        response.put("at", Instant.now().toString());
        return response;
    }

    private static Map<String, Object> error(Path workdir, Exception e) {
        var result = new LinkedHashMap<String, Object>();
        result.put("action", "scheduler.tick");
        result.put("workdir", workdir.toAbsolutePath().normalize().toString());
        result.put("status", "error");
        result.put("error", e.getMessage());
        return result;
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<Path> parseWorkdirs(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        var paths = new ArrayList<Path>();
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                paths.add(Path.of(item.strip()));
            }
        }
        return List.copyOf(paths);
    }
}
