package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

/** Immutable byte identity presented to the final human acceptance gate. */
public record DeliveryCandidateDocument(
        int schemaVersion,
        String workstreamId,
        String producerNodeInstanceId,
        String iterationId,
        String sourceWorkspaceId,
        String sourceEffectiveWorkdir,
        String baseCommit,
        String baseTree,
        List<String> acceptedFiles,
        List<String> excludedFiles,
        ArtifactReference patch,
        String expectedResultTree,
        ArtifactReference commitMessage,
        ArtifactReference prBody,
        Instant createdAt
) {
    public DeliveryCandidateDocument {
        if (schemaVersion <= 0) throw new IllegalArgumentException("schemaVersion must be positive");
        requireText(workstreamId, "workstreamId");
        requireText(producerNodeInstanceId, "producerNodeInstanceId");
        requireText(iterationId, "iterationId");
        requireText(sourceWorkspaceId, "sourceWorkspaceId");
        requireText(sourceEffectiveWorkdir, "sourceEffectiveWorkdir");
        requireHash(baseCommit, "baseCommit");
        requireHash(baseTree, "baseTree");
        acceptedFiles = sortedCopy(acceptedFiles, "acceptedFiles", false);
        excludedFiles = sortedCopy(excludedFiles, "excludedFiles", true);
        if (patch == null) throw new IllegalArgumentException("patch is required");
        requireHash(expectedResultTree, "expectedResultTree");
        if (commitMessage == null) throw new IllegalArgumentException("commitMessage is required");
        if (prBody == null) throw new IllegalArgumentException("prBody is required");
        if (createdAt == null) throw new IllegalArgumentException("createdAt is required");
    }

    private static List<String> sortedCopy(List<String> values, String field, boolean allowEmpty) {
        var copy = List.copyOf(values == null ? List.of() : values);
        if (!allowEmpty && copy.isEmpty()) throw new IllegalArgumentException(field + " is required");
        copy.forEach(value -> requireText(value, field));
        var sorted = copy.stream().sorted().toList();
        if (!copy.equals(sorted) || copy.stream().distinct().count() != copy.size()) {
            throw new IllegalArgumentException(field + " must be sorted and unique");
        }
        return copy;
    }

    private static void requireHash(String value, String field) {
        requireText(value, field);
        if (!value.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase Git or SHA-256 identity");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    public record ArtifactReference(String ref, String sha256) {
        public ArtifactReference {
            requireText(ref, "artifact ref");
            requireText(sha256, "artifact sha256");
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("artifact sha256 must be 64 lowercase hexadecimal characters");
            }
        }
    }
}
