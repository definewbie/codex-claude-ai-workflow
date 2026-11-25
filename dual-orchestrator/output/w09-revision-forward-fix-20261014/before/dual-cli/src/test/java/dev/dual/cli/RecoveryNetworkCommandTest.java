package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryNetworkCommandTest {
    @TempDir Path temp;
    @Test void cliApprovesAndCancelsWithoutRetrying() throws Exception {
        var paths = new WorkstreamPaths(temp.toRealPath(), new DualId("ws-cli-network"));
        var definition = new WorkflowDefinition("custom", 1, "verify.tests", List.of(
                new NodeDefinition("verify.tests", NodeKind.TASK, "acpx.agent_run", Map.of("agent", "claude", "permission_profile", "implement"))), List.of());
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-cli", definition);
        var node = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, node, NodeStatus.FAILED, "failed", List.of(), Map.of());
        var before = ledger.loadCurrent(paths);
        assertEquals(0, new CommandLine(new RecoveryNetworkCommand()).execute("--workdir", temp.toString(),
                "--target", "verify.tests", "--domains", "packages.example.org,api.example.org", "--note", "approved tests", "ws-cli-network"));
        var after = ledger.loadCurrent(paths);
        assertEquals(before.status(), after.status());
        assertEquals(before.activeNodeInstanceIds(), after.activeNodeInstanceIds());
        assertEquals(before.openGateIds(), after.openGateIds());
        assertEquals(before.lastEventId(), after.lastEventId());
        assertTrue(after.commitSequence() > before.commitSequence());
        var view = new ExecutionNetworkGrantStore(ledger).view(paths);
        var grant = (ExecutionNetworkGrantStore.Grant) view.get("grant");
        assertEquals(List.of("api.example.org", "packages.example.org"), grant.domains());
        assertEquals("PENDING", grant.state());
        assertEquals(0, new CommandLine(new RecoveryNetworkCommand()).execute("--workdir", temp.toString(), "--cancel", "ws-cli-network"));
        assertEquals("CANCELLED", ((ExecutionNetworkGrantStore.Grant) new ExecutionNetworkGrantStore(ledger).view(paths).get("grant")).state());
        var cancelled = ledger.loadCurrent(paths);
        assertEquals(before.status(), cancelled.status());
        assertEquals(before.activeNodeInstanceIds(), cancelled.activeNodeInstanceIds());
        assertEquals(before.openGateIds(), cancelled.openGateIds());
        assertEquals(before.lastEventId(), cancelled.lastEventId());
        assertTrue(cancelled.commitSequence() > after.commitSequence());
    }
}
