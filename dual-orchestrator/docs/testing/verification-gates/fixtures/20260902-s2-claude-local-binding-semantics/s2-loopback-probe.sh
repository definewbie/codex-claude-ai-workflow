#!/usr/bin/env bash
set -u

RESULT="s2-loopback-result.tsv"
SELF_PORT_FILE="s2-self.port"
SELF_LOG="s2-self.log"
HOST_PORT_FILE="s2-host.port"

printf 'probe_complete\tfalse\n' > "$RESULT"

if command -v python3 >/dev/null 2>&1; then
  printf 'python3_available\ttrue\n' >> "$RESULT"
else
  printf 'python3_available\tfalse\n' >> "$RESULT"
  printf 'self_bind\tinvalid_missing_python3\nself_connect\tnot_run\n' >> "$RESULT"
  printf 'host_connect\tnot_run\nexternal_connect\tnot_run\nprobe_complete\tfalse\n' >> "$RESULT"
  cat "$RESULT"
  exit 75
fi

python3 - "$SELF_PORT_FILE" >"$SELF_LOG" 2>&1 <<'PY' &
import http.server
import pathlib
import socketserver
import sys

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        body = b"S2_SELF_SENTINEL"
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
SELF_PID=$!
trap 'kill "$SELF_PID" 2>/dev/null || true' EXIT

for _ in $(seq 1 50); do
  [ -s "$SELF_PORT_FILE" ] && break
  sleep 0.1
done

if [ -s "$SELF_PORT_FILE" ]; then
  printf 'self_bind\tpass\n' >> "$RESULT"
  SELF_PORT=$(cat "$SELF_PORT_FILE")
  if curl --noproxy '*' -fsS --max-time 5 "http://127.0.0.1:${SELF_PORT}/" | grep -qx 'S2_SELF_SENTINEL'; then
    printf 'self_connect\tpass\n' >> "$RESULT"
  else
    printf 'self_connect\tfail\n' >> "$RESULT"
  fi
else
  printf 'self_bind\tfail\nself_connect\tnot_run\n' >> "$RESULT"
fi

if [ -s "$HOST_PORT_FILE" ]; then
  HOST_PORT=$(cat "$HOST_PORT_FILE")
  if curl --noproxy '*' -fsS --max-time 5 "http://127.0.0.1:${HOST_PORT}/" | grep -qx 'S2_HOST_SENTINEL'; then
    printf 'host_connect\tpass\n' >> "$RESULT"
  else
    printf 'host_connect\tdenied\n' >> "$RESULT"
  fi
else
  printf 'host_connect\tinvalid_missing_port\n' >> "$RESULT"
fi

if curl --noproxy '*' -fsS --max-time 8 https://example.com/ >/dev/null 2>&1; then
  printf 'external_connect\tunexpected_success\n' >> "$RESULT"
else
  printf 'external_connect\tdenied\n' >> "$RESULT"
fi

printf 'probe_complete\ttrue\n' >> "$RESULT"
cat "$RESULT"
