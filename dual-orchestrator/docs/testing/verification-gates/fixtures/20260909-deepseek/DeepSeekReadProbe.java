package dev.dual.adapter.acpx;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class DeepSeekReadProbe {
    public static void main(String[] args) throws Exception {
        var root = Path.of(args[0]).toRealPath();
        try (var files = Files.list(root)) {
            if (files.findAny().isPresent()) throw new IllegalArgumentException("fresh directory required");
        }
        var nonce = UUID.randomUUID().toString();
        Files.writeString(root.resolve("nonce.txt"), nonce);
        var prompt = root.resolve("prompt.md");
        Files.writeString(prompt, "Use Read exactly once to read " + root.resolve("nonce.txt")
                + ". No other tools. Reply only with JSON {\"nonce\":\"<file contents>\"}.");
        var base = AcpxPermissionProfile.observe();
        var profile = new AcpxPermissionProfile(base.name(), base.approveAll(), base.denyAll(),
                base.terminalEnabled(), base.simpleClaudeCode(), base.autoApprovedTools(),
                base.autoApproveKinds(), base.escalateKinds(), 0);
        var id = UUID.randomUUID().toString();
        var runDir = Files.createDirectories(root.resolve(".agent/workstreams/probe/runs/run-" + id));
        var binding = ClaudeExecutionPolicy.write(root, "probe", "node-probe", "run-" + id, runDir, profile);
        var transport = new AcpxAgentTransport();
        boolean closed;
        try {
            var result = transport.runClaudeDesign(new AcpxRunRequest(root, "read-probe-" + id, prompt,
                    root.resolve("stream.jsonl"), root.resolve("stderr.log"), profile,
                    AcpxExecutionEventSink.NOOP, binding));
            new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                    .writeValue(root.resolve("result.json").toFile(), result);
            if (result.exitCode() != 0 || !result.activeAdapterSignals().isEmpty())
                throw new IllegalStateException("inspect typed result before any retry");
        } finally {
            closed = transport.closeSession("claude", root, "read-probe-" + id);
            Files.writeString(root.resolve("session-closed.txt"), Boolean.toString(closed));
        }
        if (!closed) throw new IllegalStateException("session close failed");
    }
}
