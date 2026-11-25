package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptManifestWriterTest {
    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void totalsComeFromFinalPromptNotSectionSum() throws Exception {
        var prompt = tempDir.resolve("prompt.md");
        Files.writeString(prompt, "alpha\n\nbeta\n", StandardCharsets.UTF_8);
        var manifest = PromptManifestWriter.manifestPathFor(prompt);

        new PromptManifestWriter().write(
                manifest,
                "ws",
                "run-1",
                "node",
                "claude",
                "template",
                prompt,
                List.of(
                        new AgentPromptRenderer.PromptSection("one", "static_instruction", "one",
                                "alpha", "test", true),
                        new AgentPromptRenderer.PromptSection("two", "static_instruction", "two",
                                "beta", "test", true)
                )
        );

        var json = objectMapper.readValue(manifest.toFile(), Map.class);
        var finalBytes = Files.readAllBytes(prompt).length;
        assertEquals(finalBytes, ((Number) json.get("totalBytes")).intValue());
        assertEquals((finalBytes + 3) / 4, ((Number) json.get("totalEstimatedTokens")).intValue());
    }
}
