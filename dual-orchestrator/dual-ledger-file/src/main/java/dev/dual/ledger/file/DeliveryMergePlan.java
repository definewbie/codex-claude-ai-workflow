package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

/**
 * Delivery merge 的权威 ledger artifact。
 *
 * <p>它记录“已验收 worktree 是否已经安全合回 original repo”。这个状态不属于
 * workflow runtime，也不参与 resume/retry；CLI、UI 和报告只能读取它来展示交付合并状态。</p>
 */
public record DeliveryMergePlan(
        int schemaVersion,
        String workstreamId,
        String sourceWorktree,
        String targetRepo,
        String acceptanceDecision,
        List<String> changedFiles,
        List<String> excludedFiles,
        DeliveryMergeStatus status,
        String commit,
        String failureReason,
        Instant updatedAt
) {
    public static DeliveryMergePlan create(
            String workstreamId,
            String sourceWorktree,
            String targetRepo,
            String acceptanceDecision,
            List<String> changedFiles,
            List<String> excludedFiles,
            DeliveryMergeStatus status,
            String failureReason
    ) {
        return new DeliveryMergePlan(
                1,
                workstreamId,
                sourceWorktree,
                targetRepo,
                acceptanceDecision,
                List.copyOf(changedFiles),
                List.copyOf(excludedFiles),
                status,
                "",
                failureReason == null ? "" : failureReason,
                Instant.now()
        );
    }

    public DeliveryMergePlan withStatus(DeliveryMergeStatus nextStatus, String nextFailureReason) {
        return new DeliveryMergePlan(
                schemaVersion,
                workstreamId,
                sourceWorktree,
                targetRepo,
                acceptanceDecision,
                changedFiles,
                excludedFiles,
                nextStatus,
                commit,
                nextFailureReason == null ? "" : nextFailureReason,
                Instant.now()
        );
    }
}
