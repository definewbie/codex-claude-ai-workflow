#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# shellcheck source=scripts/lib/verification-gate.sh
source "$ROOT/scripts/lib/verification-gate.sh"

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

complete="$tmp/complete.md"
cat >"$complete" <<'EOF'
# Verification Gate

- Target: prove one thing
- Source of truth: run.json
- Trigger precondition: provider configured
- How precondition is checked before run: inspect typed preflight
- PASS: required fields match
- FAIL: required fields do not match
- INVALID: freshness is unproven
- Stop-loss: stop before provider if preflight fails
- Run artifact freshness: preflight proves fresh jar
EOF

assert_eq "true" "$(verification_gate_is_complete "$complete" && printf true || printf false)" "complete gate"
assert_eq "run.json" "$(verification_gate_field_value "$complete" "Source of truth")" "field value"
assert_eq "" "$(verification_gate_missing_fields "$complete")" "no missing fields"

missing="$tmp/missing.md"
cat >"$missing" <<'EOF'
# Verification Gate

- Target: prove one thing
- Source of truth: run.json
- Trigger precondition: provider configured
EOF

assert_eq \
  "How precondition is checked before run|PASS|FAIL|INVALID|Stop-loss|Run artifact freshness" \
  "$(verification_gate_missing_fields "$missing")" \
  "missing fields"

todo="$tmp/todo.md"
cat >"$todo" <<'EOF'
# Verification Gate

- Target: TODO fill this
- Source of truth: run.json
- Trigger precondition: TBD
- How precondition is checked before run: inspect typed preflight
- PASS: required fields match
- FAIL: required fields do not match
- INVALID: freshness is unproven
- Stop-loss: stop before provider if preflight fails
- Run artifact freshness: preflight proves fresh jar
EOF

assert_eq \
  "Target|Trigger precondition" \
  "$(verification_gate_missing_fields "$todo")" \
  "todo fields"
