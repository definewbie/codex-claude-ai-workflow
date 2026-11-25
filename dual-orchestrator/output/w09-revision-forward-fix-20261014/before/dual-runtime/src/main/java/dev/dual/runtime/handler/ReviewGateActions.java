package dev.dual.runtime.handler;

import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import java.io.IOException;
import java.util.Set;

/** Application entry for human decisions: qualify the subject and commit under the same local session. */
public final class ReviewGateActions {
    private ReviewGateActions() {}

    public static void revise(WorkflowFileLedger ledger, WorkstreamPaths paths, String expectedSubject, String note) throws IOException {
        if (expectedSubject == null || expectedSubject.isBlank()) {
            resolve(ledger, paths, "revise", note);
            return;
        }
        try (var write = ledger.writeSession(paths, "readiness revision", java.util.UUID.randomUUID().toString())) {
            var owned = write.ledger();
            var current = owned.loadCurrent(paths);
            if (!current.activeNodeInstanceIds().equals(java.util.List.of(expectedSubject)))
                throw new IOException("revision subject is no longer current");
            var source = owned.loadNode(paths, expectedSubject);
            if (source.kind() == dev.dual.core.workflow.NodeKind.GATE) {
                EvidenceCollection.resolveGate(owned, paths, owned.loadDefinition(paths), "revise", note);
            } else {
                var lineage = new AuthoredStageLineageResolver(owned, paths.workspaceRoot(), paths.workstreamId().value(), Long.MAX_VALUE);
                var parent = lineage.reviewed(AuthoredStageLineageResolver.Stage.PLAN, source).candidate();
                var json = TaskContractArtifactWriter.mapper();
                var parentMap = json.convertValue(parent, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                var refs = new java.util.ArrayList<String>();
                refs.add(parent.sidecar().ref());
                if (source.result().get("readiness_refs") instanceof java.util.List<?> values)
                    for (var value : values) if (value instanceof java.util.Map<?, ?> ref && ref.get("ref") instanceof String path) refs.add(path);
                owned.reviseReadinessFailure(paths, expectedSubject, note, parentMap, refs);
            }
            write.commit();
        }
    }

    public static void resolve(WorkflowFileLedger ledger, WorkstreamPaths paths, String action, String note) throws IOException {
        try (var write = ledger.writeSession(paths, "human review " + action, java.util.UUID.randomUUID().toString())) {
            var owned = write.ledger();
            var current = owned.loadCurrent(paths);
            var definition = owned.loadDefinition(paths);
            dev.dual.ledger.file.ContractQualificationChecks.Decision checks = null;
            if (Set.of("approve", "approved").contains(action) && current.activeNodeInstanceIds().size() == 1) {
                var subject = owned.loadNode(paths, current.activeNodeInstanceIds().getFirst());
                if ("contract".equals(definition.node(subject.nodeDefinitionId()).config().get("gate_purpose"))) {
                    checks = TaskContractProjectionVerifier.executionCandidate(owned, paths.workspaceRoot(),
                            paths.workstreamId().value(), subject.nodeInstanceId(), true).decisionChecks();
                    if (checks == null) throw new IOException("contract approval lacks actual qualification checks");
                }
            }
            if (checks == null) EvidenceCollection.resolveGate(owned, paths, definition, action, note);
            else owned.resolveOpenGate(paths, definition, action, note, checks);
            write.commit();
        }
    }
}
