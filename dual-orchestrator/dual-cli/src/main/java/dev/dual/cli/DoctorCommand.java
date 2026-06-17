package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Component
@Command(name = "doctor", description = "Check local prerequisites and optional workstream ledger.", mixinStandardHelpOptions = true)
public final class DoctorCommand implements Callable<Integer> {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    static final String EXPECTED_ACPX_VERSION = "0.8.0";
    static final String EXPECTED_CODEX_ACP_PACKAGE = "@zed-industries/codex-acp@0.12.0";

    @Option(names = "--workdir", description = "Effective workspace directory. Defaults to current directory.")
    Path workdir = Path.of(".");

    @Parameters(index = "0", arity = "0..1", description = "Optional workstream id.")
    String workstreamId;

    private int failures;

    @Override
    public Integer call() throws Exception {
        var workspaceRoot = workdir.toAbsolutePath().normalize();
        System.out.println("Doctor");
        System.out.println("Workspace: " + workspaceRoot);
        System.out.println();

        checkGitCommand();
        checkGitRepository(workspaceRoot);
        checkAcpxCommand();
        if (workstreamId != null && !workstreamId.isBlank()) {
            checkWorkstreamLedger(workspaceRoot, workstreamId);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("Result: OK");
            return 0;
        }
        System.out.println("Result: FAIL (" + failures + " blocking issue" + (failures == 1 ? "" : "s") + ")");
        return 1;
    }

    private void checkGitCommand() throws IOException, InterruptedException {
        var result = run("git", "--version");
        if (result.exitCode() == 0) {
            ok("git command", result.output().trim());
        } else {
            fail("git command", result.output().trim());
        }
    }

    private void checkGitRepository(Path workspaceRoot) throws IOException, InterruptedException {
        var root = run("git", "-C", workspaceRoot.toString(), "rev-parse", "--show-toplevel");
        if (root.exitCode() != 0) {
            fail("git repository", root.output().trim());
            return;
        }
        var branch = run("git", "-C", workspaceRoot.toString(), "branch", "--show-current");
        var commit = run("git", "-C", workspaceRoot.toString(), "rev-parse", "--short", "HEAD");
        ok("git repository", root.output().trim());
        ok("git branch", branch.output().isBlank() ? "HEAD" : branch.output().trim());
        ok("git commit", commit.output().trim());
    }

    private void checkAcpxCommand() throws IOException, InterruptedException {
        var result = run("acpx", "--version");
        if (result.exitCode() != 0) {
            fail("acpx command", firstLine(result.output()));
            return;
        }
        var actualVersion = result.output().trim();
        if (!EXPECTED_ACPX_VERSION.equals(actualVersion)) {
            fail("acpx version", "expected " + EXPECTED_ACPX_VERSION + ", found " + actualVersion
                    + ". Install with: npm install -g acpx@" + EXPECTED_ACPX_VERSION);
            return;
        }
        ok("acpx version", actualVersion);
        checkAcpxCodexAdapterPinned();
    }

    private void checkAcpxCodexAdapterPinned() throws IOException, InterruptedException {
        var result = run("acpx", "config", "show");
        if (result.exitCode() != 0) {
            fail("acpx config", firstLine(result.output()));
            return;
        }
        var pin = evaluateCodexAdapterPin(result.output());
        switch (pin.level()) {
            case OK -> ok("acpx codex adapter", pin.message());
            case WARN -> warn("acpx codex adapter", pin.message());
            case FAIL -> fail("acpx codex adapter", pin.message());
        }
    }

    static PinCheck evaluateCodexAdapterPin(String configJson) {
        try {
            Map<String, Object> root = MAPPER.readValue(configJson, new TypeReference<>() {
            });
            var agents = objectMap(root.get("agents"));
            var codex = objectMap(agents.get("codex"));
            var command = stringValue(codex.get("command"));
            if (command.isBlank()) {
                return PinCheck.fail("missing agents.codex.command. Run ./scripts/configure-acpx.sh");
            }
            if (command.contains(EXPECTED_CODEX_ACP_PACKAGE)) {
                return PinCheck.ok(command);
            }
            if (looksLikeLocalCodexAcp(command)) {
                return PinCheck.warn("local codex-acp command is configured; reproducibility depends on that local binary: "
                        + command);
            }
            return PinCheck.fail("expected agents.codex.command to use " + EXPECTED_CODEX_ACP_PACKAGE
                    + ". Run ./scripts/configure-acpx.sh");
        } catch (Exception e) {
            return PinCheck.fail("unable to parse `acpx config show`: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text.trim() : "";
    }

    private static boolean looksLikeLocalCodexAcp(String command) {
        return command.contains("codex-acp")
                && !command.contains("@latest")
                && !command.matches(".*@[\\^~].*");
    }

    private void checkWorkstreamLedger(Path workspaceRoot, String id) {
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(id));
        System.out.println();
        System.out.println("Workstream: " + id);
        requiredPath("workstream dir", paths.workstreamDir());
        requiredPath("workflow current", paths.workflowCurrentJson());
        requiredPath("events", paths.eventsJsonl());
        optionalPath("runs dir", paths.runsDir());
        optionalPath("context dir", paths.workstreamDir().resolve("context"));
        optionalPath("git evidence", paths.workstreamDir().resolve("artifacts/git-evidence/git-evidence.json"));
    }

    private void requiredPath(String label, Path path) {
        if (Files.exists(path)) {
            ok(label, path.toString());
        } else {
            fail(label, "missing: " + path);
        }
    }

    private void optionalPath(String label, Path path) {
        if (Files.exists(path)) {
            ok(label, path.toString());
        } else {
            warn(label, "not generated yet: " + path);
        }
    }

    private void ok(String label, String detail) {
        System.out.println("[OK]   " + label + " - " + detail);
    }

    private void warn(String label, String detail) {
        System.out.println("[WARN] " + label + " - " + detail);
    }

    private void fail(String label, String detail) {
        failures++;
        System.out.println("[FAIL] " + label + " - " + detail);
    }

    private static CommandResult run(String... command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        var completed = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new CommandResult(124, "command timed out: " + String.join(" ", command));
        }
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new CommandResult(process.exitValue(), output);
    }

    private static String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return "(no output)";
        }
        return output.lines().findFirst().orElse(output).trim();
    }

    private record CommandResult(int exitCode, String output) {
    }

    enum PinLevel {
        OK,
        WARN,
        FAIL
    }

    record PinCheck(PinLevel level, String message) {
        static PinCheck ok(String message) {
            return new PinCheck(PinLevel.OK, message);
        }

        static PinCheck warn(String message) {
            return new PinCheck(PinLevel.WARN, message);
        }

        static PinCheck fail(String message) {
            return new PinCheck(PinLevel.FAIL, message);
        }
    }
}
