package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class CliNextActionHints {
    private CliNextActionHints() {
    }

    static void print(PrintStream out, WorkflowStatus status, Path workspaceRoot, String workstreamId) {
        out.println();
        out.println("Next actions:");
        switch (status) {
            case WAITING_FOR_HUMAN -> printHumanGate(out, workspaceRoot, workstreamId);
            case WAITING_FOR_RECOVERY, FAILED, BLOCKED -> printRecovery(out, workspaceRoot, workstreamId);
            case COMPLETED -> printCompleted(out, workspaceRoot, workstreamId);
            case RUNNING, CREATED -> printContinue(out, workspaceRoot, workstreamId);
        }
    }

    private static void printHumanGate(PrintStream out, Path workspaceRoot, String workstreamId) {
        out.println("  1. Read the current review packet:");
        out.println("     dualctl review show --workdir " + workspaceRoot + " " + workstreamId);
        ReviewPacketCliHints.packetPath(workspaceRoot, workstreamId).ifPresent(packet ->
                out.println("     packet: " + packet));
        out.println("  2. Choose one action after review:");
        out.println("     dualctl approve --workdir " + workspaceRoot + " --run " + workstreamId);
        out.println("     dualctl revise --workdir " + workspaceRoot + " --run --feedback-file feedback.md " + workstreamId);
    }

    private static void printRecovery(PrintStream out, Path workspaceRoot, String workstreamId) {
        out.println("  1. Inspect the recovery summary:");
        out.println("     dualctl recovery show --workdir " + workspaceRoot + " " + workstreamId);
        currentFailurePath(workspaceRoot, workstreamId).ifPresent(path ->
                out.println("     summary: " + path));
        out.println("  2. Fix the external cause, then retry:");
        out.println("     dualctl retry --workdir " + workspaceRoot + " " + workstreamId);
    }

    private static void printCompleted(PrintStream out, Path workspaceRoot, String workstreamId) {
        out.println("  1. Review final report and handoff:");
        finalReportPath(workspaceRoot, workstreamId).ifPresentOrElse(
                path -> out.println("     final report: " + path),
                () -> out.println("     dualctl summary --workdir " + workspaceRoot + " " + workstreamId)
        );
        out.println("  2. Inspect delivery artifacts before commit or PR:");
        out.println("     dualctl summary --workdir " + workspaceRoot + " " + workstreamId);
    }

    private static void printContinue(PrintStream out, Path workspaceRoot, String workstreamId) {
        out.println("  Continue running until the next gate, recovery, failure, or completion:");
        out.println("     dualctl run --workdir " + workspaceRoot + " " + workstreamId);
    }

    private static java.util.Optional<Path> finalReportPath(Path workspaceRoot, String workstreamId) {
        try {
            var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
            var path = paths.workstreamDir().resolve("reports").resolve("latest-final-report.md");
            return Files.exists(path) ? java.util.Optional.of(path) : java.util.Optional.empty();
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<Path> currentFailurePath(Path workspaceRoot, String workstreamId) {
        try {
            var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
            var path = paths.currentFailureMarkdown();
            return Files.exists(path) ? java.util.Optional.of(path) : java.util.Optional.empty();
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }
}
