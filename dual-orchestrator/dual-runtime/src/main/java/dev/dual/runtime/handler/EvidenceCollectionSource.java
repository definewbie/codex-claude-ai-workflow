package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContractValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Raw worktree bytes, not Git's filtered blobs or the path-only inventory digest. */
record EvidenceCollectionSource(String head, String indexSha256, String sha256, List<String> paths) {
    static EvidenceCollectionSource capture(Path workspace, List<String> retainedPaths) throws IOException {
        var root = workspace.toRealPath();
        if (!root.equals(Path.of(GitTrackedPathInventory.command(root, "rev-parse", "--show-toplevel").trim()).toRealPath()))
            throw new IOException("collection workspace must be the Git root");
        var head = GitTrackedPathInventory.command(root, "rev-parse", "HEAD").trim();
        var index = GitTrackedPathInventory.commandBytes(root, "ls-files", "--stage", "-z", "--", ".", ":!.agent");
        var paths = new TreeSet<>(retainedPaths);
        paths.addAll(GitTrackedPathInventory.parseNulPaths(GitTrackedPathInventory.commandBytes(root,
                "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", ".", ":!.agent")));
        var digest = digest();
        for (var path : paths) {
            if (!TaskContractValidator.isCanonicalPath(path) || path.equals(".git") || path.startsWith(".git/")
                    || path.equals(".agent") || path.startsWith(".agent/"))
                throw new IOException("invalid collection source path: " + path);
            var file = root.resolve(path);
            for (var component = file; !component.equals(root); component = component.getParent()) {
                if (Files.isSymbolicLink(component)) throw new IOException("collection source symlink is unsupported: " + path);
            }
            update(digest, path);
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                update(digest, "absent");
            } else {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("collection source is not a regular file: " + path);
                update(digest, Files.isExecutable(file) ? "executable" : "file");
                var content = digest();
                try (var input = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    for (int count; (count = input.read(buffer)) != -1;) content.update(buffer, 0, count);
                }
                update(digest, HexFormat.of().formatHex(content.digest()));
            }
        }
        // Do not accept an inventory that changed while we were reading its files.
        if (!head.equals(GitTrackedPathInventory.command(root, "rev-parse", "HEAD").trim())
                || !Arrays.equals(index, GitTrackedPathInventory.commandBytes(root, "ls-files", "--stage", "-z", "--", ".", ":!.agent")))
            throw new IOException("repository identity changed during collection capture");
        return new EvidenceCollectionSource(head, HexFormat.of().formatHex(digest().digest(index)),
                HexFormat.of().formatHex(digest.digest()), List.copyOf(paths));
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
