package dev.dual.ledger.file;

import dev.dual.core.contract.TaskContractValidator;
import java.util.List;

/** Machine-produced approval fact; not a model-supplied permission grant. */
public record EvidenceCollectionBaseline(String gateNodeInstanceId, String contractRef, String contractSha256,
                                         String head, String indexSha256, String sourceSha256,
                                         List<String> paths) {
    public EvidenceCollectionBaseline {
        if (gateNodeInstanceId == null || !gateNodeInstanceId.matches("[A-Za-z0-9._-]+")
                || contractRef == null || !contractRef.matches("\\.agent/workstreams/[A-Za-z0-9_-]+/artifacts/task-contract\\.json")
                || !sha(contractSha256) || !sha(indexSha256) || !sha(sourceSha256)
                || head == null || !head.matches("[0-9a-f]{40}|[0-9a-f]{64}") || paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("invalid evidence collection baseline");
        }
        paths = List.copyOf(paths);
        if (!paths.equals(paths.stream().distinct().sorted().toList()) || paths.stream().anyMatch(path ->
                !TaskContractValidator.isCanonicalPath(path) || path.equals(".git") || path.startsWith(".git/")
                        || path.equals(".agent") || path.startsWith(".agent/"))) {
            throw new IllegalArgumentException("invalid evidence collection source paths");
        }
    }
    private static boolean sha(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
