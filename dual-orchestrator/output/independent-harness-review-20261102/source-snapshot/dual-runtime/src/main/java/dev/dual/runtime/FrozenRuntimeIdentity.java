package dev.dual.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/** Content identity for the existing packaged entrypoints or the complete development classpath. */
public final class FrozenRuntimeIdentity {
    private FrozenRuntimeIdentity() {}

    public static Map<String, String> capture(Class<?> entrypoint, String configuredKernel,
                                              String effectiveKernel, Map<String, String> behavior) {
        try {
            var identity = new TreeMap<String, String>();
            var classpath = System.getProperty("java.class.path", "");
            if (classpath.isBlank()) throw new IOException("runtime classpath is missing");
            var entries = classpath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1);
            Path executable = entries.length == 1 ? Path.of(entries[0]).toAbsolutePath().normalize() : null;
            if (executable != null && Files.isRegularFile(executable) && isBootEntry(executable, entrypoint)) {
                var bundle = new TreeMap<String, Path>();
                var role = role(executable);
                bundle.put(role, executable);
                var other = sibling(executable, role);
                if (Files.isRegularFile(other)) bundle.put(role.equals("cli") ? "server" : "cli", other);
                identity.putAll(packaged(bundle));
            } else {
                // Preserve classpath order: reordering shadowed classes changes executable behavior.
                for (int index = 0; index < entries.length; index++) {
                    if (entries[index].isBlank()) throw new IOException("empty runtime classpath entry");
                    identity.put("classpath." + String.format("%04d", index), content(Path.of(entries[index])));
                }
                var source = Path.of(entrypoint.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
                boolean found = false;
                for (var entry : entries) if (Path.of(entry).toRealPath().equals(source)) found = true;
                if (!found) throw new IOException("entrypoint code source is outside the captured classpath");
            }
            identity.put("identity_schema", "dual-runtime-content.v1");
            identity.put("kernel.configured", configuredKernel == null ? "" : configuredKernel);
            identity.put("kernel.effective", java.util.Objects.requireNonNull(effectiveKernel));
            identity.put("java.runtime", System.getProperty("java.runtime.version"));
            // Only explicit non-secret behavior settings may be supplied; never capture the whole environment.
            behavior.forEach((key, value) -> identity.put("behavior." + key, value));
            return Map.copyOf(identity);
        } catch (Exception failure) {
            throw new IllegalStateException("cannot establish frozen runtime content identity", failure);
        }
    }

    static Map<String, String> packaged(Map<String, Path> artifacts) throws IOException {
        if (artifacts.isEmpty()) throw new IOException("empty runtime artifact set");
        var result = new TreeMap<String, String>();
        Map<String, String> shared = null;
        for (var item : new TreeMap<>(artifacts).entrySet()) {
            if (!java.util.Set.of("cli", "server").contains(item.getKey())) throw new IOException("unknown entrypoint");
            var dependencies = new TreeMap<String, String>();
            try (var zip = new ZipFile(item.getValue().toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.getName().startsWith("BOOT-INF/lib/dual-")) {
                        try (var input = zip.getInputStream(entry)) {
                            dependencies.put(entry.getName(), sha(input.readAllBytes()));
                        }
                    }
                }
            }
            if (dependencies.isEmpty()) throw new IOException("packaged runtime has no Dual dependencies");
            if (shared != null && !shared.equals(dependencies))
                throw new IOException("CLI/server contain different shared Dual binaries");
            shared = dependencies;
            result.put("executable." + item.getKey(), content(item.getValue()));
        }
        return result;
    }

    static String content(Path input) throws IOException {
        var path = input.toRealPath();
        if (Files.isRegularFile(path)) return sha(Files.readAllBytes(path));
        if (!Files.isDirectory(path)) throw new IOException("unsupported runtime input: " + input);
        var files = new TreeMap<String, String>();
        try (var stream = Files.walk(path)) {
            for (var file : stream.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("symlink in development classpath: " + file);
                if (Files.isRegularFile(file)) files.put(path.relativize(file).toString(), sha(Files.readAllBytes(file)));
            }
        }
        var digest = new StringBuilder();
        files.forEach((name, hash) -> digest.append(name.length()).append(':').append(name).append(':').append(hash).append('\n'));
        return sha(digest.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBootEntry(Path path, Class<?> entrypoint) throws IOException {
        try (var zip = new ZipFile(path.toFile())) {
            var manifest = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifest == null) return false;
            try (var input = zip.getInputStream(manifest)) {
                return entrypoint.getName().equals(new java.util.jar.Manifest(input).getMainAttributes().getValue("Start-Class"));
            }
        }
    }

    private static String role(Path executable) throws IOException {
        var name = executable.getFileName().toString();
        if (name.startsWith("dual-cli") && name.endsWith(".jar")) return "cli";
        if (name.startsWith("dual-server") && name.endsWith(".jar")) return "server";
        throw new IOException("unrecognized packaged entrypoint: " + name);
    }

    private static Path sibling(Path executable, String role) {
        var opposite = role.equals("cli") ? "server" : "cli";
        var name = executable.getFileName().toString().replace("dual-" + role, "dual-" + opposite);
        var directory = executable.getParent();
        if (directory.getFileName().toString().equals("target")
                && directory.getParent().getFileName().toString().equals("dual-" + role))
            return directory.getParent().getParent().resolve("dual-" + opposite).resolve("target").resolve(name);
        return directory.resolve(name);
    }

    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
