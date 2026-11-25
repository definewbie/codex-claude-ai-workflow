package dev.dual.ledger.file;

/** Machine-owned reference to a prepared external verification submission. */
public record EvidenceSubmission(String requestId, String preparationRef, String preparationSha256,
                                 String manifestRef, String manifestSha256, EvidenceCollectionBaseline baseline) {
    public EvidenceSubmission {
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,80}") || baseline == null
                || !reference(preparationRef) || !reference(manifestRef)
                || !sha(preparationSha256) || !sha(manifestSha256))
            throw new IllegalArgumentException("invalid external evidence submission");
    }
    private static boolean reference(String ref) {
        return ref != null && ref.matches("\\.agent/workstreams/[A-Za-z0-9_-]+/artifacts/verification/[A-Za-z0-9._/-]+")
                && !ref.contains("..") && !ref.contains("//");
    }
    private static boolean sha(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
