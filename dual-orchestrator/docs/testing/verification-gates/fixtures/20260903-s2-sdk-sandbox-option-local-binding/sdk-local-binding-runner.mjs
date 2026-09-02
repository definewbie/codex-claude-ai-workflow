import { createHash } from 'node:crypto';
import { readFile, realpath, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const [workspaceArg, promptArg, eventsArg, summaryArg] = process.argv.slice(2);
if (!workspaceArg || !promptArg || !eventsArg || !summaryArg) {
  throw new Error('usage: <workspace> <prompt> <events-jsonl> <summary-json>');
}
const sdkPath = await realpath(process.env.S2_SDK_MODULE ?? '');
const claudeBinary = await realpath(process.env.S2_CLAUDE_BINARY ?? '');
const workspace = await realpath(workspaceArg);
const prompt = await readFile(await realpath(promptArg), 'utf8');
const { query } = await import(pathToFileURL(sdkPath).href);

const sandbox = {
  enabled: true,
  failIfUnavailable: true,
  autoAllowBashIfSandboxed: true,
  network: {
    allowLocalBinding: true,
  },
};
const managedSettings = {
  sandbox: {
    allowUnsandboxedCommands: false,
    network: {
      strictAllowlist: true,
      allowedDomains: [],
      allowUnixSockets: [],
    },
    credentials: {
      envVars: [
        { name: 'ANTHROPIC_API_KEY', mode: 'deny' },
        { name: 'ANTHROPIC_AUTH_TOKEN', mode: 'deny' },
        { name: 'ANTHROPIC_BASE_URL', mode: 'deny' },
      ],
    },
  },
};
const options = {
  cwd: workspace,
  pathToClaudeCodeExecutable: claudeBinary,
  settingSources: [],
  sandbox,
  managedSettings,
  tools: ['Bash'],
  allowedTools: ['Bash'],
  permissionMode: 'dontAsk',
  persistSession: false,
  maxTurns: 2,
};
const sha256 = async (path) => createHash('sha256').update(await readFile(path)).digest('hex');
await writeFile(summaryArg, `${JSON.stringify({
  sdkPath,
  sdkSha256: await sha256(sdkPath),
  claudeBinary,
  claudeBinarySha256: await sha256(claudeBinary),
  settingSources: options.settingSources,
  tools: options.tools,
  allowedTools: options.allowedTools,
  permissionMode: options.permissionMode,
  persistSession: options.persistSession,
  maxTurns: options.maxTurns,
  sandbox,
  managedSettings,
}, null, 2)}\n`);

const messages = [];
let success = false;
const execution = query({ prompt, options });
try {
  for await (const message of execution) {
    messages.push(JSON.stringify(message));
    if (message?.type === 'result') {
      success = message.subtype === 'success' && message.is_error === false;
    }
  }
} finally {
  execution.close?.();
  await writeFile(eventsArg, `${messages.join('\n')}\n`);
}
if (!success) {
  process.exitCode = 4;
}
