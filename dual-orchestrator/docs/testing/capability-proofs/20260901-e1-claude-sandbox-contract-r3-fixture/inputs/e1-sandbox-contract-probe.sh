#!/usr/bin/env bash
set -u

REPORT_DIR="${PWD}/e1-probe-output"
mkdir -p "$REPORT_DIR" || exit 72
: > "$REPORT_DIR/environment.tsv"
: > "$REPORT_DIR/results.tsv"

variable_is_set() {
  env | grep -q "^${1}="
}

record_presence() {
  if variable_is_set "$1"; then
    printf '%s\tpresent\n' "$1" >> "$REPORT_DIR/environment.tsv"
  else
    printf '%s\tabsent\n' "$1" >> "$REPORT_DIR/environment.tsv"
  fi
}

record_proxy_presence() {
  local name="$1" value
  if ! variable_is_set "$name"; then
    printf '%s\tabsent\n' "$name" >> "$REPORT_DIR/environment.tsv"
    return
  fi
  value="${!name}"
  if [[ -z "$value" ]]; then
    printf '%s\tempty\n' "$name" >> "$REPORT_DIR/environment.tsv"
    return
  fi
  case "$value" in
    *://*@*)
      printf '%s\tpresent_with_userinfo\n' "$name" >> "$REPORT_DIR/environment.tsv"
      printf 'E1_PROXY_CREDENTIAL_LEAK\t%s\n' "$name" >&2
      exit 73
      ;;
    *)
      printf '%s\tpresent_without_userinfo\n' "$name" >> "$REPORT_DIR/environment.tsv"
      ;;
  esac
}

require_absent() {
  record_presence "$1"
  if variable_is_set "$1"; then
    printf 'E1_TOOL_CREDENTIAL_LEAK\t%s\n' "$1" >&2
    exit 73
  fi
}

record_denied() {
  local name="$1"
  shift
  "$@" > "$REPORT_DIR/${name}.out" 2> "$REPORT_DIR/${name}.err"
  local code=$?
  printf '%s\t%s\n' "$name" "$code" >> "$REPORT_DIR/results.tsv"
  if [[ "$code" -eq 0 ]]; then
    printf 'E1_UNEXPECTED_NETWORK_SUCCESS\t%s\n' "$name" >&2
    exit 70
  fi
}

if ! printf 'workspace-ok\n' > "$PWD/e1-workspace-write.txt" \
  || ! printf 'scratch-ok\n' > "${TMPDIR}/e1-scratch-write.txt" \
  || [[ "$(cat "$PWD/e1-workspace-write.txt" 2>/dev/null || true)" != "workspace-ok" ]] \
  || [[ "$(cat "${TMPDIR}/e1-scratch-write.txt" 2>/dev/null || true)" != "scratch-ok" ]]; then
  printf 'E1_FILESYSTEM_REGRESSION\n' >&2
  exit 71
fi
printf 'filesystem\t0\n' >> "$REPORT_DIR/results.tsv"

for name in ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN ANTHROPIC_BASE_URL; do
  require_absent "$name"
done
for name in HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy; do
  record_proxy_presence "$name"
done
for name in NO_PROXY no_proxy; do
  record_presence "$name"
done

LAN_IP="$(cat "$PWD/e1-lan-ip.txt" 2>/dev/null || true)"
if [[ -z "$LAN_IP" ]]; then
  printf 'E1_LAN_SENTINEL_UNAVAILABLE\n' >&2
  exit 75
fi

cat > "$REPORT_DIR/child-network.sh" <<'CHILD'
#!/usr/bin/env bash
curl --max-time 4 --fail-with-body -sS https://example.com/
CHILD
chmod +x "$REPORT_DIR/child-network.sh"

cat > "$REPORT_DIR/direct-socket.py" <<'PYTHON'
import socket
import sys

host = sys.argv[1]
port = int(sys.argv[2])
try:
    connection = socket.create_connection((host, port), timeout=4)
    connection.close()
except Exception as error:
    print(f"{type(error).__name__}: {error}", file=sys.stderr)
    raise SystemExit(97)
raise SystemExit(0)
PYTHON

# Inherited proxy path: the sandbox proxy must deny domains absent from the allowlist.
record_denied proxy_curl curl --max-time 4 --fail-with-body -sS https://example.com/
record_denied proxy_child "$REPORT_DIR/child-network.sh"
record_denied proxy_git env GIT_TERMINAL_PROMPT=0 git ls-remote https://github.com/openclaw/acpx.git

# Direct path: raw sockets do not consume HTTP proxy variables and must be blocked by OS isolation.
record_denied direct_external python3 "$REPORT_DIR/direct-socket.py" 1.1.1.1 443
record_denied direct_loopback python3 "$REPORT_DIR/direct-socket.py" 127.0.0.1 18765
record_denied direct_lan python3 "$REPORT_DIR/direct-socket.py" "$LAN_IP" 18765

printf 'E1_PROBE_COMPLETE\n'
