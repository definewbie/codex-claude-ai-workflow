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
#   DUAL_GLM_BASELINE_TOKENS=43540000 ./scripts/run-public-benchmark.sh --validation-target glm-token-attribution
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
VERIFICATION_GATE=""
VALIDATION_TARGET="lifecycle"

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
  --skip-build              Forwarded to run-real-case.sh, which rejects real
                            provider runs when artifact freshness cannot be
                            proven.
  --cleanup-worktree        Remove the generated agent worktree after reporting.
  --update-benchmark-index  Refresh benchmark-index.md after the run.
  --validation-target <lifecycle|glm-token-attribution>
                            Verification target for the generated gate.
                            Default: lifecycle. glm-token-attribution requires
                            DUAL_GLM_BASELINE_TOKENS=<current GLM console total>.
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
      --validation-target)
        shift
        VALIDATION_TARGET="${1:-}"
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

validate_validation_target() {
  case "$VALIDATION_TARGET" in
    lifecycle) ;;
    glm-token-attribution)
      [[ "${DUAL_GLM_BASELINE_TOKENS:-}" =~ ^[0-9]+$ ]] || {
        fail "DUAL_GLM_BASELINE_TOKENS=<integer token total> is required for --validation-target glm-token-attribution"
        exit 1
      }
      ;;
    *)
      fail "--validation-target must be lifecycle or glm-token-attribution"
      exit 1
      ;;
  esac
}

public_benchmark_write_verification_gate() {
  local safe_case_id
  safe_case_id="$(printf '%s' "$CASE_ID" | tr -c 'A-Za-z0-9_.-' '_')"
  VERIFICATION_GATE="$(mktemp "${TMPDIR:-/tmp}/dual-verification-gate-${safe_case_id}.XXXXXX")"
  if [[ "$VALIDATION_TARGET" == "glm-token-attribution" ]]; then
    cat >"$VERIFICATION_GATE" <<EOF
# Verification Gate: $CASE_ID / GLM token attribution

- Target: Validate GLM-5.2 provider token attribution for the public benchmark case $CASE_ID without using money as the acceptance metric.
- Source of truth: pre-run GLM console token total DUAL_GLM_BASELINE_TOKENS=${DUAL_GLM_BASELINE_TOKENS}, post-run GLM console token total captured after the run, workstream run.json providerModelIdentity, workstream-metrics.json tokenAttribution.perRun, and dualctl trace Token Attribution.
- Trigger precondition: DUAL_GLM_BASELINE_TOKENS is a numeric token total captured from the GLM console immediately before the run; disposable benchmark repo exists; task file exists; acpx is available; run-real-case.sh builds fresh artifacts; provider connectivity probe passes before workflow execution.
- How precondition is checked before run: run-public-benchmark.sh refuses this target unless DUAL_GLM_BASELINE_TOKENS is numeric; prepare-public-benchmark-repo.sh produced $DEST_DIR; run-real-case.sh validates repo, task file, acpx, variant, prompt language, this gate, preflight artifact freshness, and providerProbeStatus=passed before starting a workstream.
- PASS: Workflow reaches COMPLETED; GLM provider runs in run.json and workstream-metrics.json preserve provider=zhipu, model=glm-5.2, billingMode=subscription_token_only, real input/output/cachedRead token counts, costAttribution.reliability=token_only, and providerCachePolicy.mode=token_policy_active; post-run GLM console token total minus DUAL_GLM_BASELINE_TOKENS matches the summed GLM totalTokens in metrics within the documented console bucket granularity.
- FAIL: Workflow does not complete, GLM provider/model/billingMode is wrong, real GLM token counts are missing or zero for completed provider runs, metrics and trace disagree on GLM totalTokens, or post-run GLM console delta contradicts summed GLM totalTokens beyond the documented console bucket granularity.
- INVALID: preflight artifact freshness is not proven, providerProbeStatus is not passed, DUAL_GLM_BASELINE_TOKENS was not captured immediately before this run, post-run GLM console total is not captured, the executed provider is not GLM-5.2 through Zhipu, required metrics/run.json/trace artifacts are missing, or this gate does not match the executed case.
- Stop-loss: Stop on provider preflight failure, start/autorun failure, missing providerModelIdentity after the first completed GLM run, or GLM console token delta exceeding 3,000,000 before acceptance; do not launch a second benchmark automatically.
- Run artifact freshness: run-real-case.sh builds dual-cli and dual-server before starting, then writes preflight.json proving the executed jar is fresh and recording provider probe evidence.
EOF
    return
  fi
  cat >"$VERIFICATION_GATE" <<EOF
# Verification Gate: $CASE_ID

- Target: Run the public benchmark case $CASE_ID through the full dual lifecycle with real acpx agents.
- Source of truth: Generated report summary.json, workstream ledger, run.json, workstream-metrics.json, and dualctl trace for the created workstream.
- Trigger precondition: Disposable benchmark repo exists, task file exists, acpx is available, run-real-case.sh builds fresh artifacts, and provider connectivity probe passes before workflow execution.
- How precondition is checked before run: prepare-public-benchmark-repo.sh produced $DEST_DIR; run-real-case.sh validates repo, task file, acpx, variant, prompt language, this gate, preflight artifact freshness, and providerProbeStatus=passed before starting a workstream.
- PASS: Workflow reaches COMPLETED, original repo stays clean, key artifacts exist, final acceptance is ACCEPT or ACCEPT_WITH_NOTES, and trace/metrics can explain execution/token state.
- FAIL: Workflow does not complete, original repo is polluted, required report/handoff/delivery artifacts are missing, or acceptance rejects without expected repair evidence.
- INVALID: preflight artifact freshness is not proven, providerProbeStatus is not passed, provider/model/token attribution fields are missing for a token benchmark target, or this gate does not match the executed case.
- Stop-loss: The script stops on start/autorun failure and writes local evidence; do not launch a second benchmark automatically.
- Run artifact freshness: run-real-case.sh builds dual-cli and dual-server before starting, then writes preflight.json proving the executed jar is fresh and recording provider probe evidence.
EOF
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

  validate_validation_target
  public_benchmark_write_verification_gate
  args+=(--verification-gate "$VERIFICATION_GATE")

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

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
