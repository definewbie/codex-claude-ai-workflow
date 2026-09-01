#!/usr/bin/env bash
set -euo pipefail

ROOT="$(mktemp -d)"
trap 'rm -rf "$ROOT"' EXIT
mkdir -p "$ROOT/bin" "$ROOT/work" "$ROOT/tmp"
cp "$(dirname "$0")/e1-sandbox-contract-probe.sh" "$ROOT/work/"
printf '192.0.2.10\n' > "$ROOT/work/e1-lan-ip.txt"

cat > "$ROOT/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf 'CLAUDE_SANDBOX_DENIED\n' >&2
exit 97
EOF
cat > "$ROOT/bin/git" <<'EOF'
#!/usr/bin/env bash
printf 'CLAUDE_SANDBOX_DENIED\n' >&2
exit 97
EOF
cat > "$ROOT/bin/python3" <<'EOF'
#!/usr/bin/env bash
printf 'PermissionError: [Errno 1] Operation not permitted\n' >&2
exit 97
EOF
chmod +x "$ROOT/bin/curl" "$ROOT/bin/git" "$ROOT/bin/python3" "$ROOT/work/e1-sandbox-contract-probe.sh"

(
  cd "$ROOT/work"
  PATH="$ROOT/bin:$PATH" TMPDIR="$ROOT/tmp" HTTP_PROXY='credential-canary' \
    ./e1-sandbox-contract-probe.sh > stdout.txt 2> stderr.txt
)

grep -q '^E1_PROBE_COMPLETE$' "$ROOT/work/stdout.txt"
grep -q $'^HTTP_PROXY\tpresent_without_userinfo$' "$ROOT/work/e1-probe-output/environment.tsv"
[[ "$(wc -l < "$ROOT/work/e1-probe-output/results.tsv" | tr -d ' ')" = 7 ]]
[[ "$(grep -c $'\t97$' "$ROOT/work/e1-probe-output/results.tsv")" = 6 ]]
if grep -R -q 'credential-canary' "$ROOT/work/e1-probe-output" "$ROOT/work/stdout.txt" "$ROOT/work/stderr.txt"; then
  printf 'proxy value leaked into evidence\n' >&2
  exit 1
fi

rm -rf "$ROOT/work/e1-probe-output"
set +e
(
  cd "$ROOT/work"
  PATH="$ROOT/bin:$PATH" TMPDIR="$ROOT/tmp" HTTP_PROXY='http://user:secret@proxy.example:8080' \
    ./e1-sandbox-contract-probe.sh > userinfo.stdout.txt 2> userinfo.stderr.txt
)
userinfo_exit=$?
set -e
[[ "$userinfo_exit" = 73 ]]
grep -q $'^HTTP_PROXY\tpresent_with_userinfo$' "$ROOT/work/e1-probe-output/environment.tsv"
if grep -R -q 'user:secret\|proxy.example\|8080' "$ROOT/work/e1-probe-output" \
  "$ROOT/work/userinfo.stdout.txt" "$ROOT/work/userinfo.stderr.txt"; then
  printf 'proxy credential or endpoint leaked into evidence\n' >&2
  exit 1
fi

printf 'PASS\n'
