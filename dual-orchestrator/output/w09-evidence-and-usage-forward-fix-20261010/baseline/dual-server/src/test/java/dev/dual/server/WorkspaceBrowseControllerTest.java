package dev.dual.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspaceBrowseControllerTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void browseReturnsDirectoriesOnly() throws Exception {
        Files.createDirectories(tempDir.resolve("repo-a"));
        Files.createDirectories(tempDir.resolve("repo-b"));
        Files.writeString(tempDir.resolve("note.txt"), "not a directory");
        var controller = new WorkspaceBrowseController();

        var response = controller.browse(tempDir.toString());

        assertEquals(tempDir.toAbsolutePath().normalize().toString(), response.get("path"));
        var directories = (List<Map<String, Object>>) response.get("directories");
        assertEquals(2, directories.size());
        assertEquals("repo-a", directories.get(0).get("name"));
        assertEquals("repo-b", directories.get(1).get("name"));
    }
}
