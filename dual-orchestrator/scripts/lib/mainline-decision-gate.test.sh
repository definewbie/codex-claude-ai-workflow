#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# shellcheck source=scripts/lib/mainline-decision-gate.sh
source "$ROOT/scripts/lib/mainline-decision-gate.sh"

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
# Mainline Decision Gate

- Target phase: P0.5
- Boundary owner: TokenEconomics
- Source of truth being changed or strengthened: ProviderModelIdentity + CostAttribution
- Drift path being removed or centralized: provider-specific cost rules move into ProviderPricingCatalog
- Explicit non-goals: no real provider run
- Proof required before claiming completion: billing CSV fixture and CLI calibration output match official pricing
- Remaining gaps that must stay visible: real bill files are unavailable in this environment
EOF

assert_eq "true" "$(mainline_decision_gate_is_complete "$complete" && printf true || printf false)" "complete gate"
assert_eq "TokenEconomics" "$(mainline_decision_gate_field_value "$complete" "Boundary owner")" "field value"
assert_eq "" "$(mainline_decision_gate_missing_fields "$complete")" "no missing fields"
assert_eq "" "$(mainline_decision_gate_tradeoff_flags "$complete")" "no tradeoff flags"

missing="$tmp/missing.md"
cat >"$missing" <<'EOF'
# Mainline Decision Gate

- Target phase: P0
- Boundary owner: AgentExecutionAttempt
- Source of truth being changed or strengthened: AgentExecutionOutcome
EOF

assert_eq \
  "Drift path being removed or centralized|Explicit non-goals|Proof required before claiming completion|Remaining gaps that must stay visible" \
  "$(mainline_decision_gate_missing_fields "$missing")" \
  "missing fields"

todo="$tmp/todo.md"
cat >"$todo" <<'EOF'
# Mainline Decision Gate

- Target phase: TODO choose phase
- Boundary owner: AgentExecutionAttempt
- Source of truth being changed or strengthened: AgentExecutionOutcome
- Drift path being removed or centralized: runtime writer split
- Explicit non-goals: no provider run
- Proof required before claiming completion: TBD
- Remaining gaps that must stay visible: real replay missing
EOF

assert_eq \
  "Target phase|Proof required before claiming completion" \
  "$(mainline_decision_gate_missing_fields "$todo")" \
  "todo fields"

tradeoff="$tmp/tradeoff.md"
cat >"$tradeoff" <<'EOF'
# Mainline Decision Gate

- Target phase: P1
- Boundary owner: Projection read model
- Source of truth being changed or strengthened: add one warning to trace
- Drift path being removed or centralized: none
- Explicit non-goals: no UI sweep
- Proof required before claiming completion: rerun and看看
- Remaining gaps that must stay visible: operator API still unknown
EOF

assert_eq \
  "Source of truth being changed or strengthened|Drift path being removed or centralized|Proof required before claiming completion" \
  "$(mainline_decision_gate_tradeoff_flags "$tradeoff")" \
  "tradeoff flags"
assert_eq "false" "$(mainline_decision_gate_is_complete "$tradeoff" && printf true || printf false)" "tradeoff gate rejected"

case_insensitive="$tmp/case-insensitive.md"
cat >"$case_insensitive" <<'EOF'
# Mainline Decision Gate

- Target phase: P1
- Boundary owner: AgentExecutionAttempt
- Source of truth being changed or strengthened: Add one WARNING to trace output
- Drift path being removed or centralized: N/A
- Explicit non-goals: no provider run
- Proof required before claiming completion: tests green
- Remaining gaps that must stay visible: real replay missing
EOF

assert_eq \
  "Source of truth being changed or strengthened|Drift path being removed or centralized" \
  "$(mainline_decision_gate_tradeoff_flags "$case_insensitive")" \
  "case-insensitive tradeoff flags"
assert_eq "false" "$(mainline_decision_gate_is_complete "$case_insensitive" && printf true || printf false)" "case-insensitive tradeoff rejected"

invalid_phase="$tmp/invalid-phase.md"
cat >"$invalid_phase" <<'EOF'
# Mainline Decision Gate

- Target phase: Maybe
- Boundary owner: AgentExecutionAttempt
- Source of truth being changed or strengthened: AgentExecutionOutcome finalizer
- Drift path being removed or centralized: legacy handler outcome writers move behind finalizer
- Explicit non-goals: no provider run
- Proof required before claiming completion: boundary test covers adapter signal preservation
- Remaining gaps that must stay visible: real replay missing
EOF

assert_eq "Target phase" "$(mainline_decision_gate_tradeoff_flags "$invalid_phase")" "invalid phase rejected"
