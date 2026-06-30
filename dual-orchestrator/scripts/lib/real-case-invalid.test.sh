#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# shellcheck source=scripts/lib/real-case-invalid.sh
source "$ROOT/scripts/lib/real-case-invalid.sh"

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

mkdir -p "$tmp/runs/run-1" "$tmp/runs/run-2" "$tmp/runs/run-3"
cat >"$tmp/runs/run-1/run.json" <<'JSON'
{
  "runId": "run-1",
  "failureReason": "RECOVERABLE_PROVIDER_CONNECTION_FAILED"
}
JSON

assert_eq "provider_connection_failed" \
  "$(real_case_invalid_reason_from_runs_dir "$tmp/runs")" \
  "failureReason provider connection"

cat >"$tmp/runs/run-1/run.json" <<'JSON'
{
  "runId": "run-1",
  "message": "Unable to connect to API (FailedToOpenSocket)"
}
JSON
cat >"$tmp/runs/run-2/run.json" <<'JSON'
{
  "runId": "run-2",
  "adapterSignals": [
    {"type": "PROVIDER_CONNECTION_FAILED"}
  ]
}
JSON

assert_eq "provider_connection_failed" \
  "$(real_case_invalid_reason_from_runs_dir "$tmp/runs")" \
  "adapter signal provider connection"

cat >"$tmp/runs/run-2/run.json" <<'JSON'
{
  "runId": "run-2",
  "executionAttempt": {
    "failureSignal": "AGENT_OVERLOADED"
  }
}
JSON

assert_eq "provider_overloaded" \
  "$(real_case_invalid_reason_from_runs_dir "$tmp/runs")" \
  "execution attempt agent overloaded"

cat >"$tmp/runs/run-2/run.json" <<'JSON'
{
  "runId": "run-2",
  "message": "Unable to connect to API (FailedToOpenSocket)"
}
JSON
cat >"$tmp/runs/run-3/run.json" <<'JSON'
{
  "runId": "run-3",
  "message": "API Error: 529 [model overloaded]"
}
JSON

assert_eq "" \
  "$(real_case_invalid_reason_from_runs_dir "$tmp/runs")" \
  "free-form provider text is not a typed invalid reason"
