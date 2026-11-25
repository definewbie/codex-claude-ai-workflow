import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { PassThrough } from "node:stream";
import test from "node:test";
import { pathToFileURL } from "node:url";
import type {
  RequestPermissionRequest,
  RequestPermissionResponse,
  JsonRpcId,
} from "@agentclientprotocol/sdk";
import {
  AcpClient,
  buildAgentSpawnOptions,
  buildQoderAcpCommandArgs,
  parseAcpJsonMessageLine,
  resolveClaudeCodeSettingSources,
  resolveAgentCloseAfterStdinEndMs,
  shouldIgnoreNonJsonAgentOutputLine,
} from "../src/acp/client.js";
import {
  AgentDisconnectedError,
  AgentStartupError,
  AuthPolicyError,
  PermissionDeniedError,
  PermissionPromptUnavailableError,
  UnsupportedPromptContentError,
} from "../src/errors.js";

test("parseAcpJsonMessageLine ignores non-object JSON values", () => {
  for (const line of ["1", "null", '"diagnostic"', "[]", "[{}]"]) {
    assert.equal(parseAcpJsonMessageLine(line), undefined);
  }
});

test("parseAcpJsonMessageLine preserves object-shaped protocol values", () => {
  assert.deepEqual(parseAcpJsonMessageLine('{"jsonrpc":"2.0","method":"session/update"}'), {
    jsonrpc: "2.0",
    method: "session/update",
  });
});

type ClientInternals = {
  resolveAgentLaunchPlan: () => Promise<unknown>;
  selectAuthMethod?: (methods: Array<{ id: string }>) =>
    | {
        methodId: string;
        credential: string;
        source: "env" | "config";
      }
    | undefined;
  authenticateIfRequired?: (
    connection: { authenticate: (params: { methodId: string }) => Promise<void> },
    methods: Array<{ id: string }>,
  ) => Promise<void>;
  handlePermissionRequest?: (
    params: RequestPermissionRequest,
    requestId?: string | number,
    requestSignal?: AbortSignal,
  ) => Promise<RequestPermissionResponse>;
  handleReadTextFile?: (params: {
    sessionId: string;
    path: string;
    line?: number | null;
    limit?: number | null;
  }) => Promise<{ content: string }>;
  handleWriteTextFile?: (
    params: {
      sessionId: string;
      path: string;
      content: string;
    },
    requestId?: string | number,
    signal?: AbortSignal,
  ) => Promise<Record<string, never>>;
  handleCreateTerminal?: (
    params: {
      sessionId: string;
      command: string;
      args?: string[];
    },
    requestId?: string | number,
    signal?: AbortSignal,
  ) => Promise<{ terminalId: string }>;
  notePromptPermissionFailure?: (
    sessionId: string,
    error: PermissionPromptUnavailableError,
  ) => void;
  consumePromptPermissionFailure?: (
    sessionId: string,
  ) => PermissionPromptUnavailableError | undefined;
  handleSessionUpdate?: (notification: { sessionId: string }) => Promise<void>;
  waitForSessionUpdateDrain?: (idleMs: number, timeoutMs: number) => Promise<void>;
  recordAgentExit?: (
    reason: "process_exit" | "process_close" | "pipe_close" | "connection_close",
    exitCode: number | null,
    signal: NodeJS.Signals | null,
  ) => void;
  filesystem?: {
    readTextFile: (params: {
      sessionId: string;
      path: string;
      line?: number | null;
      limit?: number | null;
    }) => Promise<{ content: string }>;
    writeTextFile: (params: {
      sessionId: string;
      path: string;
      content: string;
    }) => Promise<Record<string, never>>;
  };
  terminalManager?: {
    shutdown: () => Promise<void>;
    createTerminal?: (params: {
      sessionId: string;
      command: string;
      args?: string[];
    }) => Promise<{ terminalId: string }>;
  };
  cancel?: (sessionId: string) => Promise<void>;
  connection?: unknown;
  agent?: {
    pid?: number;
    killed?: boolean;
    exitCode: number | null;
    signalCode: NodeJS.Signals | null;
    stdin: PassThrough & { destroyed: boolean; end: () => void; destroy: () => void };
    stdout: PassThrough & { destroyed: boolean; destroy: () => void };
    stderr: PassThrough & { destroyed: boolean; destroy: () => void };
    kill: (signal?: NodeJS.Signals) => void;
    unref: () => void;
  };
  activePrompt?:
    | {
        sessionId: string;
        promise: Promise<{ stopReason: "end_turn" | "cancelled" }>;
      }
    | undefined;
  cancellingSessionIds: Set<string>;
  promptPermissionFailures: Map<string, PermissionPromptUnavailableError>;
  initResult?: {
    agentCapabilities?: {
      promptCapabilities?: {
        image?: boolean;
        audio?: boolean;
        embeddedContext?: boolean;
      };
      sessionCapabilities?: {
        close?: Record<string, never>;
        list?: Record<string, never>;
      };
    };
  };
  loadedSessionId?: string;
  lastKnownPid?: number;
  agentStartedAt?: string;
  closing: boolean;
  observedSessionUpdates: number;
  processedSessionUpdates: number;
  suppressSessionUpdates: boolean;
  suppressReplaySessionUpdateMessages: boolean;
};

test("buildAgentSpawnOptions normalizes auth env keys and preserves existing values", () => {
  withEnv(
    {
      ACPX_AUTH_API_TOKEN: "existing-prefixed",
      API_TOKEN: "existing-normalized",
    },
    () => {
      const options = buildAgentSpawnOptions("/tmp/acpx-agent", {
        "api-token": "from-config",
        EXPLICIT_KEY: "explicit",
        "bad=key": "ignored-for-raw-key",
        empty: "   ",
      });

      assert.equal(options.cwd, "/tmp/acpx-agent");
      assert.deepEqual(options.stdio, ["pipe", "pipe", "pipe"]);
      assert.equal(options.windowsHide, true);
      assert.equal(options.env.ACPX_AUTH_API_TOKEN, "existing-prefixed");
      assert.equal(options.env.API_TOKEN, "existing-normalized");
      assert.equal(options.env.EXPLICIT_KEY, "explicit");
      assert.equal(options.env.ACPX_AUTH_EXPLICIT_KEY, "explicit");
      assert.equal(options.env["bad=key"], undefined);
      assert.equal(options.env.ACPX_AUTH_BAD_KEY, "ignored-for-raw-key");
      assert.equal(options.env.empty, undefined);
    },
  );
});

test("resolveAgentCloseAfterStdinEndMs gives qodercli extra EOF shutdown grace", () => {
  assert.equal(resolveAgentCloseAfterStdinEndMs("qodercli --acp"), 750);
  assert.equal(resolveAgentCloseAfterStdinEndMs("/Users/me/bin/qodercli --acp"), 750);
  assert.equal(resolveAgentCloseAfterStdinEndMs("node ./test/mock-agent.js"), 100);
});

test("shouldIgnoreNonJsonAgentOutputLine ignores qoder shutdown chatter only", () => {
  assert.equal(
    shouldIgnoreNonJsonAgentOutputLine(
      "qodercli --acp",
      "Received interrupt signal. Cleaning up resources...",
    ),
    true,
  );
  assert.equal(
    shouldIgnoreNonJsonAgentOutputLine("qodercli --acp", "Cleanup completed. Exiting..."),
    true,
  );
  assert.equal(
    shouldIgnoreNonJsonAgentOutputLine(
      "node ./test/mock-agent.js",
      "Cleanup completed. Exiting...",
    ),
    false,
  );
  assert.equal(
    shouldIgnoreNonJsonAgentOutputLine("qodercli --acp", "unexpected non-json output"),
    false,
  );
});

test("buildQoderAcpCommandArgs forwards allowed-tools and max-turns", () => {
  assert.deepEqual(
    buildQoderAcpCommandArgs(["--acp"], {
      sessionOptions: {
        allowedTools: ["Read", "Grep", "custom_tool"],
        maxTurns: 9,
      },
    }),
    ["--acp", "--max-turns=9", "--allowed-tools=READ,GREP,custom_tool"],
  );
});

test("buildQoderAcpCommandArgs preserves explicit qoder startup flags", () => {
  assert.deepEqual(
    buildQoderAcpCommandArgs(
      ["--acp", "--max-turns=3", "--allowed-tools=READ", "--disallowed-tools=BASH"],
      {
        sessionOptions: {
          allowedTools: ["Write"],
          maxTurns: 7,
        },
      },
    ),
    ["--acp", "--max-turns=3", "--allowed-tools=READ", "--disallowed-tools=BASH"],
  );
});

test("AcpClient prefers env auth credentials over config credentials", async () => {
  await withEnv(
    {
      ACPX_AUTH_API_TOKEN: "from-env",
    },
    async () => {
      const client = makeClient({
        authCredentials: {
          API_TOKEN: "from-config",
          second_method: "fallback-config",
        },
      });
      const internals = asInternals(client);

      const selection = internals.selectAuthMethod?.([
        { id: "api-token" },
        { id: "second_method" },
      ]);
      assert.deepEqual(selection, {
        methodId: "api-token",
        credential: "from-env",
        source: "env",
      });

      let authenticatedMethod: string | undefined;
      await internals.authenticateIfRequired?.(
        {
          authenticate: async ({ methodId }: { methodId: string }) => {
            authenticatedMethod = methodId;
          },
        },
        [{ id: "api-token" }],
      );

      assert.equal(authenticatedMethod, "api-token");
    },
  );
});

test("AcpClient ignores ambient normalized provider env vars for auth selection", async () => {
  await withEnv(
    {
      OPENAI_API_KEY: "sk-ambient",
      ACPX_AUTH_OPENAI_API_KEY: undefined,
    },
    async () => {
      const client = makeClient();
      const internals = asInternals(client);

      const selection = internals.selectAuthMethod?.([{ id: "openai-api-key" }]);
      assert.equal(selection, undefined);

      let authenticatedMethod: string | undefined;
      await internals.authenticateIfRequired?.(
        {
          authenticate: async ({ methodId }: { methodId: string }) => {
            authenticatedMethod = methodId;
          },
        },
        [{ id: "openai-api-key" }],
      );

      assert.equal(authenticatedMethod, undefined);
    },
  );
});

test("AcpClient uses XAI_API_KEY for Grok Build xai.api_key auth", async () => {
  await withEnv(
    {
      XAI_API_KEY: "xai-ambient",
      ACPX_AUTH_XAI_API_KEY: undefined,
    },
    async () => {
      const client = makeClient({ agentCommand: "grok agent stdio" });
      const internals = asInternals(client);

      const selection = internals.selectAuthMethod?.([{ id: "xai.api_key" }]);
      assert.deepEqual(selection, {
        methodId: "xai.api_key",
        credential: "xai-ambient",
        source: "env",
      });

      let authenticatedMethod: string | undefined;
      await internals.authenticateIfRequired?.(
        {
          authenticate: async ({ methodId }: { methodId: string }) => {
            authenticatedMethod = methodId;
          },
        },
        [{ id: "xai.api_key" }],
      );

      assert.equal(authenticatedMethod, "xai.api_key");
    },
  );
});

test("AcpClient keeps XAI_API_KEY scoped to Grok Build auth", async () => {
  await withEnv(
    {
      XAI_API_KEY: "xai-ambient",
      ACPX_AUTH_XAI_API_KEY: undefined,
    },
    async () => {
      const client = makeClient({ agentCommand: "custom-acp-server" });
      const internals = asInternals(client);

      const selection = internals.selectAuthMethod?.([{ id: "xai.api_key" }]);
      assert.equal(selection, undefined);
    },
  );
});

test("AcpClient selects Grok Build cached_token as agent-managed auth", async () => {
  await withEnv(
    {
      XAI_API_KEY: undefined,
      ACPX_AUTH_CACHED_TOKEN: undefined,
    },
    async () => {
      const client = makeClient({ agentCommand: "grok agent stdio" });
      const internals = asInternals(client);

      const selection = internals.selectAuthMethod?.([{ id: "cached_token" }]);
      assert.deepEqual(selection, {
        methodId: "cached_token",
        source: "agent",
      });

      let authenticatedMethod: string | undefined;
      await internals.authenticateIfRequired?.(
        {
          authenticate: async ({ methodId }: { methodId: string }) => {
            authenticatedMethod = methodId;
          },
        },
        [{ id: "cached_token" }],
      );

      assert.equal(authenticatedMethod, "cached_token");
    },
  );
});

test("AcpClient authenticateIfRequired throws when auth policy is fail and credentials are missing", async () => {
  const client = makeClient({ authPolicy: "fail" });
  const internals = asInternals(client);

  await assert.rejects(
    async () =>
      await internals.authenticateIfRequired?.(
        {
          authenticate: async () => {},
        },
        [{ id: "api-token" }],
      ),
    AuthPolicyError,
  );
});

test("AcpClient handlePermissionRequest short-circuits cancels and tracks unavailable prompts", async () => {
  const client = makeClient({
    permissionMode: "approve-reads",
    nonInteractivePermissions: "fail",
  });
  const internals = asInternals(client);
  const request = makePermissionRequest("session-1", "edit");

  internals.cancellingSessionIds.add("session-1");
  const cancelled = await internals.handlePermissionRequest?.(request);
  assert.deepEqual(cancelled, {
    outcome: {
      outcome: "cancelled",
    },
  });
  assert.deepEqual(client.getPermissionStats(), {
    requested: 0,
    approved: 0,
    denied: 0,
    cancelled: 0,
  });

  internals.cancellingSessionIds.clear();
  await withTty(false, false, async () => {
    const unavailable = await internals.handlePermissionRequest?.(request);
    assert.deepEqual(unavailable, {
      outcome: {
        outcome: "cancelled",
      },
    });
  });

  assert.deepEqual(client.getPermissionStats(), {
    requested: 1,
    approved: 0,
    denied: 0,
    cancelled: 1,
  });
  const noted = internals.consumePromptPermissionFailure?.("session-1");
  assert(noted instanceof PermissionPromptUnavailableError);
  assert.equal(internals.consumePromptPermissionFailure?.("session-1"), undefined);
});

test("AcpClient handlePermissionRequest records approved decisions", async () => {
  const client = makeClient({
    permissionMode: "approve-all",
  });

  const response = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-2", "read"),
  );

  assert.deepEqual(response, {
    outcome: {
      outcome: "selected",
      optionId: "allow",
    },
  });
  assert.deepEqual(client.getPermissionStats(), {
    requested: 1,
    approved: 1,
    denied: 0,
    cancelled: 0,
  });
});

test("AcpClient partial runtime option updates preserve permission policy", async () => {
  const client = makeClient({
    permissionMode: "approve-all",
    permissionPolicy: {
      autoDeny: ["execute"],
    },
  });

  client.updateRuntimeOptions({ verbose: true });

  const denied = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-policy-preserve-1", "execute"),
  );

  assert.deepEqual(denied, {
    outcome: {
      outcome: "selected",
      optionId: "reject",
    },
  });

  client.updateRuntimeOptions({ permissionPolicy: undefined });

  const approved = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-policy-preserve-2", "execute"),
  );

  assert.deepEqual(approved, {
    outcome: {
      outcome: "selected",
      optionId: "allow",
    },
  });
});

test("AcpClient snapshots permission policies at configuration boundaries", async () => {
  const initialPolicy = {
    autoDeny: ["execute"],
  };
  const client = makeClient({
    permissionMode: "approve-all",
    permissionPolicy: initialPolicy,
  });

  initialPolicy.autoDeny.splice(0, 1);
  const deniedFromInitialSnapshot = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-policy-snapshot-1", "execute"),
  );
  assert.equal(deniedFromInitialSnapshot?.outcome.outcome, "selected");
  if (deniedFromInitialSnapshot?.outcome.outcome === "selected") {
    assert.equal(deniedFromInitialSnapshot.outcome.optionId, "reject");
  }

  const updatedPolicy = {
    autoDeny: ["execute"],
  };
  client.updateRuntimeOptions({ permissionPolicy: updatedPolicy });
  updatedPolicy.autoDeny.splice(0, 1);
  const deniedFromUpdatedSnapshot = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-policy-snapshot-2", "execute"),
  );
  assert.equal(deniedFromUpdatedSnapshot?.outcome.outcome, "selected");
  if (deniedFromUpdatedSnapshot?.outcome.outcome === "selected") {
    assert.equal(deniedFromUpdatedSnapshot.outcome.optionId, "reject");
  }
});

test("AcpClient onPermissionRequest decision short-circuits the mode-based resolver", async () => {
  let callbackInvocations = 0;
  const client = makeClient({
    permissionMode: "approve-reads",
    nonInteractivePermissions: "deny",
    onPermissionRequest: async (req) => {
      callbackInvocations += 1;
      assert.equal(req.sessionId, "session-cb-1");
      assert.equal(req.inferredKind, "edit");
      return { outcome: "allow_once" };
    },
  });

  const response = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-cb-1", "edit"),
  );

  assert.deepEqual(response, {
    outcome: {
      outcome: "selected",
      optionId: "allow",
    },
  });
  assert.equal(callbackInvocations, 1);
  assert.deepEqual(client.getPermissionStats(), {
    requested: 1,
    approved: 1,
    denied: 0,
    cancelled: 0,
  });
});

test("AcpClient onPermissionRequest returning undefined falls through to mode-based resolver", async () => {
  let callbackInvocations = 0;
  const client = makeClient({
    permissionMode: "approve-all",
    onPermissionRequest: async () => {
      callbackInvocations += 1;
      return undefined;
    },
  });

  const response = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-cb-2", "edit"),
  );

  assert.deepEqual(response, {
    outcome: {
      outcome: "selected",
      optionId: "allow",
    },
  });
  assert.equal(callbackInvocations, 1);
  assert.deepEqual(client.getPermissionStats(), {
    requested: 1,
    approved: 1,
    denied: 0,
    cancelled: 0,
  });
});

test("AcpClient onPermissionRequest throws fall through to mode-based resolver", async () => {
  let callbackInvocations = 0;
  const client = makeClient({
    permissionMode: "approve-all",
    onPermissionRequest: async () => {
      callbackInvocations += 1;
      throw new Error("UI exploded");
    },
  });

  const response = await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-cb-3", "edit"),
  );

  assert.deepEqual(response, {
    outcome: {
      outcome: "selected",
      optionId: "allow",
    },
  });
  assert.equal(callbackInvocations, 1);
});

test("AcpClient onPermissionRequest receives an AbortSignal that fires on session cancel", async () => {
  let observedSignal: AbortSignal | undefined;
  const client = makeClient({
    permissionMode: "approve-all",
    onPermissionRequest: async (_req, ctx) => {
      observedSignal = ctx.signal;
      return { outcome: "allow_once" };
    },
  });

  await asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("session-cb-4", "edit"),
  );

  assert(observedSignal instanceof AbortSignal);
  assert.equal(observedSignal?.aborted, false);

  const internals = asInternals(client) as unknown as {
    abortAndDropPermissionSignal: (sessionId: string) => void;
  };
  internals.abortAndDropPermissionSignal("session-cb-4");
  assert.equal(observedSignal?.aborted, true);
});

test("AcpClient onPermissionRequest cancels a late decision after session cancel", async () => {
  let resolveDecision!: (decision: { outcome: "allow_once" }) => void;
  const decisionPromise = new Promise<{ outcome: "allow_once" }>((resolve) => {
    resolveDecision = resolve;
  });
  let callbackStarted!: () => void;
  const callbackStartedPromise = new Promise<void>((resolve) => {
    callbackStarted = resolve;
  });
  let observedSignal: AbortSignal | undefined;

  const client = makeClient({
    permissionMode: "approve-all",
    onPermissionRequest: async (_req, ctx) => {
      observedSignal = ctx.signal;
      callbackStarted();
      return await decisionPromise;
    },
  });
  const internals = asInternals(client) as ClientInternals & {
    abortAndDropPermissionSignal: (sessionId: string) => void;
  };

  const responsePromise = internals.handlePermissionRequest?.(
    makePermissionRequest("session-cb-5", "edit"),
  );
  await callbackStartedPromise;

  internals.cancellingSessionIds.add("session-cb-5");
  internals.abortAndDropPermissionSignal("session-cb-5");
  assert.equal(observedSignal?.aborted, true);

  resolveDecision({ outcome: "allow_once" });
  const response = await responsePromise;

  assert.deepEqual(response, {
    outcome: {
      outcome: "cancelled",
    },
  });
  assert.deepEqual(client.getPermissionStats(), {
    requested: 1,
    approved: 0,
    denied: 0,
    cancelled: 1,
  });
});

test("AcpClient onPermissionRequest treats abort rejections as cancelled", async () => {
  let callbackStarted!: () => void;
  const callbackStartedPromise = new Promise<void>((resolve) => {
    callbackStarted = resolve;
  });

  const client = makeClient({
    permissionMode: "approve-all",
    onPermissionRequest: async (_req, ctx) => {
      callbackStarted();
      await new Promise<never>((_resolve, reject) => {
        ctx.signal.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
      });
    },
  });
  const internals = asInternals(client) as ClientInternals & {
    abortAndDropPermissionSignal: (sessionId: string) => void;
  };

  const responsePromise = internals.handlePermissionRequest?.(
    makePermissionRequest("session-cb-6", "edit"),
  );
  await callbackStartedPromise;

  internals.cancellingSessionIds.add("session-cb-6");
  internals.abortAndDropPermissionSignal("session-cb-6");
  const response = await responsePromise;

  assert.deepEqual(response, {
    outcome: {
      outcome: "cancelled",
    },
  });
  assert.deepEqual(client.getPermissionStats(), {
    requested: 1,
    approved: 0,
    denied: 0,
    cancelled: 1,
  });
});

test("AcpClient client-method permission errors update permission stats", async () => {
  const client = makeClient();
  const internals = asInternals(client);

  internals.filesystem = {
    readTextFile: async () => {
      throw new PermissionDeniedError("Permission denied for fs/read_text_file");
    },
    writeTextFile: async () => {
      throw new PermissionDeniedError("Permission denied for fs/write_text_file");
    },
  };
  internals.terminalManager = {
    shutdown: async () => {},
    createTerminal: async () => {
      throw new PermissionPromptUnavailableError();
    },
  };

  await assert.rejects(
    async () =>
      await internals.handleReadTextFile?.({
        sessionId: "session-read",
        path: "/tmp/read.txt",
      }),
    PermissionDeniedError,
  );
  await assert.rejects(
    async () =>
      await internals.handleWriteTextFile?.({
        sessionId: "session-write",
        path: "/tmp/write.txt",
        content: "updated",
      }),
    PermissionDeniedError,
  );
  await assert.rejects(
    async () =>
      await internals.handleCreateTerminal?.({
        sessionId: "session-terminal",
        command: "echo",
        args: ["hi"],
      }),
    PermissionPromptUnavailableError,
  );

  assert.deepEqual(client.getPermissionStats(), {
    requested: 3,
    approved: 0,
    denied: 2,
    cancelled: 1,
  });
  const noted = internals.consumePromptPermissionFailure?.("session-terminal");
  assert(noted instanceof PermissionPromptUnavailableError);
});

test("AcpClient createSession forwards claudeCode options in _meta", async () => {
  const cwd = path.resolve("/tmp/acpx-client-meta");
  const client = makeClient({
    sessionOptions: {
      model: "sonnet",
      allowedTools: ["Read", "Grep"],
      maxTurns: 12,
    },
  });

  let capturedParams: Record<string, unknown> | undefined;
  asInternals(client).connection = {
    newSession: async (params: Record<string, unknown>) => {
      capturedParams = params;
      return { sessionId: "session-123" };
    },
  };

  const result = await client.createSession("/tmp/acpx-client-meta");
  assert.equal(result.sessionId, "session-123");
  assert.deepEqual(capturedParams, {
    cwd,
    mcpServers: [],
    _meta: {
      claudeCode: {
        options: {
          model: "sonnet",
          allowedTools: ["Read", "Grep"],
          maxTurns: 12,
        },
      },
    },
  });
});

for (const autoAllowBash of [true, false]) {
  test(`AcpClient createSession forwards bound Bash capability ${autoAllowBash} in _meta`, async () => {
    const cwd = await mkdtemp(path.join(os.tmpdir(), "acpx-client-e1-policy-"));
    const policyDir = path.join(cwd, "runs", "run-1", "execution-authority");
    await mkdir(policyDir, { recursive: true });
    const policy = JSON.stringify({
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
        auto_allow_sandboxed_bash: autoAllowBash,
        network: {
          strict_allowlist: true,
          allowed_domains: ["example.com"],
          allow_local_binding: true,
          allow_unix_sockets: [],
        },
        filesystem: {
          allow_read: [cwd],
          allow_write: [cwd, os.tmpdir()],
          deny_read: [],
          deny_write: [],
        },
      },
      provider_subprocess_denied_env_names: [
        "ANTHROPIC_API_KEY",
        "ANTHROPIC_AUTH_TOKEN",
        "ANTHROPIC_BASE_URL",
      ],
      generated_at: "2026-08-29T00:00:00.000Z",
    });
    const ref = path.join("runs", "run-1", "execution-authority", "policy.json");
    await writeFile(path.join(cwd, ref), policy);
    const client = makeClient({
      agentCommand: "npx -y @agentclientprotocol/claude-agent-acp",
      cwd,
      sessionOptions: {
        claudeExecutionPolicyRef: ref,
        claudeExecutionPolicySha256: createHash("sha256").update(policy).digest("hex"),
      },
    });

    let capturedParams: Record<string, unknown> | undefined;
    asInternals(client).connection = {
      newSession: async (params: Record<string, unknown>) => {
        capturedParams = params;
        return { sessionId: "session-e1-policy" };
      },
    };

    await client.createSession(cwd);
    const options =
      (capturedParams?._meta as { claudeCode?: { options?: Record<string, unknown> } })?.claudeCode
        ?.options ?? {};
    assert.deepEqual(options.settingSources, []);
    assert.equal((options.sandbox as { enabled?: boolean }).enabled, true);
    assert.equal(
      (options.sandbox as { network?: { allowLocalBinding?: boolean } }).network?.allowLocalBinding,
      true,
    );
    assert.equal(
      (options.managedSettings as { sandbox?: { failIfUnavailable?: boolean } }).sandbox
        ?.failIfUnavailable,
      true,
    );
    assert.equal(
      (
        options.managedSettings as {
          sandbox?: { network?: { allowLocalBinding?: boolean } };
        }
      ).sandbox?.network?.allowLocalBinding,
      undefined,
    );
    assert.equal(options.env, undefined);
    assert.equal(
      (options.sandbox as { autoAllowBashIfSandboxed?: boolean }).autoAllowBashIfSandboxed,
      autoAllowBash,
    );
    assert.equal(
      (options.managedSettings as { permissions: { deny: string[] } }).permissions.deny.includes(
        "Bash",
      ),
      !autoAllowBash,
    );
  });
}

test("AcpClient rejects incomplete or non-Claude policy bindings before launch", async () => {
  const incomplete = makeClient({
    agentCommand: "npx -y @agentclientprotocol/claude-agent-acp@0.70.0",
    sessionOptions: { claudeExecutionPolicyRef: "runs/run-1/policy.json" },
  });
  await assert.rejects(
    asInternals(incomplete).resolveAgentLaunchPlan(),
    /ref and SHA-256 must be provided together/,
  );

  const nonClaude = makeClient({
    agentCommand: "node ./test/mock-agent.js",
    sessionOptions: {
      claudeExecutionPolicyRef: "runs/run-1/policy.json",
      claudeExecutionPolicySha256: "0".repeat(64),
    },
  });
  await assert.rejects(
    asInternals(nonClaude).resolveAgentLaunchPlan(),
    /only supported by claude-agent-acp/,
  );
});

test("AcpClient start scrubs the final E1 Claude child env before spawn", async () => {
  const canaries = {
    ALL_PROXY: "parent-all",
    HTTP_PROXY: "parent-http",
    HTTPS_PROXY: "parent-https",
    NO_PROXY: "parent-no-proxy",
  };
  await withEnv(canaries, async () => {
    const observed = await captureAgentLaunchEnvironment({
      claude: true,
      boundPolicy: true,
      sessionEnv: {
        all_proxy: "session-all",
        http_proxy: "session-http",
        https_proxy: "session-https",
        no_proxy: "session-no-proxy",
        CLAUDE_CODE_SUBPROCESS_ENV_SCRUB: "1",
        CLAUDE_CODE_EXECUTABLE: "/opt/claude",
      },
    });

    for (const name of [
      "ALL_PROXY",
      "HTTP_PROXY",
      "HTTPS_PROXY",
      "NO_PROXY",
      "all_proxy",
      "http_proxy",
      "https_proxy",
      "no_proxy",
      "CLAUDE_CODE_SUBPROCESS_ENV_SCRUB",
    ]) {
      assert.equal(observed[name], null, `${name} must be absent in the spawned E1 child`);
    }
    assert.equal(observed.CLAUDE_CODE_EXECUTABLE, "/opt/claude");
  });
});

test("AcpClient start preserves proxy env for unbound Claude and non-Claude siblings", async () => {
  for (const claude of [true, false]) {
    const observed = await captureAgentLaunchEnvironment({
      claude,
      boundPolicy: false,
      sessionEnv: { HTTP_PROXY: `sibling-${claude}` },
    });
    assert.equal(observed.HTTP_PROXY, `sibling-${claude}`);
  }
});

test("AcpClient creates built-in Claude sessions without user settings by default", async () => {
  const cwd = path.resolve("/tmp/acpx-client-claude-settings");
  const client = makeClient({
    agentCommand: "npx -y @agentclientprotocol/claude-agent-acp",
  });

  let capturedParams: Record<string, unknown> | undefined;
  asInternals(client).connection = {
    newSession: async (params: Record<string, unknown>) => {
      capturedParams = params;
      return { sessionId: "session-claude-settings" };
    },
  };

  await client.createSession("/tmp/acpx-client-claude-settings");
  assert.deepEqual(capturedParams, {
    cwd,
    mcpServers: [],
    _meta: {
      claudeCode: {
        options: {
          settingSources: ["project", "local"],
        },
      },
    },
  });
});

test("resolveClaudeCodeSettingSources includes user settings only when explicitly enabled", () => {
  assert.deepEqual(resolveClaudeCodeSettingSources({}), ["project", "local"]);
  assert.deepEqual(resolveClaudeCodeSettingSources({ ACPX_CLAUDE_INCLUDE_USER_SETTINGS: "1" }), [
    "user",
    "project",
    "local",
  ]);
  assert.deepEqual(resolveClaudeCodeSettingSources({ ACPX_CLAUDE_INCLUDE_USER_SETTINGS: "true" }), [
    "project",
    "local",
  ]);
});

test("AcpClient createSession forwards systemPrompt string in _meta", async () => {
  const cwd = path.resolve("/tmp/acpx-client-system-prompt");
  const client = makeClient({
    sessionOptions: {
      systemPrompt: "you are an obsidian assistant",
    },
  });

  let capturedParams: Record<string, unknown> | undefined;
  asInternals(client).connection = {
    newSession: async (params: Record<string, unknown>) => {
      capturedParams = params;
      return { sessionId: "session-sp-string" };
    },
  };

  await client.createSession("/tmp/acpx-client-system-prompt");
  assert.deepEqual(capturedParams, {
    cwd,
    mcpServers: [],
    _meta: {
      systemPrompt: "you are an obsidian assistant",
    },
  });
});

test("AcpClient createSession forwards systemPrompt append in _meta alongside claudeCode options", async () => {
  const cwd = path.resolve("/tmp/acpx-client-system-prompt-append");
  const client = makeClient({
    sessionOptions: {
      model: "sonnet",
      systemPrompt: { append: "always speak in spanish" },
    },
  });

  let capturedParams: Record<string, unknown> | undefined;
  asInternals(client).connection = {
    newSession: async (params: Record<string, unknown>) => {
      capturedParams = params;
      return { sessionId: "session-sp-append" };
    },
  };

  await client.createSession("/tmp/acpx-client-system-prompt-append");
  assert.deepEqual(capturedParams, {
    cwd,
    mcpServers: [],
    _meta: {
      claudeCode: {
        options: {
          model: "sonnet",
        },
      },
      systemPrompt: { append: "always speak in spanish" },
    },
  });
});

test("AcpClient createSession forwards codex model metadata without setting it explicitly", async () => {
  const cwd = path.resolve("/tmp/acpx-client-codex-model");
  const client = makeClient({
    agentCommand: "npx -y @agentclientprotocol/codex-acp",
    sessionOptions: {
      model: "GPT-5-2",
    },
  });

  let capturedNewSessionParams: Record<string, unknown> | undefined;
  let setConfigCalled = false;
  asInternals(client).connection = {
    newSession: async (params: Record<string, unknown>) => {
      capturedNewSessionParams = params;
      return { sessionId: "session-456" };
    },
    setSessionConfigOption: async () => {
      setConfigCalled = true;
      return { configOptions: [] };
    },
  };

  const result = await client.createSession("/tmp/acpx-client-codex-model");
  assert.equal(result.sessionId, "session-456");
  assert.deepEqual(capturedNewSessionParams, {
    cwd,
    mcpServers: [],
    _meta: {
      claudeCode: {
        options: {
          model: "GPT-5-2",
        },
      },
    },
  });
  assert.equal(setConfigCalled, false);
});

test("AcpClient setSessionModel uses the model session config option", async () => {
  const client = makeClient();

  let capturedSetConfigParams:
    | {
        sessionId: string;
        configId: string;
        value: string;
      }
    | undefined;
  asInternals(client).connection = {
    setSessionConfigOption: async (params: {
      sessionId: string;
      configId: string;
      value: string;
    }) => {
      capturedSetConfigParams = params;
      return { configOptions: [] };
    },
  };

  await client.setSessionModel("session-456", "GPT-5-2", { configId: "model" });
  assert.deepEqual(capturedSetConfigParams, {
    sessionId: "session-456",
    configId: "model",
    value: "GPT-5-2",
  });
});

test("AcpClient setSessionModel honors an advertised custom config id", async () => {
  const client = makeClient();

  let capturedConfigId: string | undefined;
  asInternals(client).connection = {
    setSessionConfigOption: async (params: { configId: string }) => {
      capturedConfigId = params.configId;
      return { configOptions: [] };
    },
  };

  await client.setSessionModel("session-456", "GPT-5-2", { configId: "llm" });
  assert.equal(capturedConfigId, "llm");
});

test("AcpClient normalizes a Cursor model alias to its unique advertised id", async () => {
  const client = makeClient({ agentCommand: "cursor-agent acp" });
  let capturedValue: string | undefined;
  asInternals(client).connection = {
    setSessionConfigOption: async (params: { value: string }) => {
      capturedValue = params.value;
      return { configOptions: [] };
    },
  };

  await client.setSessionModel("session-456", "composer-2.5", {
    configId: "model",
    availableModels: [{ modelId: "composer-2.5[fast=false]", name: "Composer 2.5" }],
  });
  assert.equal(capturedValue, "composer-2.5[fast=false]");
});

test("AcpClient setSessionModel rejects sessions without advertised model control", async () => {
  const client = makeClient();
  asInternals(client).connection = {};

  await assert.rejects(
    async () => await client.setSessionModel("session-456", "GPT-5-2"),
    /did not advertise a model config option or legacy session\/set_model support/,
  );
});

test("AcpClient setSessionModel preserves explicitly advertised legacy model control", async () => {
  const client = makeClient();
  let capturedLegacyParams: Record<string, unknown> | undefined;
  asInternals(client).connection = {
    newSession: async () => ({
      sessionId: "legacy-session",
      models: {
        currentModelId: "default-model",
        availableModels: [
          { modelId: "default-model", name: "Default Model" },
          { modelId: "alternate-model", name: "Alternate Model" },
        ],
      },
    }),
    extMethod: async (method: string, params: Record<string, unknown>) => {
      assert.equal(method, "session/set_model");
      capturedLegacyParams = params;
      return {};
    },
  };

  const result = await client.createSession("/tmp/acpx-client-legacy-model");
  assert.equal(result.models?.configId, undefined);
  await client.setSessionModel(result.sessionId, "alternate-model");
  assert.deepEqual(capturedLegacyParams, {
    sessionId: "legacy-session",
    modelId: "alternate-model",
  });
});

test("AcpClient treats explicit null config options as an empty snapshot", async () => {
  const client = makeClient();
  asInternals(client).connection = {
    loadSession: async () => ({ configOptions: null }),
  };

  const result = await client.loadSession("session-null-config", "/tmp/acpx-null-config");
  assert.equal(result.configOptionsPresent, true);
  assert.deepEqual(result.configOptions, []);
  assert.equal(result.models, undefined);
});

test("AcpClient closes sessions through session/close and clears the loaded session id", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let capturedCloseSessionParams: { sessionId: string } | undefined;
  internals.initResult = {
    agentCapabilities: {
      sessionCapabilities: {
        close: {},
      },
    },
  };
  internals.loadedSessionId = "session-close-1";
  internals.connection = {
    closeSession: async (params: { sessionId: string }) => {
      capturedCloseSessionParams = params;
      return {};
    },
  };

  assert.equal(client.supportsCloseSession(), true);
  await client.closeSession("session-close-1");

  assert.deepEqual(capturedCloseSessionParams, {
    sessionId: "session-close-1",
  });
  assert.equal(internals.loadedSessionId, undefined);
});

test("AcpClient lists agent sessions through session/list", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let capturedListSessionsParams:
    | {
        cwd?: string | null;
        cursor?: string | null;
      }
    | undefined;
  internals.initResult = {
    agentCapabilities: {
      sessionCapabilities: {
        list: {},
      },
    },
  };
  internals.connection = {
    listSessions: async (params: { cwd?: string | null; cursor?: string | null }) => {
      capturedListSessionsParams = params;
      return {
        sessions: [
          {
            sessionId: "agent-session-1",
            cwd: "/tmp/acpx-client-list",
            title: "Agent session",
            updatedAt: "2026-05-21T00:00:00.000Z",
            _meta: { messageCount: 3 },
          },
        ],
        nextCursor: "cursor-2",
      };
    },
  };

  assert.equal(client.supportsListSessions(), true);
  const result = await client.listSessions({
    cwd: "/tmp/acpx-client-list",
    cursor: "cursor-1",
  });

  assert.deepEqual(capturedListSessionsParams, {
    cwd: "/tmp/acpx-client-list",
    cursor: "cursor-1",
  });
  assert.equal(result.nextCursor, "cursor-2");
  assert.equal(result.sessions[0]?.sessionId, "agent-session-1");
  assert.deepEqual(result.sessions[0]?._meta, { messageCount: 3 });
});

test("AcpClient session update handling drains queued callbacks and swallows handler failures", async () => {
  const notifications: string[] = [];
  const client = makeClient({
    onSessionUpdate: (notification) => {
      notifications.push(notification.sessionId);
      if (notification.sessionId === "bad") {
        throw new Error("boom");
      }
    },
  });
  const internals = asInternals(client);

  await Promise.all([
    internals.handleSessionUpdate?.({ sessionId: "good" }),
    internals.handleSessionUpdate?.({ sessionId: "bad" }),
  ]);
  await internals.waitForSessionUpdateDrain?.(0, 100);

  assert.deepEqual(notifications, ["good", "bad"]);
  assert.equal(internals.observedSessionUpdates, 2);
  assert.equal(internals.processedSessionUpdates, 2);

  internals.suppressSessionUpdates = true;
  await internals.handleSessionUpdate?.({ sessionId: "suppressed" });
  assert.deepEqual(notifications, ["good", "bad"]);
});

test("AcpClient lifecycle snapshot and cancel helpers reflect active prompt state", async () => {
  const client = makeClient();
  const internals = asInternals(client);

  assert.equal(client.hasActivePrompt(), false);
  assert.equal(await client.requestCancelActivePrompt(), false);
  assert.equal(await client.cancelActivePrompt(0), undefined);

  let cancelledSessionId: string | undefined;
  internals.cancel = async (sessionId: string) => {
    cancelledSessionId = sessionId;
  };
  internals.activePrompt = {
    sessionId: "session-3",
    promise: Promise.resolve({ stopReason: "cancelled" }),
  };
  internals.lastKnownPid = 4321;
  internals.agentStartedAt = "2026-01-01T00:00:00.000Z";

  assert.equal(client.hasActivePrompt(), true);
  assert.equal(client.hasActivePrompt("session-3"), true);
  assert.equal(await client.requestCancelActivePrompt(), true);
  assert.equal(cancelledSessionId, "session-3");

  internals.recordAgentExit?.("process_exit", 1, "SIGTERM");
  internals.recordAgentExit?.("pipe_close", 0, null);
  const snapshot = client.getAgentLifecycleSnapshot();
  assert.equal(snapshot.pid, 4321);
  assert.equal(snapshot.startedAt, "2026-01-01T00:00:00.000Z");
  assert.equal(snapshot.running, false);
  assert.equal(snapshot.lastExit?.reason, "process_exit");
  assert.equal(snapshot.lastExit?.unexpectedDuringPrompt, true);

  const cancelled = await client.cancelActivePrompt(50);
  assert.deepEqual(cancelled, { stopReason: "cancelled" });
});

test("AcpClient rejects rich prompt content not advertised by promptCapabilities", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let promptCalled = false;
  internals.initResult = {
    agentCapabilities: {
      promptCapabilities: {
        image: true,
      },
    },
  };
  internals.connection = {
    prompt: async () => {
      promptCalled = true;
      return { stopReason: "end_turn" };
    },
  };

  await assert.rejects(
    async () =>
      await client.prompt("session-audio", [
        { type: "audio", mimeType: "audio/wav", data: "UklGRg==" },
      ]),
    (error: unknown) =>
      error instanceof UnsupportedPromptContentError &&
      error.message.includes("promptCapabilities.audio"),
  );
  assert.equal(promptCalled, false);
});

test("AcpClient sends audio prompts when the agent advertises audio support", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let capturedPrompt: unknown;
  internals.initResult = {
    agentCapabilities: {
      promptCapabilities: {
        audio: true,
      },
    },
  };
  internals.connection = {
    prompt: async (params: { prompt: unknown }) => {
      capturedPrompt = params.prompt;
      return { stopReason: "end_turn" };
    },
  };

  await client.prompt("session-audio", [
    { type: "audio", mimeType: "audio/wav", data: "UklGRg==" },
  ]);

  assert.deepEqual(capturedPrompt, [{ type: "audio", mimeType: "audio/wav", data: "UklGRg==" }]);
});

test("AcpClient reports prompt start only after the connection request exists", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  const calls: string[] = [];
  let resolvePrompt!: (value: { stopReason: "end_turn" }) => void;
  const promptResponse = new Promise<{ stopReason: "end_turn" }>((resolve) => {
    resolvePrompt = resolve;
  });
  let reportStarted!: () => void;
  const started = new Promise<void>((resolve) => {
    reportStarted = resolve;
  });
  internals.connection = {
    prompt: () => {
      calls.push("prompt");
      return promptResponse;
    },
  };

  const pending = client.prompt("session-start", "hello", () => {
    calls.push("started");
    reportStarted();
  });
  await started;

  assert.deepEqual(calls, ["prompt", "started"]);
  assert.equal(client.hasActivePrompt(), true);
  resolvePrompt({ stopReason: "end_turn" });
  assert.deepEqual(await pending, { stopReason: "end_turn" });
});

test("AcpClient does not report prompt start when request creation throws", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let reported = false;
  internals.connection = {
    prompt: () => {
      throw new Error("request creation failed");
    },
  };

  await assert.rejects(
    client.prompt("session-start-failure", "hello", () => {
      reported = true;
    }),
    /request creation failed/,
  );

  assert.equal(reported, false);
});

test("AcpClient does not report prompt start when the connection is already closed", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let reported = false;
  const failure = new Error("ACP connection closed");
  internals.connection = {
    signal: AbortSignal.abort(failure),
    prompt: () => Promise.reject(failure),
  };

  await assert.rejects(
    client.prompt("session-closed-before-start", "hello", () => {
      reported = true;
    }),
    failure,
  );

  assert.equal(reported, false);
});

test("AcpClient does not submit a prompt after agent exit settles the queued request", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let promptCalls = 0;
  let reported = false;
  internals.connection = {
    prompt: async () => {
      promptCalls += 1;
      return { stopReason: "end_turn" as const };
    },
  };

  const pending = client.prompt("session-exited-before-start", "hello", () => {
    reported = true;
  });
  internals.recordAgentExit?.("connection_close", null, null);

  await assert.rejects(pending, AgentDisconnectedError);
  await Promise.resolve();
  assert.equal(promptCalls, 0);
  assert.equal(reported, false);
});

test("AcpClient reports prompt start when the connection closes while creating the request", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  const connection = new AbortController();
  let reported = false;
  internals.connection = {
    signal: connection.signal,
    prompt: () => {
      connection.abort(new Error("closed after request creation began"));
      return Promise.resolve({ stopReason: "end_turn" });
    },
  };

  await client.prompt("session-close-during-start", "hello", () => {
    reported = true;
  });

  assert.equal(reported, true);
});

test("AcpClient prompt completion does not depend on prompt start observers", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  internals.connection = {
    prompt: async () => ({ stopReason: "end_turn" }),
  };

  await assert.doesNotReject(
    client.prompt("session-start-observer-failure", "hello", async () => {
      throw new Error("observer failed");
    }),
  );
  await assert.doesNotReject(
    client.prompt("session-start-observer-pending", "hello", () => new Promise<void>(() => {})),
  );
});

test("AcpClient prompt rejects when the agent disconnects mid-prompt", async () => {
  const client = makeClient();
  const internals = asInternals(client);

  internals.connection = {
    prompt: async () => await new Promise(() => {}),
  };

  const pending = client.prompt("session-5", "sleep 60000");
  internals.recordAgentExit?.("connection_close", null, null);

  const result = await Promise.race([
    pending.then(
      () => ({ type: "resolved" as const }),
      (error) => ({ type: "rejected" as const, error }),
    ),
    new Promise<{ type: "timeout" }>((resolve) => {
      setTimeout(() => resolve({ type: "timeout" }), 100);
    }),
  ]);

  assert.equal(result.type, "rejected");
  assert(result.error instanceof AgentDisconnectedError);
  assert.match(result.error.message, /disconnected during request/i);
  assert.equal(client.hasActivePrompt(), false);
});

test("AcpClient start fails fast when the agent exits during initialize", async () => {
  const stderrLine = "startup boom";
  const client = makeClient({
    agentCommand: `${JSON.stringify(process.execPath)} --eval ${JSON.stringify(
      `process.stderr.write(${JSON.stringify(`${stderrLine}\n`)}); process.exit(1);`,
    )}`,
  });

  const startedAt = Date.now();
  await assert.rejects(
    () => client.start(),
    (error: unknown) => {
      assert(error instanceof AgentStartupError);
      assert.equal(error.exitCode, 1);
      assert.equal(error.signal, null);
      assert.match(error.message, /startup boom/);
      return true;
    },
  );
  assert(Date.now() - startedAt < 2_000);
});

test("AcpClient close resets in-memory state and shuts down terminal manager", async () => {
  const client = makeClient();
  const internals = asInternals(client);
  let shutdownCalls = 0;
  let killCalls = 0;
  let unrefCalls = 0;

  internals.terminalManager = {
    shutdown: async () => {
      shutdownCalls += 1;
    },
  };

  const stdin = new PassThrough();
  const stdout = new PassThrough();
  const stderr = new PassThrough();
  internals.agent = {
    pid: 9876,
    killed: false,
    exitCode: 0,
    signalCode: null,
    stdin: Object.assign(stdin, {
      end: () => stdin.destroy(),
      destroy: () => PassThrough.prototype.destroy.call(stdin),
    }),
    stdout: Object.assign(stdout, {
      destroy: () => PassThrough.prototype.destroy.call(stdout),
    }),
    stderr: Object.assign(stderr, {
      destroy: () => PassThrough.prototype.destroy.call(stderr),
    }),
    kill: () => {
      killCalls += 1;
    },
    unref: () => {
      unrefCalls += 1;
    },
  };
  internals.connection = { closed: false };
  internals.activePrompt = {
    sessionId: "session-4",
    promise: new Promise(() => {}),
  };
  internals.cancellingSessionIds.add("session-4");
  internals.notePromptPermissionFailure?.("session-4", new PermissionPromptUnavailableError());
  internals.observedSessionUpdates = 5;
  internals.processedSessionUpdates = 4;
  internals.suppressSessionUpdates = true;
  internals.suppressReplaySessionUpdateMessages = true;

  await client.close();

  assert.equal(shutdownCalls, 1);
  assert.equal(killCalls, 0);
  assert.equal(unrefCalls, 0);
  assert.equal(internals.connection, undefined);
  assert.equal(internals.agent, undefined);
  assert.equal(internals.activePrompt, undefined);
  assert.equal(internals.cancellingSessionIds.size, 0);
  assert.equal(internals.promptPermissionFailures.size, 0);
  assert.equal(internals.observedSessionUpdates, 0);
  assert.equal(internals.processedSessionUpdates, 0);
  assert.equal(internals.suppressSessionUpdates, false);
  assert.equal(internals.suppressReplaySessionUpdateMessages, false);
  assert.equal(internals.closing, true);
});

const OBSERVED_LAUNCH_ENV_NAMES = [
  "ALL_PROXY",
  "HTTP_PROXY",
  "HTTPS_PROXY",
  "NO_PROXY",
  "all_proxy",
  "http_proxy",
  "https_proxy",
  "no_proxy",
  "CLAUDE_CODE_SUBPROCESS_ENV_SCRUB",
  "CLAUDE_CODE_EXECUTABLE",
] as const;

async function captureAgentLaunchEnvironment(options: {
  claude: boolean;
  boundPolicy: boolean;
  sessionEnv: Record<string, string>;
}): Promise<Record<string, string | null>> {
  const cwd = await mkdtemp(path.join(os.tmpdir(), "acpx-client-launch-env-"));
  const marker = path.join(cwd, "observed-env.json");
  const launcher = path.join(cwd, "capture-agent-env.mjs");
  const mockAgentUrl = pathToFileURL(path.resolve("dist-test/test/mock-agent.js")).href;
  await writeFile(
    launcher,
    [
      'import fs from "node:fs";',
      `const names = ${JSON.stringify(OBSERVED_LAUNCH_ENV_NAMES)};`,
      `fs.writeFileSync(${JSON.stringify(marker)}, JSON.stringify(Object.fromEntries(names.map((name) => [name, process.env[name] ?? null]))));`,
      `await import(${JSON.stringify(mockAgentUrl)});`,
    ].join("\n"),
  );

  const binding = options.boundPolicy ? await writeClaudeExecutionPolicyFixture(cwd) : undefined;
  const client = makeClient({
    agentCommand: `${JSON.stringify(process.execPath)} ${JSON.stringify(launcher)}${options.claude ? " --claude-agent-acp" : ""}`,
    cwd,
    sessionOptions: {
      env: options.sessionEnv,
      ...(binding
        ? {
            claudeExecutionPolicyRef: binding.ref,
            claudeExecutionPolicySha256: binding.sha256,
          }
        : {}),
    },
  });
  try {
    await client.start();
    if (binding) {
      await client.createSession(cwd);
    }
    return JSON.parse(await readFile(marker, "utf8")) as Record<string, string | null>;
  } finally {
    await client.close();
  }
}

async function writeClaudeExecutionPolicyFixture(cwd: string) {
  const policyDir = path.join(cwd, "runs", "run-1", "execution-authority");
  await mkdir(policyDir, { recursive: true });
  const policy = JSON.stringify({
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
        allow_read: [cwd],
        allow_write: [cwd, os.tmpdir()],
        deny_read: [],
        deny_write: [],
      },
    },
    provider_subprocess_denied_env_names: [
      "ANTHROPIC_API_KEY",
      "ANTHROPIC_AUTH_TOKEN",
      "ANTHROPIC_BASE_URL",
    ],
    generated_at: "2026-08-29T00:00:00.000Z",
  });
  const ref = path.join("runs", "run-1", "execution-authority", "policy.json");
  await writeFile(path.join(cwd, ref), policy);
  return { ref, sha256: createHash("sha256").update(policy).digest("hex") };
}

function makeClient(
  overrides: Partial<ConstructorParameters<typeof AcpClient>[0]> = {},
): AcpClient {
  return new AcpClient({
    agentCommand: "node ./test/mock-agent.js",
    cwd: process.cwd(),
    permissionMode: "approve-reads",
    ...overrides,
  });
}

function asInternals(client: AcpClient): ClientInternals {
  return client as unknown as ClientInternals;
}

function makePermissionRequest(
  sessionId: string,
  kind: RequestPermissionRequest["toolCall"]["kind"],
): RequestPermissionRequest {
  return {
    sessionId,
    toolCall: {
      toolCallId: "call-1",
      title: "edit file",
      kind,
    },
    options: [
      {
        optionId: "allow",
        name: "Allow",
        kind: "allow_once",
      },
      {
        optionId: "reject",
        name: "Reject",
        kind: "reject_once",
      },
    ],
  };
}

async function withEnv(
  entries: Record<string, string | undefined>,
  run: () => Promise<void> | void,
): Promise<void> {
  const previous = new Map<string, string | undefined>();
  for (const [key, value] of Object.entries(entries)) {
    previous.set(key, process.env[key]);
    if (value == null) {
      delete process.env[key];
    } else {
      process.env[key] = value;
    }
  }

  try {
    await run();
  } finally {
    for (const [key, value] of previous) {
      if (value == null) {
        delete process.env[key];
      } else {
        process.env[key] = value;
      }
    }
  }
}

async function withTty(
  stdinIsTty: boolean,
  stderrIsTty: boolean,
  run: () => Promise<void>,
): Promise<void> {
  const stdinDescriptor = Object.getOwnPropertyDescriptor(process.stdin, "isTTY");
  const stderrDescriptor = Object.getOwnPropertyDescriptor(process.stderr, "isTTY");

  Object.defineProperty(process.stdin, "isTTY", {
    configurable: true,
    value: stdinIsTty,
  });
  Object.defineProperty(process.stderr, "isTTY", {
    configurable: true,
    value: stderrIsTty,
  });

  try {
    await run();
  } finally {
    restoreDescriptor(process.stdin, "isTTY", stdinDescriptor);
    restoreDescriptor(process.stderr, "isTTY", stderrDescriptor);
  }
}

function restoreDescriptor(
  target: object,
  key: "isTTY",
  descriptor: PropertyDescriptor | undefined,
): void {
  if (descriptor) {
    Object.defineProperty(target, key, descriptor);
  } else {
    delete (target as Record<string, unknown>)[key];
  }
}

// B04: ACP peers below are synthetic; these tests make no native/model call.
test("exact permission ask returns the offered option and exposes the original RPC signal", async () => {
  const requestSignal = new AbortController();
  let calls = 0;
  const options = {
    permissionMode: "approve-reads" as const,
    permissionPolicy: { escalate: ["edit"] },
    onPermissionRequestExact: async (
      request: { raw: RequestPermissionRequest },
      context: { requestId: JsonRpcId; signal: AbortSignal },
    ) => {
      calls++;
      assert.equal(context.requestId, "rpc-17");
      assert.equal(context.signal.aborted, false);
      assert.equal(request.raw.sessionId, "s-exact");
      return { optionId: "allow-second" };
    },
  };
  const client = makeClient(options);
  Object.assign(client, {
    activePrompt: { sessionId: "s-exact", elicitationController: new AbortController() },
  });
  const request = makePermissionRequest("s-exact", "edit");
  request.options.push({
    kind: "allow_once",
    optionId: "allow-second",
    name: "Allow specific offered choice",
  });
  const response = await asInternals(client).handlePermissionRequest?.(
    request,
    "rpc-17",
    requestSignal.signal,
  );
  assert.deepEqual(response, { outcome: { outcome: "selected", optionId: "allow-second" } });
  assert.equal(calls, 1);
});

test("exact permission ask never falls back after callback failure or invalid choice", async () => {
  for (const callback of [
    async () => undefined,
    async () => {
      throw new Error("host unavailable");
    },
    async () => ({ optionId: "not-offered" }),
    async () => ({ optionId: "persistent" }),
  ]) {
    const options = {
      permissionMode: "approve-all" as const,
      permissionPolicy: { escalate: ["edit"] },
      onPermissionRequestExact: callback,
    };
    const client = makeClient(options);
    Object.assign(client, {
      activePrompt: { sessionId: "s-exact", elicitationController: new AbortController() },
    });
    const request = makePermissionRequest("s-exact", "edit");
    request.options.push({ kind: "allow_always", optionId: "persistent", name: "Always" });
    assert.deepEqual(
      await asInternals(client).handlePermissionRequest?.(request, 8, new AbortController().signal),
      { outcome: { outcome: "cancelled" } },
    );
  }
});

test("exact lane retains auto reads and explicit denial without asking a human", async () => {
  let calls = 0;
  const options = {
    permissionMode: "approve-reads" as const,
    permissionPolicy: { autoDeny: ["edit"] },
    onPermissionRequestExact: async () => {
      calls++;
      return { optionId: "allow" };
    },
  };
  const client = makeClient(options);
  Object.assign(client, {
    activePrompt: { sessionId: "s-exact", elicitationController: new AbortController() },
  });
  const read = makePermissionRequest("s-exact", "read");
  read.toolCall.title = "read file";
  assert.deepEqual(
    await asInternals(client).handlePermissionRequest?.(read, 1, new AbortController().signal),
    { outcome: { outcome: "selected", optionId: "allow" } },
  );
  assert.deepEqual(
    await asInternals(client).handlePermissionRequest?.(
      makePermissionRequest("s-exact", "edit"),
      2,
      new AbortController().signal,
    ),
    { outcome: { outcome: "selected", optionId: "reject" } },
  );
  assert.equal(calls, 0);
});

test("exact lane refuses stale RPCs, unowned sessions and replies after cancellation", async () => {
  let release: ((value: { optionId: string }) => void) | undefined;
  const options = {
    permissionMode: "approve-reads" as const,
    permissionPolicy: { escalate: ["edit"] },
    onPermissionRequestExact: async () =>
      await new Promise<{ optionId: string }>((resolve) => {
        release = resolve;
      }),
  };
  const client = makeClient(options);
  Object.assign(client, {
    activePrompt: { sessionId: "s-exact", elicitationController: new AbortController() },
  });
  assert.deepEqual(
    await asInternals(client).handlePermissionRequest?.(
      makePermissionRequest("another", "edit"),
      1,
      new AbortController().signal,
    ),
    { outcome: { outcome: "cancelled" } },
  );
  assert.deepEqual(
    await asInternals(client).handlePermissionRequest?.(makePermissionRequest("s-exact", "edit")),
    { outcome: { outcome: "cancelled" } },
  );
  const signal = new AbortController();
  const pending = asInternals(client).handlePermissionRequest?.(
    makePermissionRequest("s-exact", "edit"),
    4,
    signal.signal,
  );
  await Promise.resolve();
  signal.abort();
  release?.({ optionId: "allow" });
  assert.deepEqual(await pending, { outcome: { outcome: "cancelled" } });
});

test("host write and terminal confirmation use exact RPC choices before their effects", async () => {
  const cwd = await mkdtemp(path.join(os.tmpdir(), "acpx-host-permission-"));
  const calls: Array<{ method: unknown; rpc: JsonRpcId }> = [];
  let allow = false;
  const client = makeClient({
    cwd,
    permissionMode: "approve-reads",
    permissionPolicy: { escalate: ["edit", "execute"] },
    onPermissionRequestExact: async (request, context) => {
      calls.push({ method: request.raw.toolCall._meta?.method, rpc: context.requestId });
      return { optionId: allow ? "acpx-host-allow-once" : "acpx-host-reject-once" };
    },
  });
  Object.assign(client, {
    activePrompt: { sessionId: "host-session", elicitationController: new AbortController() },
  });
  const signal = new AbortController();
  const file = path.join(cwd, "product.txt");
  const write = asInternals(client).handleWriteTextFile?.bind(client);
  assert.ok(write);
  const params = { sessionId: "host-session", path: file, content: "legal product edit" };
  await assert.rejects(write(params, 31, signal.signal), PermissionDeniedError);
  await assert.rejects(readFile(file));
  allow = true;
  await write(params, 32, signal.signal);
  assert.equal(await readFile(file, "utf8"), "legal product edit");
  const terminal = await asInternals(client).handleCreateTerminal?.(
    { sessionId: "host-session", command: process.execPath, args: ["-e", "process.exit(0)"] },
    33,
    signal.signal,
  );
  assert.ok(terminal?.terminalId);
  signal.abort();
  await assert.rejects(
    write({ ...params, content: "late" }, 34, signal.signal),
    PermissionDeniedError,
  );
  assert.equal(await readFile(file, "utf8"), "legal product edit");
  assert.deepEqual(calls, [
    { method: "fs/write_text_file", rpc: 31 },
    { method: "fs/write_text_file", rpc: 32 },
    { method: "terminal/create", rpc: 33 },
  ]);
  await asInternals(client).terminalManager?.shutdown();
});

test("actual local ACP peer receives the exact offered permission response", async () => {
  const cwd = await mkdtemp(path.join(os.tmpdir(), "acpx-local-peer-"));
  const received: JsonRpcId[] = [];
  let optionId = "allow";
  const client = makeClient({
    cwd,
    agentCommand: process.execPath,
    agentArgv: [process.execPath, path.resolve("dist-test/test/mock-agent.js")],
    permissionMode: "approve-reads",
    permissionPolicy: { escalate: ["edit"] },
    onPermissionRequestExact: async (_request, context) => {
      received.push(context.requestId);
      return { optionId };
    },
  });
  try {
    await client.start();
    const session = await client.createSession(cwd);
    assert.equal(
      (await client.prompt(session.sessionId, "permission edit product")).stopReason,
      "end_turn",
    );
    optionId = "reject";
    assert.equal(
      (await client.prompt(session.sessionId, "permission edit product")).stopReason,
      "end_turn",
    );
    assert.equal(received.length, 2);
    assert.notEqual(received[0], received[1]);
    assert.deepEqual(client.getPermissionStats(), {
      requested: 2,
      approved: 1,
      denied: 1,
      cancelled: 0,
    });
  } finally {
    await client.close();
  }
});
