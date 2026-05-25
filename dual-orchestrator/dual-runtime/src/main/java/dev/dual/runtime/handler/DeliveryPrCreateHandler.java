package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeliveryPrCreateHandler implements TaskHandler {
    @Override
    public String type() {
        return "delivery.create_pr";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var deliveryDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("delivery");
        Files.createDirectories(deliveryDir);

        var prBody = deliveryDir.resolve("pr-body.md");
        if (!Files.exists(prBody)) {
            return failed("missing_pr_body", "PR body not found: " + prBody);
        }
        var branch = git(context.workspaceRoot(), "branch", "--show-current");
        if (branch.exitCode() != 0 || branch.output().trim().isBlank()) {
            return failed("git_branch_failed", branch.output());
        }
        var head = branch.output().trim();
        if ("main".equals(head) || "master".equals(head)) {
            return failed("protected_branch", "refusing to create PR from protected branch: " + head);
        }
        var remote = git(context.workspaceRoot(), "remote", "get-url", "origin");
        if (remote.exitCode() != 0 || remote.output().trim().isBlank()) {
            return failed("missing_origin_remote", remote.output());
        }

        var commitResult = new DeliveryCommitHandler().execute(context);
        if (commitResult.status() != NodeStatus.COMPLETED) {
            return commitResult;
        }

        var push = git(context.workspaceRoot(), "push", "-u", "origin", head);
        if (push.exitCode() != 0) {
            return failed("git_push_failed", push.output());
        }

        var ghCommand = configText(context.definition().config(), "gh_command", "gh");
        var base = configText(context.definition().config(), "base", "main");
        var title = configText(context.definition().config(), "title", firstLine(readOrDefault(
                context.workspaceRoot()
                        .resolve(".agent")
                        .resolve("workstreams")
                        .resolve(context.workstreamId())
                        .resolve("requirement.md"),
                "Dual agent task " + context.workstreamId()
        )));
        var draft = Boolean.TRUE.equals(context.definition().config().get("draft"));
        var gh = ghCreate(context.workspaceRoot(), ghCommand, base, head, title, prBody, draft);
        Files.writeString(deliveryDir.resolve("gh-pr-create.stdout.log"), gh.stdout(), StandardCharsets.UTF_8);
        Files.writeString(deliveryDir.resolve("gh-pr-create.stderr.log"), gh.stderr(), StandardCharsets.UTF_8);
        if (gh.exitCode() != 0) {
            return failed("gh_pr_create_failed", gh.stderr().isBlank() ? gh.stdout() : gh.stderr());
        }

        var prUrl = firstUrl(gh.stdout());
        Files.writeString(deliveryDir.resolve("pr-url.txt"), prUrl + System.lineSeparator(), StandardCharsets.UTF_8);
        var prUrlRef = toWorkspaceRef(context.workspaceRoot(), deliveryDir.resolve("pr-url.txt"));
        var result = new LinkedHashMap<String, Object>();
        result.put("pr_created", true);
        result.put("pr_url", prUrl);
        result.put("branch", head);
        result.put("base", base);
        result.put("draft", draft);
        result.put("commit_created", commitResult.result().getOrDefault("commit_created", false));
        result.put("created_at", Instant.now().toString());
        return NodeResult.completed("pr_created", List.of(prUrlRef), result);
    }

    private static CommandResult ghCreate(Path workdir, String ghCommand, String base, String head, String title, Path prBody, boolean draft)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(ghCommand);
        command.addAll(List.of(
                "pr", "create",
                "--base", base,
                "--head", head,
                "--title", title,
                "--body-file", prBody.toString()
        ));
        if (draft) {
            command.add("--draft");
        }
        return command(workdir, command);
    }

    private static CommandResult git(Path workdir, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        return command(workdir, command);
    }

    private static CommandResult command(Path workdir, List<String> command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .start();
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        return new CommandResult(exitCode, stdout, stderr);
    }

    private static NodeResult failed(String outcome, String error) {
        return new NodeResult(outcome, NodeStatus.FAILED, List.of(), Map.of(
                "pr_created", false,
                "error", error == null ? "" : error.stripTrailing()
        ), error);
    }

    private static String configText(Map<String, Object> config, String key, String fallback) {
        var value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
    }

    private static String firstLine(String text) {
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("Dual agent task");
    }

    private static String firstUrl(String text) {
        for (String token : text.split("\\s+")) {
            if (token.startsWith("http://") || token.startsWith("https://")) {
                return token.strip();
            }
        }
        return text.strip();
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
        CommandResult(int exitCode, String output) {
            this(exitCode, output, output);
        }

        String output() {
            return stdout + stderr;
        }
    }
}
