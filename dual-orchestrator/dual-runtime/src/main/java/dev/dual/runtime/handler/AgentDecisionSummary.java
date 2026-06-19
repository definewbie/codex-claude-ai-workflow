package dev.dual.runtime.handler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record AgentDecisionSummary(
        String nodeId,
        String stageOutcome,
        String decision,
        String summary,
        boolean blocking,
        List<String> mustFix,
        List<String> missingEvidence,
        List<String> openRisks,
        List<String> changedFiles,
        String verificationSummary,
        AgentEvidenceState evidenceState,
        String sidecarPath
) {
    static AgentDecisionSummary empty() {
        return new AgentDecisionSummary(
                "", "", "", "", false, List.of(), List.of(), List.of(), List.of(), "",
                new AgentEvidenceState(false, 0, 0, 0, 0, 0), ""
        );
    }

    static AgentDecisionSummary from(AgentOutputContractDocument document, String sidecarPath) {
        return new AgentDecisionSummary(
                document.nodeId(),
                document.canonicalStageOutcome(),
                document.decision(),
                document.summary(),
                document.blocking(),
                document.mustFix(),
                document.missingEvidence(),
                document.openRisks(),
                document.changedFiles(),
                document.verificationSummary(),
                AgentEvidenceState.from(document),
                sidecarPath
        );
    }

    boolean isEmpty() {
        return !hasDecision();
    }

    boolean hasDecision() {
        return !decision.isBlank();
    }

    Map<String, Object> toReviewPacketMap() {
        if (isEmpty()) {
            return Map.of();
        }
        var summary = new LinkedHashMap<String, Object>();
        summary.put("nodeId", nodeId);
        summary.put("stageOutcome", stageOutcome);
        summary.put("lifecycleStatus", stageOutcome);
        summary.put("decision", decision);
        summary.put("summary", this.summary);
        summary.put("blocking", blocking);
        summary.put("mustFix", mustFix);
        summary.put("missingEvidence", missingEvidence);
        summary.put("openRisks", openRisks);
        summary.put("changedFiles", changedFiles);
        summary.put("verificationSummary", verificationSummary);
        summary.put("evidenceState", evidenceState.toMap());
        summary.put("sidecarPath", sidecarPath);
        return summary;
    }
}
