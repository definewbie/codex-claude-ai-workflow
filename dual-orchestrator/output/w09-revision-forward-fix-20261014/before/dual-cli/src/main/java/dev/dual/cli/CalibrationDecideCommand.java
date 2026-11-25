package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

@Component
@Command(name = "decide", description = "Mark a calibration candidate as accepted, rejected, or deferred.", mixinStandardHelpOptions = true)
public final class CalibrationDecideCommand implements Callable<Integer> {
    private static final Set<String> ALLOWED_STATUSES = Set.of("ACCEPTED", "REJECTED", "DEFERRED");
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--status", required = true, description = "Decision status: accepted, rejected, or deferred.")
    String status;

    @Option(names = "--note", description = "Human decision note.")
    String note;

    @Parameters(index = "0")
    String workstreamId;

    @Parameters(index = "1")
    String candidateId;

    @Override
    public Integer call() throws Exception {
        var normalizedStatus = normalizeStatus(status);
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            System.err.println("--status must be accepted, rejected, or deferred");
            return 1;
        }

        var jsonPath = CalibrationCandidateFiles.candidateJson(workdir, workstreamId, candidateId);
        if (!Files.isRegularFile(jsonPath)) {
            System.err.println("Calibration candidate not found: " + jsonPath);
            return 1;
        }

        var tree = (ObjectNode) mapper.readTree(jsonPath.toFile());
        tree.put("status", normalizedStatus);
        tree.put("decidedAt", Instant.now().toString());
        if (note != null && !note.isBlank()) {
            tree.put("decisionNotes", note);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), tree);

        System.out.println("Calibration candidate decided");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Candidate: " + tree.path("candidateId").asText(CalibrationCandidateFiles.normalizeCandidateId(candidateId)));
        System.out.println("Status: " + normalizedStatus);
        System.out.println("Path: " + jsonPath);
        return 0;
    }

    private String normalizeStatus(String value) {
        if (value == null) {
            return "";
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "accept", "accepted" -> "ACCEPTED";
            case "reject", "rejected" -> "REJECTED";
            case "defer", "deferred" -> "DEFERRED";
            default -> value.strip().toUpperCase(Locale.ROOT);
        };
    }
}
