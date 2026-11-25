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
        var ledger = CliLedgerFactory.create();
        try (var read = ledger.readSession(paths)) {
            var current = read.ledger().loadCurrent(paths);
            System.out.println("Recovery snapshot: " + current.status() + " / commit " + current.commitSequence());
            if (current.activeNodeInstanceIds().isEmpty()) return 0;
            var node = read.ledger().loadNode(paths, current.activeNodeInstanceIds().getFirst());
            printAssessment(RecoveryAssessment.from(node, workspaceRoot, workstreamId));
            var decision = dev.dual.runtime.handler.ExecutionRecoveryAdjudication.describe(read.ledger(), paths);
            if (!decision.isEmpty()) System.out.println(new dev.dual.ledger.file.AtomicJsonWriter().objectMapper().writeValueAsString(decision));
            var summary = paths.currentFailureMarkdown();
            var markdown = read.ledger().failureSummarySnapshot(paths);
            if (!markdown.isBlank()) {
                System.out.println("Failure report: " + summary);
                System.out.print(markdown);
            }
            return 0;
        } catch (dev.dual.ledger.file.WorkstreamLockedException busy) {
            System.err.println("BUSY: " + busy.getMessage()); return 2;
        } catch (dev.dual.ledger.file.LedgerSession.PendingCommitException pending) {
            System.err.println("PENDING: " + pending.getMessage()); return 2;
        } catch (java.io.IOException invalid) {
            System.err.println("INVALID: " + invalid.getMessage()); return 2;
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
        if (!assessment.permissionRequest().isEmpty()) {
            System.out.println("- Permission Request: " + assessment.permissionRequest());
            System.out.println("- Allowed Actions: " + assessment.allowedActions());
        }
        System.out.println("- Suggested Command: " + assessment.suggestedCommand());
        System.out.println();
    }
}
