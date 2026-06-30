#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# shellcheck source=scripts/run-public-benchmark.sh
source "$ROOT/scripts/run-public-benchmark.sh"
# shellcheck source=scripts/lib/verification-gate.sh
source "$ROOT/scripts/lib/verification-gate.sh"

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

assert_gate_valid() {
  local file="$1"
  local label="$2"
  local missing weak
  missing="$(verification_gate_missing_fields "$file")"
  weak="$(verification_gate_tradeoff_flags "$file")"
  if [[ -n "$missing" || -n "$weak" ]]; then
    printf '[FAIL] %s: generated gate is invalid, missing=%s weak=%s\n' "$label" "$missing" "$weak" >&2
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

assert_gate_valid "$VERIFICATION_GATE" "lifecycle gate"
assert_contains "$VERIFICATION_GATE" "provider connectivity probe passes" "gate trigger precondition"
assert_contains "$VERIFICATION_GATE" "providerProbeStatus=passed" "gate precondition check"
assert_contains "$VERIFICATION_GATE" "providerProbeStatus is not passed" "gate invalid condition"
assert_contains "$VERIFICATION_GATE" "recording provider probe evidence" "gate freshness evidence"

VALIDATION_TARGET="glm-token-attribution"
DUAL_GLM_BASELINE_TOKENS="43540000"
validate_validation_target
public_benchmark_write_verification_gate

assert_gate_valid "$VERIFICATION_GATE" "glm token attribution gate"
assert_contains "$VERIFICATION_GATE" "GLM-5.2 provider token attribution" "glm gate target"
assert_contains "$VERIFICATION_GATE" "DUAL_GLM_BASELINE_TOKENS=43540000" "glm baseline source of truth"
assert_contains "$VERIFICATION_GATE" "provider=zhipu, model=glm-5.2, billingMode=subscription_token_only" "glm identity pass"
assert_contains "$VERIFICATION_GATE" "costAttribution.reliability=token_only" "glm token-only reliability"
assert_contains "$VERIFICATION_GATE" "providerCachePolicy.mode=token_policy_active" "glm cache policy pass"
assert_contains "$VERIFICATION_GATE" "GLM console token delta exceeding 3,000,000 before acceptance" "glm stop-loss"

DUAL_GLM_BASELINE_TOKENS=""
if (validate_validation_target) 2>/dev/null; then
  printf '[FAIL] glm-token-attribution target accepted missing baseline\n' >&2
  exit 1
fi
