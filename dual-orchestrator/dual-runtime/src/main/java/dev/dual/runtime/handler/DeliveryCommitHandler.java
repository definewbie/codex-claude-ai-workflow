package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeliveryCommitHandler implements TaskHandler {
    @Override
    public String type() {
        return "delivery.git_commit";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var commitMessage = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("delivery")
                .resolve("commit-message.txt");
        if (!Files.exists(commitMessage)) {
            return new NodeResult("missing_commit_message", NodeStatus.FAILED, List.of(), MapBuilder.of(
                    "commit_created", false,
                    "error", "commit message not found: " + commitMessage
            ), "commit message not found");
        }

        var statusBefore = git(context, "status", "--short", "--", ".", ":!.agent");
        if (statusBefore.exitCode() != 0) {
            return failed("git_status_failed", statusBefore.output());
        }
        if (statusBefore.output().isBlank()) {
            DeliveryLedgerWriter.update(deliveryDir(context), Map.of(
                    "status", "skipped_no_changes",
                    "commitCreated", false,
                    "reason", "no business changes"
            ));
            return NodeResult.completed("skipped_no_changes", List.of(), MapBuilder.of(
                    "commit_created", false,
                    "reason", "no business changes"
            ));
        }

        var add = git(context, "add", "-A", "--", ".", ":!.agent");
        if (add.exitCode() != 0) {
            return failed("git_add_failed", add.output());
        }
        var commit = git(context, "commit", "-F", commitMessage.toString());
        if (commit.exitCode() != 0) {
            return failed("git_commit_failed", commit.output());
        }
        var rev = git(context, "rev-parse", "HEAD");
        if (rev.exitCode() != 0) {
            return failed("git_rev_parse_failed", rev.output());
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("commit_created", true);
        result.put("commit", rev.output().trim());
        result.put("status_before", statusBefore.output());
        result.put("commit_message_path", toWorkspaceRef(context, commitMessage));
        DeliveryLedgerWriter.update(deliveryDir(context), Map.of(
                "status", "committed",
                "commitCreated", true,
                "commit", rev.output().trim(),
                "commitMessagePath", toWorkspaceRef(context, commitMessage)
        ));
        DeliveryReportUpdater.update(workstreamDir(context), "committed", rev.output().trim(), "");
        return NodeResult.completed("committed", List.of(toWorkspaceRef(context, commitMessage)), result);
    }

    private static java.nio.file.Path workstreamDir(TaskContext context) {
        return context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
    }

    private static java.nio.file.Path deliveryDir(TaskContext context) {
        return workstreamDir(context).resolve("delivery");
    }

    private static NodeResult failed(String outcome, String output) {
        return new NodeResult(outcome, NodeStatus.FAILED, List.of(), MapBuilder.of(
                "commit_created", false,
                "error", output == null ? "" : output.stripTrailing()
        ), output);
    }

    private static CommandResult git(TaskContext context, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(context.workspaceRoot().toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        return new CommandResult(exitCode, output);
    }

    private static String toWorkspaceRef(TaskContext context, java.nio.file.Path path) {
        return context.workspaceRoot().toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private record CommandResult(int exitCode, String output) {
    }

    private static final class MapBuilder {
        private static LinkedHashMap<String, Object> of(Object... pairs) {
            var map = new LinkedHashMap<String, Object>();
            for (int i = 0; i < pairs.length; i += 2) {
                map.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
            return map;
        }
    }
}
