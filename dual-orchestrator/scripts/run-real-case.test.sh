#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# shellcheck source=scripts/run-real-case.sh
source "$ROOT/scripts/run-real-case.sh"

assert_eq() {
  local expected="$1"
  local actual="$2"
  local label="$3"
  if [[ "$expected" != "$actual" ]]; then
    printf '[FAIL] %s: expected %s, got %s\n' "$label" "$expected" "$actual" >&2
    exit 1
  fi
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

weak_gate="$tmp/weak-verification-gate.md"
cat >"$weak_gate" <<'EOF'
# Verification Gate

- Target: prove provider-cache-aware
- Source of truth: metrics
- Trigger precondition: should trigger because cachedRead is high
- How precondition is checked before run: run it and see
- PASS: measure_only is visible
- FAIL: required fields do not match
- INVALID: pricing_unknown
- Stop-loss: stop if it looks bad
- Run artifact freshness: use --skip-build
EOF

set +e
( validate_verification_gate "$weak_gate" ) >"$tmp/stdout.log" 2>"$tmp/stderr.log"
exit_code="$?"
set -e

assert_eq "1" "$exit_code" "weak verification gate rejected by run-real-case entrypoint"
grep -q "verification gate has weak proof fields" "$tmp/stderr.log" || {
  printf '[FAIL] expected weak-proof diagnostic in stderr\n' >&2
  cat "$tmp/stderr.log" >&2
  exit 1
}
