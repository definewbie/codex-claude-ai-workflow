package dev.dual.core.domain;

import dev.dual.core.id.DualId;

import java.time.Instant;
import java.util.List;

public record TimelineEvent(
        Instant ts,
        DualId workstreamId,
        DualId workspaceId,
        String stageId,
        String iterationId,
        DualId runId,
        String actor,
        String event,
        String fromStatus,
        String toStatus,
        String reason,
        List<String> artifactRefs
) {
    public static TimelineEvent of(DualId workstreamId, DualId workspaceId, String actor, String event, String reason) {
        return of(workstreamId, workspaceId, null, actor, event, reason);
    }

    public static TimelineEvent of(DualId workstreamId, DualId workspaceId, DualId runId, String actor, String event, String reason) {
        return new TimelineEvent(
                Instant.now(),
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                runId,
                actor,
                event,
                "",
                "",
                reason,
                List.of()
        );
    }
}
