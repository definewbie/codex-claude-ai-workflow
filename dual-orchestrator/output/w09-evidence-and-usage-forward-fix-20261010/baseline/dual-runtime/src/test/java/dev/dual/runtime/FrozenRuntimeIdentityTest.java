package dev.dual.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class FrozenRuntimeIdentityTest {
    @TempDir Path root;

    @Test void bothEntrypointsShareOneReleaseIdentityButMixedBinariesReject() throws Exception {
        var cli = jar("cli", "same core", "cli bytes");
        var server = jar("server", "same core", "server bytes");
        var identity = FrozenRuntimeIdentity.packaged(Map.of("cli", cli, "server", server));
        assertEquals(identity, FrozenRuntimeIdentity.packaged(Map.of("server", server, "cli", cli)));
        assertNotEquals(identity.get("executable.cli"), identity.get("executable.server"));
        jar("server", "different core", "server bytes");
        assertThrows(java.io.IOException.class, () -> FrozenRuntimeIdentity.packaged(Map.of("cli", cli, "server", server)));
    }

    @Test void resourcesAndNestedPathsArePartOfDevelopmentIdentity() throws Exception {
        var classes = root.resolve("classes");
        Files.createDirectories(classes.resolve("schema"));
        Files.writeString(classes.resolve("Code.class"), "compiled class bytes");
        Files.writeString(classes.resolve("schema/contract.json"), "v1");
        var before = FrozenRuntimeIdentity.content(classes);
        Files.writeString(classes.resolve("schema/contract.json"), "v2");
        assertNotEquals(before, FrozenRuntimeIdentity.content(classes));
        Files.writeString(classes.resolve("schema/contract.json"), "v1");
        assertEquals(before, FrozenRuntimeIdentity.content(classes));
        Files.move(classes.resolve("schema/contract.json"), classes.resolve("contract.json"));
        assertNotEquals(before, FrozenRuntimeIdentity.content(classes));
    }

    private Path jar(String name, String common, String entry) throws Exception {
        var path = root.resolve("dual-" + name + ".jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("BOOT-INF/lib/dual-core.jar"));
            zip.write(common.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("BOOT-INF/classes/Main.class"));
            zip.write(entry.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return path;
    }
}
