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
    static final String EXPECTED_ACPX_VERSION = "0.13.2";
    static final String EXPECTED_ACPX_UPSTREAM_COMMIT = "fd173f04aa1b56f9e3f5ca5190c034ddcae28792";
    static final String EXPECTED_ACPX_PATCH_SHA256 = "41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c";
    static final String EXPECTED_ACPX_POLICY_SCHEMA = "claude_execution_policy.v2";
    static final String EXPECTED_CLAUDE_ACP_PACKAGE = "@agentclientprotocol/claude-agent-acp@0.70.0";
    static final String EXPECTED_CODEX_ACP_VERSION = "0.16.0";

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
        var executable = acpxExecutable();
        var result = run(executable, "--version");
        if (result.exitCode() != 0) {
            fail("acpx command", firstLine(result.output()));
            return;
        }
        var actualVersion = result.output().trim();
        if (!EXPECTED_ACPX_VERSION.equals(actualVersion)) {
            fail("acpx version", "expected " + EXPECTED_ACPX_VERSION + ", found " + actualVersion
                    + ". Run ./scripts/install-acpx-e1.sh and export DUAL_ACPX_EXECUTABLE");
            return;
        }
        ok("acpx version", actualVersion);
        checkAcpxBuildIdentity(executable);
        checkAcpxAdaptersPinned(executable);
    }

    private void checkAcpxBuildIdentity(String executable) throws IOException, InterruptedException {
        var result = run(executable, "build-info", "--json");
        if (result.exitCode() != 0) {
            fail("E1 policy transport", "NETWORK_AUTHORITY_NOT_CONFIGURED: patched acpx build-info is unavailable");
            return;
        }
        var check = evaluateAcpxBuildIdentity(result.output());
        reportPin("E1 policy transport", check);
    }

    static PinCheck evaluateAcpxBuildIdentity(String buildInfoJson) {
        try {
            Map<String, Object> document = MAPPER.readValue(buildInfoJson, new TypeReference<>() {
            });
            if (!EXPECTED_ACPX_UPSTREAM_COMMIT.equals(stringValue(document.get("upstream_commit")))
                    || !EXPECTED_ACPX_PATCH_SHA256.equals(stringValue(document.get("patch_sha256")))
                    || !EXPECTED_ACPX_POLICY_SCHEMA.equals(stringValue(document.get("policy_schema")))) {
                return PinCheck.fail("NETWORK_AUTHORITY_NOT_CONFIGURED: patched acpx build identity mismatch");
            }
            return PinCheck.ok("upstream=" + EXPECTED_ACPX_UPSTREAM_COMMIT
                    + ", patch=" + EXPECTED_ACPX_PATCH_SHA256);
        } catch (Exception failure) {
            return PinCheck.fail("NETWORK_AUTHORITY_NOT_CONFIGURED: invalid patched acpx build-info: "
                    + failure.getMessage());
        }
    }

    private void checkAcpxAdaptersPinned(String executable) throws IOException, InterruptedException {
        var result = run(executable, "config", "show");
        if (result.exitCode() != 0) {
            fail("acpx config", firstLine(result.output()));
            return;
        }
        reportPin("acpx claude adapter", evaluateAdapterPin(
                result.output(),
                "claude",
                "claude-agent-acp",
                EXPECTED_CLAUDE_ACP_PACKAGE
        ));
        reportPin("acpx codex adapter", evaluateCodexAdapterPin(result.output()));
        reportPin("Claude ACP environment bridge", evaluateClaudeEnvironmentBridge(
                result.output(),
                readUserClaudeSettings(),
                System.getenv()
        ));
    }

    private static String acpxExecutable() {
        var configured = System.getenv("DUAL_ACPX_EXECUTABLE");
        return configured == null || configured.isBlank() ? "acpx" : configured.trim();
    }

    static PinCheck evaluateCodexAdapterPin(String configJson) {
        return evaluateAdapterPin(
                configJson,
                "codex",
                "codex-acp",
                expectedCodexAcpPackage()
        );
    }

    static String expectedCodexAcpPackage() {
        return "@zed-industries/codex-acp-" + codexPlatformSuffix() + "@" + EXPECTED_CODEX_ACP_VERSION;
    }

    private static String codexPlatformSuffix() {
        var os = System.getProperty("os.name", "").toLowerCase();
        var arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            return arch.contains("aarch64") || arch.contains("arm64") ? "darwin-arm64" : "darwin-x64";
        }
        if (os.contains("linux")) {
            return arch.contains("aarch64") || arch.contains("arm64") ? "linux-arm64" : "linux-x64";
        }
        if (os.contains("win")) {
            return arch.contains("aarch64") || arch.contains("arm64") ? "win32-arm64" : "win32-x64";
        }
        return "unsupported-" + arch;
    }

    static PinCheck evaluateClaudeEnvironmentBridge(
            String configJson,
            String claudeSettingsJson,
            Map<String, String> processEnv
    ) {
        var settingsEnv = readEnvObject(claudeSettingsJson);
        if (!usesAnthropicCompatibleSettings(settingsEnv)) {
            return PinCheck.ok("Claude settings do not define third-party Anthropic-compatible env");
        }
        if (processEnv.containsKey("ANTHROPIC_BASE_URL")
                && (processEnv.containsKey("ANTHROPIC_AUTH_TOKEN") || processEnv.containsKey("ANTHROPIC_API_KEY"))) {
            return PinCheck.ok("ANTHROPIC_* variables are present in the current process environment");
        }
        var command = agentCommand(configJson, "claude");
        if (command.contains("acpx-claude-agent-env.sh")) {
            return PinCheck.ok("Claude adapter command reads ~/.claude/settings.json env without writing secrets to acpx config");
        }
        return PinCheck.fail("~/.claude/settings.json contains ANTHROPIC_BASE_URL and token env, "
                + "but agents.claude.command does not use scripts/acpx-claude-agent-env.sh and the current process "
                + "does not export ANTHROPIC_* variables. Run ./scripts/configure-acpx.sh");
    }

    static PinCheck evaluateAdapterPin(
            String configJson,
            String agent,
            String binaryName,
            String expectedPackage
    ) {
        try {
            Map<String, Object> root = MAPPER.readValue(configJson, new TypeReference<>() {
            });
            var agents = objectMap(root.get("agents"));
            var agentConfig = objectMap(agents.get(agent));
            var command = stringValue(agentConfig.get("command"));
            if (command.isBlank()) {
                return PinCheck.fail("missing agents." + agent + ".command. Run ./scripts/configure-acpx.sh");
            }
            if (command.contains(expectedPackage)) {
                return PinCheck.ok(command);
            }
            if (looksLikeLocalAcpAdapter(command, binaryName)) {
                return PinCheck.warn("local " + binaryName + " command is configured; reproducibility depends on that local binary: "
                        + command);
            }
            return PinCheck.fail("expected agents." + agent + ".command to use " + expectedPackage
                    + ". Run ./scripts/configure-acpx.sh");
        } catch (Exception e) {
            return PinCheck.fail("unable to parse `acpx config show`: " + e.getMessage());
        }
    }

    private static String agentCommand(String configJson, String agent) {
        try {
            Map<String, Object> root = MAPPER.readValue(configJson, new TypeReference<>() {
            });
            var agents = objectMap(root.get("agents"));
            var agentConfig = objectMap(agents.get(agent));
            return stringValue(agentConfig.get("command"));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Map<String, Object> readEnvObject(String claudeSettingsJson) {
        if (claudeSettingsJson == null || claudeSettingsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> root = MAPPER.readValue(claudeSettingsJson, new TypeReference<>() {
            });
            return objectMap(root.get("env"));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static boolean usesAnthropicCompatibleSettings(Map<String, Object> settingsEnv) {
        return settingsEnv.containsKey("ANTHROPIC_BASE_URL")
                && (settingsEnv.containsKey("ANTHROPIC_AUTH_TOKEN") || settingsEnv.containsKey("ANTHROPIC_API_KEY"));
    }

    private static String readUserClaudeSettings() {
        var path = Path.of(System.getProperty("user.home"), ".claude", "settings.json");
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private void reportPin(String label, PinCheck pin) {
        switch (pin.level()) {
            case OK -> ok(label, pin.message());
            case WARN -> warn(label, pin.message());
            case FAIL -> fail(label, pin.message());
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

    private static boolean looksLikeLocalAcpAdapter(String command, String binaryName) {
        return command.contains(binaryName)
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
