package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                {"type":"tool_use","name":"Bash","input":{"command":"grep codegraph docs"}}
                {"type":"tool_use","name":"Bash","input":{"command":"/usr/local/bin/rtk grep issueWorkflow src"}}
                Glob: {"path":"src"}
                """);

        var usage = new AcpxTranscriptToolUsageReader().read(transcript, workspace);

        assertEquals(7, usage.toolCallCount());
        assertEquals(4, usage.readCount());
        assertEquals(2, usage.writeCount());
        assertEquals(1, usage.bashCount());
        assertTrue(usage.readFiles().contains("src/main.py"));
        assertTrue(usage.readFiles().contains("docs"));
        assertTrue(usage.readFiles().contains("src"));
        assertEquals(List.of("src/main.py"), usage.directReadFiles());
        assertTrue(usage.writeFiles().contains("README.md"));
        assertTrue(usage.bashCommands().contains("npm test"));
        assertTrue(usage.commandExecutables().contains("npm"));
        assertTrue(usage.commandExecutables().contains("grep"));
        assertTrue(usage.commandExecutables().contains("rtk"));
        assertTrue(usage.toolIds().contains("Read"));
        assertTrue(usage.toolIds().contains("Bash"));
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
                [tool] ./codegraph query Symbol (completed)
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

        assertEquals(9, usage.toolCallCount());
        assertEquals(5, usage.readCount());
        assertEquals(0, usage.writeCount());
        assertEquals(4, usage.bashCount());
        assertEquals(1, usage.byTool().get("LS"));
        assertEquals(4, usage.byTool().get("Bash"));
        assertEquals(1, usage.byTool().get("Glob"));
        assertTrue(usage.readFiles().contains("README.md"));
        assertTrue(usage.readFiles().contains("src/main.py"));
        assertTrue(usage.readFiles().contains("docs/product.md"));
        assertTrue(usage.readFiles().contains(".agent/workstreams/ws/runs"));
        assertTrue(usage.readFiles().contains("package.json"));
        assertTrue(usage.directReadFiles().contains("README.md"));
        assertTrue(usage.directReadFiles().contains("src/main.py"));
        assertTrue(usage.directReadFiles().contains("docs/product.md"));
        assertTrue(!usage.directReadFiles().contains(".agent/workstreams/ws/runs"));
        assertTrue(!usage.directReadFiles().contains("package.json"));
        assertTrue(!usage.readFiles().contains("-la"));
        assertTrue(usage.bashCommands().contains("rtk git status --short"));
        assertTrue(usage.bashCommands().contains("./codegraph query Symbol"));
        assertTrue(usage.bashCommands().contains("npm test"));
        assertTrue(usage.bashCommands().contains("ls -la && git status --short"));
        assertTrue(usage.commandExecutables().contains("rtk"));
        assertTrue(usage.commandExecutables().contains("codegraph"));
        assertTrue(usage.commandExecutables().contains("npm"));
        assertTrue(usage.commandExecutables().contains("ls"));
        assertTrue(usage.toolIds().contains("Read"));
        assertTrue(usage.toolIds().contains("Bash"));
    }

    @Test
    void recordsJsonBashRtkReadAsReadBehaviorWithoutNativeReadToolId() throws Exception {
        var workspace = tempDir.resolve("repo-json-bash");
        Files.createDirectories(workspace);
        var transcript = workspace.resolve(".agent/run.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, """
                {"type":"tool_use","name":"Bash","input":{"command":"rtk read docs/product.md"}}
                """);

        var usage = new AcpxTranscriptToolUsageReader().read(transcript, workspace);

        assertEquals(1, usage.toolCallCount());
        assertEquals(1, usage.readCount());
        assertEquals(0, usage.bashCount());
        assertTrue(usage.readFiles().contains("docs/product.md"));
        assertTrue(usage.directReadFiles().contains("docs/product.md"));
        assertTrue(usage.bashCommands().contains("rtk read docs/product.md"));
        assertTrue(usage.commandExecutables().contains("rtk"));
        assertTrue(usage.toolIds().contains("Bash"));
        assertTrue(!usage.toolIds().contains("Read"));
    }

    @Test
    void bracketDisplayReadCommandDoesNotBecomeNativeReadToolId() throws Exception {
        var workspace = tempDir.resolve("repo-bracket-display");
        Files.createDirectories(workspace);
        var transcript = workspace.resolve(".agent/run.transcript.log");
        Files.createDirectories(transcript.getParent());
        Files.writeString(transcript, """
                [tool] Read docs/product.md (completed)
                """);

        var usage = new AcpxTranscriptToolUsageReader().read(transcript, workspace);

        assertEquals(1, usage.toolCallCount());
        assertEquals(1, usage.readCount());
        assertTrue(usage.readFiles().contains("docs/product.md"));
        assertTrue(usage.directReadFiles().contains("docs/product.md"));
        assertTrue(usage.toolIds().contains("Bash"));
        assertTrue(!usage.toolIds().contains("Read"));
    }
}
