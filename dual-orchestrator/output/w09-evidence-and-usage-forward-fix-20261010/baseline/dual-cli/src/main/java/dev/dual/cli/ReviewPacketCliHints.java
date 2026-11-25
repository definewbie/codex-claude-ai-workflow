package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.core.workflow.HumanGateActionSemantics;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.PrintStream;
import java.nio.file.Path;

final class ReviewPacketCliHints {
    private ReviewPacketCliHints() {
    }

    static void print(PrintStream out, WorkflowStatus status, Path workspaceRoot, String workstreamId) {
        if (status != WorkflowStatus.WAITING_FOR_HUMAN) {
            return;
        }
        out.println("Next: dualctl review show --workdir " + workspaceRoot + " " + workstreamId);
        packetPath(workspaceRoot, workstreamId).ifPresent(packet ->
                out.println("Review packet: " + packet));
        printActions(out, workspaceRoot, workstreamId);
    }

    static void printActions(PrintStream out, Path workspaceRoot, String workstreamId) {
        try {
            var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
            var ledger = CliLedgerFactory.create();
            var current = ledger.loadCurrent(paths);
            if (current.status() != WorkflowStatus.WAITING_FOR_HUMAN || current.activeNodeInstanceIds().size() != 1) return;
            var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
            if (node.kind() != NodeKind.GATE) return;
            var gate = ledger.loadOpenGateForNode(paths, node.nodeInstanceId());
            if (gate == null) return;
            var definition = ledger.loadDefinition(paths).node(node.nodeDefinitionId());
            var actions = HumanGateActionSemantics.allowedActions(definition, String.valueOf(gate.result().get("source_outcome")));
            int count = 0;
            for (var action : actions) {
                var prefix = count++ == 0 ? "Then: " : "Or:   ";
                var suffix = " --workdir " + workspaceRoot + " " + workstreamId;
                switch (action) {
                    case "approve" -> out.println(prefix + "dualctl approve --run" + suffix);
                    case "revise" -> out.println(prefix + "dualctl revise --feedback-file feedback.md --run" + suffix);
                    case "request_action" -> out.println(prefix + "dualctl review request-action --gate " + node.nodeInstanceId()
                            + " --request-id REQUEST_ID --expected-result 'EXPECTED_RESULT' --reason 'REASON'" + suffix);
                    case "submit_evidence" -> {
                        out.println(prefix + "dualctl review prepare-evidence" + suffix);
                        out.println("      Then verify unchanged source and use dualctl review submit-evidence --help; explicit files and preparation SHA are required.");
                    }
                    default -> {
                        if (action.matches("[a-z_]+")) out.println(prefix + "dualctl review decide --action " + action
                                + " --note 'DECISION_NOTE' --run" + suffix);
                    }
                }
            }
        } catch (Exception unavailable) {
            out.println("Gate actions unavailable; inspect the current review before making a decision.");
        }
    }

    static java.util.Optional<Path> packetPath(Path workspaceRoot, String workstreamId) {
        try {
            var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
            var ledger = CliLedgerFactory.create();
            var current = ledger.loadCurrent(paths);
            if (current.activeNodeInstanceIds().isEmpty()) {
                return java.util.Optional.empty();
            }
            var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
            if (node.kind() != NodeKind.GATE) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(workspaceRoot.resolve(".agent/workstreams/" + workstreamId
                    + "/reviews/" + node.nodeInstanceId() + ".md"));
        } catch (Exception ignored) {
            return java.util.Optional.empty();
        }
    }
}
