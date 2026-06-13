#!/usr/bin/env bash
#
# real-case-benchmark-summary.sh — Build a markdown index from real-case summaries.
#
# This script does not run agents. It scans docs/testing/real-case-reports/*/summary.json
# and writes a compact benchmark index for comparing real lifecycle runs over time.
#
# Usage:
#   ./scripts/real-case-benchmark-summary.sh
#   ./scripts/real-case-benchmark-summary.sh --output /tmp/benchmark-index.md
#   ./scripts/real-case-benchmark-summary.sh --reports-dir docs/testing/real-case-reports
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORTS_DIR="$PROJECT_ROOT/docs/testing/real-case-reports"
OUTPUT="$PROJECT_ROOT/docs/testing/real-case-benchmarks/benchmark-index.md"
INCLUDE_PRIVATE=false

# shellcheck source=scripts/lib/real-case-grade.sh
source "$PROJECT_ROOT/scripts/lib/real-case-grade.sh"

usage() {
  cat <<'EOF'
Usage:
  real-case-benchmark-summary.sh [options]

Options:
  --reports-dir <path>  Directory containing */summary.json files.
  --output <path>       Markdown file to write. Default: docs/testing/real-case-benchmarks/benchmark-index.md
  --include-private     Include non-public local/private real-case summaries.
  --stdout              Print to stdout instead of writing a file.
  -h, --help            Show this help.
EOF
}

fail() {
  printf '[BENCHMARK][FAIL] %s\n' "$*" >&2
}

require_jq() {
  command -v jq >/dev/null 2>&1 || {
    fail "jq is required to aggregate benchmark summaries."
    fail "Install jq, then rerun this script."
    exit 1
  }
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
  local stdout=false
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --reports-dir)
        shift
        REPORTS_DIR="$(abs_path "${1:-}")"
        ;;
      --output)
        shift
        OUTPUT="$(abs_path "${1:-}")"
        ;;
      --stdout)
        stdout=true
        ;;
      --include-private)
        INCLUDE_PRIVATE=true
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
  if [[ "$stdout" == true ]]; then
    OUTPUT=""
  fi
}

json_files() {
  while IFS= read -r file; do
    if [[ "$INCLUDE_PRIVATE" == true ]] || is_public_summary "$file"; then
      printf '%s\n' "$file"
    fi
  done < <(find "$REPORTS_DIR" -mindepth 2 -maxdepth 2 -name summary.json -type f | sort)
}

is_public_summary() {
  local file="$1"
  local task_file
  task_file="$(jq -r '.task_file // ""' "$file")"
  [[ "$(basename "$task_file")" == public-* ]]
}

json_value() {
  local file="$1"
  local query="$2"
  jq -r "$query // \"\"" "$file"
}

json_int() {
  local file="$1"
  local query="$2"
  local value
  value="$(jq -r "$query // 0" "$file")"
  [[ "$value" =~ ^[0-9]+$ ]] || value=0
  printf '%s\n' "$value"
}

json_has_key() {
  local file="$1"
  local key="$2"
  jq -e --arg key "$key" 'has($key)' "$file" >/dev/null 2>&1
}

repair_loop_count() {
  local file="$1"
  if json_has_key "$file" "repair_loop_count"; then
    json_int "$file" '.repair_loop_count'
  else
    json_int "$file" '.acceptance_reject_count'
  fi
}

relative_ref() {
  local path="$1"
  if [[ "$path" == "$PROJECT_ROOT/"* ]]; then
    printf '%s\n' "${path#"$PROJECT_ROOT/"}"
  else
    printf '%s\n' "$path"
  fi
}

artifact_health() {
  local file="$1"
  local missing
  missing="$(missing_artifacts "$file")"
  if [[ -z "$missing" ]]; then
    printf 'ok'
  else
    printf 'missing: %s' "$missing"
  fi
}

missing_artifacts() {
  local file="$1"
  jq -r '
    .artifacts // {}
    | to_entries
    | map(select(.value != true) | .key)
    | join(",")
  ' "$file"
}

case_name() {
  local file="$1"
  local task_file name
  task_file="$(json_value "$file" '.task_file')"
  if [[ -z "$task_file" ]]; then
    printf 'unknown'
    return
  fi
  name="$(basename "$task_file")"
  printf '%s' "${name%.*}"
}

benchmark_grade() {
  local file="$1"
  local status repo_clean missing_artifacts rejects decisions
  status="$(json_value "$file" '.final_status')"
  repo_clean="$(json_value "$file" '.original_repo_clean')"
  missing_artifacts="$(missing_artifacts "$file")"
  rejects="$(json_int "$file" '.acceptance_reject_count')"
  decisions="$(jq -r '(.acceptance_decisions // []) | join(", ")' "$file")"
  real_case_grade "$status" "$repo_clean" "$missing_artifacts" "$decisions" "$rejects"
}

benchmark_grade_reason() {
  local file="$1"
  local status repo_clean missing_artifacts rejects decisions
  status="$(json_value "$file" '.final_status')"
  repo_clean="$(json_value "$file" '.original_repo_clean')"
  missing_artifacts="$(missing_artifacts "$file")"
  rejects="$(json_int "$file" '.acceptance_reject_count')"
  decisions="$(jq -r '(.acceptance_decisions // []) | join(", ")' "$file")"
  real_case_grade_reason "$status" "$repo_clean" "$missing_artifacts" "$decisions" "$rejects"
}

confidence_from_decision() {
  local final_decision="$1"
  case "$final_decision" in
    ACCEPT) printf 'HIGH' ;;
    ACCEPT_WITH_NOTES) printf 'MEDIUM' ;;
    REJECT) printf 'LOW' ;;
    *) printf 'UNKNOWN' ;;
  esac
}

write_markdown() {
  local -a files=("$@")
  local total=${#files[@]}
  local completed=0
  local pass=0
  local pass_warn=0
  local fail_count=0
  local clean=0
  local reject_total=0
  local repair_total=0
  local gate_total=0
  local agent_total=0
  local generated_at
  generated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

  for file in "${files[@]}"; do
    local grade
    grade="$(benchmark_grade "$file")"
    case "$grade" in
      PASS) pass=$((pass + 1)) ;;
      PASS-WARN) pass_warn=$((pass_warn + 1)) ;;
      FAIL) fail_count=$((fail_count + 1)) ;;
    esac
    [[ "$(json_value "$file" '.final_status')" == "COMPLETED" ]] && completed=$((completed + 1))
    [[ "$(json_value "$file" '.original_repo_clean')" == "true" ]] && clean=$((clean + 1))
    reject_total=$((reject_total + $(json_int "$file" '.acceptance_reject_count')))
    repair_total=$((repair_total + $(repair_loop_count "$file")))
    gate_total=$((gate_total + $(json_int "$file" '.gate_rounds')))
    agent_total=$((agent_total + $(json_int "$file" '.agent_run_count')))
  done

  cat <<EOF
# Real-case Benchmark Index

> Generated: $generated_at
> Source: $(relative_ref "$REPORTS_DIR")

## Aggregate

| Metric | Value |
| --- | --- |
| Runs | $total |
| PASS | $pass |
| PASS-WARN | $pass_warn |
| FAIL | $fail_count |
| Completed | $completed |
| Original repo clean | $clean |
| Acceptance rejects | $reject_total |
| Repair loops | $repair_total |
| Gate rounds | $gate_total |
| Agent runs | $agent_total |

## Runs

| Date | Case | Grade | Reason | Variant | Status | Workstream | Gates | Agent runs | Final acceptance | Repair loops | Confidence | Repo clean | Artifacts | Local report |
| --- | --- | --- | --- | --- | --- | --- | ---: | ---: | --- | ---: | --- | --- | --- | --- |
EOF

  for file in "${files[@]}"; do
    local run_dir date case variant status workstream gates agent_runs final_decision repair_loops confidence repo_clean artifacts grade reason
    run_dir="$(dirname "$file")"
    date="$(basename "$run_dir")"
    case="$(case_name "$file")"
    grade="$(benchmark_grade "$file")"
    reason="$(benchmark_grade_reason "$file")"
    variant="$(json_value "$file" '.variant')"
    status="$(json_value "$file" '.final_status')"
    workstream="$(json_value "$file" '.workstream')"
    gates="$(json_int "$file" '.gate_rounds')"
    agent_runs="$(json_int "$file" '.agent_run_count')"
    final_decision="$(json_value "$file" '.final_acceptance_decision')"
    if [[ -z "$final_decision" ]]; then
      final_decision="$(jq -r '(.acceptance_decisions // []) | last // "none"' "$file")"
    fi
    repair_loops="$(repair_loop_count "$file")"
    confidence="$(json_value "$file" '.verification_confidence')"
    [[ -n "$confidence" ]] || confidence="$(confidence_from_decision "$final_decision")"
    repo_clean="$(json_value "$file" '.original_repo_clean')"
    artifacts="$(artifact_health "$file")"
    printf '| %s | `%s` | %s | %s | %s | %s | `%s` | %s | %s | %s | %s | %s | %s | %s | ignored local file |\n' \
      "$date" "$case" "$grade" "$reason" "$variant" "$status" "$workstream" "$gates" "$agent_runs" \
      "${final_decision:-none}" "$repair_loops" "$confidence" "$repo_clean" "$artifacts"
  done

  cat <<'EOF'

## How To Update

After running a real case:

```bash
./scripts/real-case-benchmark-summary.sh
```

Use this index to compare lifecycle health over time. It is not a replacement for reading the detailed report when a case fails or returns `PASS-WARN`.
EOF
}

main() {
  parse_args "$@"
  require_jq
  [[ -d "$REPORTS_DIR" ]] || { fail "reports directory not found: $REPORTS_DIR"; exit 1; }

  local files=()
  while IFS= read -r file; do
    files+=("$file")
  done < <(json_files)
  [[ ${#files[@]} -gt 0 ]] || { fail "no summary.json files found under $REPORTS_DIR"; exit 1; }

  if [[ -z "$OUTPUT" ]]; then
    write_markdown "${files[@]}"
    return
  fi

  mkdir -p "$(dirname "$OUTPUT")"
  local tmp
  tmp="$(mktemp "${OUTPUT}.tmp.XXXXXX")"
  write_markdown "${files[@]}" >"$tmp"
  mv "$tmp" "$OUTPUT"
  printf '[BENCHMARK] Wrote %s\n' "$OUTPUT"
}

main "$@"
