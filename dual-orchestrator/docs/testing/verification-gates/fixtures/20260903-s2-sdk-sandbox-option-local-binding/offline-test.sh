#!/usr/bin/env bash
set -euo pipefail

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
(cd "$DIR" && shasum -a 256 -c SHA256SUMS)
bash -n "$DIR/s2-loopback-probe.sh" "$DIR/run-once.sh"
node --check "$DIR/sdk-local-binding-runner.mjs"
grep -q 'allowLocalBinding: true' "$DIR/sdk-local-binding-runner.mjs"
grep -q 'managedSettings' "$DIR/sdk-local-binding-runner.mjs"
grep -q "settingSources: \[\]" "$DIR/sdk-local-binding-runner.mjs"
grep -q "tools: \['Bash'\]" "$DIR/sdk-local-binding-runner.mjs"
grep -q "permissionMode: 'dontAsk'" "$DIR/sdk-local-binding-runner.mjs"
grep -q "persistSession: false" "$DIR/sdk-local-binding-runner.mjs"

ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
mkdir -p "$ROOT/bin" "$ROOT/empty-claude"
cat > "$ROOT/fake-sdk.mjs" <<'JS'
import { spawnSync } from 'node:child_process';

export async function* query({ options }) {
  if (options.sandbox?.network?.allowLocalBinding !== true) throw new Error('missing local binding');
  if (options.managedSettings?.sandbox?.network?.allowLocalBinding !== undefined) throw new Error('local binding must not use managed settings');
  if (options.managedSettings?.sandbox?.network?.allowedDomains?.length !== 0) throw new Error('domains not empty');
  if (options.settingSources?.length !== 0) throw new Error('setting sources not empty');
  if (options.tools?.join(',') !== 'Bash' || options.allowedTools?.join(',') !== 'Bash') throw new Error('tool drift');
  if (options.permissionMode !== 'dontAsk' || options.persistSession !== false) throw new Error('session drift');
  const run = spawnSync('./s2-loopback-probe.sh', { cwd: options.cwd, encoding: 'utf8' });
  yield { type: 'assistant', message: { content: [{ type: 'tool_use', name: 'Bash', input: { command: './s2-loopback-probe.sh' } }] } };
  yield { type: 'user', message: { content: [{ type: 'tool_result', content: run.stdout, is_error: run.status !== 0 }] } };
  yield { type: 'result', subtype: run.status === 0 ? 'success' : 'error_during_execution', is_error: run.status !== 0 };
}
JS
cat > "$ROOT/bin/curl" <<'SH'
#!/usr/bin/env bash
url=${!#}
if [[ "$url" == http://127.0.0.1:* ]]; then
  exec /usr/bin/curl "$@"
fi
exit 7
SH
printf '#!/usr/bin/env bash\nexit 0\n' > "$ROOT/fake-claude"
chmod +x "$ROOT/bin/curl" "$ROOT/fake-claude"
PATH="$ROOT/bin:$PATH" CLAUDE_CONFIG_DIR="$ROOT/empty-claude" \
  S2_SDK_MODULE="$ROOT/fake-sdk.mjs" S2_CLAUDE_BINARY="$ROOT/fake-claude" \
  "$DIR/run-once.sh" "$ROOT/result"
grep -q $'python3_available\ttrue' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'self_bind\tpass' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'self_connect\tpass' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'host_connect\tpass' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'external_connect\tdenied' "$ROOT/result/work/s2-loopback-result.tsv"
grep -qx '0' "$ROOT/result/sdk-exit-code.txt"
grep -qx 'pass' "$ROOT/result/host-precheck.txt"
grep -qx 'pass' "$ROOT/result/host-postcheck.txt"
grep -q '"allowLocalBinding": true' "$ROOT/result/sdk-options-summary.json"
grep -q '"command":"./s2-loopback-probe.sh"' "$ROOT/result/sdk-events.jsonl"
cmp "$ROOT/result/fixture-sha-check.txt" "$ROOT/result/fixture-sha-recheck.txt"
echo 'S2_SDK_OFFLINE_FIXTURE_PASS'
