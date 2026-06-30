#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# shellcheck source=scripts/run-public-benchmark.sh
source "$ROOT/scripts/run-public-benchmark.sh"

assert_contains() {
  local file="$1"
  local expected="$2"
  local label="$3"
  if ! grep -Fq "$expected" "$file"; then
    printf '[FAIL] %s: missing "%s"\n' "$label" "$expected" >&2
    printf '--- file ---\n' >&2
    cat "$file" >&2
    exit 1
  fi
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

TMPDIR="$tmp"
CASE_ID="public-issue-flow-review-state"
DEST_DIR="$tmp/repo"
public_benchmark_write_verification_gate

assert_contains "$VERIFICATION_GATE" "provider connectivity probe passes" "gate trigger precondition"
assert_contains "$VERIFICATION_GATE" "providerProbeStatus=passed" "gate precondition check"
assert_contains "$VERIFICATION_GATE" "providerProbeStatus is failed/timeout" "gate invalid condition"
assert_contains "$VERIFICATION_GATE" "recording provider probe evidence" "gate freshness evidence"
