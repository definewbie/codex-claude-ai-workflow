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
        resolve(ledger, paths, action, note, null);
    }

    /**
     * Binds the decision to the gate occurrence the decider saw. The check happens inside the write session before
     * any qualification work, so a request composed against a superseded gate is refused rather than applied to
     * whichever gate is open now. Both the contract-approval path and the ordinary path carry the subject.
     */
    public static void resolve(WorkflowFileLedger ledger, WorkstreamPaths paths, String action, String note,
                               String expectedGate) throws IOException {
        try (var write = ledger.writeSession(paths, "human review " + action, java.util.UUID.randomUUID().toString())) {
            if (expectedGate != null && !expectedGate.isBlank()) {
                var active = write.ledger().loadCurrent(paths).activeNodeInstanceIds();
                if (!java.util.List.of(expectedGate).equals(active))
                    throw new IOException("gate decision subject is no longer the open gate: requested " + expectedGate
                            + " but the open gate is " + active);
            }
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
            if (checks == null) EvidenceCollection.resolveGate(owned, paths, definition, action, note, expectedGate);
            else if (expectedGate == null) owned.resolveOpenGate(paths, definition, action, note, checks);
            else owned.resolveOpenGate(paths, definition, action, note, checks, expectedGate);
            write.commit();
        }
    }
}
