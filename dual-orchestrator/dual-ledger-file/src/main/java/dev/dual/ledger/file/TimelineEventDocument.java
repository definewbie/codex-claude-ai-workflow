package dev.dual.ledger.file;

import dev.dual.core.domain.TimelineEvent;

import java.time.Instant;
import java.util.List;

public record TimelineEventDocument(
        Instant ts,
        String workstreamId,
        String workspaceId,
        String stageId,
        String iterationId,
        String runId,
        String actor,
        String event,
        String fromStatus,
        String toStatus,
        String reason,
        List<String> artifactRefs
) {
    static TimelineEventDocument from(TimelineEvent event) {
        return new TimelineEventDocument(
                event.ts(),
                event.workstreamId() == null ? "" : event.workstreamId().value(),
                event.workspaceId() == null ? "" : event.workspaceId().value(),
                event.stageId(),
                event.iterationId(),
                event.runId() == null ? "" : event.runId().value(),
                event.actor(),
                event.event(),
                event.fromStatus(),
                event.toStatus(),
                event.reason(),
                event.artifactRefs()
        );
    }
}
