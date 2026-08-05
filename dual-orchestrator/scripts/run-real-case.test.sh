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

workflow_fixture="$tmp/terminal-delivery.json"
cat >"$workflow_fixture" <<'EOF'
{
  "workflowId": "agent-lifecycle-terminal-delivery-experiment",
  "version": 1
}
EOF
workflow_fixture="$(cd "$(dirname "$workflow_fixture")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "$workflow_fixture")")"

terminal_audit_workstream="$tmp/terminal-audit-workstream"
mkdir -p "$terminal_audit_workstream/domain"
cat >"$terminal_audit_workstream/domain/completion-audit.json" <<'EOF'
{
  "schemaVersion": 1,
  "lifecycleState": "ENDED",
  "status": "ready",
  "workflowStatus": "COMPLETED",
  "gaps": []
}
EOF
WS_DIR="$terminal_audit_workstream"
assert_eq "" "$(missing_artifacts)" \
  "terminal completion audit makes absent handoff artifacts not applicable"
assert_eq "PASS" "$(real_case_grade "COMPLETED" "true" "$(missing_artifacts)" "ACCEPT" "0" "")" \
  "terminal completion audit permits benchmark pass without handoff artifacts"
assert_eq "not_applicable" "$(completion_artifact_status "artifacts/next-stage/handoff.json")" \
  "terminal completion audit projects absent handoff as not applicable"

incomplete_audit_workstream="$tmp/incomplete-audit-workstream"
mkdir -p "$incomplete_audit_workstream/domain"
cat >"$incomplete_audit_workstream/domain/completion-audit.json" <<'EOF'
{
  "schemaVersion": 1,
  "lifecycleState": "ENDED",
  "status": "needs_review",
  "workflowStatus": "COMPLETED",
  "gaps": ["handoff.json is missing"]
}
EOF
WS_DIR="$incomplete_audit_workstream"
assert_eq "completion_audit_not_ready_or_invalid" "$(missing_artifacts)" \
  "completion audit gaps remain benchmark blocking"
assert_eq "FAIL" "$(real_case_grade "COMPLETED" "true" "$(missing_artifacts)" "ACCEPT" "0" "")" \
  "completion audit gaps keep benchmark failed"
assert_eq "missing" "$(completion_artifact_status "artifacts/next-stage/handoff.json")" \
  "required handoff remains visibly missing"

missing_audit_workstream="$tmp/missing-audit-workstream"
mkdir -p "$missing_audit_workstream"
WS_DIR="$missing_audit_workstream"
assert_eq "completion_audit_missing" "$(missing_artifacts)" \
  "missing completion audit fails visible"

invalid_audit_workstream="$tmp/invalid-audit-workstream"
mkdir -p "$invalid_audit_workstream/domain"
printf '{"status":"ready","gaps":[]' >"$invalid_audit_workstream/domain/completion-audit.json"
WS_DIR="$invalid_audit_workstream"
assert_eq "completion_audit_not_ready_or_invalid" "$(missing_artifacts)" \
  "invalid completion audit fails visible"

set +e
(
  WORKFLOW_PATH="$workflow_fixture"
  WORKFLOW_EXPLICIT=true
  VARIANT="full"
  VARIANT_EXPLICIT=true
  validate_workflow_selection
) >"$tmp/workflow-mutual-stdout.log" 2>"$tmp/workflow-mutual-stderr.log"
workflow_mutual_exit="$?"
set -e
assert_eq "1" "$workflow_mutual_exit" "external workflow and variant are mutually exclusive"
grep -q -- "--workflow and --variant are mutually exclusive" "$tmp/workflow-mutual-stderr.log" || {
  printf '[FAIL] workflow mutual-exclusion diagnostic was not emitted\n' >&2
  cat "$tmp/workflow-mutual-stderr.log" >&2
  exit 1
}

set +e
(
  WORKFLOW_PATH=""
  WORKFLOW_EXPLICIT=true
  VARIANT="full"
  VARIANT_EXPLICIT=false
  validate_workflow_selection
) >"$tmp/workflow-missing-path-stdout.log" 2>"$tmp/workflow-missing-path-stderr.log"
workflow_missing_path_exit="$?"
set -e
assert_eq "1" "$workflow_missing_path_exit" "external workflow requires a path"
grep -q -- "--workflow requires a path" "$tmp/workflow-missing-path-stderr.log" || {
  printf '[FAIL] missing workflow path did not fail before full fallback\n' >&2
  cat "$tmp/workflow-missing-path-stderr.log" >&2
  exit 1
}

workflow_case="$tmp/workflow-case"
mkdir -p "$workflow_case/logs"
(
  CASE_DIR="$workflow_case"
  WORKFLOW_PATH="$workflow_fixture"
  WORKFLOW_EXPLICIT=true
  VARIANT="full"
  VARIANT_EXPLICIT=false
  validate_workflow_selection
  dualctl() {
    [[ "$1" == "workflow" && "$2" == "validate" && "$3" == "$workflow_fixture" ]]
    printf 'Workflow definition is valid.\n'
    printf 'Path: %s\n' "$workflow_fixture"
    printf 'Workflow: agent-lifecycle-terminal-delivery-experiment@1\n'
  }
  validate_external_workflow
  expected_sha="$(shasum -a 256 "$workflow_fixture" | awk '{print $1}')"
  assert_eq "$expected_sha" "$WORKFLOW_SOURCE_SHA256" "external workflow SHA-256"
  assert_eq "agent-lifecycle-terminal-delivery-experiment" "$WORKFLOW_ID" "external workflow id"
  assert_eq "1" "$WORKFLOW_VERSION" "external workflow version"
  assert_eq "passed" "$WORKFLOW_VALIDATION_STATUS" "external workflow validation status"
  assert_eq "true" "$WORKFLOW_PATH_MATCHES_VALIDATED" "external workflow validated path match"

  REPO="$tmp/repo"
  TASK_FILE="$tmp/task.md"
  VERIFICATION_GATE="$tmp/gate.md"
  PROMPT_LANGUAGE="en"
  SKIP_BUILD=false
  BUILD_MODE="test"
  BUILD_STARTED_EPOCH=1
  BUILD_FINISHED_EPOCH=2
  PROVIDER_PROBE_ENABLED=1
  PROVIDER_PROBE_STATUS="passed"
  PROVIDER_PROBE_REASON="provider_probe_completed"
  PROVIDER_PROBE_EXIT_CODE=0
  PROVIDER_PROBE_TIMEOUT_SECONDS=1
  PROVIDER_PROBE_RESULT_PATH="$tmp/probe.json"
  PROVIDER_PROBE_STDERR_PATH="$tmp/probe.stderr"
  EXPECTED_RUNNER_KIND="acpx_claude_session"
  PROVIDER_PROBE_RUNNER_KIND="acpx_claude_session"
  PROVIDER_PROBE_PATH_MATCHES_EXPECTED=true
  JAR_PATH="$tmp/dualctl.jar"
  : >"$JAR_PATH"
  real_case_latest_jvm_input_epoch() { printf '1'; }
  real_case_artifact_freshness_status() { printf 'fresh'; }
  real_case_artifact_freshness_reason() { printf 'test_fresh'; }
  real_case_artifact_freshness_is_valid() { return 0; }
  real_case_file_mtime_epoch() { printf '2'; }
  real_case_provider_probe_is_valid() { return 0; }
  write_preflight_evidence
)

grep -q '"workflowSelection": "external"' "$workflow_case/preflight.json" || {
  printf '[FAIL] preflight did not record external workflow selection\n' >&2
  cat "$workflow_case/preflight.json" >&2
  exit 1
}
grep -q "\"workflowPath\": \"$workflow_fixture\"" "$workflow_case/preflight.json" || {
  printf '[FAIL] preflight did not record exact external workflow path\n' >&2
  cat "$workflow_case/preflight.json" >&2
  exit 1
}
grep -q '"workflowId": "agent-lifecycle-terminal-delivery-experiment"' "$workflow_case/preflight.json" || {
  printf '[FAIL] preflight did not record external workflow id\n' >&2
  cat "$workflow_case/preflight.json" >&2
  exit 1
}
grep -q '"workflowVersion": "1"' "$workflow_case/preflight.json" || {
  printf '[FAIL] preflight did not record external workflow version\n' >&2
  cat "$workflow_case/preflight.json" >&2
  exit 1
}
grep -q '"workflowPathMatchesValidated": true' "$workflow_case/preflight.json" || {
  printf '[FAIL] preflight did not record exact workflow path validation\n' >&2
  cat "$workflow_case/preflight.json" >&2
  exit 1
}
expected_workflow_sha="$(shasum -a 256 "$workflow_fixture" | awk '{print $1}')"
grep -q "\"workflowSourceSha256\": \"$expected_workflow_sha\"" "$workflow_case/preflight.json" || {
  printf '[FAIL] preflight did not record external workflow SHA-256\n' >&2
  cat "$workflow_case/preflight.json" >&2
  exit 1
}

(
  CASE_DIR="$tmp/start-workflow-case"
  mkdir -p "$CASE_DIR/logs"
  REPO="$tmp/repo"
  TASK_FILE="$tmp/task.md"
  MAX_STEPS=80
  PROMPT_LANGUAGE="en"
  WORKFLOW_PATH="$workflow_fixture"
  VARIANT=""
  captured_args="$tmp/start-workflow-args.txt"
  dualctl() {
    printf '%s\n' "$@" >"$captured_args"
    printf 'Workstream: ws-external-workflow\n'
    printf 'Workspace: %s\n' "$tmp/workspace"
    printf 'Ledger: %s\n' "$tmp/workspace/.agent/workstreams/ws-external-workflow"
  }
  start_workstream
  assert_eq "1" "$(grep -cx -- '--workflow' "$captured_args")" "external workflow flag count"
  grep -Fxq "$workflow_fixture" "$captured_args"
  if grep -Fxq -- '--workflow-variant' "$captured_args"; then
    printf '[FAIL] external workflow start fell back to a bundled variant\n' >&2
    exit 1
  fi
)

set +e
(
  CASE_DIR="$tmp/workflow-path-mismatch-case"
  mkdir -p "$CASE_DIR/logs"
  WORKFLOW_PATH="$workflow_fixture"
  dualctl() {
    printf 'Workflow definition is valid.\n'
    printf 'Path: %s\n' "$tmp/different-workflow.json"
    printf 'Workflow: agent-lifecycle-terminal-delivery-experiment@1\n'
  }
  validate_external_workflow
) >"$tmp/workflow-path-mismatch-stdout.log" 2>"$tmp/workflow-path-mismatch-stderr.log"
workflow_path_mismatch_exit="$?"
set -e
assert_eq "1" "$workflow_path_mismatch_exit" "validated workflow path mismatch fails closed"

(
  provider_probe_called=false
  validate_external_workflow() { return 1; }
  write_preflight_evidence() { printf 'workflow-preflight-failed\n'; }
  run_provider_probe() { provider_probe_called=true; }
  if run_execution_preflight; then
    printf '[FAIL] invalid external workflow preflight unexpectedly succeeded\n' >&2
    exit 1
  fi
  assert_eq "false" "$provider_probe_called" "provider probe blocked by workflow validation failure"
)

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
grep -q "sessions-reap --workdir $interrupted_workspace --workstream $interrupted_workstream" \
  "$interrupted_case/logs/session-quiescence-interruption.log" || {
  printf '[FAIL] interruption did not close durable workstream sessions\n' >&2
  cat "$interrupted_case/logs/session-quiescence-interruption.log" >&2
  exit 1
}

WORKSPACE_DIR=""
WORKSTREAM_ID=""
if quiesce_interrupted_workstream >/dev/null 2>&1; then
  printf '[FAIL] interruption quiescence succeeded without durable workstream identity\n' >&2
  exit 1
fi

CASE_DIR="$interrupted_case"
WORKSPACE_DIR="$interrupted_workspace"
WORKSTREAM_ID="$interrupted_workstream"
dualctl() {
  if [[ "$1" == "recovery" ]]; then
    return 9
  fi
  printf '%s\n' "$*"
}
if quiesce_interrupted_workstream; then
  printf '[FAIL] interruption quiescence hid stale recovery failure\n' >&2
  exit 1
fi
grep -q "sessions-reap --workdir $interrupted_workspace --workstream $interrupted_workstream" \
  "$interrupted_case/logs/session-quiescence-interruption.log" || {
  printf '[FAIL] stale recovery failure bypassed session close\n' >&2
  cat "$interrupted_case/logs/session-quiescence-interruption.log" >&2
  exit 1
}

natural_case="$tmp/natural-stop-loss-case"
natural_workspace="$tmp/natural-stop-loss-workspace"
natural_workstream="ws-natural-stop-loss"
mkdir -p "$natural_case/logs"
CASE_DIR="$natural_case"
WORKSPACE_DIR="$natural_workspace"
WORKSTREAM_ID="$natural_workstream"
MAX_STEPS=1
MAX_GATES=1

dualctl() {
  if [[ "$1" == "autorun" ]]; then
    return 7
  fi
  if [[ "$1" == "status" ]]; then
    printf '  Status: WAITING_FOR_RECOVERY\n'
    return 0
  fi
  printf '%s\n' "$*"
}

if run_autorun; then
  printf '[FAIL] natural autorun stop-loss unexpectedly succeeded\n' >&2
  exit 1
fi
assert_eq "WAITING_FOR_RECOVERY" "$FINAL_STATUS" "natural stop-loss preserves workflow status"
grep -q "sessions-reap --workdir $natural_workspace --workstream $natural_workstream" \
  "$natural_case/logs/session-quiescence-autorun-stop-loss.log" || {
  printf '[FAIL] natural stop-loss did not close durable workstream sessions\n' >&2
  cat "$natural_case/logs/session-quiescence-autorun-stop-loss.log" >&2
  exit 1
}

dualctl() {
  if [[ "$1" == "autorun" ]]; then
    return 7
  fi
  if [[ "$1" == "status" ]]; then
    printf '  Status: WAITING_FOR_RECOVERY\n'
    return 0
  fi
  if [[ "$1" == "sessions-reap" ]]; then
    return 8
  fi
  printf '%s\n' "$*"
}
if run_autorun; then
  printf '[FAIL] session-close failure unexpectedly succeeded\n' >&2
  exit 1
fi
assert_eq "SESSION_QUIESCENCE_FAILED" "$FINAL_STATUS" \
  "natural stop-loss surfaces session quiescence failure"
