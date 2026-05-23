package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class AcpxAgentTransport {
    public boolean isAvailable(Path workdir) {
        try {
            var process = new ProcessBuilder("acpx", "--help")
                    .directory(workdir.toFile())
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws IOException, InterruptedException {
        var result = run("claude", request);
        if (result.exitCode() == 4 && stderrContainsMissingSession(request.stderrPath())) {
            createSession("claude", request.workdir(), request.sessionName());
            result = run("claude", request);
        }
        return result;
    }

    public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws IOException, InterruptedException {
        var result = run("codex", request);
        if (result.exitCode() == 4 && stderrContainsMissingSession(request.stderrPath())) {
            createSession("codex", request.workdir(), request.sessionName());
            result = run("codex", request);
        }
        return result;
    }

    private AcpxRunResult run(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("acpx");
        command.add("--timeout");
        command.add("600");
        command.add("--approve-reads");
        command.add("--non-interactive-permissions");
        command.add("fail");
        command.add(agent);
        command.add("-s");
        command.add(request.sessionName());
        command.add("-f");
        command.add(request.promptPath().toString());

        var startedAt = Instant.now();
        var process = new ProcessBuilder(command)
                .directory(request.workdir().toFile())
                .redirectOutput(request.stdoutPath().toFile())
                .redirectError(request.stderrPath().toFile())
                .start();
        var exitCode = process.waitFor();
        return new AcpxRunResult(exitCode, startedAt, Instant.now(), List.copyOf(command));
    }

    private void createSession(String agent, Path workdir, String sessionName) throws IOException, InterruptedException {
        var process = new ProcessBuilder("acpx", agent, "sessions", "new", "--name", sessionName)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("failed to create acpx " + agent + " session " + sessionName + ": " + output);
        }
    }

    private boolean stderrContainsMissingSession(Path stderrPath) {
        try {
            return java.nio.file.Files.readString(stderrPath).contains("No acpx session found");
        } catch (IOException ignored) {
            return false;
        }
    }
}
