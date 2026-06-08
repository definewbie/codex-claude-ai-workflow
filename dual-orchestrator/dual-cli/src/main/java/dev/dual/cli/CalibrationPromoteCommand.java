package dev.dual.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Component
@Command(name = "promote", description = "Promote an accepted calibration plan into a project principle template.", mixinStandardHelpOptions = true)
public final class CalibrationPromoteCommand implements Callable<Integer> {
    private static final String CONTRACT_TEMPLATE_TARGET = "contract-review-principles.md";
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Parameters(index = "1")
    String candidateId;

    @Override
    public Integer call() throws Exception {
        var normalizedCandidateId = CalibrationCandidateFiles.normalizeCandidateId(candidateId);
        var candidatePath = CalibrationCandidateFiles.candidateJson(workdir, workstreamId, normalizedCandidateId);
        var planPath = CalibrationCandidateFiles.applyPlanJson(workdir, workstreamId, normalizedCandidateId);
        if (!Files.isRegularFile(candidatePath)) {
            System.err.println("Calibration candidate not found: " + candidatePath);
            return 1;
        }
        if (!Files.isRegularFile(planPath)) {
            System.err.println("Calibration apply plan not found. Run calibration plan first: " + planPath);
            return 1;
        }

        var candidate = (ObjectNode) mapper.readTree(candidatePath.toFile());
        var plan = (ObjectNode) mapper.readTree(planPath.toFile());
        var error = validate(candidate, plan);
        if (!error.isBlank()) {
            System.err.println(error);
            return 1;
        }

        var templatesDir = workdir.toAbsolutePath().normalize().resolve(".agent").resolve("templates");
        var target = templatesDir.resolve(CONTRACT_TEMPLATE_TARGET);
        Files.createDirectories(templatesDir);

        var block = promotionBlock(normalizedCandidateId, candidate, plan);
        var existing = Files.isRegularFile(target) ? Files.readString(target, StandardCharsets.UTF_8) : "";
        if (!existing.contains("calibration-candidate: " + normalizedCandidateId)) {
            var prefix = existing.isBlank() ? "# Project Contract Review Principles\n\n" : ensureTrailingBlankLine(existing);
            Files.writeString(target, prefix + block, StandardCharsets.UTF_8);
        }

        var promotedAt = Instant.now().toString();
        candidate.put("status", "PROMOTED");
        candidate.put("promotedAt", promotedAt);
        candidate.put("promotedTarget", workdir.toAbsolutePath().normalize().relativize(target).toString());
        plan.put("status", "PROMOTED");
        plan.put("promotedAt", promotedAt);
        plan.put("promotedTarget", workdir.toAbsolutePath().normalize().relativize(target).toString());
        mapper.writerWithDefaultPrettyPrinter().writeValue(candidatePath.toFile(), candidate);
        mapper.writerWithDefaultPrettyPrinter().writeValue(planPath.toFile(), plan);

        System.out.println("Calibration candidate promoted");
        System.out.println("Workstream: " + workstreamId);
        System.out.println("Candidate: " + normalizedCandidateId);
        System.out.println("Target: " + target);
        System.out.println("Note: promotion only appends a project principle; it does not change built-in prompts or workflow definitions.");
        return 0;
    }

    private String validate(JsonNode candidate, JsonNode plan) {
        if (!"ACCEPTED".equals(candidate.path("status").asText(""))) {
            return "Calibration candidate must be ACCEPTED before promotion.";
        }
        if (!"PROPOSED".equals(plan.path("status").asText(""))) {
            return "Calibration apply plan must be PROPOSED before promotion.";
        }
        if (!"CONTRACT_TEMPLATE".equals(candidate.path("proposedUpdateType").asText(""))) {
            return "Only CONTRACT_TEMPLATE candidates can be promoted in this MVP.";
        }
        var proposal = plan.path("templateProposal");
        if (!"CONTRACT_TEMPLATE".equals(proposal.path("proposalType").asText(""))) {
            return "Apply plan templateProposal must have proposalType CONTRACT_TEMPLATE.";
        }
        if (proposal.path("proposedChange").asText("").isBlank()) {
            return "Apply plan templateProposal.proposedChange is required.";
        }
        return "";
    }

    private String promotionBlock(String normalizedCandidateId, JsonNode candidate, JsonNode plan) {
        var proposal = plan.path("templateProposal");
        var lines = new ArrayList<String>();
        lines.add("## Calibration Candidate: " + normalizedCandidateId);
        lines.add("");
        lines.add("<!-- calibration-candidate: " + normalizedCandidateId + " -->");
        lines.add("");
        lines.add("- Source Workstream: " + candidate.path("sourceWorkstreamId").asText(workstreamId));
        lines.add("- Source Node: " + candidate.path("sourceNodeId").asText(""));
        lines.add("- Failure Category: " + candidate.path("failureCategory").asText(""));
        lines.add("- Promoted At: " + Instant.now());
        lines.add("");
        lines.add("### Principle");
        lines.add("");
        lines.add(proposal.path("proposedChange").asText(""));
        lines.add("");
        lines.add("### Promotion Checklist");
        lines.add("");
        appendArray(lines, proposal.path("checklistItems"));
        lines.add("");
        lines.add("### Evidence");
        lines.add("");
        appendArray(lines, candidate.path("evidenceRefs"));
        lines.add("");
        lines.add("### Regression Suggestion");
        lines.add("");
        lines.add(proposal.path("regressionSuggestion").asText("N/A"));
        lines.add("");
        return String.join("\n", lines);
    }

    private void appendArray(List<String> lines, JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            lines.add("- N/A");
            return;
        }
        for (var item : node) {
            lines.add("- " + item.asText(""));
        }
    }

    private String ensureTrailingBlankLine(String existing) {
        var stripped = existing.stripTrailing();
        return stripped + "\n\n";
    }
}
