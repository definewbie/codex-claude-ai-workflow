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
#   ./scripts/run-public-benchmark.sh --dest /tmp/dual-issue-flow-benchmark
#   ./scripts/run-public-benchmark.sh --skip-build --update-benchmark-index
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST_DIR=""
VARIANT="full"
MAX_STEPS=80
MAX_GATES=12
SKIP_BUILD=false
CLEANUP_WORKTREE=false
UPDATE_BENCHMARK_INDEX=false

usage() {
  cat <<'EOF'
Usage:
  run-public-benchmark.sh [options]

Options:
  --dest <path>             Destination sample repo. Defaults to /tmp.
  --variant <variant>       Workflow variant. Default: full.
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

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --dest)
        shift
        DEST_DIR="${1:-}"
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
  local -a args=(
    --repo "$DEST_DIR"
    --task-file "$PROJECT_ROOT/docs/testing/real-case-benchmarks/cases/public-issue-flow-review-state.md"
    --variant "$VARIANT"
    --max-steps "$MAX_STEPS"
    --max-gates "$MAX_GATES"
  )
  [[ "$SKIP_BUILD" == true ]] && args+=(--skip-build)
  [[ "$CLEANUP_WORKTREE" == true ]] && args+=(--cleanup-worktree)
  [[ "$UPDATE_BENCHMARK_INDEX" == true ]] && args+=(--update-benchmark-index)

  log "Running public benchmark against: $DEST_DIR"
  "$PROJECT_ROOT/scripts/run-real-case.sh" "${args[@]}"
}

main() {
  parse_args "$@"
  prepare_repo
  run_case
}

main "$@"
