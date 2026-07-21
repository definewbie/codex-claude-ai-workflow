package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
final class AgentEvidenceIndex {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

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

    List<Entry> recent(int maxItems) {
        if (maxItems <= 0) {
            return List.of();
        }
        return entries.stream().limit(maxItems).toList();
    }

    Optional<Entry> latest() {
        return entries.stream().findFirst();
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
        if (!path.startsWith(workspaceRoot) || !Files.isRegularFile(path)) {
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
        var document = AgentOutputContractDocument.read(path, objectMapper);
        if (document.isEmpty()) {
            throw new IllegalStateException("routed agent evidence sidecar is unreadable: " + path);
        }
        var violation = AgentOutputLineageVerifier.violation(workspaceRoot, workstreamId, path, document.get());
        if (violation.isPresent()) {
            throw new IllegalStateException("routed agent evidence lineage is invalid: " + violation.get());
        }
        try {
            var raw = objectMapper.readValue(path.toFile(), MAP_TYPE);
            return Optional.of(new Entry(path.toAbsolutePath().normalize(), document.get(), raw, lastModified(path)));
        } catch (IOException e) {
            return Optional.of(new Entry(path.toAbsolutePath().normalize(), document.get(), Map.of(), lastModified(path)));
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    record Entry(
            Path path,
            AgentOutputContractDocument document,
            Map<String, Object> raw,
            Instant lastModified
    ) {
        private static final Comparator<Entry> ORDER = Comparator
                .comparing(Entry::lastModified)
                .thenComparing(entry -> entry.path().toString());

        Map<String, Object> evidenceState() {
            if (raw.get("evidence_state") instanceof Map<?, ?> source) {
                var copy = new LinkedHashMap<String, Object>();
                for (var entry : source.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        copy.put(key, entry.getValue());
                    }
                }
                return copy;
            }
            return Map.of();
        }
    }
}
