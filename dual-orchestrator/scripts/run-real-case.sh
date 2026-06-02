#!/usr/bin/env bash
#
# run-real-case.sh — Run a real repository through dual-orchestrator lifecycle.
#
# This script is intentionally small: it starts one workstream, auto-approves
# human gates for orchestration validation, and writes a report. It does not
# commit, push, create PRs, or delete the original repository.
#
# Usage:
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --variant p6
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --cleanup-worktree
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_PATH="$PROJECT_ROOT/dual-cli/target/dual-cli-0.1.0-SNAPSHOT.jar"
REPORT_ROOT="$PROJECT_ROOT/docs/testing/real-case-reports"

REPO=""
TASK_FILE=""
VARIANT="p6"
MAX_STEPS=80
MAX_GATES=12
SKIP_BUILD=false
CLEANUP_WORKTREE=false

WORKSTREAM_ID=""
WORKSPACE_DIR=""
WS_DIR=""
CASE_DIR=""
FINAL_STATUS="UNKNOWN"
STARTED_AT=""
FINISHED_AT=""

usage() {
  cat <<'EOF'
Usage:
  run-real-case.sh --repo <repo> --task-file <task.md> [options]

Options:
  --repo <path>             Target git repository.
  --task-file <path>        Requirement markdown file.
  --variant <thin|p5|p6>    Workflow variant. Default: p6.
  --max-steps <n>           Max nodes per run/approve. Default: 80.
  --max-gates <n>           Max human gates to auto-approve. Default: 12.
  --skip-build              Do not run Maven package before starting.
  --cleanup-worktree        Remove the created worktree after report generation.
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
      --variant)
        shift
        VARIANT="${1:-}"
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

  REPO="$(abs_path "$REPO")"
  TASK_FILE="$(abs_path "$TASK_FILE")"

  [[ -d "$REPO" ]] || { fail "repo not found: $REPO"; exit 1; }
  [[ -f "$TASK_FILE" ]] || { fail "task file not found: $TASK_FILE"; exit 1; }
  git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
    fail "repo must be a git repository: $REPO"
    exit 1
  }
  command -v acpx >/dev/null 2>&1 || {
    fail "acpx not found. Initialize acpx sessions before running a real case."
    exit 1
  }
  case "$VARIANT" in
    thin|p5|p6) ;;
    *) fail "--variant must be thin, p5, or p6"; exit 1 ;;
  esac
}

build_project() {
  if [[ "$SKIP_BUILD" == true ]]; then
    [[ -f "$JAR_PATH" ]] || {
      fail "dualctl jar not found: $JAR_PATH"
      exit 1
    }
    log "Skipping build; using $JAR_PATH"
    return
  fi

  log "Building dual-cli and dual-server..."
  (cd "$PROJECT_ROOT" && mvn -pl dual-cli,dual-server -am package)
}

init_case_dir() {
  STARTED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  local stamp
  stamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  mkdir -p "$REPORT_ROOT"
  CASE_DIR="$REPORT_ROOT/$stamp"
  mkdir -p "$CASE_DIR/logs"
  cp "$TASK_FILE" "$CASE_DIR/task.md"
}

start_workstream() {
  log "Starting workstream in repo: $REPO"
  local -a args=(start --run --workdir "$REPO" --max-steps "$MAX_STEPS")
  if [[ "$VARIANT" != "thin" ]]; then
    args+=(--workflow-variant "$VARIANT")
  fi
  args+=(--task-file "$TASK_FILE")

  if ! dualctl "${args[@]}" >"$CASE_DIR/logs/start.log" 2>&1; then
    cat "$CASE_DIR/logs/start.log"
    fail "dualctl start failed"
    FINAL_STATUS="START_FAILED"
    write_report
    exit 1
  fi

  WORKSTREAM_ID="$(awk '/^Workstream:/ { value = $2 } END { print value }' "$CASE_DIR/logs/start.log")"
  WORKSPACE_DIR="$(awk '/^Workspace:/ { value = $2 } END { print value }' "$CASE_DIR/logs/start.log")"

  [[ -n "$WORKSTREAM_ID" ]] || { cat "$CASE_DIR/logs/start.log"; fail "could not parse Workstream"; exit 1; }
  [[ -n "$WORKSPACE_DIR" ]] || { cat "$CASE_DIR/logs/start.log"; fail "could not parse Workspace"; exit 1; }

  WS_DIR="$WORKSPACE_DIR/.agent/workstreams/$WORKSTREAM_ID"
  log "Workstream: $WORKSTREAM_ID"
  log "Workspace:  $WORKSPACE_DIR"
  log "Ledger:     $WS_DIR"
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

run_gate_loop() {
  local round=0
  while [[ "$round" -lt "$MAX_GATES" ]]; do
    round=$((round + 1))
    log "Gate round $round"

    local status_output
    status_output="$(current_status_text)"
    printf '%s\n' "$status_output" >"$CASE_DIR/logs/status-gate-$round.log"

    if grep -q 'COMPLETED' "$CASE_DIR/logs/status-gate-$round.log"; then
      FINAL_STATUS="COMPLETED"
      return 0
    fi
    if grep -q 'FAILED' "$CASE_DIR/logs/status-gate-$round.log"; then
      FINAL_STATUS="FAILED"
      return 1
    fi
    if grep -q 'BLOCKED' "$CASE_DIR/logs/status-gate-$round.log"; then
      FINAL_STATUS="BLOCKED"
      return 1
    fi
    if grep -q 'WAITING_FOR_RECOVERY' "$CASE_DIR/logs/status-gate-$round.log"; then
      FINAL_STATUS="WAITING_FOR_RECOVERY"
      return 1
    fi

    local active_def
    active_def="$(active_node_definition "$status_output")"
    log "Active gate: ${active_def:-unknown}"

    if [[ "$active_def" == "gate.human_next_stage_approval" ]]; then
      log "Completing final next-stage gate."
      if ! dualctl review complete --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/review-complete-$round.log" 2>&1; then
        cat "$CASE_DIR/logs/review-complete-$round.log"
        FINAL_STATUS="COMPLETE_FAILED"
        return 1
      fi
      FINAL_STATUS="COMPLETED"
      return 0
    fi

    log "Auto-approving gate and continuing."
    if ! dualctl review approve --workdir "$WORKSPACE_DIR" --run --max-steps "$MAX_STEPS" "$WORKSTREAM_ID" >"$CASE_DIR/logs/review-approve-$round.log" 2>&1; then
      cat "$CASE_DIR/logs/review-approve-$round.log"
      FINAL_STATUS="APPROVE_FAILED"
      return 1
    fi

    if grep -q 'COMPLETED' "$CASE_DIR/logs/review-approve-$round.log"; then
      FINAL_STATUS="COMPLETED"
      return 0
    fi
    if grep -q 'FAILED' "$CASE_DIR/logs/review-approve-$round.log"; then
      FINAL_STATUS="FAILED"
      return 1
    fi
    if grep -q 'WAITING_FOR_RECOVERY' "$CASE_DIR/logs/review-approve-$round.log"; then
      FINAL_STATUS="WAITING_FOR_RECOVERY"
      return 1
    fi
  done

  FINAL_STATUS="MAX_GATES_EXCEEDED"
  return 1
}

collect_evidence() {
  [[ -n "$WORKSPACE_DIR" && -d "$WORKSPACE_DIR" ]] || return 0
  git -C "$WORKSPACE_DIR" status --short >"$CASE_DIR/logs/git-status-short.log" 2>&1 || true
  git -C "$WORKSPACE_DIR" diff --stat >"$CASE_DIR/logs/git-diff-stat.log" 2>&1 || true
  git -C "$WORKSPACE_DIR" diff >"$CASE_DIR/logs/git-diff.patch" 2>&1 || true
  dualctl timeline --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/timeline.log" 2>&1 || true
  dualctl events --workdir "$WORKSPACE_DIR" --limit 100 "$WORKSTREAM_ID" >"$CASE_DIR/logs/events.log" 2>&1 || true
  dualctl runs --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/runs.log" 2>&1 || true
  dualctl recovery show --workdir "$WORKSPACE_DIR" "$WORKSTREAM_ID" >"$CASE_DIR/logs/recovery.log" 2>&1 || true
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
  local report="$CASE_DIR/report.md"
  local final_report="$WS_DIR/reports/latest-final-report.md"
  local handoff_json="$WS_DIR/artifacts/next-stage/handoff.json"
  local next_prompt="$WS_DIR/artifacts/next-stage/next-stage-prompt.md"
  local delivery_body="$WS_DIR/delivery/pr-body.md"
  local delivery_commit="$WS_DIR/delivery/commit-message.txt"

  {
    echo "# Real Lifecycle Case Report"
    echo
    echo "## Metadata"
    echo
    echo "- Started At: $STARTED_AT"
    echo "- Finished At: $FINISHED_AT"
    echo "- Final Status: $FINAL_STATUS"
    echo "- Variant: $VARIANT"
    echo "- Repo: $REPO"
    echo "- Workstream: ${WORKSTREAM_ID:-unknown}"
    echo "- Effective Workdir: ${WORKSPACE_DIR:-unknown}"
    echo "- Ledger: ${WS_DIR:-unknown}"
    echo "- Task File: $TASK_FILE"
    echo
    echo "## Artifacts"
    echo
    echo "| Artifact | Status | Path |"
    echo "| --- | --- | --- |"
    if [[ -n "$WS_DIR" ]]; then
      echo "| Final Report | $(artifact_status "reports/latest-final-report.md") | $final_report |"
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

main() {
  parse_args "$@"
  validate_inputs
  build_project
  init_case_dir
  start_workstream
  status_snapshot "after-start" || true

  if run_gate_loop; then
    FINAL_STATUS="COMPLETED"
  fi

  collect_evidence
  write_report
  cleanup_worktree

  if [[ "$FINAL_STATUS" == "COMPLETED" ]]; then
    log "Completed real lifecycle case."
    exit 0
  fi

  fail "Real lifecycle case stopped with status: $FINAL_STATUS"
  exit 1
}

main "$@"
