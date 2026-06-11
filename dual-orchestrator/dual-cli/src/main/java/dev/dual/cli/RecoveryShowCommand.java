package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.RecoveryAssessment;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Component
@Command(name = "show", description = "Show the current workflow recovery summary.", mixinStandardHelpOptions = true)
public final class RecoveryShowCommand implements Callable<Integer> {
    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0")
    String workstreamId;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var summaryPath = paths.currentFailureMarkdown();
        if (!Files.exists(summaryPath)) {
            System.err.println("No current failure summary found: " + workspaceRoot.relativize(summaryPath));
            return 1;
        }

        System.out.println("Current Failure");
        System.out.println("Path: " + workspaceRoot.relativize(summaryPath));
        System.out.println();
        printAssessment(workspaceRoot, paths, workstreamId);
        System.out.print(Files.readString(summaryPath, StandardCharsets.UTF_8));
        return 0;
    }

    private void printAssessment(Path workspaceRoot, WorkstreamPaths paths, String workstreamId) {
        try {
            var ledger = CliLedgerFactory.create();
            var current = ledger.loadCurrent(paths);
            if (current.activeNodeInstanceIds().isEmpty()) {
                return;
            }
            var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
            printAssessment(RecoveryAssessment.from(node, workspaceRoot, workstreamId));
        } catch (Exception ignored) {
            // The raw recovery summary below is still useful if structured assessment fails.
        }
    }

    private static void printAssessment(RecoveryAssessment assessment) {
        System.out.println("Recovery Assessment");
        System.out.println("- Classification: " + assessment.classification());
        System.out.println("- Retry Readiness: " + assessment.retryReadiness());
        System.out.println("- Reason: " + assessment.reason());
        if (!assessment.diagnosticJsonRef().isBlank()) {
            System.out.println("- Diagnostic JSON: " + assessment.diagnosticJsonRef());
        }
        if (!assessment.diagnosticMarkdownRef().isBlank()) {
            System.out.println("- Diagnostic Markdown: " + assessment.diagnosticMarkdownRef());
        }
        System.out.println("- Suggested Command: " + assessment.suggestedCommand());
        System.out.println();
    }
}
