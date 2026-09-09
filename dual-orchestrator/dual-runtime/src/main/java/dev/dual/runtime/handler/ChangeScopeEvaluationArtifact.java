package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

final class ChangeScopeEvaluationArtifact {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Pattern REF = Pattern.compile(
            "\\.agent/workstreams/([^/]+)/artifacts/git-evidence/occurrences/([^/]+)/change-scope-evaluation-([0-9a-f]{64})\\.json");

    private ChangeScopeEvaluationArtifact() {
    }

    static Reference publish(
            Path workspaceRoot,
            String workstreamId,
            String sourceNodeInstanceId,
            ChangeScopeEvaluation evaluation
    ) throws IOException {
        var document = new Document(1, workstreamId, sourceNodeInstanceId, evaluation);
        var bytes = OBJECT_MAPPER.writeValueAsBytes(document);
        var sha256 = sha256(bytes);
        var ref = ".agent/workstreams/" + workstreamId
                + "/artifacts/git-evidence/occurrences/" + sourceNodeInstanceId
                + "/change-scope-evaluation-" + sha256 + ".json";
        var target = resolveInside(workspaceRoot, ref);
        Files.createDirectories(target.getParent());
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target) || !sha256(Files.readAllBytes(target)).equals(sha256)) {
                throw new IOException("existing change-scope occurrence does not match its content address");
            }
            return new Reference(ref, sha256);
        }
        var temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new Reference(ref, sha256);
    }

    static Verified verify(
            Path workspaceRoot,
            String expectedWorkstreamId,
            String expectedSourceNodeInstanceId,
            String ref,
            String expectedSha256
    ) throws IOException {
        var matcher = REF.matcher(ref == null ? "" : ref);
        if (!matcher.matches()
                || !expectedWorkstreamId.equals(matcher.group(1))
                || !expectedSourceNodeInstanceId.equals(matcher.group(2))) {
            throw new IOException("change-scope evaluation ref is not canonical for the expected occurrence");
        }
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")
                || !expectedSha256.equals(matcher.group(3))) {
            throw new IOException("change-scope evaluation ref sha256 does not match the lineage anchor");
        }
        var path = resolveInside(workspaceRoot, ref);
        if (!Files.isRegularFile(path)) {
            throw new IOException("change-scope evaluation occurrence is missing");
        }
        var bytes = Files.readAllBytes(path);
        var actualSha256 = sha256(bytes);
        if (!expectedSha256.equals(actualSha256)) {
            throw new IOException("change-scope evaluation occurrence sha256 mismatch");
        }
        final Document document;
        try {
            document = OBJECT_MAPPER.readValue(bytes, Document.class);
        } catch (Exception failure) {
            throw new IOException("change-scope evaluation occurrence cannot be parsed", failure);
        }
        if (document.schemaVersion() != 1
                || !expectedWorkstreamId.equals(document.workstreamId())
                || !expectedSourceNodeInstanceId.equals(document.sourceNodeInstanceId())
                || document.evaluation() == null) {
            throw new IOException("change-scope evaluation occurrence lineage mismatch");
        }
        return new Verified(ref, actualSha256, document, path);
    }

    static String sourceNodeInstanceId(String ref) {
        var matcher = REF.matcher(ref == null ? "" : ref);
        return matcher.matches() ? matcher.group(2) : "";
    }

    static Verified verifyGateSource(Path root, String workstreamId, String iterationId,
                                     java.util.List<String> inputRefs) throws IOException {
        var refs = inputRefs.stream().filter(ref -> isCanonicalRefForWorkstream(ref, workstreamId)).toList();
        if (refs.size() != 1) {
            throw new IOException("change-scope violation gate must receive exactly one occurrence evaluation");
        }
        var ref = refs.getFirst();
        var sourceId = sourceNodeInstanceId(ref);
        var sourcePath = resolveInside(root, ".agent/workstreams/" + workstreamId + "/nodes/" + sourceId + ".json");
        var source = OBJECT_MAPPER.readValue(sourcePath.toFile(), dev.dual.ledger.file.NodeInstanceDocument.class);
        if (!sourceId.equals(source.nodeInstanceId())
                || !"implement.artifact_validation".equals(source.nodeDefinitionId())
                || !"artifact.validate".equals(source.handlerType())
                || source.status() != dev.dual.core.workflow.NodeStatus.COMPLETED
                || !iterationId.equals(source.iterationId()) || !source.outputRefs().contains(ref)
                || !"FAIL".equals(source.result().get("change_scope_status"))
                || !"CHANGE_SCOPE_VIOLATION".equals(source.result().get("change_scope_disposition"))
                || !ref.equals(source.result().get("change_scope_evaluation_ref"))) {
            throw new IOException("change-scope evaluation producer lineage is invalid");
        }
        var verified = verify(root, workstreamId, sourceId, ref,
                String.valueOf(source.result().get("change_scope_evaluation_sha256")));
        if (verified.document().evaluation().status() != ChangeScopeEvaluation.Status.FAIL) {
            throw new IOException("change-scope occurrence is not a failed evaluation");
        }
        return verified;
    }

    static boolean isCanonicalRefForWorkstream(String ref, String workstreamId) {
        var matcher = REF.matcher(ref == null ? "" : ref);
        return matcher.matches() && workstreamId.equals(matcher.group(1));
    }

    private static Path resolveInside(Path workspaceRoot, String ref) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var path = root.resolve(ref).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("change-scope evaluation ref escapes the workspace");
        }
        return path;
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    record Document(
            int schemaVersion,
            String workstreamId,
            String sourceNodeInstanceId,
            ChangeScopeEvaluation evaluation
    ) {
    }

    record Reference(String ref, String sha256) {
    }

    record Verified(String ref, String sha256, Document document, Path path) {
    }
}
