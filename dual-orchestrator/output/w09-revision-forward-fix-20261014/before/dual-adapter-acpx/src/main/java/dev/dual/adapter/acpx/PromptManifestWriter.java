package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes prompt provenance manifests: which sections formed the final prompt,
 * where each section came from, and its approximate token size.
 *
 * <p>The manifest is a projection/read model, not a source of truth. It does
 * not store prompt body text; full content is only written when
 * DUAL_AGENT_PROMPT_DEBUG is enabled. The manifest stores section
 * bytes/estimatedTokens/sha256 plus source metadata so memory optimizations can
 * be verified without re-parsing prompt text.</p>
 */
class PromptManifestWriter {

    static Path manifestPathFor(Path promptPath) {
        return promptPath.resolveSibling(promptPath.getFileName() + ".manifest.json");
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    void write(Path manifestPath,
               String workstreamId,
               String runId,
               String nodeId,
               String agent,
               String promptTemplate,
               Path promptPath,
               List<AgentPromptRenderer.PromptSection> sections) throws IOException {
        write(manifestPath, workstreamId, runId, nodeId, agent, promptTemplate, promptPath, sections, null);
    }

    void write(Path manifestPath, String workstreamId, String runId, String nodeId, String agent,
               String promptTemplate, Path promptPath, List<AgentPromptRenderer.PromptSection> sections,
               dev.dual.runtime.handler.AuthoredExecutionContext.Snapshot authoredContext) throws IOException {
        Files.createDirectories(manifestPath.getParent());
        var sectionEntries = new ArrayList<Map<String, Object>>();
        for (var section : sections) {
            var bytes = section.content().getBytes(StandardCharsets.UTF_8);
            var estimatedTokens = estimatedTokens(bytes.length);
            var entry = new LinkedHashMap<String, Object>();
            entry.put("name", section.name());
            entry.put("sourceType", section.sourceType());
            entry.put("sourceRef", section.sourceRef());
            entry.put("bytes", bytes.length);
            entry.put("estimatedTokens", estimatedTokens);
            entry.put("sha256", sha256(bytes));
            entry.put("includedReason", section.includedReason());
            entry.put("required", section.required());
            sectionEntries.add(entry);
        }
        var promptBytes = Files.exists(promptPath) ? Files.readAllBytes(promptPath) : new byte[0];
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("schemaVersion", 1);
        manifest.put("workstreamId", workstreamId);
        manifest.put("runId", runId);
        manifest.put("nodeId", nodeId);
        manifest.put("agent", agent);
        manifest.put("promptTemplate", promptTemplate);
        manifest.put("promptPath", promptPath.toString());
        manifest.put("totalBytes", promptBytes.length);
        manifest.put("totalEstimatedTokens", estimatedTokens(promptBytes.length));
        manifest.put("sections", sectionEntries);
        if (authoredContext != null) manifest.put("authoredContext", authoredContext);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
    }

    private static long estimatedTokens(long byteSize) {
        return (byteSize + 3) / 4;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ignored) {
            return "";
        }
    }
}
