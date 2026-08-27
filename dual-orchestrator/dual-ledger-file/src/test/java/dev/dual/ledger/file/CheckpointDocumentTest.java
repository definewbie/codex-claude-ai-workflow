package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointDocumentTest {
    @Test
    void initialHasCorrectDefaults() {
        var checkpoint = CheckpointDocument.initial("ws-001", "workspace-001");

        assertEquals(1, checkpoint.schemaVersion());
        assertEquals("checkpoint-initial", checkpoint.checkpointId());
        assertEquals("ws-001", checkpoint.workstreamId());
        assertEquals("workspace-001", checkpoint.workspaceId());
        assertEquals("stage-design", checkpoint.stageId());
        assertEquals("iter-001", checkpoint.iterationId());
        assertEquals("stable", checkpoint.kind());
        assertEquals("workstream_created", checkpoint.lastCompletedAction());
        assertEquals("claude_design", checkpoint.pendingAction());
        assertEquals("start_created_design_workstream", checkpoint.resumeReason());
        assertTrue(checkpoint.requiredArtifactRefs().contains("artifact-requirement"));
        assertEquals("reuse_or_recreate_by_transport_health", checkpoint.sessionStrategy());
        assertTrue(checkpoint.resumeCommand().contains("ws-001"));
        assertNotNull(checkpoint.createdAt());
    }
}
