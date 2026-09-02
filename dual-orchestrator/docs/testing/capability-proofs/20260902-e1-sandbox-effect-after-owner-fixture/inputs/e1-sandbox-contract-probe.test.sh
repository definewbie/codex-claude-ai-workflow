#!/usr/bin/env bash
set -euo pipefail

PROBE="$(dirname "$0")/e1-sandbox-contract-probe.sh"
if grep -Eq 'userinfo|present_with_|E1_PROXY_CREDENTIAL_LEAK|\$\{![A-Za-z_]' "$PROBE"; then
  printf 'probe must not inspect or classify proxy values\n' >&2
  exit 1
fi

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT
mkdir -p "$ROOT/bin" "$ROOT/work" "$ROOT/tmp"
cp "$PROBE" "$ROOT/work/"
printf '192.0.2.10\n' > "$ROOT/work/e1-lan-ip.txt"

cat > "$ROOT/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf 'Connection blocked by network allowlist\n' >&2
exit 97
EOF
cat > "$ROOT/bin/git" <<'EOF'
#!/usr/bin/env bash
printf 'Connection blocked by network allowlist\n' >&2
exit 97
EOF
cat > "$ROOT/bin/python3" <<'EOF'
#!/usr/bin/env bash
printf 'PermissionError: [Errno 1] Operation not permitted\n' >&2
exit 97
EOF
chmod +x "$ROOT/bin/curl" "$ROOT/bin/git" "$ROOT/bin/python3" "$ROOT/work/e1-sandbox-contract-probe.sh"

run_probe() {
  local proxy_value="$1" prefix="$2"
  rm -rf "$ROOT/work/e1-probe-output"
  (
    cd "$ROOT/work"
    PATH="$ROOT/bin:$PATH" TMPDIR="$ROOT/tmp" HTTP_PROXY="$proxy_value" \
      ./e1-sandbox-contract-probe.sh > "${prefix}.stdout.txt" 2> "${prefix}.stderr.txt"
  )
  grep -q '^E1_PROBE_COMPLETE$' "$ROOT/work/${prefix}.stdout.txt"
  grep -q $'^HTTP_PROXY\tpresent$' "$ROOT/work/e1-probe-output/environment.tsv"
  [[ "$(wc -l < "$ROOT/work/e1-probe-output/results.tsv" | tr -d ' ')" = 7 ]]
  [[ "$(grep -c $'\t97$' "$ROOT/work/e1-probe-output/results.tsv")" = 6 ]]
  if grep -R -F -q "$proxy_value" "$ROOT/work/e1-probe-output" \
    "$ROOT/work/${prefix}.stdout.txt" "$ROOT/work/${prefix}.stderr.txt"; then
    printf 'proxy value leaked into evidence\n' >&2
    exit 1
  fi
}

run_probe 'credential-canary' 'opaque'
run_probe 'http://user:secret@proxy.example:8080' 'url-shaped'

printf 'PASS\n'
