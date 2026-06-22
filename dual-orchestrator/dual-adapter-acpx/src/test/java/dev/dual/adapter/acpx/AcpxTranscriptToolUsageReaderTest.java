package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxTranscriptToolUsageReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsToolCountsAndFilesFromTranscript() throws Exception {
        var workspace = tempDir.resolve("repo");
        Files.createDirectories(workspace);
        var transcript = workspace.resolve(".agent/run.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, """
                {"type":"tool_use","name":"Read","input":{"file_path":"src/main.py"}}
                {"type":"tool_use","name":"Edit","input":{"file_path":"/tmp/ignored"}}
                {"type":"tool_use","name":"Write","input":{"file_path":"README.md"}}
                {"type":"tool_use","name":"Bash","input":{"command":"npm test"}}
                Glob: {"path":"src"}
                """);

        var usage = new AcpxTranscriptToolUsageReader().read(transcript, workspace);

        assertEquals(5, usage.toolCallCount());
        assertEquals(2, usage.readCount());
        assertEquals(2, usage.writeCount());
        assertEquals(1, usage.bashCount());
        assertTrue(usage.readFiles().contains("src/main.py"));
        assertTrue(usage.readFiles().contains("src"));
        assertTrue(usage.writeFiles().contains("README.md"));
        assertTrue(usage.bashCommands().contains("npm test"));
    }

    @Test
    void extractsCompletedBracketToolLinesWithFollowingInputEvidence() throws Exception {
        var workspace = tempDir.resolve("repo");
        Files.createDirectories(workspace);
        var transcript = workspace.resolve(".agent/run.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, """
                [tool] Read File (pending)
                [tool] Read README.md (completed)
                  input: {"file_path":"/tmp/ignored"}
                [tool] Read File (completed)
                  input: {"file_path":"%s"}
                [tool] rtk read docs/product.md (completed)
                  input: {"command":["/bin/zsh","-lc","rtk read docs/product.md"],"cwd":"%s"}
                [tool] ls -la .agent/workstreams/ws/runs (completed)
                  input: {"command":["/bin/zsh","-lc","ls -la .agent/workstreams/ws/runs"],"cwd":"%s"}
                [tool] rtk git status --short (completed)
                [tool] Bash (completed)
                  input: {"command":["/bin/zsh","-lc","npm test"],"cwd":"%s"}
                [tool] ls -la && git status --short (completed)
                [tool] Find `package.json` (completed)
                """.formatted(
                workspace.resolve("src/main.py").toString(),
                workspace,
                workspace,
                workspace
        ));

        var usage = new AcpxTranscriptToolUsageReader().read(transcript, workspace);

        assertEquals(8, usage.toolCallCount());
        assertEquals(5, usage.readCount());
        assertEquals(0, usage.writeCount());
        assertEquals(3, usage.bashCount());
        assertEquals(1, usage.byTool().get("LS"));
        assertEquals(3, usage.byTool().get("Bash"));
        assertEquals(1, usage.byTool().get("Glob"));
        assertTrue(usage.readFiles().contains("README.md"));
        assertTrue(usage.readFiles().contains("src/main.py"));
        assertTrue(usage.readFiles().contains("docs/product.md"));
        assertTrue(usage.readFiles().contains(".agent/workstreams/ws/runs"));
        assertTrue(usage.readFiles().contains("package.json"));
        assertTrue(!usage.readFiles().contains("-la"));
        assertTrue(usage.bashCommands().contains("rtk git status --short"));
        assertTrue(usage.bashCommands().contains("npm test"));
        assertTrue(usage.bashCommands().contains("ls -la && git status --short"));
    }
}
