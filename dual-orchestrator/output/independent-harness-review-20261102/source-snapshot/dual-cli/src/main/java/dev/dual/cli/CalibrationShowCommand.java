package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "show", description = "Show a calibration candidate.", mixinStandardHelpOptions = true)
public final class CalibrationShowCommand implements Callable<Integer> {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Option(names = "--json", description = "Print the candidate JSON instead of markdown.")
    boolean json;

    @Parameters(index = "0")
    String workstreamId;

    @Parameters(index = "1")
    String candidateId;

    @Override
    public Integer call() throws Exception {
        var jsonPath = CalibrationCandidateFiles.candidateJson(workdir, workstreamId, candidateId);
        if (!Files.isRegularFile(jsonPath)) {
            System.err.println("Calibration candidate not found: " + jsonPath);
            return 1;
        }

        if (json) {
            System.out.print(Files.readString(jsonPath, StandardCharsets.UTF_8));
            return 0;
        }

        var tree = mapper.readTree(jsonPath.toFile());
        var mdPath = CalibrationCandidateFiles.candidateMarkdown(jsonPath);
        System.out.println("Calibration Candidate");
        System.out.println("Candidate: " + tree.path("candidateId").asText(CalibrationCandidateFiles.normalizeCandidateId(candidateId)));
        System.out.println("Status: " + tree.path("status").asText("(unknown)"));
        System.out.println("Failure: " + tree.path("failureCategory").asText("(unknown)"));
        System.out.println("Update Type: " + tree.path("proposedUpdateType").asText("(unknown)"));
        System.out.println("Path: " + jsonPath);
        System.out.println();
        if (Files.isRegularFile(mdPath)) {
            System.out.print(Files.readString(mdPath, StandardCharsets.UTF_8));
        } else {
            System.out.print(Files.readString(jsonPath, StandardCharsets.UTF_8));
        }
        return 0;
    }
}
