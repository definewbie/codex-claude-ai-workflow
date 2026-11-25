package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryMergePlanTest {
    @Test
    void explicitUnknownProcessFieldsRemainNullableAndImmutable() throws Exception {
        var facts = new java.util.LinkedHashMap<String,Object>(); facts.put("process", null); facts.put("application_closed", false);
        var plan = DeliveryMergePlan.create("ws", "/source", "/target", "approved", "candidate", "sha", "base", "tree", "expected",
                java.util.List.of("README.md"), java.util.List.of(), DeliveryMergeStatus.UNKNOWN, "", "").withOperation(facts);
        facts.put("process", "changed external map");
        assertEquals(null, plan.operation().get("process"));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> plan.operation().put("process", "new"));
        var mapper = new ObjectMapper().findAndRegisterModules();
        assertEquals(plan.operation(), mapper.readValue(mapper.writeValueAsBytes(plan), DeliveryMergePlan.class).operation());
    }

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
