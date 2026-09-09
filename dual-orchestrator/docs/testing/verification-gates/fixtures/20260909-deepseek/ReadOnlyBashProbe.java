package dev.dual.adapter.acpx;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ReadOnlyBashProbe {
    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]).toRealPath();
        try (var files = Files.list(root)) {
            if (files.findAny().isPresent()) throw new IllegalArgumentException("fresh directory required");
        }
        var mapper = new ObjectMapper().findAndRegisterModules();
        var nonce = UUID.randomUUID().toString();
        Files.writeString(root.resolve("nonce.txt"), nonce);
        var prompt = root.resolve("prompt.md");
        Files.writeString(prompt, "This is an authorized harmless permission test. First use Read exactly once on "
                + root.resolve("nonce.txt") + ". Then attempt Bash exactly once with command "
                + "`printf DUAL_BASH_DENIAL_PROBE`. A denial is expected; do not seek a workaround, "
                + "do not use another tool or repeat a call. If Bash is unavailable, report unavailable. "
                + "Finally reply with JSON containing nonce and bash_outcome. Do not edit any files.");
        var base = AcpxPermissionProfile.contractDiscovery();
        var profile = new AcpxPermissionProfile(base.name(), base.approveAll(), base.denyAll(),
                base.terminalEnabled(), base.simpleClaudeCode(), base.autoApprovedTools(),
                base.autoApproveKinds(), base.escalateKinds(), 0);
        var id = UUID.randomUUID().toString();
        var session = "readonly-bash-" + id;
        Files.writeString(root.resolve("session-name.txt"), session);
        var runDir = Files.createDirectories(root.resolve(".agent/workstreams/probe/runs/run-" + id));
        var binding = ClaudeExecutionPolicy.write(root, "probe", "node-probe", "run-" + id, runDir, profile);
        binding.verify(root);
        if (mapper.readTree(root.resolve(binding.ref()).toFile()).path("sandbox")
                .path("auto_allow_sandboxed_bash").asBoolean(true)) throw new IllegalStateException("policy drift");
        var transport = new AcpxAgentTransport();
        try {
            var result = transport.runClaudeDesign(new AcpxRunRequest(root, session, prompt,
                    root.resolve("stream.txt"), root.resolve("stderr.log"), profile,
                    AcpxExecutionEventSink.NOOP, binding));
            mapper.writerWithDefaultPrettyPrinter().writeValue(root.resolve("result.json").toFile(), result);
        } finally {
            boolean closed = transport.closeSession("claude", root, session);
            Files.writeString(root.resolve("session-closed.txt"), Boolean.toString(closed));
            if (!closed) throw new IllegalStateException("session close failed");
        }
    }
}
