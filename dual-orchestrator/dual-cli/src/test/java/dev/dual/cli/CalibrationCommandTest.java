package dev.dual.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalibrationCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void listShowsCalibrationCandidates() throws Exception {
        writeCandidate("candidate-1", "DRAFT");

        var command = new CalibrationListCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Candidate ID\tStatus\tFailure\tUpdate Type\tDecision\tSource Node"));
        assertTrue(printed.contains("candidate-1\tDRAFT\tCONTRACT_GAP\tCONTRACT_TEMPLATE\tREJECT\tacceptance.classify_failure"));
    }

    @Test
    void showPrintsCandidateMarkdownByDefault() throws Exception {
        writeCandidate("candidate-2", "DRAFT");

        var command = new CalibrationShowCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-2";

        var printed = captureStdout(command::call);

        assertTrue(printed.contains("Calibration Candidate"));
        assertTrue(printed.contains("Status: DRAFT"));
        assertTrue(printed.contains("# Candidate candidate-2"));
    }

    @Test
    void decideUpdatesCandidateStatusAndNotes() throws Exception {
        writeCandidate("candidate-3", "DRAFT");

        var command = new CalibrationDecideCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-3";
        command.status = "accepted";
        command.note = "Promote this into the contract template later.";

        assertEquals(0, command.call());

        var json = Files.readString(candidateJson("candidate-3"), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"status\" : \"ACCEPTED\""));
        assertTrue(json.contains("\"decisionNotes\" : \"Promote this into the contract template later.\""));
        assertTrue(json.contains("\"decidedAt\""));
    }

    @Test
    void planWritesApplyPlanWithoutChangingCandidate() throws Exception {
        writeCandidate("candidate-4", "ACCEPTED");
        var before = Files.readString(candidateJson("candidate-4"), StandardCharsets.UTF_8);

        var command = new CalibrationPlanCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-cal";
        command.candidateId = "candidate-4";

        var printed = captureStdout(command::call);

        var planJson = tempDir.resolve(".agent/workstreams/ws-cal/calibration/apply-plans/candidate-4.json");
        var planMd = tempDir.resolve(".agent/workstreams/ws-cal/calibration/apply-plans/candidate-4.md");
        assertTrue(Files.isRegularFile(planJson));
        assertTrue(Files.isRegularFile(planMd));

        var json = Files.readString(planJson, StandardCharsets.UTF_8);
        assertTrue(json.contains("\"candidateId\" : \"candidate-4\""));
        assertTrue(json.contains("\"candidateStatus\" : \"ACCEPTED\""));
        assertTrue(json.contains("\"proposedTargetArea\" : \"Task contract template or contract compiler checklist\""));
        assertTrue(json.contains("\"status\" : \"PROPOSED\""));
        assertTrue(json.contains("\"templateProposal\""));
        assertTrue(json.contains("\"proposalType\" : \"CONTRACT_TEMPLATE\""));
        assertTrue(json.contains("\"status\" : \"REVIEW_REQUIRED\""));

        var markdown = Files.readString(planMd, StandardCharsets.UTF_8);
        assertTrue(markdown.contains("# Calibration Apply Plan"));
        assertTrue(markdown.contains("## Recommended Human Action"));
        assertTrue(markdown.contains("## Template Proposal"));
        assertTrue(markdown.contains("### Checklist Items"));
        assertTrue(markdown.contains("Add one reusable contract principle"));
        assertTrue(markdown.contains("## Not Applied Automatically"));
        assertTrue(markdown.contains("This command does not modify prompts"));

        assertEquals(before, Files.readString(candidateJson("candidate-4"), StandardCharsets.UTF_8));
        assertTrue(printed.contains("Calibration apply plan created"));
        assertTrue(printed.contains("candidate-4"));
    }

    private String captureStdout(ThrowingCallable callable) throws Exception {
        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, callable.call());
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private void writeCandidate(String candidateId, String status) throws Exception {
        Files.createDirectories(candidateJson(candidateId).getParent());
        Files.writeString(candidateJson(candidateId), """
                {
                  "schemaVersion": 1,
                  "candidateId": "%s",
                  "sourceWorkstreamId": "ws-cal",
                  "sourceNodeId": "acceptance.classify_failure",
                  "failureCategory": "CONTRACT_GAP",
                  "reviewDecision": "REJECT",
                  "matchedRule": 1,
                  "proposedUpdateType": "CONTRACT_TEMPLATE",
                  "reasoning": "Missing contract detail",
                  "suggestedAction": "Revise contract template",
                  "evidenceRefs": [".agent/workstreams/ws-cal/artifacts/failure-classification.json"],
                  "status": "%s"
                }
                """.formatted(candidateId, status), StandardCharsets.UTF_8);
        Files.writeString(candidateJson(candidateId).resolveSibling(candidateId + ".md"),
                "# Candidate " + candidateId + "\n", StandardCharsets.UTF_8);
    }

    private Path candidateJson(String candidateId) {
        return tempDir.resolve(".agent/workstreams/ws-cal/calibration/candidates")
                .resolve(candidateId + ".json");
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        int call() throws Exception;
    }
}
