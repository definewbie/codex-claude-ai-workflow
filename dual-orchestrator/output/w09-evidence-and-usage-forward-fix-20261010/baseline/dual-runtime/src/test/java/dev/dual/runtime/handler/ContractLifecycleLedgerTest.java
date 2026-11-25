package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ContractLifecycleLedgerTest {

    private static final String WORKSTREAM_ID = "ws-lifecycle-001";

    @Test
    void contractCompileEmitsEventMetadata(@TempDir Path tempDir) throws Exception {
        var fixture = new AuthoredStageSequenceTest.Fixture(tempDir, "README.md", AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, WORKSTREAM_ID);
        var context = fixture.before("contract.compile_execution");
        fixture.runtime.resume(new dev.dual.runtime.WorkflowCommand(fixture.root, WORKSTREAM_ID));
        var result = fixture.ledger.loadNode(fixture.paths, context.instance().nodeInstanceId());
        assertEquals(NodeStatus.COMPLETED, result.status(), result.toString());

        // Verify result contains contract metadata fields
        assertNotNull(result.result().get("contract_id"),
                "result should contain contract_id");
        assertNotNull(result.result().get("contract_status"),
                "result should contain contract_status");
        assertNotNull(result.result().get("generated_at"),
                "result should contain generated_at");
    }

    @Test
    void contractEventTypesExistAsConstants() {
        // Emitted events
        assertEquals("contract_compilation_completed", ContractEventTypes.COMPILATION_COMPLETED);
        assertEquals("failure_classified", ContractEventTypes.FAILURE_CLASSIFIED);

        // Reserved for future gate-layer emission (not yet wired)
        assertEquals("contract_review_completed", ContractEventTypes.REVIEW_COMPLETED);
        assertEquals("contract_approved", ContractEventTypes.APPROVED);
        assertEquals("contract_requires_clarification", ContractEventTypes.REQUIRES_CLARIFICATION);
        assertEquals("contract_scope_reduction_requested", ContractEventTypes.SCOPE_REDUCTION_REQUESTED);
        assertEquals("contract_rejected", ContractEventTypes.REJECTED);
    }

}
