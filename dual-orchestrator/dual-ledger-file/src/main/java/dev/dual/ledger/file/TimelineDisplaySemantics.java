package dev.dual.ledger.file;

final class TimelineDisplaySemantics {
    private TimelineDisplaySemantics() {
    }

    static String displayName(NodeInstanceDocument node) {
        if (!"acceptance.classify_failure".equals(node.nodeDefinitionId())) {
            return node.nodeDefinitionId();
        }
        var result = node.result();
        var reviewDecision = result == null ? "" : String.valueOf(result.getOrDefault("review_decision", ""));
        if ("ACCEPT_WITH_NOTES".equals(reviewDecision)) {
            return "acceptance.classify_notes";
        }
        return "acceptance.classify_failure";
    }
}
