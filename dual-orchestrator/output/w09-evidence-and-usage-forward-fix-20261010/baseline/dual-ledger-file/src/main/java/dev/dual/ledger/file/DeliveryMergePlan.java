package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

/** Mutable application lifecycle for one accepted immutable delivery candidate. */
public record DeliveryMergePlan(
        int schemaVersion,
        String workstreamId,
        String sourceWorktree,
        String targetRepo,
        String acceptanceDecision,
        String candidateRef,
        String candidateSha256,
        String baseCommit,
        String baseTree,
        String expectedResultTree,
        List<String> changedFiles,
        List<String> excludedFiles,
        DeliveryMergeStatus status,
        String commit,
        String failureCode,
        String failureReason,
        Instant updatedAt,
        java.util.Map<String, Object> operation
) {
    public DeliveryMergePlan {
        operation = operation == null ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(operation));
        candidateRef = blank(candidateRef);
        candidateSha256 = blank(candidateSha256);
        baseCommit = blank(baseCommit);
        baseTree = blank(baseTree);
        expectedResultTree = blank(expectedResultTree);
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        excludedFiles = excludedFiles == null ? List.of() : List.copyOf(excludedFiles);
        commit = blank(commit);
        failureCode = blank(failureCode);
        failureReason = blank(failureReason);
    }

    public static DeliveryMergePlan create(
            String workstreamId,
            String sourceWorktree,
            String targetRepo,
            String acceptanceDecision,
            String candidateRef,
            String candidateSha256,
            String baseCommit,
            String baseTree,
            String expectedResultTree,
            List<String> changedFiles,
            List<String> excludedFiles,
            DeliveryMergeStatus status,
            String failureCode,
            String failureReason
    ) {
        return new DeliveryMergePlan(
                3, workstreamId, sourceWorktree, targetRepo, acceptanceDecision,
                blank(candidateRef), blank(candidateSha256), blank(baseCommit), blank(baseTree), blank(expectedResultTree),
                List.copyOf(changedFiles), List.copyOf(excludedFiles), status, "", blank(failureCode),
                blank(failureReason), Instant.now(), java.util.Map.of()
        );
    }

    public DeliveryMergePlan withStatus(
            DeliveryMergeStatus nextStatus,
            String nextFailureCode,
            String nextFailureReason
    ) {
        return new DeliveryMergePlan(
                schemaVersion, workstreamId, sourceWorktree, targetRepo, acceptanceDecision,
                candidateRef, candidateSha256, baseCommit, baseTree, expectedResultTree,
                changedFiles, excludedFiles, nextStatus, commit, blank(nextFailureCode), blank(nextFailureReason), Instant.now(), operation
        );
    }

    public DeliveryMergePlan withCommitted(String commitSha) {
        if (commitSha == null || commitSha.isBlank()) throw new IllegalArgumentException("commitSha is required");
        return new DeliveryMergePlan(
                schemaVersion, workstreamId, sourceWorktree, targetRepo, acceptanceDecision,
                candidateRef, candidateSha256, baseCommit, baseTree, expectedResultTree,
                changedFiles, excludedFiles, DeliveryMergeStatus.COMMITTED, commitSha, "", "", Instant.now(), operation
        );
    }

    public DeliveryMergePlan withFailure(String nextFailureCode, String nextFailureReason) {
        return new DeliveryMergePlan(
                schemaVersion, workstreamId, sourceWorktree, targetRepo, acceptanceDecision,
                candidateRef, candidateSha256, baseCommit, baseTree, expectedResultTree,
                changedFiles, excludedFiles, status, commit, blank(nextFailureCode), blank(nextFailureReason), Instant.now(), operation
        );
    }

    public DeliveryMergePlan withOperation(java.util.Map<String, Object> facts) {
        return new DeliveryMergePlan(schemaVersion, workstreamId, sourceWorktree, targetRepo, acceptanceDecision,
                candidateRef, candidateSha256, baseCommit, baseTree, expectedResultTree, changedFiles, excludedFiles,
                status, commit, failureCode, failureReason, Instant.now(), facts);
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
