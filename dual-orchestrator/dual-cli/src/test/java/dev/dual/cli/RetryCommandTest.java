package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RetryCommandTest {
    @TempDir Path root;

    @Test void cliRetryUsesLedgerDiagnosticWithoutLaunchingProviderOrChangingScope() throws Exception {
        var paths = new WorkstreamPaths(root, new DualId("ws-cli-retry-diagnostic"));
        var definition = new WorkflowDefinition("retry-fixture", 1, "author",
                List.of(new NodeDefinition("author", NodeKind.TASK, "acpx.agent_run", Map.of())), List.of());
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-cli-retry", definition);
        var id = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, id, NodeStatus.FAILED, "AGENT_OUTPUT_CONTRACT_VIOLATION", List.of(),
                Map.of("reason", "AGENT_OUTPUT_CONTRACT_VIOLATION", "parse_error",
                        Map.of("code", "SCOPE_INVALID", "message", "Outside scope", "details", Map.of())));
        var before = ledger.loadNode(paths, id);
        var command = new RetryCommand();
        command.workdir = root;
        command.workstreamId = paths.workstreamId().value();
        command.note = "Retry without changing the approved task";
        assertEquals(0, command.call());
        var after = new WorkflowFileLedger().loadNode(paths, id);
        assertEquals(NodeStatus.READY, after.status());
        assertEquals(before.inputRefs(), after.inputRefs());
        assertEquals(before.iterationId(), after.iterationId());
        assertTrue(((Map<?, ?>) after.result().get("retry_failure_context"))
                .get("diagnostic_text").toString().contains("SCOPE_INVALID"));
        assertFalse(after.result().containsKey("permission_approval"));
    }
}
