package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

@Component
@Command(name = "events", description = "Show recent workflow events.", mixinStandardHelpOptions = true)
public final class EventsCommand implements Callable<Integer> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--limit", description = "Number of recent events to show. Defaults to ${DEFAULT-VALUE}.")
    int limit = 20;

    @Option(names = "--raw", description = "Print raw JSONL lines.")
    boolean raw;

    @Option(names = "--type", description = "Filter by event type.")
    String type;

    @Option(names = "--contains", description = "Filter events whose raw JSON contains this text.")
    String contains;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        if (limit <= 0) {
            throw new IllegalArgumentException("--limit must be positive");
        }
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        if (!Files.exists(paths.eventsJsonl())) {
            System.out.println("No events found: " + paths.eventsJsonl());
            return 0;
        }

        var lines = tail(filter(Files.readAllLines(paths.eventsJsonl())), limit);
        for (String line : lines) {
            if (raw) {
                System.out.println(line);
            } else {
                System.out.println(format(line));
            }
        }
        return 0;
    }

    private List<String> filter(List<String> lines) {
        return lines.stream()
                .filter(this::matchesType)
                .filter(this::matchesContains)
                .toList();
    }

    private boolean matchesType(String line) {
        if (type == null || type.isBlank()) {
            return true;
        }
        try {
            var event = objectMapper.readValue(line, Map.class);
            return equalsIgnoreCase(text(event.get("event_type")), type);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean matchesContains(String line) {
        return contains == null || contains.isBlank() || line.contains(contains);
    }

    private static List<String> tail(List<String> lines, int limit) {
        if (lines.size() <= limit) {
            return lines;
        }
        return lines.subList(lines.size() - limit, lines.size());
    }

    private String format(String line) throws Exception {
        var event = objectMapper.readValue(line, Map.class);
        var eventId = text(event.get("event_id"));
        var ts = text(event.get("ts"));
        var type = text(event.get("event_type"));
        var payload = event.get("payload");
        return eventId + "  " + ts + "  " + type + "  " + objectMapper.writeValueAsString(payload);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null
                && left.toLowerCase(Locale.ROOT).equals(right.toLowerCase(Locale.ROOT));
    }
}
