import type { Command } from "commander";
import { CLAUDE_EXECUTION_POLICY_SCHEMA } from "../acp/claude-execution-policy.js";
import { E1_PATCH_SHA256, E1_UPSTREAM_COMMIT } from "../e1-build-info.generated.js";

export function e1BuildInfoDocument() {
  return {
    upstream_commit: E1_UPSTREAM_COMMIT,
    patch_sha256: E1_PATCH_SHA256,
    policy_schema: CLAUDE_EXECUTION_POLICY_SCHEMA,
  };
}

export function registerBuildInfoCommand(program: Command): void {
  program
    .command("build-info")
    .description("Show the fixed E1 policy transport build identity")
    .option("--json", "Print build identity as JSON")
    .action((flags: { json?: boolean }) => {
      const info = e1BuildInfoDocument();
      if (flags.json) {
        process.stdout.write(`${JSON.stringify(info)}\n`);
        return;
      }
      process.stdout.write(
        `upstream_commit=${info.upstream_commit}\npatch_sha256=${info.patch_sha256}\npolicy_schema=${info.policy_schema}\n`,
      );
    });
}
