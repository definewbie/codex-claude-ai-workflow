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

(trap - EXIT; exec sleep 30) &
AUTORUN_PID="$!"
stopped_autorun_pid="$AUTORUN_PID"
stop_autorun_process
assert_eq "" "$AUTORUN_PID" "autorun pid cleared after explicit stop"
if kill -0 "$stopped_autorun_pid" >/dev/null 2>&1; then
  printf '[FAIL] explicit autorun process was not stopped before recovery\n' >&2
  exit 1
fi

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

interrupted_case="$tmp/interrupted-case"
interrupted_workspace="$tmp/interrupted-workspace"
interrupted_workstream="ws-interrupted-test"
mkdir -p "$interrupted_case/logs"
mkdir -p "$interrupted_workspace/.agent/workstreams/$interrupted_workstream/runs"
cat >"$interrupted_case/logs/start.log" <<EOF
Workstream: $interrupted_workstream
Workspace: $interrupted_workspace
Ledger: $interrupted_workspace/.agent/workstreams/$interrupted_workstream
EOF
: >"$interrupted_case/logs/original-git-status-short.log"

CASE_DIR="$interrupted_case"
WORKSTREAM_ID=""
WORKSPACE_DIR=""
WS_DIR=""
STARTED_AT="2026-07-29T00:00:00Z"
FINAL_STATUS="UNKNOWN"
REPO="$tmp/original-repo"
TASK_FILE="$tmp/task.md"
VARIANT="full"
PROMPT_LANGUAGE="en"
UPDATE_BENCHMARK_INDEX=false

collect_evidence() {
  [[ "$WORKSTREAM_ID" == "$interrupted_workstream" ]]
  [[ "$WORKSPACE_DIR" == "$interrupted_workspace" ]]
  [[ "$WS_DIR" == "$interrupted_workspace/.agent/workstreams/$interrupted_workstream" ]]
  [[ -f "$CASE_DIR/logs/interruption-recovery.log" ]]
  cat >"$CASE_DIR/logs/runs.log" <<'EOF'
RUN STATUS
run-1 COMPLETED
EOF
}

dualctl() {
  if [[ -n "${interrupt_autorun_pid:-}" ]] && kill -0 "$interrupt_autorun_pid" >/dev/null 2>&1; then
    printf '[FAIL] interruption recovery started before the autorun process stopped\n' >&2
    return 1
  fi
  printf '%s\n' "$*"
}

(trap - EXIT; exec sleep 30) &
AUTORUN_PID="$!"
interrupt_autorun_pid="$AUTORUN_PID"
set +e
( handle_interrupt ) >"$tmp/interrupted-stdout.log" 2>"$tmp/interrupted-stderr.log"
interrupt_exit="$?"
set -e

assert_eq "130" "$interrupt_exit" "interrupt handler exit code"
if kill -0 "$interrupt_autorun_pid" >/dev/null 2>&1; then
  printf '[FAIL] interrupt handler left the autorun process alive\n' >&2
  exit 1
fi
grep -q "\"workstream\": \"$interrupted_workstream\"" "$interrupted_case/summary.json" || {
  printf '[FAIL] interrupted summary did not recover workstream identity\n' >&2
  cat "$interrupted_case/summary.json" >&2
  exit 1
}
grep -q "\"effective_workdir\": \"$interrupted_workspace\"" "$interrupted_case/summary.json" || {
  printf '[FAIL] interrupted summary did not recover workspace identity\n' >&2
  cat "$interrupted_case/summary.json" >&2
  exit 1
}
grep -q '"agent_run_count": 1' "$interrupted_case/summary.json" || {
  printf '[FAIL] interrupted summary did not project collected run evidence\n' >&2
  cat "$interrupted_case/summary.json" >&2
  exit 1
}
grep -q -- "- Workstream: $interrupted_workstream" "$interrupted_case/report.md" || {
  printf '[FAIL] interrupted report did not recover workstream identity\n' >&2
  cat "$interrupted_case/report.md" >&2
  exit 1
}
grep -q "recovery stale --workdir $interrupted_workspace --terminate-process" \
  "$interrupted_case/logs/interruption-recovery.log" || {
  printf '[FAIL] interruption did not invoke quiescent stale recovery before evidence collection\n' >&2
  cat "$interrupted_case/logs/interruption-recovery.log" >&2
  exit 1
}

WORKSPACE_DIR=""
WORKSTREAM_ID=""
if quiesce_interrupted_workstream >/dev/null 2>&1; then
  printf '[FAIL] interruption quiescence succeeded without durable workstream identity\n' >&2
  exit 1
fi
