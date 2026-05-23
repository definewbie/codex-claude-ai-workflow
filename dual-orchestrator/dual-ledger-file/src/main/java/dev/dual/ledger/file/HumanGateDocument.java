package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

public record HumanGateDocument(
        int schemaVersion,
        String gateId,
        String type,
        String status,
        String workstreamId,
        String stageId,
        String iterationId,
        String reviewPackagePath,
        List<String> allowedDecisions,
        Instant openedAt,
        Instant decidedAt,
        String decision
) {
    static HumanGateDocument openDesignGate(String workstreamId, String reviewPackagePath) {
        return new HumanGateDocument(
                1,
                "gate-design-001",
                "design_human_review",
                "open",
                workstreamId,
                "stage-design",
                "iter-001",
                reviewPackagePath,
                List.of("approve", "revise"),
                Instant.now(),
                null,
                ""
        );
    }

    HumanGateDocument decided(String nextStatus, String humanDecision) {
        return new HumanGateDocument(
                schemaVersion,
                gateId,
                type,
                nextStatus,
                workstreamId,
                stageId,
                iterationId,
                reviewPackagePath,
                allowedDecisions,
                openedAt,
                Instant.now(),
                humanDecision
        );
    }
}
