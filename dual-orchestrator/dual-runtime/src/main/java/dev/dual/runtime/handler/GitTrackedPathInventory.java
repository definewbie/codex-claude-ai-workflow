package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContractValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Authoritative tracked-path snapshot for contract discovery; it does not infer task relevance. */
record GitTrackedPathInventory(String head, String sha256, List<String> paths) {
    GitTrackedPathInventory {
        head = text(head);
        sha256 = text(sha256);
        paths = paths == null ? List.of() : List.copyOf(paths);
    }

    static GitTrackedPathInventory capture(Path workspaceRoot) throws IOException {
        var root = workspaceRoot.toRealPath();
        var gitRoot = text(command(root, "rev-parse", "--show-toplevel"));
        if (!root.equals(Path.of(gitRoot).toRealPath())) {
            throw new IOException("effective workspace is not the Git worktree root: " + root);
        }
        var head = text(command(root, "rev-parse", "HEAD"));
        var raw = commandBytes(root, "ls-files", "-z", "--cached", "--", ".", ":!.agent");
        var paths = parseNulPaths(raw).stream()
                .map(GitTrackedPathInventory::normalize)
                .filter(TaskContractValidator::isCanonicalPath)
                .distinct()
                .sorted()
                .toList();
        return new GitTrackedPathInventory(head, digest(paths), paths);
    }

    static GitTrackedPathInventory empty() {
        return new GitTrackedPathInventory("", "", List.of());
    }

    private static String command(Path root, String... args) throws IOException {
        return new String(commandBytes(root, args), StandardCharsets.UTF_8);
    }

    private static byte[] commandBytes(Path root, String... args) throws IOException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(root.toFile())
                .start();
        var stdout = process.getInputStream().readAllBytes();
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
            var exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("git " + String.join(" ", args) + " failed: " + stderr);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("git inventory capture interrupted", interrupted);
        }
        return stdout;
    }

    private static List<String> parseNulPaths(byte[] raw) throws IOException {
        var paths = new ArrayList<String>();
        var start = 0;
        for (var index = 0; index < raw.length; index++) {
            if (raw[index] != 0) {
                continue;
            }
            if (index > start) {
                paths.add(new String(raw, start, index - start, StandardCharsets.UTF_8));
            }
            start = index + 1;
        }
        if (start != raw.length) {
            throw new IOException("git tracked-path inventory has malformed NUL framing");
        }
        return List.copyOf(paths);
    }

    private static String digest(List<String> paths) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var path : paths) {
                digest.update(path.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static String normalize(String value) {
        var normalized = text(value).replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
