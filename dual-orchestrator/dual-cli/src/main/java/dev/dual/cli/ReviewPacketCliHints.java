package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowStatus;
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
        out.println("Then: dualctl review approve|revise --workdir " + workspaceRoot + " " + workstreamId);
    }

    private static java.util.Optional<Path> packetPath(Path workspaceRoot, String workstreamId) {
        try {
            var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
            var ledger = new WorkflowFileLedger();
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
