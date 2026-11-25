import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { buildClaudeCodeOptionsMeta, isClaudeAcpCommand } from "../src/acp/agent-command.js";
import { loadClaudeExecutionPolicy } from "../src/acp/claude-execution-policy.js";

const DENIED_ENV_NAMES = ["ANTHROPIC_API_KEY", "ANTHROPIC_AUTH_TOKEN", "ANTHROPIC_BASE_URL"];

test("non-execute policy denies Bash in managed permissions and forwarded metadata", async () => {
  const fixture = await policyFixture((raw) =>
    raw.replace('"auto_allow_sandboxed_bash":true', '"auto_allow_sandboxed_bash":false'),
  );
  const options = loadClaudeExecutionPolicy(fixture.root, fixture.binding);
  assert.equal(options.sandbox.autoAllowBashIfSandboxed, false);
  assert.equal(options.managedSettings.sandbox.autoAllowBashIfSandboxed, false);
  assert.ok(options.managedSettings.permissions.deny.includes("Bash"));
  const meta = buildClaudeCodeOptionsMeta(
    {
      claudeExecutionPolicyRef: fixture.binding.ref,
      claudeExecutionPolicySha256: fixture.binding.sha256,
    },
    true,
    fixture.root,
  );
  assert.ok(meta);
  assert.deepEqual((meta.claudeCode as { options: unknown }).options, options);
  const execute = await policyFixture();
  const executeOptions = loadClaudeExecutionPolicy(execute.root, execute.binding);
  assert.equal(executeOptions.sandbox.autoAllowBashIfSandboxed, true);
  assert.ok(!executeOptions.managedSettings.permissions.deny.includes("Bash"));
});

test("Bash approval must be a boolean rather than a truthy permission", async () => {
  for (const value of ['"false"', "null", "1"]) {
    const fixture = await policyFixture((raw) =>
      raw.replace('"auto_allow_sandboxed_bash":true', `"auto_allow_sandboxed_bash":${value}`),
    );
    assert.throws(
      () => loadClaudeExecutionPolicy(fixture.root, fixture.binding),
      /auto_allow_sandboxed_bash must be a boolean/,
    );
  }
});

test("Claude execution policy separates local binding option from managed restrictions", async () => {
  const fixture = await policyFixture((raw) =>
    raw.replace('"allow_local_binding":false', '"allow_local_binding":true'),
  );
  const options = loadClaudeExecutionPolicy(fixture.root, fixture.binding);

  assert.deepEqual(options.settingSources, []);
  assert.equal(options.sandbox.enabled, true);
  assert.equal(options.sandbox.failIfUnavailable, true);
  assert.equal(options.sandbox.allowUnsandboxedCommands, false);
  assert.deepEqual(
    (options.sandbox.credentials as { envVars: unknown[] }).envVars,
    DENIED_ENV_NAMES.map((name) => ({ name, mode: "deny" })),
  );
  assert.equal(
    (options.sandbox.network as { allowLocalBinding?: boolean }).allowLocalBinding,
    true,
  );
  assert.equal(
    (options.managedSettings.sandbox.network as { allowLocalBinding?: boolean }).allowLocalBinding,
    undefined,
  );
  assert.equal(
    (options.managedSettings.sandbox.network as { strictAllowlist?: boolean }).strictAllowlist,
    true,
  );
});

test("Claude execution policy preserves a false local binding option", async () => {
  const fixture = await policyFixture();
  const options = loadClaudeExecutionPolicy(fixture.root, fixture.binding);

  assert.equal(
    (options.sandbox.network as { allowLocalBinding?: boolean }).allowLocalBinding,
    false,
  );
  assert.equal(
    (options.managedSettings.sandbox.network as { allowLocalBinding?: boolean }).allowLocalBinding,
    undefined,
  );
});

test("production wrapper command shape is recognized as Claude ACP", () => {
  assert.equal(
    isClaudeAcpCommand("/opt/dual/acpx-claude-agent-env.sh", [
      "npx",
      "-y",
      "@agentclientprotocol/claude-agent-acp@0.70.0",
    ]),
    true,
  );
});

test("validated DNS names are not mistaken for token prefixes", async () => {
  const fixture = await policyFixture((raw) =>
    raw.replace('"example.com"', '"task-api.example.org"'),
  );
  const options = loadClaudeExecutionPolicy(fixture.root, fixture.binding);
  assert.deepEqual((options.sandbox.network as { allowedDomains: string[] }).allowedDomains, [
    "task-api.example.org",
  ]);
  const invalid = await policyFixture((raw) =>
    raw.replace('"example.com"', '"https://sk-a.example.org"'),
  );
  assert.throws(
    () => loadClaudeExecutionPolicy(invalid.root, invalid.binding),
    /non-canonical domain/,
  );
});

test("control file protection covers sandbox processes and native file tools", async () => {
  const fixture = await policyFixture((raw) =>
    raw.replace(
      '"deny_write":[]',
      '"deny_write":["/control/nodes","/control/execution-authority"]',
    ),
  );
  const options = loadClaudeExecutionPolicy(fixture.root, fixture.binding);
  assert.deepEqual((options.sandbox.filesystem as { denyWrite: string[] }).denyWrite, [
    "/control/nodes",
    "/control/execution-authority",
  ]);
  assert.deepEqual(options.managedSettings.permissions.deny, [
    "Edit(//control/nodes)",
    "Edit(//control/nodes/**)",
    "Edit(//control/execution-authority)",
    "Edit(//control/execution-authority/**)",
  ]);
  const meta = buildClaudeCodeOptionsMeta(
    {
      claudeExecutionPolicyRef: fixture.binding.ref,
      claudeExecutionPolicySha256: fixture.binding.sha256,
    },
    true,
    fixture.root,
  );
  assert.ok(meta);
  assert.deepEqual((meta.claudeCode as { options: unknown }).options, options);
});

test("Claude ACP metadata consumes the fixed policy while other agents reject it", async () => {
  const fixture = await policyFixture();
  const sessionOptions = {
    claudeExecutionPolicyRef: fixture.binding.ref,
    claudeExecutionPolicySha256: fixture.binding.sha256,
  };
  const meta = buildClaudeCodeOptionsMeta(sessionOptions, true, fixture.root);
  assert(meta);
  const options = (meta.claudeCode as { options: Record<string, unknown> }).options;
  assert.deepEqual(options.settingSources, []);
  assert.equal((options.sandbox as { enabled: boolean }).enabled, true);
  assert.ok(options.managedSettings);
  assert.equal(options.env, undefined);

  assert.throws(
    () => buildClaudeCodeOptionsMeta(sessionOptions, false, fixture.root),
    /only supported by claude-agent-acp/,
  );
});

test("fixed Claude execution policy rejects duplicate fields before parsing", async () => {
  const fixture = await policyFixture((raw) =>
    raw.replace(
      '"schema_version":"claude_execution_policy.v2"',
      '"schema_version":"claude_execution_policy.v2","schema_version":"claude_execution_policy.v2"',
    ),
  );
  assert.throws(() => loadClaudeExecutionPolicy(fixture.root, fixture.binding), /duplicate key/);
});

test("fixed Claude execution policy rejects SHA drift, unknown fields, and path escape", async () => {
  const fixture = await policyFixture();
  assert.throws(
    () => loadClaudeExecutionPolicy(fixture.root, { ...fixture.binding, sha256: "0".repeat(64) }),
    /SHA-256 mismatch/,
  );

  const unknown = await policyFixture((raw) => raw.replace(/}$/, ',"settings":{}}'));
  assert.throws(
    () => loadClaudeExecutionPolicy(unknown.root, unknown.binding),
    /unknown or missing/,
  );
  assert.throws(
    () => loadClaudeExecutionPolicy(fixture.root, { ...fixture.binding, ref: "../policy.json" }),
    /escapes cwd/,
  );
});

test("fixed Claude execution policy rejects weakened network, escape, and credential controls", async () => {
  const weakenedNetwork = await policyFixture((raw) =>
    raw.replace('"strict_allowlist":true', '"strict_allowlist":false'),
  );
  assert.throws(
    () => loadClaudeExecutionPolicy(weakenedNetwork.root, weakenedNetwork.binding),
    /strict_allowlist must equal true/,
  );
  const escapeEnabled = await policyFixture((raw) =>
    raw.replace('"allow_unsandboxed_commands":false', '"allow_unsandboxed_commands":true'),
  );
  assert.throws(
    () => loadClaudeExecutionPolicy(escapeEnabled.root, escapeEnabled.binding),
    /allow_unsandboxed_commands must equal false/,
  );
  const missingCredential = await policyFixture((raw) =>
    raw.replace('"ANTHROPIC_AUTH_TOKEN",', ""),
  );
  assert.throws(
    () => loadClaudeExecutionPolicy(missingCredential.root, missingCredential.binding),
    /fixed E1 provider deny set/,
  );
});

test("fixed Claude execution policy rejects old schema, wrong owner, and proxy credential fields", async () => {
  const oldSchema = await policyFixture((raw) =>
    raw.replace("claude_execution_policy.v2", "claude_execution_policy.v1"),
  );
  assert.throws(() => loadClaudeExecutionPolicy(oldSchema.root, oldSchema.binding), /must equal/);

  const wrongOwner = await policyFixture((raw) =>
    raw.replace('"network_transport_owner":"claude_sandbox"', '"network_transport_owner":"dual"'),
  );
  assert.throws(() => loadClaudeExecutionPolicy(wrongOwner.root, wrongOwner.binding), /must equal/);

  const proxyCredential = await policyFixture((raw) =>
    raw.replace('"ANTHROPIC_BASE_URL"]', '"ANTHROPIC_BASE_URL","HTTPS_PROXY"]'),
  );
  assert.throws(
    () => loadClaudeExecutionPolicy(proxyCredential.root, proxyCredential.binding),
    /fixed E1 provider deny set/,
  );
});

async function policyFixture(transform: (raw: string) => string = (raw) => raw) {
  const root = await mkdtemp(path.join(os.tmpdir(), "acpx-e1-policy-"));
  const policyDir = path.join(root, "runs", "run-1", "execution-authority");
  await mkdir(policyDir, { recursive: true });
  const document = {
    schema_version: "claude_execution_policy.v2",
    workstream_id: "ws-1",
    node_instance_id: "node-1",
    run_id: "run-1",
    network_transport_owner: "claude_sandbox",
    setting_sources: [],
    sandbox: {
      enabled: true,
      fail_if_unavailable: true,
      allow_unsandboxed_commands: false,
      auto_allow_sandboxed_bash: true,
      network: {
        strict_allowlist: true,
        allowed_domains: ["example.com"],
        allow_local_binding: false,
        allow_unix_sockets: [],
      },
      filesystem: {
        allow_read: [root],
        allow_write: [root, os.tmpdir()],
        deny_read: [],
        deny_write: [],
      },
    },
    provider_subprocess_denied_env_names: DENIED_ENV_NAMES,
    generated_at: "2026-08-29T00:00:00.000Z",
  };
  const raw = transform(JSON.stringify(document));
  const ref = path.relative(root, path.join(policyDir, "claude-execution-policy.json"));
  await writeFile(path.join(root, ref), raw);
  return {
    root,
    binding: { ref, sha256: createHash("sha256").update(raw).digest("hex") },
  };
}
