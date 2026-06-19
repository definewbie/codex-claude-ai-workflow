#!/usr/bin/env bash
#
# run-public-benchmark.sh — Prepare and run the reusable public benchmark case.
#
# This is the shortest public validation path for a real acpx lifecycle run.
# It creates a disposable issue-flow sample repo, then delegates the actual
# lifecycle to run-real-case.sh.
#
# Usage:
#   ./scripts/run-public-benchmark.sh
#   ./scripts/run-public-benchmark.sh --list-cases
#   ./scripts/run-public-benchmark.sh --case public-issue-flow-review-state
#   ./scripts/run-public-benchmark.sh --dest /tmp/dual-issue-flow-benchmark
#   ./scripts/run-public-benchmark.sh --skip-build --update-benchmark-index
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR=""
CASE_ID="public-issue-flow-review-state"
TASK_FILE=""
VARIANT="full"
PROMPT_LANGUAGE="auto"
MAX_STEPS=80
MAX_GATES=12
SKIP_BUILD=false
CLEANUP_WORKTREE=false
UPDATE_BENCHMARK_INDEX=false
SUITE_FILE="$PROJECT_ROOT/docs/testing/real-case-benchmarks/benchmark-suite.json"

usage() {
  cat <<'EOF'
Usage:
  run-public-benchmark.sh [options]

Options:
  --dest <path>             Destination sample repo. Defaults to /tmp.
  --case <id|path>          Public case id or task markdown path. Default: public-issue-flow-review-state.
  --list-cases              List public benchmark cases and exit.
  --variant <variant>       Workflow variant. Default: full.
  --prompt-language <auto|en|zh-CN>
                            Human-readable Agent output language. Default: auto.
  --max-steps <n>           Max nodes per run/approve. Default: 80.
  --max-gates <n>           Max human gates to auto-approve. Default: 12.
  --skip-build              Reuse the existing dualctl jar.
  --cleanup-worktree        Remove the generated agent worktree after reporting.
  --update-benchmark-index  Refresh benchmark-index.md after the run.
  -h, --help                Show this help.

The script uses real acpx Claude/Codex sessions. It auto-approves human gates
for benchmark validation, so do not treat it as final product acceptance.
EOF
}

log() { printf '[PUBLIC-BENCH] %s\n' "$*"; }
fail() { printf '[PUBLIC-BENCH][FAIL] %s\n' "$*" >&2; }

require_jq() {
  command -v jq >/dev/null 2>&1 || {
    fail "jq is required for benchmark case selection."
    fail "Install jq, then rerun this script."
    exit 1
  }
}

list_cases() {
  require_jq
  jq -r '
    .cases[]
    | "- \(.id): \(.purpose) [\(.expected_grade)]"
  ' "$SUITE_FILE"
}

resolve_case() {
  require_jq
  if [[ "$CASE_ID" = /* || "$CASE_ID" == ./* || "$CASE_ID" == ../* ]]; then
    TASK_FILE="$CASE_ID"
    return
  fi

  local task variant steps gates
  task="$(jq -r --arg id "$CASE_ID" '.cases[] | select(.id == $id) | .task_file // ""' "$SUITE_FILE")"
  [[ -n "$task" ]] || {
    fail "unknown public benchmark case: $CASE_ID"
    fail "Run ./scripts/run-public-benchmark.sh --list-cases"
    exit 1
  }

  TASK_FILE="$PROJECT_ROOT/docs/testing/real-case-benchmarks/$task"
  variant="$(jq -r --arg id "$CASE_ID" '.cases[] | select(.id == $id) | .variant // ""' "$SUITE_FILE")"
  steps="$(jq -r --arg id "$CASE_ID" '.cases[] | select(.id == $id) | .max_steps // ""' "$SUITE_FILE")"
  gates="$(jq -r --arg id "$CASE_ID" '.cases[] | select(.id == $id) | .max_gates // ""' "$SUITE_FILE")"
  [[ -n "$variant" ]] && VARIANT="$variant"
  [[ -n "$steps" ]] && MAX_STEPS="$steps"
  [[ -n "$gates" ]] && MAX_GATES="$gates"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dest)
        shift
        DEST_DIR="${1:-}"
        ;;
      --case)
        shift
        CASE_ID="${1:-}"
        ;;
      --list-cases)
        list_cases
        exit 0
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

prepare_repo() {
  local output
  if [[ -n "$DEST_DIR" ]]; then
    output="$("$PROJECT_ROOT/scripts/prepare-public-benchmark-repo.sh" --dest "$DEST_DIR")"
  else
    output="$("$PROJECT_ROOT/scripts/prepare-public-benchmark-repo.sh")"
  fi

  printf '%s\n' "$output"
  DEST_DIR="$(printf '%s\n' "$output" | awk '/^  \/.*$/ { value = $1 } END { print value }')"
  [[ -n "$DEST_DIR" ]] || {
    fail "could not parse prepared benchmark repo path"
    exit 1
  }
}

run_case() {
  resolve_case
  [[ -f "$TASK_FILE" ]] || {
    fail "task file not found: $TASK_FILE"
    exit 1
  }

  local -a args=(
    --repo "$DEST_DIR"
    --task-file "$TASK_FILE"
    --variant "$VARIANT"
    --prompt-language "$PROMPT_LANGUAGE"
    --max-steps "$MAX_STEPS"
    --max-gates "$MAX_GATES"
  )
  [[ "$SKIP_BUILD" == true ]] && args+=(--skip-build)
  [[ "$CLEANUP_WORKTREE" == true ]] && args+=(--cleanup-worktree)
  [[ "$UPDATE_BENCHMARK_INDEX" == true ]] && args+=(--update-benchmark-index)

  log "Running public benchmark against: $DEST_DIR"
  log "Case: $CASE_ID"
  log "Task file: $TASK_FILE"
  log "Variant: $VARIANT, max steps: $MAX_STEPS, max gates: $MAX_GATES"
  log "Prompt language: $PROMPT_LANGUAGE"
  "$PROJECT_ROOT/scripts/run-real-case.sh" "${args[@]}"
}

main() {
  parse_args "$@"
  prepare_repo
  run_case
}

main "$@"
