#!/usr/bin/env bash
set -euo pipefail

DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
(cd "$DIR" && shasum -a 256 -c SHA256SUMS)
bash -n "$DIR/s2-loopback-probe.sh" "$DIR/run-once.sh"
grep -q 'ClaudeExecutionPolicy.write' "$DIR/E1LocalBindingDiagnosticLauncher.java.txt"
grep -q 'network.put("allow_local_binding", true)' "$DIR/E1LocalBindingDiagnosticLauncher.java.txt"
grep -q 'new AcpxAgentTransport().runClaudeDesign' "$DIR/E1LocalBindingDiagnosticLauncher.java.txt"
grep -q 'AcpxPermissionProfile.implement()' "$DIR/E1LocalBindingDiagnosticLauncher.java.txt"
! grep -q 'allowed_domains.*example' "$DIR/E1LocalBindingDiagnosticLauncher.java.txt"
grep -q 'host-precheck.txt' "$DIR/run-once.sh"
grep -q 'host-postcheck.txt' "$DIR/run-once.sh"
grep -q 'fixture-sha-recheck.txt' "$DIR/run-once.sh"
grep -qF 'python3_available\ttrue' "$DIR/s2-loopback-probe.sh"
grep -q 'external_connect\\tunexpected_success' "$DIR/s2-loopback-probe.sh"

ROOT=$(mktemp -d)
trap 'rm -rf "$ROOT"' EXIT
mkdir -p "$ROOT/bin" "$ROOT/classes"
printf 'offline-classpath\n' > "$ROOT/classpath.txt"
cat > "$ROOT/bin/acpx" <<'SH'
#!/usr/bin/env bash
test "${1:-}" = "build-info"
printf '%s\n' '{"upstreamCommit":"offline","patchSha256":"offline","policySchema":"v2"}'
SH
cat > "$ROOT/bin/java" <<'SH'
#!/usr/bin/env bash
test "${1:-}" = "-cp"
test "${3:-}" = "dev.dual.adapter.acpx.E1LocalBindingDiagnosticLauncher"
cd "$4"
./s2-loopback-probe.sh >/dev/null
printf '%s\n' '{"exitCode":0,"policyRef":"offline","policySha256":"offline"}'
SH
cat > "$ROOT/bin/curl" <<'SH'
#!/usr/bin/env bash
url=${!#}
if [[ "$url" == http://127.0.0.1:* ]]; then
  exec /usr/bin/curl "$@"
fi
exit 7
SH
chmod +x "$ROOT/bin/acpx" "$ROOT/bin/java" "$ROOT/bin/curl"
PATH="$ROOT/bin:$PATH" DUAL_ACPX_EXECUTABLE="$ROOT/bin/acpx" \
  "$DIR/run-once.sh" "$ROOT/result" "$ROOT/classes" "$ROOT/classpath.txt"
grep -q $'self_bind\tpass' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'python3_available\ttrue' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'self_connect\tpass' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'host_connect\tpass' "$ROOT/result/work/s2-loopback-result.tsv"
grep -q $'external_connect\tdenied' "$ROOT/result/work/s2-loopback-result.tsv"
grep -qx '0' "$ROOT/result/launcher-exit-code.txt"
grep -qx 'pass' "$ROOT/result/host-precheck.txt"
grep -qx 'pass' "$ROOT/result/host-postcheck.txt"
grep -Eq '^s2-local-binding-[0-9]{8}T[0-9]{6}Z-[0-9]+$' "$ROOT/result/session-name.txt"
cmp "$ROOT/result/fixture-sha-check.txt" "$ROOT/result/fixture-sha-recheck.txt"
echo 'S2_OFFLINE_FIXTURE_PASS'
