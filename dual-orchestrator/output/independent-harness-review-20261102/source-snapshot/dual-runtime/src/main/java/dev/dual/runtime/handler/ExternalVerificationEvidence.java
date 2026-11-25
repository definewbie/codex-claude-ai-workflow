package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Explicit operator material ingestion. No execution, approval inference, or directory discovery. */
public final class ExternalVerificationEvidence {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final int FILE_LIMIT = 8 * 1024 * 1024;
    private static final int TOTAL_LIMIT = 32 * 1024 * 1024;
    public record Reference(String ref, String sha256) {}
    public record Preparation(EvidenceCollectionBaseline baseline, Instant recordedAt) {}
    public record Declaration(String command, String environment, Instant startedAt, Instant finishedAt,
                              int exitCode, String purpose) {
        public Declaration {
            if (blank(command) || blank(environment) || blank(purpose) || startedAt == null || finishedAt == null
                    || finishedAt.isBefore(startedAt)) throw new IllegalArgumentException("complete execution declaration required");
        }
    }
    public record Material(String ref, String sha256, long size, String name) {}
    public record Manifest(String requestId, Reference preparation, EvidenceCollectionBaseline baseline,
                           Declaration declaredExecution, String note, List<Material> materials) {}
    private ExternalVerificationEvidence() {}

    public static Reference prepare(WorkflowFileLedger ledger, WorkstreamPaths paths) throws IOException {
        var preparation = ledger.inspect(paths, (scoped, files) -> new Preparation(
                EvidenceCollection.captureBaseline(scoped, paths, scoped.loadDefinition(paths), "submit_evidence"), Instant.now()));
        // Material production grants no authority. Submission rechecks the fixed baseline under L05.
        return save(paths, "preparations", JSON.writeValueAsBytes(preparation), ".json");
    }

    public static EvidenceSubmission submit(WorkflowFileLedger ledger, WorkstreamPaths paths, Reference preparation,
                                             String requestId, List<Path> files, Declaration execution, String note) throws IOException {
        if (blank(note) || requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,80}")
                || files == null || files.isEmpty() || files.size() > 32 || execution == null)
            throw new IOException("submission requires request id, note, declaration, and 1-32 explicit files");
        var prepared = readPreparation(paths, preparation);
        if (execution.startedAt().isBefore(prepared.recordedAt()))
            throw new IOException("declared execution precedes preparation; timestamps are operator declarations, not machine proof");
        EvidenceCollection.verifySource(paths.workspaceRoot(), prepared.baseline());
        var materials = new ArrayList<Material>();
        long total = 0;
        for (var file : files) {
            var source = file.toAbsolutePath().normalize();
            if (!source.toRealPath().equals(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("material must be a canonical regular file: " + source);
            byte[] bytes;
            try (var input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(FILE_LIMIT + 1); }
            total += bytes.length;
            if (bytes.length > FILE_LIMIT || total > TOTAL_LIMIT) throw new IOException("material size limit exceeded");
            var name = source.getFileName().toString();
            var dot = name.lastIndexOf('.');
            var extension = dot >= 0 ? name.substring(dot) : ".bin";
            if (!extension.matches("\\.[A-Za-z0-9]{1,10}")) extension = ".bin";
            var stored = save(paths, "files", bytes, extension);
            materials.add(new Material(stored.ref(), stored.sha256(), bytes.length, source.getFileName().toString()));
        }
        var manifest = new Manifest(requestId, preparation, prepared.baseline(), execution, note, List.copyOf(materials));
        var reference = save(paths, "submissions", JSON.writeValueAsBytes(manifest), ".json");
        var submission = new EvidenceSubmission(requestId, preparation.ref(), preparation.sha256(),
                reference.ref(), reference.sha256(), prepared.baseline());
        try (var write = ledger.writeSession(paths, "submit evidence", requestId)) {
            var scoped = write.ledger();
            verify(paths, submission);
            var definition = scoped.loadDefinition(paths);
            var gateNode = scoped.loadNode(paths, prepared.baseline().gateNodeInstanceId());
            // Only a fully routed prior decision is idempotent. Partial gate writes never authorize another route.
            if (gateNode.status() == NodeStatus.COMPLETED) {
                var prior = JSON.convertValue(gateNode.result().get("external_evidence"), EvidenceSubmission.class);
                if (!submission.equals(prior) || !"submit_evidence".equals(gateNode.result().get("human_action")))
                    throw new IOException("gate already resolved with different evidence");
                var gate = JSON.readValue(paths.gateJson("gate-" + gateNode.nodeInstanceId()).toFile(), GateDocument.class);
                if (!"resolved".equals(gate.status()) || !gateNode.nodeInstanceId().equals(gate.nodeInstanceId())
                        || !"submit_evidence".equals(gate.result().get("human_action"))
                        || !note.equals(gate.result().get("note")) || !note.equals(gateNode.result().get("note")))
                    throw new IOException("inconsistent prior evidence decision");
                var routed = false;
                try (var lines = Files.lines(paths.eventsJsonl())) {
                    for (var iterator = lines.iterator(); iterator.hasNext();) {
                        var line = iterator.next();
                        if (line.isBlank()) continue;
                        var event = JSON.readTree(line);
                        if ("node_routed".equals(event.path("event_type").asText())
                                && gateNode.nodeInstanceId().equals(event.path("payload").path("transition")
                                .path("evidence_collection_gate").asText())) routed = true;
                    }
                }
                if (!routed) throw new IOException("partial evidence decision; no automatic recovery");
                return prior;
            }
            var currentBaseline = EvidenceCollection.captureBaseline(scoped, paths, definition, "submit_evidence");
            if (!prepared.baseline().equals(currentBaseline)) throw new IOException("preparation no longer belongs to current source/gate");
            scoped.submitEvidence(paths, definition, note, submission);
            write.commit();
            return submission;
        }
    }

    static Manifest verify(WorkstreamPaths paths, EvidenceSubmission submission) throws IOException {
        var preparation = new Reference(submission.preparationRef(), submission.preparationSha256());
        var prepared = readPreparation(paths, preparation);
        var manifest = JSON.readValue(read(paths, new Reference(submission.manifestRef(), submission.manifestSha256())), Manifest.class);
        if (!Objects.equals(manifest.preparation(), preparation) || !Objects.equals(manifest.baseline(), submission.baseline())
                || !Objects.equals(prepared.baseline(), submission.baseline()) || !submission.requestId().equals(manifest.requestId())
                || blank(manifest.note()) || manifest.declaredExecution() == null
                || manifest.declaredExecution().startedAt().isBefore(prepared.recordedAt())
                || manifest.materials() == null || manifest.materials().isEmpty() || manifest.materials().size() > 32)
            throw new IOException("external material manifest identity mismatch");
        long total = 0;
        for (var material : manifest.materials()) {
            var bytes = read(paths, new Reference(material.ref(), material.sha256()));
            total += bytes.length;
            if (bytes.length != material.size() || bytes.length > FILE_LIMIT || total > TOTAL_LIMIT)
                throw new IOException("external material size mismatch");
        }
        EvidenceCollection.verifySource(paths.workspaceRoot(), submission.baseline());
        return manifest;
    }

    static List<String> refs(WorkstreamPaths paths, EvidenceSubmission submission) throws IOException {
        var manifest = verify(paths, submission);
        var refs = new LinkedHashSet<String>();
        refs.add(submission.manifestRef());
        for (var material : manifest.materials()) refs.add(material.ref());
        return List.copyOf(refs);
    }

    private static Preparation readPreparation(WorkstreamPaths paths, Reference ref) throws IOException {
        var preparation = JSON.readValue(read(paths, ref), Preparation.class);
        if (preparation.baseline() == null || preparation.recordedAt() == null)
            throw new IOException("invalid evidence preparation");
        return preparation;
    }

    private static byte[] read(WorkstreamPaths paths, Reference reference) throws IOException {
        var prefix = ".agent/workstreams/" + paths.workstreamId().value() + "/artifacts/verification/";
        if (reference == null || reference.ref() == null || !reference.ref().startsWith(prefix)
                || !dev.dual.core.contract.TaskContractValidator.isCanonicalPath(reference.ref().substring(prefix.length()))
                || reference.sha256() == null || !reference.sha256().matches("[0-9a-f]{64}"))
            throw new IOException("invalid material reference");
        var file = paths.workspaceRoot().resolve(reference.ref());
        if (!file.toRealPath().equals(paths.workspaceRoot().toRealPath().resolve(reference.ref()))
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("noncanonical material reference");
        byte[] bytes;
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(FILE_LIMIT + 1); }
        if (bytes.length > FILE_LIMIT || !sha(bytes).equals(reference.sha256())) throw new IOException("material SHA mismatch");
        return bytes;
    }

    private static Reference save(WorkstreamPaths paths, String kind, byte[] bytes, String extension) throws IOException {
        var directory = paths.workstreamDir().resolve("artifacts/verification/" + kind);
        var root = paths.workspaceRoot().toRealPath();
        var cursor = root;
        for (var segment : paths.workspaceRoot().relativize(directory)) {
            cursor = cursor.resolve(segment);
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(cursor);
            if (!Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS) || !cursor.toRealPath().equals(cursor))
                throw new IOException("noncanonical material storage");
        }
        var hash = sha(bytes);
        var target = directory.resolve(hash + extension);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            var temporary = Files.createTempFile(directory, ".evidence-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
        }
        var ref = new Reference(paths.workspaceRoot().relativize(target).toString().replace('\\', '/'), hash);
        read(paths, ref);
        return ref;
    }

    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
