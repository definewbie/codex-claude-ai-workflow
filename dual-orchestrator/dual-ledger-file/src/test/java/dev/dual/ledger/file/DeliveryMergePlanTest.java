package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryMergePlanTest {
    @Test
    void missingFailureCodeFromOlderPlanRemainsReadable() throws Exception {
        var plan = new ObjectMapper().findAndRegisterModules().readValue("""
                {
                  "schemaVersion": 2,
                  "workstreamId": "ws",
                  "sourceWorktree": "/source",
                  "targetRepo": "/target",
                  "acceptanceDecision": "approved",
                  "candidateRef": "candidate.json",
                  "candidateSha256": "abc",
                  "baseCommit": "base",
                  "baseTree": "tree",
                  "expectedResultTree": "result",
                  "changedFiles": ["README.md"],
                  "excludedFiles": [],
                  "status": "FAILED",
                  "commit": "",
                  "failureReason": "legacy failure",
                  "updatedAt": "2026-08-28T00:00:00Z"
                }
                """, DeliveryMergePlan.class);

        assertEquals("", plan.failureCode());
        assertEquals("legacy failure", plan.failureReason());
    }
}
