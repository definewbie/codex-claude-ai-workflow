import { createHash } from "node:crypto";
import fs from "node:fs";
import path from "node:path";

export const CLAUDE_EXECUTION_POLICY_SCHEMA = "claude_execution_policy.v2";
const NETWORK_TRANSPORT_OWNER = "claude_sandbox";

const POLICY_KEYS = new Set([
  "schema_version",
  "workstream_id",
  "node_instance_id",
  "run_id",
  "network_transport_owner",
  "setting_sources",
  "sandbox",
  "provider_subprocess_denied_env_names",
  "generated_at",
]);
const SANDBOX_KEYS = new Set([
  "enabled",
  "fail_if_unavailable",
  "allow_unsandboxed_commands",
  "auto_allow_sandboxed_bash",
  "network",
  "filesystem",
]);
const NETWORK_KEYS = new Set([
  "strict_allowlist",
  "allowed_domains",
  "allow_local_binding",
  "allow_unix_sockets",
]);
const FILESYSTEM_KEYS = new Set(["allow_read", "allow_write", "deny_read", "deny_write"]);
const REQUIRED_DENIED_ENV_NAMES = [
  "ANTHROPIC_API_KEY",
  "ANTHROPIC_AUTH_TOKEN",
  "ANTHROPIC_BASE_URL",
] as const;
const DOMAIN_PATTERN = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$/;
const SHA256_PATTERN = /^[a-f0-9]{64}$/;
const SECRET_LIKE_PATTERN =
  /(?:bearer\s+|sk-[a-z0-9]|ghp_[a-z0-9]|-----begin [^-]*private key-----)/i;

type JsonObject = Record<string, unknown>;

export type ClaudeExecutionPolicyBinding = {
  ref: string;
  sha256: string;
};

export type ClaudeExecutionPolicyOptions = {
  settingSources: [];
  sandbox: JsonObject;
  managedSettings: { sandbox: JsonObject; permissions: { deny: string[] } };
};

export function loadClaudeExecutionPolicy(
  cwd: string,
  binding: ClaudeExecutionPolicyBinding,
): ClaudeExecutionPolicyOptions {
  const policyPath = resolvePolicyPath(cwd, binding.ref);
  const bytes = fs.readFileSync(policyPath);
  const actualSha = createHash("sha256").update(bytes).digest("hex");
  if (!SHA256_PATTERN.test(binding.sha256) || actualSha !== binding.sha256) {
    throw new Error("claude execution policy SHA-256 mismatch");
  }

  const raw = bytes.toString("utf8");
  assertNoDuplicateObjectKeys(raw);
  const parsed = JSON.parse(raw) as unknown;
  const policy = requireObject(parsed, "claude execution policy");
  assertExactKeys(policy, POLICY_KEYS, "claude execution policy");
  assertText(policy, "schema_version", CLAUDE_EXECUTION_POLICY_SCHEMA);
  assertIdentifier(policy, "workstream_id");
  assertIdentifier(policy, "node_instance_id");
  assertIdentifier(policy, "run_id");
  assertText(policy, "network_transport_owner", NETWORK_TRANSPORT_OWNER);
  assertEmptyArray(policy.setting_sources, "setting_sources");
  assertGeneratedAt(policy.generated_at);

  const sdkSandbox = validatedSandboxOptions(policy.sandbox);
  assertDeniedEnvironment(policy.provider_subprocess_denied_env_names);
  if (containsSecretLikeValue(policy)) {
    throw new Error("claude execution policy contains a secret-like value");
  }
  return {
    settingSources: [],
    sandbox: sdkSandbox,
    managedSettings: {
      sandbox: managedSandboxOptions(sdkSandbox),
      permissions: {
        deny: [
          // Disabling auto-allow alone still permits Claude Code's read-only shell shortcuts.
          ...(sdkSandbox.autoAllowBashIfSandboxed ? [] : ["Bash"]),
          ...(requireObject(sdkSandbox.filesystem, "filesystem").denyWrite as string[]).flatMap(
            (entry) => [`Edit(/${entry})`, `Edit(/${entry}/**)`],
          ),
        ],
      },
    },
  };
}

function validatedSandboxOptions(value: unknown): JsonObject {
  const sandbox = requireObject(value, "sandbox");
  assertExactKeys(sandbox, SANDBOX_KEYS, "sandbox");
  assertBoolean(sandbox, "enabled", true);
  assertBoolean(sandbox, "fail_if_unavailable", true);
  assertBoolean(sandbox, "allow_unsandboxed_commands", false);
  const autoAllowBashIfSandboxed = requireBoolean(sandbox, "auto_allow_sandboxed_bash");

  const network = validatedNetworkOptions(sandbox.network);
  const filesystem = validatedFilesystemOptions(sandbox.filesystem);
  return {
    enabled: true,
    failIfUnavailable: true,
    allowUnsandboxedCommands: false,
    autoAllowBashIfSandboxed,
    network,
    filesystem,
    credentials: {
      envVars: REQUIRED_DENIED_ENV_NAMES.map((name) => ({ name, mode: "deny" })),
    },
  };
}

function validatedNetworkOptions(value: unknown): JsonObject {
  const network = requireObject(value, "sandbox.network");
  assertExactKeys(network, NETWORK_KEYS, "sandbox.network");
  assertBoolean(network, "strict_allowlist", true);
  const allowLocalBinding = requireBoolean(network, "allow_local_binding");
  assertEmptyArray(network.allow_unix_sockets, "sandbox.network.allow_unix_sockets");
  const allowedDomains = requireStringArray(network.allowed_domains, "allowed_domains");
  if (
    allowedDomains.length > 32 ||
    allowedDomains.some(
      (domain) =>
        domain !== domain.toLowerCase() ||
        domain.length > 253 ||
        !DOMAIN_PATTERN.test(domain) ||
        /^[0-9.]+$/.test(domain) ||
        domain.endsWith(".localhost") ||
        domain.endsWith(".local") ||
        domain.split(".").some((label) => label.length > 63),
    )
  ) {
    throw new Error("sandbox.network.allowed_domains contains a non-canonical domain");
  }
  return {
    strictAllowlist: true,
    allowManagedDomainsOnly: true,
    allowedDomains,
    allowLocalBinding,
    allowAllUnixSockets: false,
    allowUnixSockets: [],
  };
}

function managedSandboxOptions(sandbox: JsonObject): JsonObject {
  const network = requireObject(sandbox.network, "sandbox.network");
  const { allowLocalBinding: _allowLocalBinding, ...managedNetwork } = network;
  return { ...sandbox, network: managedNetwork };
}

function validatedFilesystemOptions(value: unknown): JsonObject {
  const filesystem = requireObject(value, "sandbox.filesystem");
  assertExactKeys(filesystem, FILESYSTEM_KEYS, "sandbox.filesystem");
  const allowRead = requireCanonicalPaths(filesystem.allow_read, "sandbox.filesystem.allow_read");
  const allowWrite = requireCanonicalPaths(
    filesystem.allow_write,
    "sandbox.filesystem.allow_write",
  );
  const denyRead = requireCanonicalPaths(filesystem.deny_read, "sandbox.filesystem.deny_read");
  const denyWrite = requireCanonicalPaths(filesystem.deny_write, "sandbox.filesystem.deny_write");
  if (allowRead.length === 0 || allowWrite.length === 0) {
    throw new Error("sandbox filesystem allow lists must not be empty");
  }
  return { allowRead, allowWrite, denyRead, denyWrite };
}

function assertDeniedEnvironment(value: unknown): void {
  const deniedEnvNames = requireStringArray(value, "provider_subprocess_denied_env_names");
  if (
    deniedEnvNames.length !== REQUIRED_DENIED_ENV_NAMES.length ||
    deniedEnvNames.some((name, index) => name !== REQUIRED_DENIED_ENV_NAMES[index])
  ) {
    throw new Error(
      "provider_subprocess_denied_env_names must equal the fixed E1 provider deny set",
    );
  }
}

function resolvePolicyPath(cwd: string, ref: string): string {
  if (!ref || path.isAbsolute(ref) || ref.includes("\0")) {
    throw new Error("claude execution policy ref must be a relative path");
  }
  const root = fs.realpathSync(cwd);
  const resolved = path.resolve(root, ref);
  if (resolved === root || !resolved.startsWith(`${root}${path.sep}`)) {
    throw new Error("claude execution policy ref escapes cwd");
  }
  const real = fs.realpathSync(resolved);
  if (!real.startsWith(`${root}${path.sep}`)) {
    throw new Error("claude execution policy resolves outside cwd");
  }
  return real;
}

function requireObject(value: unknown, label: string): JsonObject {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`${label} must be an object`);
  }
  return value as JsonObject;
}

function assertExactKeys(value: JsonObject, expected: ReadonlySet<string>, label: string): void {
  const actual = Object.keys(value);
  if (actual.length !== expected.size || actual.some((key) => !expected.has(key))) {
    throw new Error(`${label} has unknown or missing fields`);
  }
}

function assertText(value: JsonObject, key: string, expected: string): void {
  if (value[key] !== expected) {
    throw new Error(`${key} must equal ${expected}`);
  }
}

function assertIdentifier(value: JsonObject, key: string): void {
  const text = value[key];
  if (typeof text !== "string" || !/^[A-Za-z0-9._-]+$/.test(text)) {
    throw new Error(`${key} must be a canonical identifier`);
  }
}

function assertBoolean(value: JsonObject, key: string, expected: boolean): void {
  if (value[key] !== expected) {
    throw new Error(`${key} must equal ${expected}`);
  }
}

function requireBoolean(value: JsonObject, key: string): boolean {
  if (typeof value[key] !== "boolean") {
    throw new Error(`${key} must be a boolean`);
  }
  return value[key];
}

function assertEmptyArray(value: unknown, label: string): void {
  if (!Array.isArray(value) || value.length !== 0) {
    throw new Error(`${label} must be an empty array`);
  }
}

function assertGeneratedAt(value: unknown): void {
  if (
    typeof value !== "string" ||
    !/^\d{4}-\d{2}-\d{2}T/.test(value) ||
    Number.isNaN(Date.parse(value))
  ) {
    throw new Error("generated_at must be an ISO-8601 timestamp");
  }
}

function requireStringArray(value: unknown, label: string): string[] {
  if (!Array.isArray(value) || !value.every((entry) => typeof entry === "string")) {
    throw new Error(`${label} must be a string array`);
  }
  if (new Set(value).size !== value.length) {
    throw new Error(`${label} must not contain duplicates`);
  }
  return [...value];
}

function requireCanonicalPaths(value: unknown, label: string): string[] {
  const paths = requireStringArray(value, label);
  if (paths.some((entry) => !path.isAbsolute(entry) || path.normalize(entry) !== entry)) {
    throw new Error(`${label} contains a non-canonical absolute path`);
  }
  if (
    label === "sandbox.filesystem.deny_write" &&
    paths.some((entry) => /[[\]{}()*?\\\n\r]/.test(entry))
  ) {
    throw new Error(
      "protected filesystem paths must not contain permission-pattern metacharacters",
    );
  }
  return paths;
}

function containsSecretLikeValue(value: unknown, field = ""): boolean {
  // This field has already passed domain validation; token-prefix heuristics reject valid DNS names.
  if (field === "sandbox.network.allowed_domains") {
    return false;
  }
  if (typeof value === "string") {
    return SECRET_LIKE_PATTERN.test(value);
  }
  if (Array.isArray(value)) {
    return value.some((entry) => containsSecretLikeValue(entry, field));
  }
  if (value && typeof value === "object") {
    return Object.entries(value as JsonObject).some(([key, entry]) =>
      containsSecretLikeValue(entry, field ? `${field}.${key}` : key),
    );
  }
  return false;
}

function assertNoDuplicateObjectKeys(raw: string): void {
  new DuplicateKeyScanner(raw).scan();
}

class DuplicateKeyScanner {
  private readonly stack: Array<Set<string> | null> = [];
  private index = 0;
  private expectingObjectKey = false;

  constructor(private readonly raw: string) {}

  scan(): void {
    while (this.index < this.raw.length) {
      const char = this.raw[this.index];
      if (/\s/.test(char)) {
        this.index += 1;
      } else if (char === '"') {
        this.consumeString();
      } else {
        this.consumeStructural(char);
      }
    }
  }

  private consumeStructural(char: string): void {
    const action = STRUCTURAL_ACTIONS[char];
    if (action) {
      action(this);
    }
    this.index += 1;
  }

  private consumeString(): void {
    const start = this.index;
    this.index += 1;
    this.consumeStringBody();
    if (this.expectingObjectKey) {
      this.recordKey(JSON.parse(this.raw.slice(start, this.index)) as string);
    }
  }

  private consumeStringBody(): void {
    let escaped = false;
    while (this.index < this.raw.length) {
      const current = this.raw[this.index];
      this.index += 1;
      if (escaped) {
        escaped = false;
      } else if (current === "\\") {
        escaped = true;
      } else if (current === '"') {
        return;
      }
    }
  }

  private recordKey(key: string): void {
    const keys = this.stack.at(-1);
    if (!(keys instanceof Set)) {
      throw new Error("invalid object-key state in claude execution policy");
    }
    if (keys.has(key)) {
      throw new Error(`claude execution policy contains duplicate key: ${key}`);
    }
    keys.add(key);
    this.expectingObjectKey = false;
  }

  openObject(): void {
    this.stack.push(new Set());
    this.expectingObjectKey = true;
  }

  openArray(): void {
    this.stack.push(null);
    this.expectingObjectKey = false;
  }

  closeContainer(): void {
    this.stack.pop();
    this.expectingObjectKey = false;
  }

  nextEntry(): void {
    this.expectingObjectKey = this.stack.at(-1) instanceof Set;
  }
}

const STRUCTURAL_ACTIONS: Record<string, (scanner: DuplicateKeyScanner) => void> = {
  "{": (scanner) => scanner.openObject(),
  "[": (scanner) => scanner.openArray(),
  "}": (scanner) => scanner.closeContainer(),
  "]": (scanner) => scanner.closeContainer(),
  ",": (scanner) => scanner.nextEntry(),
};
