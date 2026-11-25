package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryCandidateWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void publishesContentAddressedOccurrenceAndRejectsArtifactTampering() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws"));
        var occurrence = "node-delivery-001";
        var dir = paths.deliveryCandidateDir(occurrence);
        var patch = "patch\n".getBytes(StandardCharsets.UTF_8);
        var commit = "commit\n".getBytes(StandardCharsets.UTF_8);
        var pr = "pr\n".getBytes(StandardCharsets.UTF_8);
        var candidate = new DeliveryCandidateDocument(
                1, "ws", occurrence, "iter-001", "wsp-001", tempDir.toString(),
                "1111111111111111111111111111111111111111",
                "2222222222222222222222222222222222222222",
                List.of("README.md"), List.of("target/generated.log"),
                ref(tempDir, dir.resolve("merge.patch"), patch),
                "3333333333333333333333333333333333333333",
                ref(tempDir, dir.resolve("commit-message.txt"), commit),
                ref(tempDir, dir.resolve("pr-body.md"), pr),
                Instant.parse("2026-08-28T00:00:00Z")
        );
        var writer = new DeliveryCandidateWriter();

        var published = writer.publish(paths, candidate, Map.of(
                "merge.patch", patch,
                "commit-message.txt", commit,
                "pr-body.md", pr
        ));

        assertTrue(published.ref().endsWith("candidate-" + published.sha256() + ".json"));
        assertEquals(published.ref(), writer.existing(paths, occurrence).ref());
        Files.writeString(dir.resolve("merge.patch"), "tampered\n");
        var failure = assertThrows(java.io.IOException.class, () -> writer.existing(paths, occurrence));
        assertTrue(failure.getMessage().contains("SHA does not match"));
    }

    private static DeliveryCandidateDocument.ArtifactReference ref(Path root, Path path, byte[] bytes) {
        return new DeliveryCandidateDocument.ArtifactReference(
                root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString(),
                DeliveryCandidateWriter.sha256(bytes)
        );
    }
}
