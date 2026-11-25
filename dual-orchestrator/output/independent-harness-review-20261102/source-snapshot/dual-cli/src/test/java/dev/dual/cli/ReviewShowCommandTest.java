package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewShowCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void showPrintsCurrentGateReviewPacket() throws Exception {
        var workstreamId = "ws-review-show";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var definition = new WorkflowDefinition(
                "test-review-show",
                1,
                "gate.human_design_approval",
                List.of(new NodeDefinition("gate.human_design_approval", NodeKind.GATE, "human.review",
                        Map.of("allowed_actions", List.of("approved", "revise")))),
                List.of()
        );
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-review-show", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        var packetRef = ".agent/workstreams/" + workstreamId + "/reviews/" + node.nodeInstanceId() + ".md";
        var packetPath = tempDir.resolve(packetRef);
        Files.createDirectories(packetPath.getParent());
        Files.writeString(packetPath, """
                # Human Review Packet

                - Gate: gate.human_design_approval

                ## Review Focus

                Read the Codex review.

                ## Authored Stage

                candidate: runs/run-current/outputs/result.contract.json
                active obligation: human-note-1 / Preserve behavior
                revision response: human-note-1 / NOT_ADDRESSED / Requires human decision
                """, StandardCharsets.UTF_8);

        var command = new ReviewShowCommand();
        command.workdir = tempDir;
        command.workstreamId = workstreamId;

        var originalOut = System.out;
        var out = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            assertEquals(0, command.call());
        } finally {
            System.setOut(originalOut);
        }

        var printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("Current Review Gate"));
        assertTrue(printed.contains("gate.human_design_approval"));
        assertTrue(printed.contains(packetRef));
        assertTrue(printed.contains("Read the Codex review."));
        assertTrue(printed.contains(Files.readString(packetPath)), "CLI must not strip authored content from the packet");
    }
}
