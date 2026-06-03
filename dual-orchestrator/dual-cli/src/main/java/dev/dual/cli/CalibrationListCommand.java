package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;

@Component
@Command(name = "list", description = "List calibration candidates.", mixinStandardHelpOptions = true)
public final class CalibrationListCommand implements Callable<Integer> {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--status", description = "Filter by candidate status, for example DRAFT or ACCEPTED.")
    String status;

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var candidatesDir = CalibrationCandidateFiles.candidatesDir(workdir, workstreamId);
        if (!Files.isDirectory(candidatesDir)) {
            System.out.println("No calibration candidates found.");
            System.out.println("Candidates dir: " + candidatesDir);
            return 0;
        }

        var candidates = Files.list(candidatesDir)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(this::read)
                .filter(candidate -> status == null || status.isBlank()
                        || text(candidate.status()).equalsIgnoreCase(status))
                .toList();

        if (candidates.isEmpty()) {
            System.out.println("No matching calibration candidates found.");
            System.out.println("Candidates dir: " + candidatesDir);
            return 0;
        }

        System.out.println("Candidate ID\tStatus\tFailure\tUpdate Type\tDecision\tSource Node");
        for (var candidate : candidates) {
            System.out.println(String.join("\t",
                    text(candidate.candidateId()),
                    text(candidate.status()),
                    text(candidate.failureCategory()),
                    text(candidate.proposedUpdateType()),
                    text(candidate.reviewDecision()),
                    text(candidate.sourceNodeId())
            ));
        }
        return 0;
    }

    private CandidateRow read(Path path) {
        try {
            var json = mapper.readTree(path.toFile());
            return new CandidateRow(
                    json.path("candidateId").asText(stripJsonSuffix(path)),
                    json.path("status").asText(""),
                    json.path("failureCategory").asText(""),
                    json.path("proposedUpdateType").asText(""),
                    json.path("reviewDecision").asText(""),
                    json.path("sourceNodeId").asText("")
            );
        } catch (Exception e) {
            return new CandidateRow(stripJsonSuffix(path), "UNREADABLE", "", "", "", "");
        }
    }

    private String stripJsonSuffix(Path path) {
        var name = path.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private record CandidateRow(
            String candidateId,
            String status,
            String failureCategory,
            String proposedUpdateType,
            String reviewDecision,
            String sourceNodeId
    ) {}
}
