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
#   ./scripts/run-real-case.sh --repo /path/to/repo --task-file /path/to/task.md --workflow /path/to/workflow.json
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
# shellcheck source=scripts/lib/real-case-workspace-preflight.sh
source "$PROJECT_ROOT/scripts/lib/real-case-workspace-preflight.sh"
# shellcheck source=scripts/lib/verification-gate.sh
source "$PROJECT_ROOT/scripts/lib/verification-gate.sh"

REPO=""
TASK_FILE=""
VERIFICATION_GATE=""
VARIANT="full"
VARIANT_EXPLICIT=false
WORKFLOW_PATH=""
WORKFLOW_EXPLICIT=false
WORKFLOW_SOURCE_SHA256=""
WORKFLOW_ID=""
WORKFLOW_VERSION=""
WORKFLOW_VALIDATION_STATUS="not_applicable"
WORKFLOW_VALIDATED_PATH=""
WORKFLOW_PATH_MATCHES_VALIDATED=false
PROMPT_LANGUAGE="auto"
MAX_STEPS=80
MAX_GATES=12
SKIP_BUILD=false
CLEANUP_WORKTREE=false
UPDATE_BENCHMARK_INDEX=false
WORKSPACE_PREFLIGHT_CHECK=""
WORKSPACE_PREFLIGHT_CHECK_SHA256=""
WORKSPACE_PREFLIGHT_CONFIGURED=false
WORKSPACE_PREFLIGHT_EVIDENCE=""
SOURCE_HEAD=""
SOURCE_TREE=""
SOURCE_TRACKED_STATUS_SHA256=""

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
EXPECTED_RUNNER_KIND=""
PROVIDER_PROBE_RUNNER_KIND=""
PROVIDER_PROBE_PATH_MATCHES_EXPECTED=false
AUTORUN_PID=""
BUSINESS_RUN_STARTED=false

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
  --variant <contract|full|terminal-delivery>
                            Workflow variant. Default: full.
  --workflow <path>         External workflow definition JSON. Mutually exclusive
                            with --variant; its path, SHA-256, id, and version are
                            validated and persisted before provider execution.
  --prompt-language <auto|en|zh-CN>
                            Human-readable Agent output language. Default: auto.
  --expected-runner-kind <acpx_claude_session|acpx_codex_session>
                            Actual first agent runner kind, derived by
                            dualctl runner-kind. Required so provider preflight
                            matches the workflow execution path.
  --workspace-preflight-check <executable-script>
                            Run this target-owned eligibility check inside the
                            persisted execution workspace before the first
                            business Agent execution.
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
  log "Interrupted. Quiescing the active provider execution before collecting evidence..."
  stop_autorun_process
  parse_started_workstream || true
  if [[ "$BUSINESS_RUN_STARTED" == true ]]; then
    if ! quiesce_interrupted_workstream; then
      FINAL_STATUS="INTERRUPTION_RECOVERY_FAILED"
      fail "Interrupted provider execution could not be closed to recovery."
    fi
  else
    log "Interrupted before the first business Agent execution; no recovery or session reap is required."
  fi
  collect_evidence || true
  write_report || true
  update_benchmark_index || true
  if [[ -n "$WORKSPACE_DIR" && -n "$WORKSTREAM_ID" ]]; then
    log "Inspect: java -jar \"$JAR_PATH\" status --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
    log "Resume:  java -jar \"$JAR_PATH\" resume --workdir \"$WORKSPACE_DIR\" \"$WORKSTREAM_ID\""
  fi
  exit 130
}

stop_autorun_process() {
  [[ -n "$AUTORUN_PID" ]] || return 0
  if kill -0 "$AUTORUN_PID" >/dev/null 2>&1; then
    kill "$AUTORUN_PID" >/dev/null 2>&1 || true
    for _ in 1 2 3 4 5 6 7 8 9 10; do
      kill -0 "$AUTORUN_PID" >/dev/null 2>&1 || break
      sleep 0.1
    done
    if kill -0 "$AUTORUN_PID" >/dev/null 2>&1; then
      kill -9 "$AUTORUN_PID" >/dev/null 2>&1 || true
    fi
  fi
  wait "$AUTORUN_PID" >/dev/null 2>&1 || true
  AUTORUN_PID=""
}

quiesce_interrupted_workstream() {
  [[ -n "$WORKSPACE_DIR" && -n "$WORKSTREAM_ID" ]] || {
    fail "Cannot close interrupted provider execution without a durable workspace and workstream id."
    return 1
  }
  local recovery_failed=false
  local session_failed=false
  dualctl recovery stale \
    --workdir "$WORKSPACE_DIR" \
    --terminate-process \
    --note "real-case runner interrupted by stop-loss" \
    "$WORKSTREAM_ID" >"$CASE_DIR/logs/interruption-recovery.log" 2>&1 || recovery_failed=true
  quiesce_workstream_sessions "interruption" || session_failed=true
  [[ "$recovery_failed" == false && "$session_failed" == false ]]
}

quiesce_workstream_sessions() {
  local reason="$1"
  [[ -n "$WORKSPACE_DIR" && -n "$WORKSTREAM_ID" ]] || {
    fail "Cannot close provider sessions without a durable workspace and workstream id."
    return 1
  }
  dualctl sessions-reap \
    --workdir "$WORKSPACE_DIR" \
    --workstream "$WORKSTREAM_ID" \
    >"$CASE_DIR/logs/session-quiescence-${reason}.log" 2>&1
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
        VARIANT_EXPLICIT=true
        ;;
      --workflow)
        shift
        WORKFLOW_PATH="${1:-}"
        WORKFLOW_EXPLICIT=true
        ;;
      --prompt-language)
        shift
        PROMPT_LANGUAGE="${1:-}"
        ;;
      --expected-runner-kind)
        shift
        EXPECTED_RUNNER_KIND="${1:-}"
        ;;
      --workspace-preflight-check)
        shift
        WORKSPACE_PREFLIGHT_CHECK="${1:-}"
        WORKSPACE_PREFLIGHT_CONFIGURED=true
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

  validate_workflow_selection || exit 1

  [[ -d "$REPO" ]] || { fail "repo not found: $REPO"; exit 1; }
  [[ -f "$TASK_FILE" ]] || { fail "task file not found: $TASK_FILE"; exit 1; }
  [[ -f "$VERIFICATION_GATE" ]] || { fail "verification gate not found: $VERIFICATION_GATE"; exit 1; }
  validate_verification_gate "$VERIFICATION_GATE"
  git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 || {
    fail "repo must be a git repository: $REPO"
    exit 1
  }
  REPO="$(git -C "$REPO" rev-parse --show-toplevel)"
  REPO="$(real_case_workspace_canonical_dir "$REPO")"
  if [[ "$WORKSPACE_PREFLIGHT_CONFIGURED" == true ]]; then
    [[ -n "$WORKSPACE_PREFLIGHT_CHECK" ]] || { fail "--workspace-preflight-check requires a path"; exit 1; }
    WORKSPACE_PREFLIGHT_CHECK="$(abs_path "$WORKSPACE_PREFLIGHT_CHECK")"
    [[ -f "$WORKSPACE_PREFLIGHT_CHECK" && -x "$WORKSPACE_PREFLIGHT_CHECK" ]] || {
      fail "workspace preflight check must be an executable file: $WORKSPACE_PREFLIGHT_CHECK"
      exit 1
    }
    WORKSPACE_PREFLIGHT_CHECK="$(cd "$(dirname "$WORKSPACE_PREFLIGHT_CHECK")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "$WORKSPACE_PREFLIGHT_CHECK")")"
    WORKSPACE_PREFLIGHT_CHECK_SHA256="$(real_case_workspace_sha256_file "$WORKSPACE_PREFLIGHT_CHECK")"
  fi
  command -v acpx >/dev/null 2>&1 || {
    fail "acpx not found. Initialize acpx sessions before running a real case."
    exit 1
  }
  command -v jq >/dev/null 2>&1 || {
    fail "jq not found. A structured JSON parser is required for completion-audit reporting."
    exit 1
  }
  case "$PROMPT_LANGUAGE" in
    auto|en|zh-CN|zh|zh-cn) ;;
    *) fail "--prompt-language must be auto, en, or zh-CN"; exit 1 ;;
  esac
  case "$EXPECTED_RUNNER_KIND" in
    acpx_claude_session|acpx_codex_session) ;;
    *)
      fail "--expected-runner-kind is required for the selected workflow. Got: '${EXPECTED_RUNNER_KIND:-<empty>}'."
      fail "Provider preflight must match the workflow's acpx runner path."
      exit 1
      ;;
  esac
}

validate_workflow_selection() {
  if [[ "$WORKFLOW_EXPLICIT" == true && "$VARIANT_EXPLICIT" == true ]]; then
    fail "--workflow and --variant are mutually exclusive"
    return 1
  fi
  if [[ "$WORKFLOW_EXPLICIT" == true ]]; then
    [[ -n "$WORKFLOW_PATH" ]] || { fail "--workflow requires a path"; return 1; }
    WORKFLOW_PATH="$(abs_path "$WORKFLOW_PATH")"
    [[ -f "$WORKFLOW_PATH" ]] || { fail "workflow definition not found: $WORKFLOW_PATH"; return 1; }
    WORKFLOW_PATH="$(cd "$(dirname "$WORKFLOW_PATH")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "$WORKFLOW_PATH")")"
    VARIANT=""
    return 0
  fi

  case "$VARIANT" in
    contract|full|terminal-delivery) ;;
    *) fail "--variant must be contract, full, or terminal-delivery"; return 1 ;;
  esac
}

validate_external_workflow() {
  [[ -n "$WORKFLOW_PATH" ]] || return 0
  local validation_log="$CASE_DIR/logs/workflow-validate.log"
  WORKFLOW_SOURCE_SHA256="$(shasum -a 256 "$WORKFLOW_PATH" | awk '{print $1}')"
  if ! dualctl workflow validate "$WORKFLOW_PATH" >"$validation_log" 2>&1; then
    WORKFLOW_VALIDATION_STATUS="failed"
    return 1
  fi
  WORKFLOW_VALIDATED_PATH="$(awk -F': ' '/^Path: / {value=$2} END {print value}' "$validation_log")"
  local workflow_ref
  workflow_ref="$(awk -F': ' '/^Workflow: / {value=$2} END {print value}' "$validation_log")"
  WORKFLOW_ID="${workflow_ref%@*}"
  WORKFLOW_VERSION="${workflow_ref##*@}"
  if [[ -z "$WORKFLOW_VALIDATED_PATH" || "$WORKFLOW_VALIDATED_PATH" != "$WORKFLOW_PATH"
        || -z "$WORKFLOW_ID" || "$WORKFLOW_ID" == "$workflow_ref"
        || -z "$WORKFLOW_VERSION" ]]; then
    WORKFLOW_VALIDATION_STATUS="failed"
    return 1
  fi
  WORKFLOW_PATH_MATCHES_VALIDATED=true
  WORKFLOW_VALIDATION_STATUS="passed"
}

validate_verification_gate() {
  local file="$1"
  local missing weak
  missing="$(verification_gate_missing_fields "$file")"
  if [[ -n "$missing" ]]; then
    fail "verification gate is incomplete: ${missing//|/ }"
    fail "Gate file: $file"
    exit 1
  fi
  weak="$(verification_gate_tradeoff_flags "$file")"
  if [[ -n "$weak" ]]; then
    fail "verification gate has weak proof fields: ${weak//|/ }"
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

freeze_source_identity() {
  SOURCE_HEAD="$(git -C "$REPO" rev-parse HEAD)"
  SOURCE_TREE="$(git -C "$REPO" rev-parse 'HEAD^{tree}')"
  SOURCE_TRACKED_STATUS_SHA256="$(real_case_workspace_sha256_text \
    "$(git -C "$REPO" status --porcelain=v1 --untracked-files=no)")"
}

run_provider_probe() {
  PROVIDER_PROBE_RESULT_PATH="$CASE_DIR/provider-probe-result.json"
  PROVIDER_PROBE_STDERR_PATH="$CASE_DIR/provider-probe-stderr.log"
  local prompt_path="$CASE_DIR/provider-probe-prompt.txt"

  PROVIDER_PROBE_RUNNER_KIND="$EXPECTED_RUNNER_KIND"
  PROVIDER_PROBE_PATH_MATCHES_EXPECTED=true

  if [[ "$PROVIDER_PROBE_ENABLED" == "0" || "$PROVIDER_PROBE_ENABLED" == "false" ]]; then
    PROVIDER_PROBE_STATUS="skipped"
    PROVIDER_PROBE_REASON="$(real_case_provider_probe_reason "$PROVIDER_PROBE_STATUS")"
    return 0
  fi

  printf 'Provider connectivity probe. Reply exactly: OK. Do not use tools.\n' >"$prompt_path"
  local agent session_name
  case "$EXPECTED_RUNNER_KIND" in
    acpx_claude_session)
      agent="claude"
      session_name="dual-preflight-claude-$$"
      ;;
    acpx_codex_session)
      agent="codex"
      session_name="dual-preflight-codex-$$"
      ;;
    *)
      PROVIDER_PROBE_STATUS="failed"
      PROVIDER_PROBE_REASON="runner_probe_not_implemented_for_${EXPECTED_RUNNER_KIND}"
      PROVIDER_PROBE_EXIT_CODE=1
      return 1
      ;;
  esac

  local -a session_command=(
    acpx
    "$agent"
    "sessions"
    "new"
    "--name"
    "$session_name"
  )

  if ! "${session_command[@]}" >>"$PROVIDER_PROBE_STDERR_PATH" 2>&1; then
    PROVIDER_PROBE_EXIT_CODE=1
    PROVIDER_PROBE_STATUS="failed"
    PROVIDER_PROBE_REASON="$(real_case_provider_probe_reason "$PROVIDER_PROBE_STATUS")"
    return 1
  fi

  local -a command=(
    acpx
    --timeout
    "$PROVIDER_PROBE_TIMEOUT_SECONDS"
    --ttl
    "300"
    --deny-all
    --non-interactive-permissions
    "fail"
    "$agent"
    -s
    "$session_name"
    -f
    "$prompt_path"
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
      acpx "$agent" sessions close "$session_name" >>"$PROVIDER_PROBE_STDERR_PATH" 2>&1 || true
      return 1
    fi
    sleep 1
  done
  wait "$pid" || PROVIDER_PROBE_EXIT_CODE="$?"
  PROVIDER_PROBE_EXIT_CODE="${PROVIDER_PROBE_EXIT_CODE:-0}"
  PROVIDER_PROBE_STATUS="$(real_case_acpx_provider_probe_status "$PROVIDER_PROBE_EXIT_CODE" "$PROVIDER_PROBE_RESULT_PATH")"
  PROVIDER_PROBE_REASON="$(real_case_provider_probe_reason "$PROVIDER_PROBE_STATUS")"
  acpx "$agent" sessions close "$session_name" >>"$PROVIDER_PROBE_STDERR_PATH" 2>&1 || true
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
    echo "  \"workflowSelection\": \"$(if [[ -n "$WORKFLOW_PATH" ]]; then printf external; else printf variant; fi)\","
    echo "  \"workflowPath\": \"$(json_escape "$WORKFLOW_PATH")\","
    echo "  \"workflowSourceSha256\": \"$(json_escape "$WORKFLOW_SOURCE_SHA256")\","
    echo "  \"workflowId\": \"$(json_escape "$WORKFLOW_ID")\","
    echo "  \"workflowVersion\": \"$(json_escape "$WORKFLOW_VERSION")\","
    echo "  \"workflowValidationStatus\": \"$(json_escape "$WORKFLOW_VALIDATION_STATUS")\","
    echo "  \"workflowValidatedPath\": \"$(json_escape "$WORKFLOW_VALIDATED_PATH")\","
    echo "  \"workflowPathMatchesValidated\": $WORKFLOW_PATH_MATCHES_VALIDATED,"
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
    echo "  \"providerProbeStderrPath\": \"$(json_escape "$PROVIDER_PROBE_STDERR_PATH")\","
    echo "  \"expectedRunnerKind\": \"$(json_escape "$EXPECTED_RUNNER_KIND")\","
    echo "  \"providerProbeRunnerKind\": \"$(json_escape "$PROVIDER_PROBE_RUNNER_KIND")\","
    echo "  \"providerProbePathMatchesExpected\": $PROVIDER_PROBE_PATH_MATCHES_EXPECTED,"
    echo "  \"sourceHead\": \"$(json_escape "$SOURCE_HEAD")\","
    echo "  \"sourceTree\": \"$(json_escape "$SOURCE_TREE")\","
    echo "  \"sourceTrackedStatusSha256\": \"$(json_escape "$SOURCE_TRACKED_STATUS_SHA256")\","
    echo "  \"workspacePreflightConfigured\": $WORKSPACE_PREFLIGHT_CONFIGURED,"
    echo "  \"workspacePreflightCheckPath\": \"$(json_escape "$WORKSPACE_PREFLIGHT_CHECK")\","
    echo "  \"workspacePreflightCheckSha256\": \"$(json_escape "$WORKSPACE_PREFLIGHT_CHECK_SHA256")\""
    echo "}"
  } >"$preflight"

  if [[ -n "$WORKFLOW_PATH" && "$WORKFLOW_VALIDATION_STATUS" != "passed" ]]; then
    fail "external workflow validation is not proven: $WORKFLOW_VALIDATION_STATUS"
    fail "Preflight evidence: $preflight"
    FINAL_STATUS="INVALID_WORKFLOW_PREFLIGHT"
    write_report
    update_benchmark_index
    exit 1
  fi

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

run_execution_preflight() {
  freeze_source_identity
  if ! validate_external_workflow; then
    write_preflight_evidence
    return 1
  fi
  run_provider_probe || true
  write_preflight_evidence
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
  local -a args=(start --workdir "$REPO" --max-steps "$MAX_STEPS" --prompt-language "$PROMPT_LANGUAGE")
  if [[ -n "$WORKFLOW_PATH" ]]; then
    args+=(--workflow "$WORKFLOW_PATH")
  elif [[ "$VARIANT" != "full" ]]; then
    args+=(--workflow-variant "$VARIANT")
  fi
  args+=(--task-file "$TASK_FILE")

  if ! dualctl "${args[@]}" >"$CASE_DIR/logs/start.log" 2>&1; then
    cat "$CASE_DIR/logs/start.log"
    fail "dualctl start failed"
    FINAL_STATUS="START_FAILED"
    parse_started_workstream || true
    collect_evidence || true
    status_snapshot "final" || true
    write_report
    update_benchmark_index
    exit 1
  fi

  if ! parse_started_workstream; then
    cat "$CASE_DIR/logs/start.log"
    fail "persisted execution workspace identity could not be resolved"
    FINAL_STATUS="INVALID_EXECUTION_WORKSPACE_IDENTITY"
    collect_evidence || true
    status_snapshot "final" || true
    write_report
    update_benchmark_index
    exit 1
  fi

  WS_DIR="$WORKSPACE_DIR/.agent/workstreams/$WORKSTREAM_ID"
  log "Workstream: $WORKSTREAM_ID"
  log "Workspace:  $WORKSPACE_DIR"
  log "Ledger:     $WS_DIR"
  print_step_lines "$(cat "$CASE_DIR/logs/start.log")"
}

parse_started_workstream() {
  [[ -f "$CASE_DIR/logs/start.log" ]] || return 1
  WORKSTREAM_ID="$(awk '/^Workstream:/ { value = $2 } END { print value }' "$CASE_DIR/logs/start.log")"
  [[ -n "$WORKSTREAM_ID" ]] || return 1
  local candidate
  candidate="$(dirname "$REPO")/$(basename "$REPO")-agent-runs/$WORKSTREAM_ID"
  real_case_workspace_resolve_identity "$REPO" "$candidate" "$WORKSTREAM_ID" || return 1
  WORKSPACE_DIR="$REAL_CASE_RESOLVED_WORKSPACE"
  WS_DIR="$WORKSPACE_DIR/.agent/workstreams/$WORKSTREAM_ID"
}

write_workspace_preflight_not_configured() {
  WORKSPACE_PREFLIGHT_EVIDENCE="$CASE_DIR/workspace-preflight.json"
  jq -n \
    --arg workstreamId "$WORKSTREAM_ID" \
    --arg workspaceId "$REAL_CASE_RESOLVED_WORKSPACE_ID" \
    --arg effectiveWorkdir "$WORKSPACE_DIR" \
    '{
      schemaVersion: 1,
      status: "not_configured",
      reason: "workspace_preflight_check_not_configured",
      verificationStatus: "not_applicable",
      workstreamId: $workstreamId,
      workspaceId: $workspaceId,
      effectiveWorkdir: $effectiveWorkdir,
      terminalDigest: "",
      verifiedAt: ""
    }' >"$WORKSPACE_PREFLIGHT_EVIDENCE"
}

run_workspace_preflight() {
  WORKSPACE_PREFLIGHT_EVIDENCE="$CASE_DIR/workspace-preflight.json"
  if [[ "$WORKSPACE_PREFLIGHT_CONFIGURED" != true ]]; then
    write_workspace_preflight_not_configured
    return 0
  fi
  log "Running target eligibility check in persisted execution workspace..."
  if real_case_workspace_preflight_capture \
    "$REPO" "$WORKSPACE_DIR" "$WORKSTREAM_ID" "$SOURCE_HEAD" "$SOURCE_TREE" \
    "$WORKSPACE_PREFLIGHT_CHECK" "$WORKSPACE_PREFLIGHT_CHECK_SHA256" "$CASE_DIR"; then
    return 0
  fi
  FINAL_STATUS="INVALID_EXECUTION_WORKSPACE_PREFLIGHT"
  fail "execution workspace eligibility preflight failed"
  return 1
}

run_initial_segment() {
  if [[ "$WORKSPACE_PREFLIGHT_CONFIGURED" == true ]]; then
    if ! real_case_workspace_preflight_verify "$WORKSPACE_PREFLIGHT_EVIDENCE"; then
      FINAL_STATUS="INVALID_EXECUTION_WORKSPACE_PREFLIGHT"
      fail "execution workspace identity changed before the first run"
      return 1
    fi
  elif ! real_case_workspace_resolve_identity "$REPO" "$WORKSPACE_DIR" "$WORKSTREAM_ID"; then
    FINAL_STATUS="INVALID_EXECUTION_WORKSPACE_IDENTITY"
    fail "persisted execution workspace identity changed before the first run"
    return 1
  fi

  log "Running initial workflow segment..."
  local initial_exit=0
  BUSINESS_RUN_STARTED=true
  dualctl run \
    --workdir "$WORKSPACE_DIR" \
    --max-steps "$MAX_STEPS" \
    "$WORKSTREAM_ID" >"$CASE_DIR/logs/initial-run.log" 2>&1 || initial_exit="$?"
  if [[ "$initial_exit" -eq 0 ]]; then
    print_step_lines "$(cat "$CASE_DIR/logs/initial-run.log")"
    return 0
  fi

  cat "$CASE_DIR/logs/initial-run.log"
  local status_output
  status_output="$(current_status_text || true)"
  printf '%s\n' "$status_output" >"$CASE_DIR/logs/status-after-initial-run-failure.log"
  FINAL_STATUS="$(workflow_status "$status_output")"
  [[ -n "$FINAL_STATUS" ]] || FINAL_STATUS="INITIAL_RUN_FAILED"
  if ! quiesce_workstream_sessions "initial-run-stop-loss"; then
    FINAL_STATUS="SESSION_QUIESCENCE_FAILED"
    fail "Stopped workflow provider sessions could not be closed."
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
  dualctl autorun \
    --workdir "$WORKSPACE_DIR" \
    --max-steps "$MAX_STEPS" \
    --max-gates "$MAX_GATES" \
    "$WORKSTREAM_ID" >"$CASE_DIR/logs/autorun.log" 2>&1 &
  AUTORUN_PID="$!"
  local autorun_exit=0
  wait "$AUTORUN_PID" || autorun_exit="$?"
  AUTORUN_PID=""
  if [[ "$autorun_exit" -eq 0 ]]; then
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
  if ! quiesce_workstream_sessions "autorun-stop-loss"; then
    FINAL_STATUS="SESSION_QUIESCENCE_FAILED"
    fail "Stopped workflow provider sessions could not be closed."
  fi
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

workspace_preflight_json() {
  local evidence="${WORKSPACE_PREFLIGHT_EVIDENCE:-$CASE_DIR/workspace-preflight.json}"
  if [[ -f "$evidence" ]]; then
    cat "$evidence"
  else
    printf '{}'
  fi
}

workspace_eligibility_status() {
  local evidence="${WORKSPACE_PREFLIGHT_EVIDENCE:-$CASE_DIR/workspace-preflight.json}"
  [[ -f "$evidence" ]] || { printf 'missing'; return; }
  jq -r '.status // "invalid_evidence"' "$evidence" 2>/dev/null || printf 'invalid_evidence'
}

workspace_eligibility_reason() {
  local evidence="${WORKSPACE_PREFLIGHT_EVIDENCE:-$CASE_DIR/workspace-preflight.json}"
  [[ -f "$evidence" ]] || { printf 'workspace_preflight_evidence_missing'; return; }
  jq -r '.reason // "workspace_preflight_evidence_invalid"' "$evidence" 2>/dev/null || printf 'workspace_preflight_evidence_invalid'
}

workspace_eligibility_proven() {
  local evidence="${WORKSPACE_PREFLIGHT_EVIDENCE:-$CASE_DIR/workspace-preflight.json}"
  if [[ -f "$evidence" ]] && jq -e '.status == "passed" and .verificationStatus == "passed"' "$evidence" >/dev/null 2>&1; then
    printf 'true'
  else
    printf 'false'
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
  local audit="$WS_DIR/domain/completion-audit.json"
  [[ -f "$audit" ]] || {
    printf 'completion_audit_missing'
    return
  }

  if jq -e '
    .schemaVersion == 1 and
    .lifecycleState == "ENDED" and
    .status == "ready" and
    .workflowStatus == "COMPLETED" and
    (.gaps | type == "array" and length == 0) and
    all(.gaps[]; type == "string")
  ' "$audit" >/dev/null 2>&1; then
    printf ''
  else
    printf 'completion_audit_not_ready_or_invalid'
  fi
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
  local workspace_evidence="${WORKSPACE_PREFLIGHT_EVIDENCE:-$CASE_DIR/workspace-preflight.json}"
  if [[ -f "$workspace_evidence" ]]; then
    local workspace_status
    workspace_status="$(workspace_eligibility_status)"
    if [[ "$workspace_status" == "failed" || "$workspace_status" == "invalid_evidence" ]]; then
      printf 'workspace_preflight_%s' "$(workspace_eligibility_reason)"
      return
    fi
  elif [[ "$FINAL_STATUS" == INVALID_EXECUTION_WORKSPACE_* ]]; then
    printf 'workspace_preflight_evidence_missing'
    return
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
    echo "  \"workspace_eligibility_status\": \"$(json_escape "$(workspace_eligibility_status)")\","
    echo "  \"workspace_eligibility_reason\": \"$(json_escape "$(workspace_eligibility_reason)")\","
    echo "  \"workspace_eligibility_proven\": $(workspace_eligibility_proven),"
    echo "  \"workspace_preflight_evidence\": \"$(json_escape "${WORKSPACE_PREFLIGHT_EVIDENCE:-$CASE_DIR/workspace-preflight.json}")\","
    echo "  \"workspace_preflight\": $(workspace_preflight_json),"
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

completion_artifact_status() {
  local ref="$1"
  if [[ -f "$WS_DIR/$ref" ]]; then
    printf 'present'
  elif [[ -z "$(missing_artifacts)" ]]; then
    printf 'not_applicable'
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
    echo "- Variant: ${VARIANT:-external}"
    echo "- Workflow Path: ${WORKFLOW_PATH:-bundled}"
    echo "- Workflow Source SHA-256: ${WORKFLOW_SOURCE_SHA256:-not_applicable}"
    echo "- Workflow: ${WORKFLOW_ID:-bundled}@${WORKFLOW_VERSION:-unknown}"
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
    echo "## Execution Workspace Eligibility"
    echo
    echo "- Status: $(workspace_eligibility_status)"
    echo "- Reason: $(workspace_eligibility_reason)"
    echo "- Proven: $(workspace_eligibility_proven)"
    echo "- Evidence: ${WORKSPACE_PREFLIGHT_EVIDENCE:-$CASE_DIR/workspace-preflight.json}"
    echo
    echo '```json'
    workspace_preflight_json
    echo
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
      echo "| Handoff JSON | $(completion_artifact_status "artifacts/next-stage/handoff.json") | $handoff_json |"
      echo "| Next-stage Prompt | $(completion_artifact_status "artifacts/next-stage/next-stage-prompt.md") | $next_prompt |"
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
  run_execution_preflight
  trap handle_interrupt INT TERM
  start_workstream
  status_snapshot "after-start" || true

  if ! run_workspace_preflight; then
    collect_evidence
    status_snapshot "final" || true
    write_report
    update_benchmark_index
    cleanup_worktree
    exit 1
  fi

  if ! run_initial_segment; then
    collect_evidence
    status_snapshot "final" || true
    write_report
    update_benchmark_index
    cleanup_worktree
    exit 1
  fi

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

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
