package dev.dual.runtime.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class TestLedgerFixture {
    Path workspaceRoot;
    String workstreamId;
    Path wsDir;

    TestLedgerFixture(Path workspaceRoot, String workstreamId) {
        this.workspaceRoot = workspaceRoot;
        this.workstreamId = workstreamId;
    }

    void init() throws IOException {
        wsDir = workspaceRoot.resolve(".agent").resolve("workstreams").resolve(workstreamId);
        Files.createDirectories(wsDir);
    }

    Path writeRequirement(String text) throws IOException {
        var path = wsDir.resolve("requirement.md");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    Path writeAgentOutput(String name, String content) throws IOException {
        var path = wsDir.resolve("runs").resolve("run-001").resolve("outputs").resolve(name);
        Files.createDirectories(path.getParent());
        if (name.endsWith(".contract.json")) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var json = mapper.readTree(content);
                if (json instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
                    var sourceName = name.substring(0, name.length() - ".contract.json".length()) + ".json";
                    var source = path.resolveSibling(sourceName);
                    object.put("workstream_id", workstreamId);
                    object.put("run_id", "run-001");
                    object.put("node_instance_id", "node-fixture-" + sourceName.replaceAll("[^A-Za-z0-9._-]", "-"));
                    object.put("source_output_ref", workspaceRoot.relativize(source).toString());
                    content = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
                }
            } catch (Exception ignored) {
                // Malformed sidecars are intentional fixtures in rejection tests.
            }
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
        if (name.endsWith(".contract.json")) {
            try {
                AgentOutputLineageFixture.publish(workspaceRoot, workstreamId, path);
            } catch (Exception e) {
                throw new IOException("failed to publish test lineage", e);
            }
        }
        return path;
    }

    Path writeContextPackage(String name, String content) throws IOException {
        var path = wsDir.resolve("context").resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    Path writeHandoffContract(
            java.util.List<String> completedWork,
            java.util.List<String> changedFiles,
            java.util.List<String> openRisks,
            String verification,
            String suggestedPrompt
    ) throws IOException {
        var path = wsDir.resolve("runs").resolve("run-010").resolve("outputs").resolve("stage-claude-handoff.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "{}", StandardCharsets.UTF_8);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                AgentOutputContractSidecar.pathFor(path).toFile(),
                java.util.Map.ofEntries(
                        java.util.Map.entry("artifact_type", "agent_output_contract"),
                        java.util.Map.entry("workstream_id", workstreamId),
                        java.util.Map.entry("run_id", "run-010"),
                        java.util.Map.entry("node_id", "stage.claude_handoff"),
                        java.util.Map.entry("node_instance_id", "node-stage-claude-handoff"),
                        java.util.Map.entry("source_output_ref", workspaceRoot.relativize(path).toString()),
                        java.util.Map.entry("source_artifact_type", "stage_handoff"),
                        java.util.Map.entry("decision", "HANDOFF_READY"),
                        java.util.Map.entry("summary", "handoff ready"),
                        java.util.Map.entry("completed_work", completedWork),
                        java.util.Map.entry("important_decisions", java.util.List.of()),
                        java.util.Map.entry("changed_files", changedFiles),
                        java.util.Map.entry("open_risks", openRisks),
                        java.util.Map.entry("verification_summary", verification),
                        java.util.Map.entry("recommended_next_stage", "delivery"),
                        java.util.Map.entry("suggested_next_prompt", suggestedPrompt)
                ));
        try {
            AgentOutputLineageFixture.publish(workspaceRoot, workstreamId, AgentOutputContractSidecar.pathFor(path));
        } catch (Exception e) {
            throw new IOException("failed to publish handoff lineage", e);
        }
        return path;
    }

    Path writeGitEvidence(String statusShort, String diffStat, String diffPatch) throws IOException {
        var evidenceDir = wsDir.resolve("artifacts").resolve("git-evidence");
        Files.createDirectories(evidenceDir);
        if (statusShort != null) {
            Files.writeString(evidenceDir.resolve("git-status-short.txt"), statusShort, StandardCharsets.UTF_8);
        }
        if (diffStat != null) {
            Files.writeString(evidenceDir.resolve("diff.stat"), diffStat, StandardCharsets.UTF_8);
        }
        if (diffPatch != null) {
            Files.writeString(evidenceDir.resolve("diff.patch"), diffPatch, StandardCharsets.UTF_8);
        }
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        var changedFiles = new StringBuilder();
        if (statusShort != null) {
            for (var line : statusShort.split("\n")) {
                var parts = line.trim().split("\\s+", 2);
                changedFiles.append(parts.length > 1 ? parts[1] : line.trim()).append("\n");
            }
        }
        Files.writeString(evidenceDir.resolve("changed-files.txt"), changedFiles.toString(), StandardCharsets.UTF_8);
        return evidenceDir;
    }
}
