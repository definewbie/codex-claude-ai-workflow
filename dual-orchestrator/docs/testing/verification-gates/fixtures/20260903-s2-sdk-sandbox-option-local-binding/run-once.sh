#!/usr/bin/env bash
set -euo pipefail

FIXTURE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ORCHESTRATOR_ROOT=$(CDPATH= cd -- "$FIXTURE_DIR/../../../../.." && pwd)
RESULT_ROOT=${1:?usage: run-once.sh <new-result-directory>}
: "${S2_SDK_MODULE:?S2_SDK_MODULE must point to sdk.mjs from SDK 0.3.232}"
: "${S2_CLAUDE_BINARY:?S2_CLAUDE_BINARY must point to embedded Claude 2.1.232}"

if [ -e "$RESULT_ROOT" ]; then
  echo "result directory already exists: $RESULT_ROOT" >&2
  exit 64
fi
command -v node >/dev/null
command -v python3 >/dev/null
test -f "$S2_SDK_MODULE"
test -x "$S2_CLAUDE_BINARY"
mkdir -p "$RESULT_ROOT/work"
(cd "$FIXTURE_DIR" && shasum -a 256 -c SHA256SUMS) > "$RESULT_ROOT/fixture-sha-check.txt"
cp "$FIXTURE_DIR/prompt.md" "$RESULT_ROOT/work/prompt.md"
cp "$FIXTURE_DIR/s2-loopback-probe.sh" "$RESULT_ROOT/work/s2-loopback-probe.sh"
chmod +x "$RESULT_ROOT/work/s2-loopback-probe.sh"

python3 - "$RESULT_ROOT/work/s2-host.port" >"$RESULT_ROOT/host-sentinel.log" 2>&1 <<'PY' &
import http.server
import pathlib
import socketserver
import sys

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = b"S2_HOST_SENTINEL"
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self, *_):
        pass

with socketserver.TCPServer(("127.0.0.1", 0), Handler) as server:
    pathlib.Path(sys.argv[1]).write_text(str(server.server_address[1]), encoding="ascii")
    server.serve_forever()
PY
HOST_PID=$!
SDK_PID=""
WATCHDOG_PID=""
cleanup() {
  [ -z "$WATCHDOG_PID" ] || kill "$WATCHDOG_PID" 2>/dev/null || true
  [ -z "$SDK_PID" ] || kill "$SDK_PID" 2>/dev/null || true
  kill "$HOST_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 50); do
  [ -s "$RESULT_ROOT/work/s2-host.port" ] && break
  sleep 0.1
done
[ -s "$RESULT_ROOT/work/s2-host.port" ]
HOST_PORT=$(cat "$RESULT_ROOT/work/s2-host.port")
curl --noproxy '*' -fsS --max-time 5 "http://127.0.0.1:${HOST_PORT}/" | grep -qx 'S2_HOST_SENTINEL'
printf 'pass\n' > "$RESULT_ROOT/host-precheck.txt"

(
  cd "$RESULT_ROOT/work"
  "$ORCHESTRATOR_ROOT/scripts/acpx-claude-agent-env.sh" \
    node "$FIXTURE_DIR/sdk-local-binding-runner.mjs" \
    "$RESULT_ROOT/work" "$RESULT_ROOT/work/prompt.md" \
    "$RESULT_ROOT/sdk-events.jsonl" "$RESULT_ROOT/sdk-options-summary.json" \
    >"$RESULT_ROOT/sdk.stdout.txt" 2>"$RESULT_ROOT/sdk.stderr.txt"
) &
SDK_PID=$!
python3 -c 'import os, signal, sys, time; time.sleep(180); os.kill(int(sys.argv[1]), signal.SIGTERM)' \
  "$SDK_PID" &
WATCHDOG_PID=$!
set +e
wait "$SDK_PID"
SDK_EXIT=$?
set -e
kill "$WATCHDOG_PID" 2>/dev/null || true
wait "$WATCHDOG_PID" 2>/dev/null || true
printf '%s\n' "$SDK_EXIT" > "$RESULT_ROOT/sdk-exit-code.txt"

if curl --noproxy '*' -fsS --max-time 5 "http://127.0.0.1:${HOST_PORT}/" | grep -qx 'S2_HOST_SENTINEL'; then
  printf 'pass\n' > "$RESULT_ROOT/host-postcheck.txt"
else
  printf 'fail\n' > "$RESULT_ROOT/host-postcheck.txt"
fi
(cd "$FIXTURE_DIR" && shasum -a 256 -c SHA256SUMS) > "$RESULT_ROOT/fixture-sha-recheck.txt"
date -u +%Y-%m-%dT%H:%M:%SZ > "$RESULT_ROOT/completed-at.txt"
(
  cd "$RESULT_ROOT"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 shasum -a 256 > SHA256SUMS
)

[ "$SDK_EXIT" -eq 0 ]
grep -qx 'pass' "$RESULT_ROOT/host-postcheck.txt"
cmp "$RESULT_ROOT/fixture-sha-check.txt" "$RESULT_ROOT/fixture-sha-recheck.txt"
