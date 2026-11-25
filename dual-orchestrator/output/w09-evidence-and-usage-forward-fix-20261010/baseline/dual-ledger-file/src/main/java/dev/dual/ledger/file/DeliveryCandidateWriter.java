package dev.dual.ledger.file;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** Atomically publishes one immutable candidate occurrence. */
public final class DeliveryCandidateWriter {
    private final AtomicJsonWriter jsonWriter;

    public DeliveryCandidateWriter() {
        this(new AtomicJsonWriter());
    }

    DeliveryCandidateWriter(AtomicJsonWriter jsonWriter) {
        this.jsonWriter = jsonWriter;
    }

    public Published publish(
            WorkstreamPaths paths,
            DeliveryCandidateDocument candidate,
            Map<String, byte[]> artifacts
    ) throws IOException {
        var finalDir = paths.deliveryCandidateDir(candidate.producerNodeInstanceId()).toAbsolutePath().normalize();
        var existing = existing(paths, candidate.producerNodeInstanceId());
        if (existing != null) return existing;

        Files.createDirectories(paths.deliveryCandidatesDir());
        var temporaryDir = paths.deliveryCandidatesDir().resolve(
                ".tmp-" + candidate.producerNodeInstanceId() + "-" + UUID.randomUUID()
        );
        Files.createDirectories(temporaryDir);
        try {
            for (var entry : artifacts.entrySet()) {
                requireFileName(entry.getKey());
                Files.write(temporaryDir.resolve(entry.getKey()), entry.getValue());
            }
            verifyArtifactReferences(paths, finalDir, temporaryDir, candidate);
            var candidateBytes = jsonWriter.objectMapper().writeValueAsBytes(candidate);
            var candidateSha = sha256(candidateBytes);
            var candidateName = "candidate-" + candidateSha + ".json";
            Files.write(temporaryDir.resolve(candidateName), candidateBytes);
            moveDirectory(temporaryDir, finalDir);
            var candidatePath = finalDir.resolve(candidateName);
            return new Published(workspaceRef(paths, candidatePath), candidateSha, candidate, candidatePath);
        } finally {
            deleteRecursively(temporaryDir);
        }
    }

    public Published existing(WorkstreamPaths paths, String producerNodeInstanceId) throws IOException {
        var dir = paths.deliveryCandidateDir(producerNodeInstanceId);
        if (!Files.isDirectory(dir)) return null;
        try (var stream = Files.list(dir)) {
            var candidates = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("candidate-[0-9a-f]{64}\\.json"))
                    .toList();
            if (candidates.size() != 1) {
                throw new IOException("delivery candidate occurrence must contain exactly one candidate document: " + dir);
            }
            var path = candidates.get(0);
            var bytes = Files.readAllBytes(path);
            var sha = sha256(bytes);
            if (!path.getFileName().toString().equals("candidate-" + sha + ".json")) {
                throw new IOException("delivery candidate filename SHA does not match its bytes: " + path);
            }
            var document = jsonWriter.objectMapper().readValue(bytes, DeliveryCandidateDocument.class);
            if (!producerNodeInstanceId.equals(document.producerNodeInstanceId())) {
                throw new IOException("delivery candidate producer node does not match occurrence directory");
            }
            verifyArtifactReferences(paths, dir.toAbsolutePath().normalize(), dir, document);
            return new Published(workspaceRef(paths, path), sha, document, path);
        }
    }

    private void verifyArtifactReferences(
            WorkstreamPaths paths,
            Path finalDir,
            Path actualDir,
            DeliveryCandidateDocument candidate
    ) throws IOException {
        for (var reference : new DeliveryCandidateDocument.ArtifactReference[]{
                candidate.patch(), candidate.commitMessage(), candidate.prBody()
        }) {
            var expectedFinal = paths.workspaceRoot().resolve(reference.ref()).toAbsolutePath().normalize();
            if (!expectedFinal.startsWith(finalDir) || expectedFinal.equals(finalDir)) {
                throw new IOException("delivery candidate artifact ref is outside its occurrence: " + reference.ref());
            }
            var actual = actualDir.resolve(expectedFinal.getFileName());
            if (!Files.isRegularFile(actual) || !reference.sha256().equals(sha256(Files.readAllBytes(actual)))) {
                throw new IOException("delivery candidate artifact SHA does not match: " + reference.ref());
            }
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }

    private static void requireFileName(String value) {
        if (value == null || value.isBlank() || !Path.of(value).getFileName().toString().equals(value)) {
            throw new IllegalArgumentException("delivery candidate artifact name must be a file name");
        }
    }

    private static String workspaceRef(WorkstreamPaths paths, Path path) {
        return paths.workspaceRoot().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (var path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record Published(
            String ref,
            String sha256,
            DeliveryCandidateDocument document,
            Path path
    ) {
    }
}
