#!/usr/bin/env bash
#
# run-real-case.sh — Run a real repository through dual-orchestrator lifecycle.
#
# This script is intentionally small: it starts one workstream, delegates
# cross-gate driving to dualctl autorun, and writes a report. It does not commit,
# push, create PRs, or delete the original repository.
#
# Usage:
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --variant full
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --prompt-language zh-CN
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --verification-gate /path/to/gate.md
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --cleanup-worktree
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --update-benchmark-index
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_PATH="$PROJECT_ROOT/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
REPORT_ROOT="$PROJECT_ROOT/docs/testing/real-case-reports"

# shellcheck source=scripts/lib/real-case-grade.sh
source "$PROJECT_ROOT/scripts/lib/real-case-grade.sh"
# shellcheck source=scripts/lib/real-case-invalid.sh
source "$PROJECT_ROOT/scripts/lib/real-case-invalid.sh"
# shellcheck source=scripts/lib/real-case-preflight.sh
source "$PROJECT_ROOT/scripts/lib/real-case-preflight.sh"
# shellcheck source=scripts/lib/verification-gate.sh
source "$PROJECT_ROOT/scripts/lib/verification-gate.sh"

REPO=""
TASK_FILE=""
VERIFICATION_GATE=""
VARIANT="full"
PROMPT_LANGUAGE="auto"
MAX_STEPS=80
MAX_GATES=12
SKIP_BUILD=false
CLEANUP_WORKTREE=false
UPDATE_BENCHMARK_INDEX=false

WORKSTREAM_ID=""
WORKSPACE_DIR=""
WS_DIR=""
CASE_DIR=""
FINAL_STATUS="UNKNOWN"
STARTED_AT=""
FINISHED_AT=""
BUILD_STARTED_EPOCH=""
BUILD_FINISHED_EPOCH=""
BUILD_MODE=""
PROVIDER_PROBE_ENABLED="${DUAL_REAL_CASE_PROVIDER_PROBE:-1}"
PROVIDER_PROBE_STATUS="not_run"
PROVIDER_PROBE_REASON="not_run"
PROVIDER_PROBE_EXIT_CODE=0
PROVIDER_PROBE_TIMEOUT_SECONDS="${DUAL_REAL_CASE_PROVIDER_PROBE_TIMEOUT_SECONDS:-180}"
PROVIDER_PROBE_RESULT_PATH=""
PROVIDER_PROBE_STDERR_PATH=""

usage() {
  cat <<'EOF'
Usage:
  run-real-case.sh --repo <repo> --task-file <task.md> [options]

Options:
  --repo <path>             Target git repository.
  --task-file <path>        Requirement markdown file.
  --verification-gate <path>
                            Required markdown gate proving target, source of truth,
                            preconditions, pass/fail/invalid criteria, stop-loss,
                            and artifact freshness before provider execution.
  --variant <contract|full>
                            Workflow variant. Default: full.
  --prompt-language <auto|en|zh-CN>
                            Human-readable Agent output language. Default: auto.
  --max-steps <n>           Max nodes per run/approve. Default: 80.
  --max-gates <n>           Max human gates to auto-approve. Default: 12.
  --skip-build              Do not run Maven package before starting. This is
                            rejected for real provider runs because artifact
                            freshness cannot be proven.
  --cleanup-worktree        Remove the created worktree after report generation.
  --update-benchmark-index  Refresh docs/testing/real-case-benchmarks/benchmark-index.md after writing this case report.
  -h, --help                Show this help.

The script auto-approves human gates. Use it for lifecycle validation, not final
business acceptance.
EOF
}

log() { printf '[REAL-CASE] %s\n' "$*"; }
fail() { printf '[REAL-CASE][FAIL] %s\n' "$*" >&2; }

dualctl() {
  java -jar "$JAR_PATH" "$@"
}

handle_interrupt() {
  FINAL_STATUS="INTERRUPTED"
  log "Interrupted. Collecting current evidence before exit..."
  collect_evidence || true
  write_report || true
  update_benchmark_index || true
  if [[ -n "$WORKSPACE_DIR" && -n "$WORKSTREAM_ID" ]]; then
    log "Inspect: java -jar \"$JAR_PATH\" status --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
    log "Resume:  java -jar \"$JAR_PATH\" resume --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
  fi
  exit 130
}

abs_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s\n' "$path"
  else
    printf '%s/%s\n' "$(pwd)" "$path"
  fi
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --repo)
        shift
        REPO="${1:-}"
        ;;
      --task-file)
        shift
        TASK_FILE="${1:-}"
        ;;
      --verification-gate)
        shift
        VERIFICATION_GATE="${1:-}"
        ;;
      --variant)
        shift
        VARIANT="${1:-}"
        ;;
      --prompt-language)
        shift
        PROMPT_LANGUAGE="${1:-}"
        ;;
      --max-steps)
        shift
        MAX_STEPS="${1:-}"
        ;;
      --max-gates)
        shift
        MAX_GATES="${1:-}"
        ;;
      --skip-build)
        SKIP_BUILD=true
        ;;
      --cleanup-worktree)
        CLEANUP_WORKTREE=true
        ;;
      --update-benchmark-index)
        UPDATE_BENCHMARK_INDEX=true
        ;;
      -h|--help)
        usage
        exit 0
        ;;
      *)
        fail "Unknown argument: $1"
        usage
        exit 1
        ;;
    esac
    shift
  done
}

validate_inputs() {
  [[ -n "$REPO" ]] || { fail "--repo is required"; exit 1; }
  [[ -n "$TASK_FILE" ]] || { fail "--task-file is required"; exit 1; }
  [[ -n "$VERIFICATION_GATE" ]] || { fail "--verification-gate is required before running real provider cases"; exit 1; }

  REPO="$(abs_path "$REPO")"
  TASK_FILE="$(abs_path "$TASK_FILE")"
  VERIFICATION_GATE="$(abs_path "$VERIFICATION_GATE")"

  [[ -d "$REPO" ]] || { fail "repo not found: $REPO"; exit 1; }
  [[ -f "$TASK_FILE" ]] || { fail "task file not found: $TASK_FILE"; exit 1; }
  [[ -f "$VERIFICATION_GATE" ]] || { fail "verification gate not found: $VERIFICATION_GATE"; exit 1; }
  validate_verification_gate "$VERIFICATION_GATE"
  git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
    fail "repo must be a git repository: $REPO"
    exit 1
  }
  command -v acpx >/dev/null 2>&1 || {
    fail "acpx not found. Initialize acpx sessions before running a real case."
    exit 1
  }
  case "$VARIANT" in
    contract|full) ;;
    *) fail "--variant must be contract or full"; exit 1 ;;
  esac
  case "$PROMPT_LANGUAGE" in
    auto|en|zh-CN|zh|zh-cn) ;;
    *) fail "--prompt-language must be auto, en, or zh-CN"; exit 1 ;;
  esac
}

validate_verification_gate() {
  local file="$1"
  local missing
  missing="$(verification_gate_missing_fields "$file")"
  if [[ -n "$missing" ]]; then
    fail "verification gate is incomplete: ${missing//|/ }"
    fail "Gate file: $file"
    exit 1
  fi
}

build_project() {
  BUILD_STARTED_EPOCH="$(date '+%s')"
  if [[ "$SKIP_BUILD" == true ]]; then
    [[ -f "$JAR_PATH" ]] || {
      fail "dualctl jar not found: $JAR_PATH"
      exit 1
    }
    BUILD_MODE="skip_build"
    BUILD_FINISHED_EPOCH="$(date '+%s')"
    log "Skipping build; using $JAR_PATH"
    return
  fi

  BUILD_MODE="maven_package"
  log "Building dual-cli and dual-server..."
  (cd "$PROJECT_ROOT" && mvn -pl dual-cli,dual-server -am package)
  BUILD_FINISHED_EPOCH="$(date '+%s')"
}

run_provider_probe() {
  PROVIDER_PROBE_RESULT_PATH="$CASE_DIR/provider-probe-result.json"
  PROVIDER_PROBE_STDERR_PATH="$CASE_DIR/provider-probe-stderr.log"
  local prompt_path="$CASE_DIR/provider-probe-prompt.txt"

  if [[ "$PROVIDER_PROBE_ENABLED" == "0" || "$PROVIDER_PROBE_ENABLED" == "false" ]]; then
    PROVIDER_PROBE_STATUS="skipped"
    PROVIDER_PROBE_REASON="$(real_case_provider_probe_reason "$PROVIDER_PROBE_STATUS")"
    return 0
  fi

  command -v claude >/dev/null 2>&1 || {
    PROVIDER_PROBE_STATUS="failed"
    PROVIDER_PROBE_REASON="claude_cli_not_found"
    PROVIDER_PROBE_EXIT_CODE=127
    return 1
  }

  printf 'Provider connectivity probe. Reply exactly: OK. Do not use tools.\n' >"$prompt_path"
  local -a command=(
    claude
    --print
    --input-format text
    --output-format json
    --bare
    --safe-mode
    --no-session-persistence
    --permission-mode dontAsk
    --disallowedTools Bash,Read,Edit,Write,NotebookEdit,WebFetch,WebSearch
  )

  "${command[@]}" <"$prompt_path" >"$PROVIDER_PROBE_RESULT_PATH" 2>"$PROVIDER_PROBE_STDERR_PATH" &
  local pid="$!"
  local started now elapsed
  started="$(date '+%s')"
  while kill -0 "$pid" >/dev/null 2>&1; do
    now="$(date '+%s')"
    elapsed="$((now - started))"
    if [[ "$elapsed" -ge "$PROVIDER_PROBE_TIMEOUT_SECONDS" ]]; then
      kill "$pid" >/dev/null 2>&1 || true
      sleep 1
      kill -9 "$pid" >/dev/null 2>&1 || true
      wait "$pid" >/dev/null 2>&1 || true
      PROVIDER_PROBE_EXIT_CODE=124
      PROVIDER_PROBE_STATUS="timeout"
      PROVIDER_PROBE_REASON="$(real_case_provider_probe_reason "$PROVIDER_PROBE_STATUS")"
      return 1
    fi
    sleep 1
  done
  wait "$pid" || PROVIDER_PROBE_EXIT_CODE="$?"
  PROVIDER_PROBE_EXIT_CODE="${PROVIDER_PROBE_EXIT_CODE:-0}"
  PROVIDER_PROBE_STATUS="$(real_case_provider_probe_status "$PROVIDER_PROBE_EXIT_CODE" "$PROVIDER_PROBE_RESULT_PATH")"
  PROVIDER_PROBE_REASON="$(real_case_provider_probe_reason "$PROVIDER_PROBE_STATUS")"
  real_case_provider_probe_is_valid "$PROVIDER_PROBE_STATUS"
}

write_preflight_evidence() {
  local preflight="$CASE_DIR/preflight.json"
  local freshness_status freshness_reason freshness_valid jar_mtime latest_input_mtime
  latest_input_mtime="$(real_case_latest_jvm_input_epoch "$PROJECT_ROOT")"
  freshness_status="$(real_case_artifact_freshness_status "$SKIP_BUILD" "$JAR_PATH" "$BUILD_STARTED_EPOCH" "$latest_input_mtime")"
  freshness_reason="$(real_case_artifact_freshness_reason "$freshness_status")"
  if real_case_artifact_freshness_is_valid "$freshness_status"; then
    freshness_valid=true
  else
    freshness_valid=false
  fi
  jar_mtime="$(real_case_file_mtime_epoch "$JAR_PATH")"

  {
    echo "{"
    echo "  \"schemaVersion\": 1,"
    echo "  \"repo\": \"$(json_escape "$REPO")\","
    echo "  \"taskFile\": \"$(json_escape "$TASK_FILE")\","
    echo "  \"verificationGate\": \"$(json_escape "$VERIFICATION_GATE")\","
    echo "  \"variant\": \"$(json_escape "$VARIANT")\","
    echo "  \"promptLanguage\": \"$(json_escape "$PROMPT_LANGUAGE")\","
    echo "  \"acpxAvailable\": true,"
    echo "  \"buildMode\": \"$(json_escape "$BUILD_MODE")\","
    echo "  \"buildStartedEpoch\": ${BUILD_STARTED_EPOCH:-0},"
    echo "  \"buildFinishedEpoch\": ${BUILD_FINISHED_EPOCH:-0},"
    echo "  \"jarPath\": \"$(json_escape "$JAR_PATH")\","
    echo "  \"jarMtimeEpoch\": ${jar_mtime:-0},"
    echo "  \"latestJvmInputMtimeEpoch\": ${latest_input_mtime:-0},"
    echo "  \"artifactFreshnessStatus\": \"$(json_escape "$freshness_status")\","
    echo "  \"artifactFreshnessReason\": \"$(json_escape "$freshness_reason")\","
    echo "  \"artifactFreshnessProven\": $freshness_valid,"
    echo "  \"providerProbeEnabled\": \"$(json_escape "$PROVIDER_PROBE_ENABLED")\","
    echo "  \"providerProbeStatus\": \"$(json_escape "$PROVIDER_PROBE_STATUS")\","
    echo "  \"providerProbeReason\": \"$(json_escape "$PROVIDER_PROBE_REASON")\","
    echo "  \"providerProbeExitCode\": ${PROVIDER_PROBE_EXIT_CODE:-0},"
    echo "  \"providerProbeTimeoutSeconds\": ${PROVIDER_PROBE_TIMEOUT_SECONDS:-0},"
    echo "  \"providerProbeResultPath\": \"$(json_escape "$PROVIDER_PROBE_RESULT_PATH")\","
    echo "  \"providerProbeStderrPath\": \"$(json_escape "$PROVIDER_PROBE_STDERR_PATH")\""
    echo "}"
  } >"$preflight"

  if [[ "$freshness_valid" != true ]]; then
    fail "run artifact freshness is not proven: $freshness_status ($freshness_reason)"
    fail "Preflight evidence: $preflight"
    FINAL_STATUS="INVALID_ARTIFACT_FRESHNESS"
    write_report
    update_benchmark_index
    exit 1
  fi
  if ! real_case_provider_probe_is_valid "$PROVIDER_PROBE_STATUS"; then
    fail "provider preflight failed: $PROVIDER_PROBE_STATUS ($PROVIDER_PROBE_REASON)"
    fail "Preflight evidence: $preflight"
    FINAL_STATUS="INVALID_PROVIDER_PREFLIGHT"
    write_report
    update_benchmark_index
    exit 1
  fi
}

init_case_dir() {
  STARTED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  local stamp
  stamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  mkdir -p "$REPORT_ROOT"
  CASE_DIR="$REPORT_ROOT/$stamp"
  mkdir -p "$CASE_DIR/logs"
  cp "$TASK_FILE" "$CASE_DIR/task.md"
  cp "$VERIFICATION_GATE" "$CASE_DIR/verification-gate.md"
}

start_workstream() {
  log "Starting workstream in repo: $REPO"
  local -a args=(start --run --workdir "$REPO" --max-steps "$MAX_STEPS" --prompt-language "$PROMPT_LANGUAGE")
  if [[ "$VARIANT" != "full" ]]; then
    args+=(--workflow-variant "$VARIANT")
  fi
  args+=(--task-file "$TASK_FILE")

  if ! dualctl "${args[@]}" >"$CASE_DIR/logs/start.log" 2>&1; then
    cat "$CASE_DIR/logs/start.log"
    fail "dualctl start failed"
    parse_started_workstream || true
    FINAL_STATUS="START_FAILED"
    collect_evidence || true
    status_snapshot "final" || true
    write_report
    update_benchmark_index
    exit 1
  fi

  parse_started_workstream

  [[ -n "$WORKSTREAM_ID" ]] || { cat "$CASE_DIR/logs/start.log"; fail "could not parse Workstream"; exit 1; }
  [[ -n "$WORKSPACE_DIR" ]] || { cat "$CASE_DIR/logs/start.log"; fail "could not parse Workspace"; exit 1; }

  WS_DIR="$WORKSPACE_DIR/.agent/workstreams/$WORKSTREAM_ID"
  log "Workstream: $WORKSTREAM_ID"
  log "Workspace:  $WORKSPACE_DIR"
  log "Ledger:     $WS_DIR"
  print_step_lines "$(cat "$CASE_DIR/logs/start.log")"
}

parse_started_workstream() {
  [[ -f "$CASE_DIR/logs/start.log" ]] || return 1
  WORKSTREAM_ID="$(awk '/^Workstream:/ { value = $2 } END { print value }' "$CASE_DIR/logs/start.log")"
  WORKSPACE_DIR="$(awk '/^Workspace:/ { value = $2 } END { print value }' "$CASE_DIR/logs/start.log")"
  if [[ -n "$WORKSTREAM_ID" && -n "$WORKSPACE_DIR" ]]; then
    WS_DIR="$WORKSPACE_DIR/.agent/workstreams/$WORKSTREAM_ID"
    return 0
  fi
  return 1
}

status_snapshot() {
  local name="$1"
  dualctl status --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/status-$name.log" 2>&1
}

current_status_text() {
  dualctl status --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" 2>&1
}

active_node_definition() {
  printf '%s\n' "$1" | awk '/Active node definition:/ { value = $NF } END { print value }'
}

workflow_status() {
  printf '%s\n' "$1" | awk '/^  Status:/ { value = $2 } END { print value }'
}

active_nodes() {
  printf '%s\n' "$1" | awk '/^  Active nodes:/ { sub(/^  Active nodes: /, ""); value = $0 } END { print value }'
}

open_gates() {
  printf '%s\n' "$1" | awk '/^  Open gates:/ { sub(/^  Open gates: /, ""); value = $0 } END { print value }'
}

print_status_summary() {
  local label="$1"
  local status_output="$2"
  local status active_def active_ids gates
  status="$(workflow_status "$status_output")"
  active_def="$(active_node_definition "$status_output")"
  active_ids="$(active_nodes "$status_output")"
  gates="$(open_gates "$status_output")"
  log "Status [$label]: ${status:-unknown}; active=${active_def:-unknown}; nodes=${active_ids:-[]}; gates=${gates:-[]}"
}

print_step_lines() {
  local output="$1"
  printf '%s\n' "$output" | awk '/^Step |^Status: |^Gate |^Autorun / { print }' | while IFS= read -r line; do
    log "  $line"
  done
}

run_autorun() {
  log "Autorunning workflow across gates..."
  if dualctl autorun \
    --workdir "$WORKSPACE_DIR" \
    --max-steps "$MAX_STEPS" \
    --max-gates "$MAX_GATES" \
    "$WORKSTREAM_ID" >"$CASE_DIR/logs/autorun.log" 2>&1; then
    print_step_lines "$(cat "$CASE_DIR/logs/autorun.log")"
    FINAL_STATUS="COMPLETED"
    return 0
  fi

  cat "$CASE_DIR/logs/autorun.log"
  print_step_lines "$(cat "$CASE_DIR/logs/autorun.log")"
  local status_output
  status_output="$(current_status_text || true)"
  printf '%s\n' "$status_output" >"$CASE_DIR/logs/status-after-autorun-failure.log"
  FINAL_STATUS="$(workflow_status "$status_output")"
  [[ -n "$FINAL_STATUS" ]] || FINAL_STATUS="AUTORUN_FAILED"
  return 1
}

collect_evidence() {
  [[ -n "$WORKSPACE_DIR" && -d "$WORKSPACE_DIR" ]] || return 0
  git -C "$REPO" status --short >"$CASE_DIR/logs/original-git-status-short.log" 2>&1 || true
  git -C "$WORKSPACE_DIR" status --short >"$CASE_DIR/logs/git-status-short.log" 2>&1 || true
  git -C "$WORKSPACE_DIR" diff --stat >"$CASE_DIR/logs/git-diff-stat.log" 2>&1 || true
  git -C "$WORKSPACE_DIR" diff >"$CASE_DIR/logs/git-diff.patch" 2>&1 || true
  dualctl timeline --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/timeline.log" 2>&1 || true
  dualctl events --workdir "$WORKSPACE_DIR" --limit 100 "$WORKSTREAM_ID" >"$CASE_DIR/logs/events.log" 2>&1 || true
  dualctl runs --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/runs.log" 2>&1 || true
  dualctl recovery show --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/recovery.log" 2>&1 || true
  dualctl summary --workdir "$WORKSPACE_DIR" --write "$WORKSTREAM_ID" >"$CASE_DIR/logs/workstream-summary.log" 2>&1 || true
  sync_runtime_git_evidence
}

sync_runtime_git_evidence() {
  [[ -n "$WS_DIR" && -d "$WS_DIR/artifacts/git-evidence" ]] || return 0
  local evidence_dir="$WS_DIR/artifacts/git-evidence"
  [[ -f "$evidence_dir/git-status-short.txt" ]] && cp "$evidence_dir/git-status-short.txt" "$CASE_DIR/logs/git-status-short.log"
  [[ -f "$evidence_dir/diff.stat" ]] && cp "$evidence_dir/diff.stat" "$CASE_DIR/logs/git-diff-stat.log"
  [[ -f "$evidence_dir/diff.patch" ]] && cp "$evidence_dir/diff.patch" "$CASE_DIR/logs/git-diff.patch"
  [[ -f "$evidence_dir/changed-files.txt" ]] && cp "$evidence_dir/changed-files.txt" "$CASE_DIR/logs/changed-files.log"
}

json_escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

verification_gate_json() {
  local gate="$CASE_DIR/verification-gate.md"
  if [[ ! -f "$gate" ]]; then
    printf '{}'
    return
  fi
  local fields=(
    "Target"
    "Source of truth"
    "Trigger precondition"
    "How precondition is checked before run"
    "PASS"
    "FAIL"
    "INVALID"
    "Stop-loss"
    "Run artifact freshness"
  )
  local first=true field value key
  printf '{'
  for field in "${fields[@]}"; do
    value="$(verification_gate_field_value "$gate" "$field")"
    key="$(printf '%s' "$field" | tr '[:upper:]' '[:lower:]' | sed 's/ /_/g; s/-/_/g')"
    if [[ "$first" == true ]]; then
      first=false
    else
      printf ', '
    fi
    printf '"%s": "%s"' "$key" "$(json_escape "$value")"
  done
  printf '}'
}

preflight_json() {
  local preflight="$CASE_DIR/preflight.json"
  if [[ -f "$preflight" ]]; then
    cat "$preflight"
  else
    printf '{}'
  fi
}

count_lines_matching() {
  local file="$1"
  local pattern="$2"
  [[ -f "$file" ]] || {
    printf '0'
    return
  }
  local count
  count="$(grep -c "$pattern" "$file" 2>/dev/null || true)"
  printf '%s' "${count:-0}"
}

agent_run_count() {
  local runs_log="$CASE_DIR/logs/runs.log"
  [[ -f "$runs_log" ]] || {
    printf '0'
    return
  }
  awk 'NR > 1 && NF { count++ } END { print count + 0 }' "$runs_log"
}

acceptance_decisions_json() {
  [[ -n "$WS_DIR" && -d "$WS_DIR/runs" ]] || {
    printf '[]'
    return
  }
  local first=true
  printf '['
  while IFS= read -r file; do
    local decision
    decision="$(grep -E '"decision"[[:space:]]*:' "$file" | head -1 | sed -E 's/.*"decision"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')"
    [[ -n "$decision" ]] || continue
    if [[ "$first" == true ]]; then
      first=false
    else
      printf ', '
    fi
    printf '"%s"' "$(json_escape "$decision")"
  done < <(find "$WS_DIR/runs" -path '*/outputs/acceptance-codex-review.contract.json' -type f 2>/dev/null | sort)
  printf ']'
}

acceptance_decisions_text() {
  acceptance_decisions_json | sed 's/^\[//; s/\]$//; s/"//g'
}

final_acceptance_decision() {
  local decisions="$1"
  if [[ -z "$decisions" ]]; then
    printf 'none'
    return
  fi
  printf '%s' "$decisions" | awk -F',' '{ value=$NF; gsub(/^[[:space:]]+|[[:space:]]+$/, "", value); print value }'
}

acceptance_reject_count() {
  [[ -n "$WS_DIR" && -d "$WS_DIR/runs" ]] || {
    printf '0'
    return
  }
  local count=0
  while IFS= read -r file; do
    local decision
    decision="$(grep -E '"decision"[[:space:]]*:' "$file" | head -1 | sed -E 's/.*"decision"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')"
    if [[ "$decision" == "REJECT" ]]; then
      count=$((count + 1))
    fi
  done < <(find "$WS_DIR/runs" -path '*/outputs/acceptance-codex-review.contract.json' -type f 2>/dev/null | sort)
  printf '%s' "$count"
}

repair_loop_count() {
  local events="$WS_DIR/events.jsonl"
  [[ -f "$events" ]] || {
    printf '0'
    return
  }
  local count
  count="$(grep -c '"event_type":"iteration_started".*"trigger_action":"REJECT_' "$events" 2>/dev/null || true)"
  printf '%s' "${count:-0}"
}

bool_from_count() {
  local count="$1"
  if [[ "$count" -gt 0 ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

verification_confidence() {
  local final_decision="$1"
  local missing="$2"
  case "$final_decision" in
    ACCEPT)
      if [[ -z "$missing" ]]; then
        printf 'HIGH'
      else
        printf 'MEDIUM'
      fi
      ;;
    ACCEPT_WITH_NOTES)
      printf 'MEDIUM'
      ;;
    REJECT)
      printf 'LOW'
      ;;
    *)
      printf 'UNKNOWN'
      ;;
  esac
}

verification_confidence_reason() {
  local final_decision="$1"
  local missing="$2"
  case "$final_decision" in
    ACCEPT)
      if [[ -z "$missing" ]]; then
        printf 'accepted_without_notes'
      else
        printf 'accepted_but_missing_artifacts=%s' "$missing"
      fi
      ;;
    ACCEPT_WITH_NOTES)
      printf 'accepted_with_notes'
      ;;
    REJECT)
      printf 'final_acceptance_rejected'
      ;;
    *)
      printf 'no_final_acceptance_decision'
      ;;
  esac
}

artifact_present_bool() {
  local ref="$1"
  if [[ -f "$WS_DIR/$ref" ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

missing_artifacts() {
  local missing=()
  [[ "$(artifact_present_bool "reports/latest-final-report.md")" == "true" ]] || missing+=("final_report")
  [[ "$(artifact_present_bool "artifacts/next-stage/handoff.json")" == "true" ]] || missing+=("handoff_json")
  [[ "$(artifact_present_bool "artifacts/next-stage/next-stage-prompt.md")" == "true" ]] || missing+=("next_stage_prompt")
  [[ "$(artifact_present_bool "delivery/pr-body.md")" == "true" ]] || missing+=("delivery_pr_body")
  [[ "$(artifact_present_bool "reports/workstream-summary.md")" == "true" ]] || missing+=("workstream_summary")
  if [[ "${#missing[@]}" -eq 0 ]]; then
    printf ''
    return
  fi
  local IFS=,
  printf '%s' "${missing[*]}"
}

invalid_reason() {
  local preflight="$CASE_DIR/preflight.json"
  if [[ -f "$preflight" ]]; then
    local freshness_status probe_status reason
    if command -v jq >/dev/null 2>&1; then
      freshness_status="$(jq -r '.artifactFreshnessStatus // ""' "$preflight" 2>/dev/null || true)"
      probe_status="$(jq -r '.providerProbeStatus // ""' "$preflight" 2>/dev/null || true)"
    else
      freshness_status="$(grep -E '"artifactFreshnessStatus"[[:space:]]*:' "$preflight" | sed -E 's/.*"artifactFreshnessStatus"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/' | head -1)"
      probe_status="$(grep -E '"providerProbeStatus"[[:space:]]*:' "$preflight" | sed -E 's/.*"providerProbeStatus"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/' | head -1)"
    fi
    reason="$(real_case_artifact_freshness_invalid_reason "$freshness_status")"
    if [[ -n "$reason" ]]; then
      printf '%s' "$reason"
      return
    fi
    reason="$(real_case_provider_probe_invalid_reason "$probe_status")"
    if [[ -n "$reason" ]]; then
      printf '%s' "$reason"
      return
    fi
  fi
  real_case_invalid_reason_from_runs_dir "${WS_DIR:-}/runs"
}

original_repo_clean_bool() {
  local file="$CASE_DIR/logs/original-git-status-short.log"
  if [[ -f "$file" && ! -s "$file" ]]; then
    printf 'true'
  else
    printf 'false'
  fi
}

write_summary_json() {
  local summary="$CASE_DIR/summary.json"
  local gate_rounds node_count run_count iteration_count design_revision_count reject_count repair_count
  local original_clean final_report_present handoff_present next_prompt_present delivery_body_present workstream_summary_present
  local decisions final_decision missing invalid grade reason confidence confidence_reason
  gate_rounds="$(count_lines_matching "$CASE_DIR/logs/autorun.log" '^Gate ')"
  node_count="$(count_lines_matching "$CASE_DIR/logs/timeline.log" '^  ')"
  run_count="$(agent_run_count)"
  iteration_count="$(count_lines_matching "$WS_DIR/events.jsonl" '"event_type":"iteration_started"')"
  design_revision_count="$(count_lines_matching "$WS_DIR/events.jsonl" '"outcome":"NEEDS_REVISION"')"
  original_clean="$(original_repo_clean_bool)"
  final_report_present="$(artifact_present_bool "reports/latest-final-report.md")"
  handoff_present="$(artifact_present_bool "artifacts/next-stage/handoff.json")"
  next_prompt_present="$(artifact_present_bool "artifacts/next-stage/next-stage-prompt.md")"
  delivery_body_present="$(artifact_present_bool "delivery/pr-body.md")"
  workstream_summary_present="$(artifact_present_bool "reports/workstream-summary.md")"
  decisions="$(acceptance_decisions_text)"
  missing="$(missing_artifacts)"
  invalid="$(invalid_reason)"
  reject_count="$(acceptance_reject_count)"
  repair_count="$(repair_loop_count)"
  final_decision="$(final_acceptance_decision "$decisions")"
  confidence="$(verification_confidence "$final_decision" "$missing")"
  confidence_reason="$(verification_confidence_reason "$final_decision" "$missing")"
  grade="$(real_case_grade "$FINAL_STATUS" "$original_clean" "$missing" "$decisions" "$reject_count" "$invalid")"
  reason="$(real_case_grade_reason "$FINAL_STATUS" "$original_clean" "$missing" "$decisions" "$reject_count" "$invalid")"

  {
    echo "{"
    echo "  \"started_at\": \"$(json_escape "$STARTED_AT")\","
    echo "  \"finished_at\": \"$(json_escape "$FINISHED_AT")\","
    echo "  \"final_status\": \"$(json_escape "$FINAL_STATUS")\","
    echo "  \"variant\": \"$(json_escape "$VARIANT")\","
    echo "  \"prompt_language\": \"$(json_escape "$PROMPT_LANGUAGE")\","
    echo "  \"repo\": \"$(json_escape "$REPO")\","
    echo "  \"task_file\": \"$(json_escape "$TASK_FILE")\","
    echo "  \"verification_gate\": $(verification_gate_json),"
    echo "  \"preflight\": $(preflight_json),"
    echo "  \"workstream\": \"$(json_escape "${WORKSTREAM_ID:-unknown}")\","
    echo "  \"effective_workdir\": \"$(json_escape "${WORKSPACE_DIR:-unknown}")\","
    echo "  \"ledger\": \"$(json_escape "${WS_DIR:-unknown}")\","
    echo "  \"workstream_summary\": \"$(json_escape "${WS_DIR:-unknown}/reports/workstream-summary.md")\","
    echo "  \"gate_rounds\": $gate_rounds,"
    echo "  \"agent_run_count\": $run_count,"
    echo "  \"timeline_node_count\": $node_count,"
    echo "  \"iteration_count\": $iteration_count,"
    echo "  \"design_revision_count\": $design_revision_count,"
    echo "  \"acceptance_decisions\": $(acceptance_decisions_json),"
    echo "  \"final_acceptance_decision\": \"$(json_escape "$final_decision")\","
    echo "  \"acceptance_reject_count\": $reject_count,"
    echo "  \"had_acceptance_reject\": $(bool_from_count "$reject_count"),"
    echo "  \"repair_loop_count\": $repair_count,"
    echo "  \"verification_confidence\": \"$(json_escape "$confidence")\","
    echo "  \"verification_confidence_reason\": \"$(json_escape "$confidence_reason")\","
    echo "  \"original_repo_clean\": $original_clean,"
    echo "  \"invalid_reason\": \"$(json_escape "$invalid")\","
    echo "  \"benchmark_grade\": \"$(json_escape "$grade")\","
    echo "  \"benchmark_grade_reason\": \"$(json_escape "$reason")\","
    echo "  \"artifacts\": {"
    echo "    \"final_report\": $final_report_present,"
    echo "    \"handoff_json\": $handoff_present,"
    echo "    \"next_stage_prompt\": $next_prompt_present,"
    echo "    \"delivery_pr_body\": $delivery_body_present,"
    echo "    \"workstream_summary\": $workstream_summary_present"
    echo "  }"
    echo "}"
  } >"$summary"
}

artifact_status() {
  local ref="$1"
  if [[ -f "$WS_DIR/$ref" ]]; then
    printf 'present'
  else
    printf 'missing'
  fi
}

write_report() {
  FINISHED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  write_summary_json
  local report="$CASE_DIR/report.md"
  local summary="$CASE_DIR/summary.json"
  local final_report="$WS_DIR/reports/latest-final-report.md"
  local workstream_summary="$WS_DIR/reports/workstream-summary.md"
  local handoff_json="$WS_DIR/artifacts/next-stage/handoff.json"
  local next_prompt="$WS_DIR/artifacts/next-stage/next-stage-prompt.md"
  local delivery_body="$WS_DIR/delivery/pr-body.md"
  local delivery_commit="$WS_DIR/delivery/commit-message.txt"
  local grade reason decisions final_decision reject_count repair_count missing invalid confidence confidence_reason
  grade="$(grep -E '"benchmark_grade"[[:space:]]*:' "$summary" | sed -E 's/.*"benchmark_grade"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')"
  reason="$(grep -E '"benchmark_grade_reason"[[:space:]]*:' "$summary" | sed -E 's/.*"benchmark_grade_reason"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')"
  decisions="$(acceptance_decisions_text)"
  final_decision="$(final_acceptance_decision "$decisions")"
  reject_count="$(acceptance_reject_count)"
  repair_count="$(repair_loop_count)"
  missing="$(missing_artifacts)"
  invalid="$(invalid_reason)"
  confidence="$(verification_confidence "$final_decision" "$missing")"
  confidence_reason="$(verification_confidence_reason "$final_decision" "$missing")"

  {
    echo "# Real Lifecycle Case Report"
    echo
    echo "## Metadata"
    echo
    echo "- Started At: $STARTED_AT"
    echo "- Finished At: $FINISHED_AT"
    echo "- Final Status: $FINAL_STATUS"
    echo "- Variant: $VARIANT"
    echo "- Prompt Language: $PROMPT_LANGUAGE"
    echo "- Repo: $REPO"
    echo "- Workstream: ${WORKSTREAM_ID:-unknown}"
    echo "- Effective Workdir: ${WORKSPACE_DIR:-unknown}"
    echo "- Ledger: ${WS_DIR:-unknown}"
    echo "- Task File: $TASK_FILE"
    echo
    echo "## Verification Gate"
    echo
    if [[ -f "$CASE_DIR/verification-gate.md" ]]; then
      cat "$CASE_DIR/verification-gate.md"
    else
      echo "Verification gate was not captured."
    fi
    echo
    echo "## Metrics"
    echo
    echo "- Gate Rounds: $(count_lines_matching "$CASE_DIR/logs/autorun.log" '^Gate ')"
    echo "- Agent Runs: $(agent_run_count)"
    echo "- Timeline Nodes: $(count_lines_matching "$CASE_DIR/logs/timeline.log" '^  ')"
    echo "- Iteration Starts: $(count_lines_matching "$WS_DIR/events.jsonl" '"event_type":"iteration_started"')"
    echo "- Design Revisions: $(count_lines_matching "$WS_DIR/events.jsonl" '"outcome":"NEEDS_REVISION"')"
    echo "- Acceptance Decisions: $(acceptance_decisions_json)"
    echo "- Acceptance Reject Count: $reject_count"
    echo "- Final Acceptance Decision: $final_decision"
    echo "- Had Acceptance Reject: $(bool_from_count "$reject_count")"
    echo "- Repair Loop Count: $repair_count"
    echo "- Verification Confidence: $confidence"
    echo "- Verification Confidence Reason: $confidence_reason"
    echo "- Original Repo Clean: $(original_repo_clean_bool)"
    echo "- Invalid Reason: ${invalid:-none}"
    echo "- Benchmark Grade: ${grade:-unknown}"
    echo "- Benchmark Reason: ${reason:-unknown}"
    echo
    echo "## Preflight Evidence"
    echo
    echo '```json'
    if [[ -f "$CASE_DIR/preflight.json" ]]; then
      cat "$CASE_DIR/preflight.json"
    else
      echo "{}"
    fi
    echo '```'
    echo
    echo "## Summary JSON"
    echo
    echo '```text'
    echo "$CASE_DIR/summary.json"
    echo '```'
    echo
    echo "## Final Status Snapshot"
    echo
    echo '```text'
    if [[ -f "$CASE_DIR/logs/status-final.log" ]]; then
      cat "$CASE_DIR/logs/status-final.log"
    elif [[ -f "$CASE_DIR/logs/status-after-start.log" ]]; then
      cat "$CASE_DIR/logs/status-after-start.log"
    fi
    echo '```'
    echo
    echo "## Artifacts"
    echo
    echo "| Artifact | Status | Path |"
    echo "| --- | --- | --- |"
    if [[ -n "$WS_DIR" ]]; then
      echo "| Final Report | $(artifact_status "reports/latest-final-report.md") | $final_report |"
      echo "| Workstream Summary | $(artifact_status "reports/workstream-summary.md") | $workstream_summary |"
      echo "| Handoff JSON | $(artifact_status "artifacts/next-stage/handoff.json") | $handoff_json |"
      echo "| Next-stage Prompt | $(artifact_status "artifacts/next-stage/next-stage-prompt.md") | $next_prompt |"
      echo "| Delivery PR Body | $(artifact_status "delivery/pr-body.md") | $delivery_body |"
      echo "| Delivery Commit Message | $(artifact_status "delivery/commit-message.txt") | $delivery_commit |"
    fi
    echo
    echo "## Git Status"
    echo
    echo '```text'
    [[ -f "$CASE_DIR/logs/git-status-short.log" ]] && cat "$CASE_DIR/logs/git-status-short.log"
    echo '```'
    echo
    echo "## Git Diff Stat"
    echo
    echo '```text'
    [[ -f "$CASE_DIR/logs/git-diff-stat.log" ]] && cat "$CASE_DIR/logs/git-diff-stat.log"
    echo '```'
    echo
    echo "## Useful Commands"
    echo
    echo '```bash'
    echo "java -jar \"$JAR_PATH\" status --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
    echo "java -jar \"$JAR_PATH\" summary --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
    echo "java -jar \"$JAR_PATH\" timeline --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
    echo "java -jar \"$JAR_PATH\" runs --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
    echo "java -jar \"$JAR_PATH\" recovery show --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
    echo "cd \"$WORKSPACE_DIR\""
    echo "git status --short"
    echo "git diff --stat"
    echo '```'
    echo
    echo "## Logs"
    echo
    echo "All command logs are under:"
    echo
    echo '```text'
    echo "$CASE_DIR/logs"
    echo '```'
  } >"$report"

  log "Report: $report"
}

cleanup_worktree() {
  [[ "$CLEANUP_WORKTREE" == true ]] || return 0
  [[ -n "$WORKSPACE_DIR" && -d "$WORKSPACE_DIR" ]] || return 0
  log "Removing worktree: $WORKSPACE_DIR"
  git -C "$REPO" worktree remove --force "$WORKSPACE_DIR" || true
}

update_benchmark_index() {
  [[ "$UPDATE_BENCHMARK_INDEX" == true ]] || return 0
  log "Updating benchmark index..."
  "$PROJECT_ROOT/scripts/real-case-benchmark-summary.sh" >"$CASE_DIR/logs/update-benchmark-index.log" 2>&1
  cat "$CASE_DIR/logs/update-benchmark-index.log"
}

main() {
  parse_args "$@"
  validate_inputs
  init_case_dir
  build_project
  run_provider_probe || true
  write_preflight_evidence
  trap handle_interrupt INT TERM
  start_workstream
  status_snapshot "after-start" || true

  if run_autorun; then
    FINAL_STATUS="COMPLETED"
  fi

  collect_evidence
  status_snapshot "final" || true
  write_report
  update_benchmark_index
  cleanup_worktree

  if [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
    log "Completed real lifecycle case."
    exit 0
  fi

  fail "Real lifecycle case stopped with status: $FINAL_STATUS"
  exit 1
}

main "$@"
