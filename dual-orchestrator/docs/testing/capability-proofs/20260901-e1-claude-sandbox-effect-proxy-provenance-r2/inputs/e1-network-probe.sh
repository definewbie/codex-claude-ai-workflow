#!/usr/bin/env bash
set -u

REPORT_DIR="${PWD}/e1-probe-output"
mkdir -p "$REPORT_DIR" || exit 72

variable_is_set() {
  env | grep -q "^${1}="
}

record_classification() {
  printf '%s\t%s\n' "$1" "$2" >> "$REPORT_DIR/environment.tsv"
}

classify_required_absent() {
  local name="$1"
  if variable_is_set "$name"; then
    record_classification "$name" present
    printf 'E1_TOOL_CREDENTIAL_LEAK\t%s\n' "$name" >&2
    exit 73
  fi
  record_classification "$name" absent
}

classify_proxy_url() {
  local name="$1" value classifier_exit
  if ! variable_is_set "$name"; then
    record_classification "$name" absent
    return
  fi
  value="${!name}"
  if [[ -z "$value" ]]; then
    record_classification "$name" empty
    return
  fi
  printf '%s' "$value" | node -e '
    const fs = require("fs");
    const raw = fs.readFileSync(0, "utf8");
    try {
      const value = new URL(raw);
      const protocols = new Set(["http:", "https:", "socks5:", "socks5h:"]);
      const ipv4Parts = value.hostname.split(".");
      const ipv4Loopback = ipv4Parts.length === 4
        && ipv4Parts[0] === "127"
        && ipv4Parts.every(part => /^[0-9]+$/.test(part) && Number(part) <= 255);
      const loopbackHost = value.hostname === "localhost"
        || value.hostname === "[::1]"
        || ipv4Loopback;
      const cleanPath = value.pathname === "" || value.pathname === "/";
      const validPort = /^[0-9]+$/.test(value.port)
        && Number(value.port) > 0 && Number(value.port) <= 65535;
      if (!protocols.has(value.protocol) || !loopbackHost || !validPort
          || value.username || value.password || !cleanPath || value.search || value.hash) {
        process.exit(1);
      }
    } catch {
      process.exit(1);
    }
  '
  classifier_exit=$?
  case "$classifier_exit" in
    0)
      record_classification "$name" controlled_loopback
      return
      ;;
    1)
      record_classification "$name" external
      printf 'E1_UNSAFE_PROXY_PROVENANCE\t%s\texternal\n' "$name" >&2
      exit 74
      ;;
    *)
      record_classification "$name" classifier_unavailable
      printf 'E1_PROXY_CLASSIFIER_UNAVAILABLE\t%s\texit_%s\n' "$name" "$classifier_exit" >&2
      exit 76
      ;;
  esac
}

classify_no_proxy() {
  local name="$1" value entry
  if ! variable_is_set "$name"; then
    record_classification "$name" absent
    return
  fi
  value="${!name}"
  if [[ -z "$value" ]]; then
    record_classification "$name" empty
    return
  fi
  local old_ifs="$IFS"
  IFS=','
  for entry in $value; do
    [[ -z "$entry" ]] && continue
    case "$entry" in
      localhost|127.0.0.1|::1|'[::1]') ;;
      *)
        IFS="$old_ifs"
        record_classification "$name" external_bypass
        printf 'E1_UNSAFE_PROXY_PROVENANCE\t%s\texternal_bypass\n' "$name" >&2
        exit 74
        ;;
    esac
  done
  IFS="$old_ifs"
  record_classification "$name" controlled_local_only
}

record() {
  local name="$1"
  shift
  "$@" >"$REPORT_DIR/${name}.out" 2>"$REPORT_DIR/${name}.err"
  local code=$?
  printf '%s\t%s\n' "$name" "$code" | tee -a "$REPORT_DIR/results.tsv"
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
printf 'filesystem\t0\n' > "$REPORT_DIR/results.tsv"
: > "$REPORT_DIR/environment.tsv"

if ! command -v node >/dev/null 2>&1; then
  printf 'E1_PROXY_CLASSIFIER_UNAVAILABLE\n' >&2
  exit 76
fi
node -e 'process.exit(0)' </dev/null >/dev/null 2>&1
node_self_test_exit=$?
if [[ "$node_self_test_exit" -ne 0 ]]; then
  printf 'E1_PROXY_CLASSIFIER_UNAVAILABLE\tself_test\texit_%s\n' "$node_self_test_exit" >&2
  exit 76
fi

for name in ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN ANTHROPIC_BASE_URL; do
  classify_required_absent "$name"
done
for name in HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy; do
  classify_proxy_url "$name"
done
for name in NO_PROXY no_proxy; do
  classify_no_proxy "$name"
done

if [[ "${1:-}" == "--classify-env-only" ]]; then
  printf 'E1_PROXY_CLASSIFICATION_COMPLETE\n'
  exit 0
fi

cat > "$REPORT_DIR/child-network.sh" <<'CHILD'
#!/usr/bin/env bash
curl --max-time 4 -fsS https://example.com/
CHILD
chmod +x "$REPORT_DIR/child-network.sh"

record direct_curl curl --max-time 4 -fsS https://example.com/
record shell_chain sh -c 'true && curl --max-time 4 -fsS https://example.com/'
record variable_substitution sh -c 'tool=curl; "$tool" --max-time 4 -fsS https://example.com/'
record child_script "$REPORT_DIR/child-network.sh"
record git_remote env GIT_TERMINAL_PROMPT=0 git ls-remote https://github.com/openclaw/acpx.git
record npm_registry env npm_config_cache="$REPORT_DIR/npm-cache" npm_config_fetch_timeout=4000 \
  npm view acpx version
record maven_repository mvn -q -Dmaven.repo.local="$REPORT_DIR/m2" -DskipTests \
  dependency:get -Dartifact=org.apache.commons:commons-lang3:3.17.0 \
  -DremoteRepositories=https://repo1.maven.org/maven2
record ip_literal curl --max-time 4 -kfsS https://1.1.1.1/
record invalid_dns curl --max-time 4 -fsS https://e1-capability.invalid/
record loopback curl --max-time 4 -fsS http://127.0.0.1:18765/

LAN_IP="$(cat "$PWD/e1-lan-ip.txt" 2>/dev/null || true)"
if [[ -z "$LAN_IP" ]]; then
  printf 'lan\t125\n' | tee -a "$REPORT_DIR/results.tsv"
  printf 'LAN address unavailable\n' > "$REPORT_DIR/lan.err"
  exit 75
fi
record lan curl --max-time 4 -fsS "http://${LAN_IP}:18765/"

PROXY_ENV=(
  -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u NO_PROXY
  -u http_proxy -u https_proxy -u all_proxy -u no_proxy
)
record unset_proxy_direct env "${PROXY_ENV[@]}" curl --max-time 4 -fsS https://example.com/
record unset_proxy_loopback env "${PROXY_ENV[@]}" curl --max-time 4 -fsS http://127.0.0.1:18765/
record unset_proxy_lan env "${PROXY_ENV[@]}" curl --max-time 4 -fsS "http://${LAN_IP}:18765/"

printf 'E1_PROBE_COMPLETE\n'
