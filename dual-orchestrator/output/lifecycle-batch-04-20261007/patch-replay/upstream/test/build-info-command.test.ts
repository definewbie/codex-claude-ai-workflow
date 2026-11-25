import assert from "node:assert/strict";
import test from "node:test";
import { CLAUDE_EXECUTION_POLICY_SCHEMA } from "../src/acp/claude-execution-policy.js";
import { e1BuildInfoDocument } from "../src/cli/build-info-command.js";
import { E1_PATCH_SHA256, E1_UPSTREAM_COMMIT } from "../src/e1-build-info.generated.js";

test("E1 build info exposes only the frozen transport identity", () => {
  assert.deepEqual(e1BuildInfoDocument(), {
    upstream_commit: E1_UPSTREAM_COMMIT,
    patch_sha256: E1_PATCH_SHA256,
    policy_schema: CLAUDE_EXECUTION_POLICY_SCHEMA,
  });
});
