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

# provider-model-attribution: generic L1/L2 gate, no baseline required
VALIDATION_TARGET="provider-model-attribution"
DUAL_GLM_BASELINE_TOKENS=""
validate_validation_target
public_benchmark_write_verification_gate
assert_gate_valid "$VERIFICATION_GATE" "provider-model-attribution gate"
assert_contains "$VERIFICATION_GATE" "Provider/Model usage & economics attribution" "pma gate title"
assert_contains "$VERIFICATION_GATE" "measurementType=PROVIDER_RUN_USAGE (not an artifact estimate)" "pma L1 pass criterion"
assert_contains "$VERIFICATION_GATE" "expectedRunnerKind/providerProbeRunnerKind/providerProbePathMatchesExpected" "pma probe-path evidence"

# zhipu-glm-console-calibration: direct L3 target, requires baseline
VALIDATION_TARGET="zhipu-glm-console-calibration"
DUAL_GLM_BASELINE_TOKENS="43540000"
validate_validation_target
public_benchmark_write_verification_gate
assert_gate_valid "$VERIFICATION_GATE" "zhipu-glm-console-calibration gate"
assert_contains "$VERIFICATION_GATE" "Zhipu GLM console calibration" "calibration gate title"

# deprecated alias glm-token-attribution normalizes to zhipu-glm-console-calibration
VALIDATION_TARGET="glm-token-attribution"
DUAL_GLM_BASELINE_TOKENS="43540000"
validate_validation_target
[[ "$VALIDATION_TARGET" == "zhipu-glm-console-calibration" ]] || {
  printf '[FAIL] glm-token-attribution alias did not normalize (got: %s)\n' "$VALIDATION_TARGET" >&2
  exit 1
}

# invalid target rejected
VALIDATION_TARGET="bogus-target"
if (validate_validation_target) 2>/dev/null; then
  printf '[FAIL] invalid validation target accepted\n' >&2
  exit 1
fi

# baseline missing for zhipu-glm-console-calibration rejected
VALIDATION_TARGET="zhipu-glm-console-calibration"
DUAL_GLM_BASELINE_TOKENS=""
if (validate_validation_target) 2>/dev/null; then
  printf '[FAIL] zhipu-glm-console-calibration target accepted missing baseline\n' >&2
  exit 1
fi
