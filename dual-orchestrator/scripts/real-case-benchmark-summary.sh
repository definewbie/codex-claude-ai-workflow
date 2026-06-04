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

usage() {
  cat <<'EOF'
Usage:
  real-case-benchmark-summary.sh [options]

Options:
  --reports-dir <path>  Directory containing */summary.json files.
  --output <path>       Markdown file to write. Default: docs/testing/real-case-benchmarks/benchmark-index.md
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
  find "$REPORTS_DIR" -mindepth 2 -maxdepth 2 -name summary.json -type f | sort
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
  missing="$(jq -r '
    .artifacts // {}
    | to_entries
    | map(select(.value != true) | .key)
    | join(",")
  ' "$file")"
  if [[ -z "$missing" ]]; then
    printf 'ok'
  else
    printf 'missing: %s' "$missing"
  fi
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

write_markdown() {
  local -a files=("$@")
  local total=${#files[@]}
  local completed=0
  local clean=0
  local reject_total=0
  local gate_total=0
  local agent_total=0
  local generated_at
  generated_at="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"

  for file in "${files[@]}"; do
    [[ "$(json_value "$file" '.final_status')" == "COMPLETED" ]] && completed=$((completed + 1))
    [[ "$(json_value "$file" '.original_repo_clean')" == "true" ]] && clean=$((clean + 1))
    reject_total=$((reject_total + $(json_int "$file" '.acceptance_reject_count')))
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
| Completed | $completed |
| Original repo clean | $clean |
| Acceptance rejects | $reject_total |
| Gate rounds | $gate_total |
| Agent runs | $agent_total |

## Runs

| Date | Case | Variant | Status | Workstream | Gates | Agent runs | Acceptance decisions | Rejects | Repo clean | Artifacts | Report |
| --- | --- | --- | --- | --- | ---: | ---: | --- | ---: | --- | --- | --- |
EOF

  for file in "${files[@]}"; do
    local run_dir report_ref date case variant status workstream gates agent_runs decisions rejects repo_clean artifacts
    run_dir="$(dirname "$file")"
    report_ref="$(relative_ref "$run_dir/report.md")"
    date="$(basename "$run_dir")"
    case="$(case_name "$file")"
    variant="$(json_value "$file" '.variant')"
    status="$(json_value "$file" '.final_status')"
    workstream="$(json_value "$file" '.workstream')"
    gates="$(json_int "$file" '.gate_rounds')"
    agent_runs="$(json_int "$file" '.agent_run_count')"
    decisions="$(jq -r '(.acceptance_decisions // []) | join(", ")' "$file")"
    rejects="$(json_int "$file" '.acceptance_reject_count')"
    repo_clean="$(json_value "$file" '.original_repo_clean')"
    artifacts="$(artifact_health "$file")"
    printf '| %s | `%s` | %s | %s | `%s` | %s | %s | %s | %s | %s | %s | [%s](../real-case-reports/%s/report.md) |\n' \
      "$date" "$case" "$variant" "$status" "$workstream" "$gates" "$agent_runs" \
      "${decisions:-none}" "$rejects" "$repo_clean" "$artifacts" "$report_ref" "$date"
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
