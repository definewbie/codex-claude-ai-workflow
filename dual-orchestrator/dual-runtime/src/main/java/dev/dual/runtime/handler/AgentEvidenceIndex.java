package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read model for validated agent output sidecars.
 *
 * Consumers should use this index instead of walking runs and re-parsing raw
 * maps independently. It keeps ordering and structured-field interpretation in
 * one place while preserving the file ledger as the source of truth.
 */
public final class AgentEvidenceIndex {
    private final List<Entry> entries;

    private AgentEvidenceIndex(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static AgentEvidenceIndex fromRefs(
            Path workspaceRoot,
            String workstreamId,
            List<String> refs,
            ObjectMapper objectMapper
    ) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var entries = refs.stream()
                .map(ref -> sidecarPathForRef(root, ref))
                .flatMap(Optional::stream)
                .distinct()
                .map(path -> readEntry(root, workstreamId, path, objectMapper))
                .flatMap(Optional::stream)
                .sorted(Entry.ORDER.reversed())
                .toList();
        return new AgentEvidenceIndex(entries);
    }

    static Optional<Entry> latestFromRefs(
            Path workspaceRoot,
            String workstreamId,
            List<String> refs,
            ObjectMapper objectMapper
    ) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        return refs.stream()
                .map(ref -> sidecarPathForRef(root, ref))
                .flatMap(Optional::stream)
                .map(path -> readEntry(root, workstreamId, path, objectMapper))
                .flatMap(Optional::stream)
                .sorted(Entry.ORDER.reversed())
                .findFirst();
    }

    static DecisionRead latestDecisionFromRefs(
            Path workspaceRoot,
            String workstreamId,
            List<String> refs,
            ObjectMapper objectMapper
    ) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        for (int i = refs.size() - 1; i >= 0; i--) {
            var path = sidecarPathForRef(root, refs.get(i));
            if (path.isEmpty()) {
                continue;
            }
            var read = readValidatedEntry(root, workstreamId, path.get(), objectMapper);
            if (read.entry() == null) {
                return DecisionRead.invalid(read.errorCode(), read.message(), path.get().toString());
            }
            if (!read.entry().document().hasDecision()) {
                return DecisionRead.invalid(
                        "CONTRACT_SIDECAR_MISSING_DECISION",
                        "Contract sidecar is missing required decision: " + path.get().getFileName(),
                        path.get().toString()
                );
            }
            return DecisionRead.valid(read.entry(), path.get().toString());
        }
        return DecisionRead.notFound();
    }

    public static Optional<AgentOutputContractDocument> validatedDocument(
            Path workspaceRoot,
            String workstreamId,
            String ref,
            ObjectMapper objectMapper
    ) {
        var index = fromRefs(workspaceRoot, workstreamId, List.of(ref), objectMapper);
        return index.entryForRef(workspaceRoot, ref).map(Entry::document);
    }

    List<Entry> recent(int maxItems) {
        if (maxItems <= 0) {
            return List.of();
        }
        return entries.stream().limit(maxItems).toList();
    }

    Optional<Entry> latest() {
        return entries.stream().findFirst();
    }

    Optional<Entry> entryForRef(Path workspaceRoot, String ref) {
        var normalizedRef = normalizeRef(ref);
        return entries.stream()
                .filter(entry -> normalizedRef.equals(normalizeRef(entry.document().sourceOutputRef()))
                        || normalizedRef.equals(normalizeRef(relativeRef(workspaceRoot, entry.path()))))
                .findFirst();
    }

    boolean isValidatedPlanProjectionRef(Path workspaceRoot, String ref) {
        var normalizedRef = normalizeRef(ref);
        return entries.stream()
                .filter(entry -> entry.document().planArtifact() != null)
                .map(entry -> workspaceRoot.resolve(entry.document().sourceOutputRef()).normalize())
                .map(PlanArtifactSidecar::pathFor)
                .map(path -> relativeRef(workspaceRoot, path))
                .map(AgentEvidenceIndex::normalizeRef)
                .anyMatch(normalizedRef::equals);
    }

    List<String> validatedSidecarRefs(Path workspaceRoot) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        return entries.stream()
                .map(Entry::path)
                .map(root::relativize)
                .map(Path::toString)
                .toList();
    }

    boolean contains(String nodeId, String sourceArtifactType, String decision) {
        return entries.stream().map(Entry::document).anyMatch(document ->
                nodeId.equals(document.nodeId())
                        && sourceArtifactType.equals(document.sourceArtifactType())
                        && decision.equals(document.decision())
                        && document.hasRequiredTypedPayload());
    }

    private static Optional<Path> sidecarPathForRef(Path workspaceRoot, String ref) {
        if (ref == null || ref.isBlank() || ref.startsWith("artifact://")) {
            return Optional.empty();
        }
        var referenced = workspaceRoot.resolve(ref).normalize();
        if (!referenced.startsWith(workspaceRoot)) {
            return Optional.empty();
        }
        var path = ref.endsWith(".contract.json")
                ? referenced
                : AgentOutputContractSidecar.pathFor(referenced);
        if (!path.startsWith(workspaceRoot)
                || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            if (!path.toRealPath().startsWith(workspaceRoot.toRealPath())) {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private static Optional<Entry> readEntry(
            Path workspaceRoot,
            String workstreamId,
            Path path,
            ObjectMapper objectMapper
    ) {
        var read = readValidatedEntry(workspaceRoot, workstreamId, path, objectMapper);
        if (read.entry() == null) {
            throw new IllegalStateException(read.message());
        }
        return Optional.of(read.entry());
    }

    private static EntryRead readValidatedEntry(
            Path workspaceRoot,
            String workstreamId,
            Path path,
            ObjectMapper objectMapper
    ) {
        var document = AgentOutputContractDocument.read(path, objectMapper);
        if (document.isEmpty()) {
            return EntryRead.invalid(
                    "CONTRACT_SIDECAR_PARSE_FAILED",
                    "routed agent evidence sidecar is unreadable: " + path
            );
        }
        var violation = AgentOutputLineageVerifier.violation(workspaceRoot, workstreamId, path, document.get());
        if (violation.isPresent()) {
            return EntryRead.invalid(
                    "CONTRACT_SIDECAR_LINEAGE_INVALID",
                    "routed agent evidence lineage is invalid: " + violation.get()
            );
        }
        var semanticViolation = AgentContractSemantics.isAcceptanceReview(document.get())
                ? AgentContractSemantics.validateOutputContract(document.get()) : java.util.Optional.<ContractViolation>empty();
        if (semanticViolation.isPresent()) {
            return EntryRead.invalid(semanticViolation.get().errorCode(), semanticViolation.get().message());
        }
        return EntryRead.valid(new Entry(path.toAbsolutePath().normalize(), document.get(), lastModified(path)));
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String relativeRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString();
    }

    private static String normalizeRef(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    record Entry(
            Path path,
            AgentOutputContractDocument document,
            Instant lastModified
    ) {
        private static final Comparator<Entry> ORDER = Comparator
                .comparing(Entry::lastModified)
                .thenComparing(entry -> entry.path().toString());

        Map<String, Object> evidenceState() {
            return AgentEvidenceState.from(document).toMap();
        }
    }

    record DecisionRead(
            Entry entry,
            String errorCode,
            String message,
            String path,
            boolean missing
    ) {
        static DecisionRead valid(Entry entry, String path) {
            return new DecisionRead(entry, "", "", path, false);
        }

        static DecisionRead invalid(String code, String message, String path) {
            return new DecisionRead(null, code, message, path, false);
        }

        static DecisionRead notFound() {
            return new DecisionRead(null, "", "", "", true);
        }
    }

    private record EntryRead(Entry entry, String errorCode, String message) {
        static EntryRead valid(Entry entry) {
            return new EntryRead(entry, "", "");
        }

        static EntryRead invalid(String code, String message) {
            return new EntryRead(null, code, message);
        }
    }
}
