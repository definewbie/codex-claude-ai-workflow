#!/usr/bin/env bash
set -u

REPORT_DIR="${PWD}/e1-probe-output"
mkdir -p "$REPORT_DIR" || exit 72

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

for name in ANTHROPIC_API_KEY ANTHROPIC_AUTH_TOKEN ANTHROPIC_BASE_URL \
  HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY http_proxy https_proxy all_proxy no_proxy; do
  if env | grep -q "^${name}="; then
    printf '%s\tpresent\n' "$name" >> "$REPORT_DIR/environment.tsv"
  else
    printf '%s\tabsent\n' "$name" >> "$REPORT_DIR/environment.tsv"
  fi
done
if grep -q $'\tpresent$' "$REPORT_DIR/environment.tsv"; then
  printf 'E1_TOOL_ENV_LEAK\n' >&2
  exit 73
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
if [[ -n "$LAN_IP" ]]; then
  record lan curl --max-time 4 -fsS "http://${LAN_IP}:18765/"
else
  printf 'lan\t125\n' | tee -a "$REPORT_DIR/results.tsv"
  printf 'LAN address unavailable\n' > "$REPORT_DIR/lan.err"
fi

printf 'E1_PROBE_COMPLETE\n'
