package dev.dual.runtime.handler;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.RuntimeTestLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Old mutable projection bytes cannot provide contract authority at any consuming boundary. */
class LegacyContractConsumerRejectionTest {
    @TempDir Path root;
    @Test void mandatoryConsumersRejectAbsentPhaseEvenWithACompleteMutableContract() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, "src/approved.txt");
        var source = f.before("implement.claude_implement");
        var qualified = TaskContractProjectionVerifier.resolve(f.ledger, root, f.ws).qualified().orElseThrow();
        Files.copy(root.resolve(qualified.contractRef()), f.paths.workstreamDir().resolve("artifacts/task-contract.json"));
        for (var handler : List.<TaskHandler>of(new ArtifactValidateHandler(), new FailureClassifyHandler(), new ReportGenerateFinalHandler())) {
            var definition = new NodeDefinition("missing.phase", NodeKind.TASK, handler.type(), Map.of());
            var result = handler.execute(new TaskHandler.TaskContext(root, f.ws, definition, source.instance(), f.ledger));
            assertEquals(NodeStatus.FAILED, result.status(), handler.type());
            assertTrue(result.result().toString().contains("CONTRACT_PHASE_MISSING"), result.toString());
            assertTrue(result.outputRefs().isEmpty(), "no report or evidence is published before qualification");
        }
    }

    @Test void contextAndGateDoNotDisplayMutableContractAsTheCurrentApprovedGoal() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(root, "src/approved.txt");
        var goal = TaskContractProjectionVerifier.resolve(f.ledger, root, f.ws, dev.dual.core.contract.TaskContract.Phase.GOAL).qualified().orElseThrow();
        Files.writeString(f.paths.workstreamDir().resolve("artifacts/task-contract.json"), goal.rawJson().replace("Verify output", "FORGED STABLE OBJECTIVE"));
        f.advanceToGate();
        var packet = Files.readString(f.paths.workstreamDir().resolve("reviews/" + f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst() + ".json"));
        assertFalse(packet.contains("FORGED STABLE OBJECTIVE"));
        var parsed = new com.fasterxml.jackson.databind.ObjectMapper().readTree(packet);
        assertFalse(parsed.isMissingNode()); // gate projection must remain readable after the forged stable file
        assertEquals(goal.sha256(), TaskContractProjectionVerifier.resolve(f.ledger, root, f.ws, dev.dual.core.contract.TaskContract.Phase.GOAL).qualified().orElseThrow().sha256());
        var manifest = EvidenceManifest.rebuild(f.ledger, root, f.paths.workstreamDir(), f.ws, List.of(), new com.fasterxml.jackson.databind.ObjectMapper());
        assertFalse(manifest.toString().contains("artifacts/task-contract.json"));
        assertFalse(manifest.toString().contains("FORGED STABLE OBJECTIVE"));
    }
}
