import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { AcpClient } from '../../.tools/acpx-e1/source/src/acp/client.ts';

// Diagnostic only: no connect/newSession/prompt; invoke existing host handlers.
assert.ok(!process.stdin.isTTY && !process.stderr.isTTY);
const evidence = path.dirname(new URL(import.meta.url).pathname);
const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'dual-d04-host-proof-'));
const original = 'alpha\nbeta\ngamma\n';
const target = path.join(scratch, 'sample.txt');
const created = path.join(scratch, 'created.txt');
const marker = path.join(scratch, 'execution-marker');
await fs.writeFile(target, original);
const events: unknown[] = [];
const client = (mode: string) => new AcpClient({
  cwd: scratch, agentCommand: 'NOT_STARTED', permissionMode: mode,
  nonInteractivePermissions: 'deny', terminal: false,
  onClientOperation: (event: unknown) => events.push(event),
} as any) as any;
const readOnly = client('approve-reads');
const req = { sessionId: 'offline-handler-only', path: target };
const cases: unknown[] = [];
const checked = (name: string, observed: unknown) => cases.push({ name, observed, assertion: 'PASS' });
async function denied(name: string, invoke: () => Promise<unknown>) {
  let caught: any;
  try { await invoke(); } catch (error) { caught = error; }
  assert.equal(caught?.name, 'PermissionDeniedError');
  checked(name, { error: caught.name, message: caught.message });
}
assert.equal((await readOnly.handleReadTextFile(req)).content, original);
checked('necessary_full_read', original);
assert.equal((await readOnly.handleReadTextFile({ ...req, line: 2, limit: 1 })).content, 'beta');
checked('necessary_window_read', 'beta');
await denied('existing_file_modify_blocked', () => readOnly.handleWriteTextFile({ ...req, content: 'unauthorized' }));
assert.equal(await fs.readFile(target, 'utf8'), original);
await denied('new_file_create_blocked', () => readOnly.handleWriteTextFile({ ...req, path: created, content: 'unauthorized' }));
assert.equal(await fs.stat(created).then(() => true, () => false), false);
const terminalRequest = {
  sessionId: req.sessionId, command: process.execPath,
  args: ['-e', 'require("node:fs").writeFileSync(process.argv[1], "executed")', marker],
  cwd: scratch,
};
await denied('terminal_request_blocked', () => readOnly.handleCreateTerminal(terminalRequest));
assert.equal(await fs.stat(marker).then(() => true, () => false), false);
await denied('deny_all_breaks_necessary_read', () => client('deny-all').handleReadTextFile(req));

// Counterexample: advertising terminal=false is not itself a request-time guard.
const permissive = client('approve-all');
const terminal = await permissive.handleCreateTerminal(terminalRequest);
const exited = await permissive.handleWaitForTerminalExit({ sessionId: req.sessionId, terminalId: terminal.terminalId });
assert.equal(exited.exitCode, 0);
assert.equal(await fs.readFile(marker, 'utf8'), 'executed');
await permissive.handleReleaseTerminal({ sessionId: req.sessionId, terminalId: terminal.terminalId });
checked('terminal_false_is_not_a_hard_guard', { executed: true, permissionMode: 'approve-all', terminal: false });
assert.equal(await fs.readFile(target, 'utf8'), original);
const hash = (s: string) => createHash('sha256').update(s).digest('hex');
await fs.writeFile(path.join(evidence, 'host-probe.json'), JSON.stringify({
  scope: 'real acpx host handlers only; no provider, no ACP wire, no model tools',
  capturedAt: new Date().toISOString(), scratch, cases, events,
  finalOriginalSha: hash(await fs.readFile(target, 'utf8')),
  expectedOriginalSha: hash(original), providerCalls: 0,
}, null, 2) + '\n');
console.log(JSON.stringify({ assertions: cases.length, evidence: path.join(evidence, 'host-probe.json'), providerCalls: 0 }));
