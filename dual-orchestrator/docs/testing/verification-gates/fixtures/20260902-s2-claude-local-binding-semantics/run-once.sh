#!/usr/bin/env bash
set -euo pipefail

FIXTURE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ORCHESTRATOR_ROOT=$(CDPATH= cd -- "$FIXTURE_DIR/../../../../.." && pwd)
RESULT_ROOT=${1:?usage: run-once.sh <new-result-directory> <launcher-classes> <adapter-classpath-file>}
LAUNCHER_CLASSES=${2:?missing launcher classes directory}
ADAPTER_CLASSPATH_FILE=${3:?missing adapter classpath file}
SESSION="s2-local-binding-$(date -u +%Y%m%dT%H%M%SZ)-$$"
: "${DUAL_ACPX_EXECUTABLE:?DUAL_ACPX_EXECUTABLE must point to the reviewed E1 acpx binary}"

if [ -e "$RESULT_ROOT" ]; then
  echo "result directory already exists: $RESULT_ROOT" >&2
  exit 64
fi
test -d "$LAUNCHER_CLASSES"
test -s "$ADAPTER_CLASSPATH_FILE"
test -x "$DUAL_ACPX_EXECUTABLE"
command -v java >/dev/null
command -v python3 >/dev/null
mkdir -p "$RESULT_ROOT/work"
(cd "$FIXTURE_DIR" && shasum -a 256 -c SHA256SUMS) > "$RESULT_ROOT/fixture-sha-check.txt"
printf '%s\n' "$SESSION" > "$RESULT_ROOT/session-name.txt"
"$DUAL_ACPX_EXECUTABLE" build-info --json > "$RESULT_ROOT/acpx-build-info.json"
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
LAUNCHER_PID=""
WATCHDOG_PID=""
cleanup() {
  [ -z "$WATCHDOG_PID" ] || kill "$WATCHDOG_PID" 2>/dev/null || true
  [ -z "$LAUNCHER_PID" ] || kill "$LAUNCHER_PID" 2>/dev/null || true
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
  java -cp "$LAUNCHER_CLASSES:$ORCHESTRATOR_ROOT/dual-adapter-acpx/target/classes:$ORCHESTRATOR_ROOT/dual-runtime/target/classes:$ORCHESTRATOR_ROOT/dual-core/target/classes:$(cat "$ADAPTER_CLASSPATH_FILE")" \
    dev.dual.adapter.acpx.E1LocalBindingDiagnosticLauncher \
    "$RESULT_ROOT/work" "$SESSION" "$RESULT_ROOT/work/prompt.md" \
    >../launcher.json 2>../launcher.stderr.txt
) &
LAUNCHER_PID=$!
python3 -c 'import os, signal, sys, time; time.sleep(180); os.kill(int(sys.argv[1]), signal.SIGTERM)' \
  "$LAUNCHER_PID" &
WATCHDOG_PID=$!
set +e
wait "$LAUNCHER_PID"
LAUNCHER_EXIT=$?
set -e
kill "$WATCHDOG_PID" 2>/dev/null || true
wait "$WATCHDOG_PID" 2>/dev/null || true
printf '%s\n' "$LAUNCHER_EXIT" > "$RESULT_ROOT/launcher-exit-code.txt"

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

[ "$LAUNCHER_EXIT" -eq 0 ]
grep -qx 'pass' "$RESULT_ROOT/host-postcheck.txt"
cmp "$RESULT_ROOT/fixture-sha-check.txt" "$RESULT_ROOT/fixture-sha-recheck.txt"
