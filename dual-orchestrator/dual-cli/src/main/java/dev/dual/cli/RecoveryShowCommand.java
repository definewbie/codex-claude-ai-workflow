package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
            var ledger = new WorkflowFileLedger();
            var current = ledger.loadCurrent(paths);
            if (current.activeNodeInstanceIds().isEmpty()) {
                return;
            }
            var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
            var assessment = RecoveryAssessment.from(node, workspaceRoot, workstreamId);
            assessment.print();
        } catch (Exception ignored) {
            // The raw recovery summary below is still useful if structured assessment fails.
        }
    }

    private record RecoveryAssessment(
            String classification,
            String retryReadiness,
            String reason,
            String nextCommand
    ) {
        static RecoveryAssessment from(NodeInstanceDocument node, Path workspaceRoot, String workstreamId) {
            var result = node.result() == null ? Map.<String, Object>of() : node.result();
            var nodeStatus = node.status().name();
            var failureReason = text(firstNonBlank(
                    result.get("acpx_failure.reason"),
                    result.get("reason"),
                    result.get("error"),
                    result.get("message")
            ));
            var recoverable = Boolean.TRUE.equals(result.get("acpx_failure.recoverable"))
                    || Boolean.TRUE.equals(result.get("recoverable"));
            var retry = "dualctl retry --workdir " + workspaceRoot + " " + workstreamId;
            var abort = "dualctl recovery abort --workdir " + workspaceRoot + " " + workstreamId;

            if ("revision_limit_reached".equals(failureReason) || "BLOCKED".equals(nodeStatus)) {
                return new RecoveryAssessment(
                        "semantic_revision_limit",
                        "NEEDS_HUMAN_DECISION",
                        "The workflow exhausted its revision budget. Blind retry will likely repeat the same loop.",
                        "Inspect evidence, adjust the task/contract, then run `" + retry + "`; or stop with `" + abort + "`."
                );
            }
            if (recoverable) {
                return new RecoveryAssessment(
                        "recoverable_agent_runtime",
                        "RETRY_AFTER_EXTERNAL_FIX",
                        "The failed node is marked recoverable. Fix the external cause first, such as quota, reconnect, or session state.",
                        retry
                );
            }
            if (failureReason.contains("DUAL_CONTROL") || failureReason.contains("CONTRACT")) {
                return new RecoveryAssessment(
                        "artifact_contract_failure",
                        "RETRY_AFTER_OUTPUT_CONTRACT_FIX",
                        "The agent output contract was invalid or missing. Inspect the output before retrying.",
                        retry
                );
            }
            return new RecoveryAssessment(
                    "manual_recovery_required",
                    "INSPECT_BEFORE_RETRY",
                    "The failure is not marked recoverable. Read the evidence before deciding whether retry is safe.",
                    retry + "  # only after fixing the cause"
            );
        }

        void print() {
            System.out.println("Recovery Assessment");
            System.out.println("- Classification: " + classification);
            System.out.println("- Retry Readiness: " + retryReadiness);
            System.out.println("- Reason: " + reason);
            System.out.println("- Suggested Command: " + nextCommand);
            System.out.println();
        }

        private static Object firstNonBlank(Object... values) {
            for (var value : values) {
                if (value != null && !value.toString().isBlank()) {
                    return value;
                }
            }
            return "";
        }

        private static String text(Object value) {
            return value == null ? "" : value.toString();
        }
    }
}
