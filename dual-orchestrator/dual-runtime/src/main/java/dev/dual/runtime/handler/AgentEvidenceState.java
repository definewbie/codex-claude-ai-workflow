package dev.dual.runtime.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record AgentEvidenceState(
        boolean blocking,
        int evidenceCount,
        int missingEvidenceCount,
        int mustFixCount,
        int acceptanceNoteCount,
        int openRiskCount
) {
    static AgentEvidenceState from(AgentStructuredArtifactDocument document) {
        return new AgentEvidenceState(
                document.blocking(),
                document.evidence().size(),
                document.missingEvidence().size(),
                document.mustFix().size(),
                document.acceptanceNotes().size(),
                document.openRisks().size()
        );
    }

    static AgentEvidenceState from(AgentOutputContractDocument document) {
        return new AgentEvidenceState(
                document.blocking(),
                0,
                document.missingEvidence().size(),
                document.mustFix().size(),
                0,
                document.openRisks().size()
        );
    }

    static AgentEvidenceState fromSidecar(Map<String, Object> document) {
        return new AgentEvidenceState(
                booleanValue(document.get("blocking")),
                listSize(document.get("evidence")),
                listSize(document.get("missing_evidence")),
                listSize(document.get("must_fix")),
                listSize(document.get("acceptance_notes")),
                listSize(document.get("open_risks"))
        );
    }

    boolean hasBlockingDecisionIssues() {
        return blocking || mustFixCount > 0;
    }

    boolean hasMaterialEvidenceGaps() {
        return missingEvidenceCount > 0;
    }

    boolean hasBlockingIssues() {
        return blocking || mustFixCount > 0 || missingEvidenceCount > 0;
    }

    Map<String, Object> toMap() {
        var result = new LinkedHashMap<String, Object>();
        result.put("blocking", blocking);
        result.put("evidence_count", evidenceCount);
        result.put("missing_evidence_count", missingEvidenceCount);
        result.put("must_fix_count", mustFixCount);
        result.put("acceptance_note_count", acceptanceNoteCount);
        result.put("open_risk_count", openRiskCount);
        return result;
    }

    private static int listSize(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
